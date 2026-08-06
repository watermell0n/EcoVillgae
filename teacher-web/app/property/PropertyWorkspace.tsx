"use client";

import { useCallback, useEffect, useMemo, useState, type CSSProperties } from "react";
import { Check, Clock3, Grid3X3, RefreshCw, X } from "lucide-react";
import styles from "./PropertyWorkspace.module.css";
import {
  cancelSubscriptionOffer,
  createSubscriptionOffer,
  decidePropertyContract,
  drawSubscriptionOffer,
  fetchPendingPropertyContracts,
  fetchPropertyLayout,
  fetchSubscriptionOffers,
  replacePropertyLayout,
  updateClassroomProperty,
  type ClassroomProperty,
  type PendingPropertyContract,
  type PropertyLayout,
  type SubscriptionOffer
} from "./api";

type Props = {
  classroomId: string;
  currencyName: string;
  realEstateEnabled: boolean;
  subscriptionEnabled: boolean;
  onNotice: (message: string, tone?: "success" | "warning" | "error") => void;
};

const emptyLayout: PropertyLayout = { row_count: 0, column_count: 0, state_version: 0, properties: [] };

function defaultCloseTime() {
  const date = new Date(Date.now() + 24 * 60 * 60 * 1000);
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}

function statusLabel(status: string) {
  return ({
    AVAILABLE: "직판 가능",
    TEACHER_OWNED: "비공개",
    STUDENT_OWNED: "학생 소유",
    LISTED_FOR_SALE: "재판매",
    LISTED_FOR_RENT: "임대",
    SUBSCRIPTION_OPEN: "청약 공급",
    RENTED: "사용 중",
    INACTIVE: "비활성"
  } as Record<string, string>)[status] ?? status;
}

export function PropertyWorkspace({ classroomId, currencyName, realEstateEnabled, subscriptionEnabled, onNotice }: Props) {
  const [layout, setLayout] = useState<PropertyLayout>(emptyLayout);
  const [contracts, setContracts] = useState<PendingPropertyContract[]>([]);
  const [offers, setOffers] = useState<SubscriptionOffer[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [offerSeats, setOfferSeats] = useState<Set<string>>(new Set());
  const [rows, setRows] = useState("5");
  const [columns, setColumns] = useState("5");
  const [salePrice, setSalePrice] = useState("100");
  const [displayName, setDisplayName] = useState("");
  const [isPublic, setIsPublic] = useState(true);
  const [offerTitle, setOfferTitle] = useState("우리 반 자리 청약");
  const [offerPrice, setOfferPrice] = useState("100");
  const [minimumCount, setMinimumCount] = useState("1");
  const [minimumBalance, setMinimumBalance] = useState("100");
  const [closesAt, setClosesAt] = useState(defaultCloseTime);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const selected = useMemo(
    () => layout.properties.find((property) => property.property_id === selectedId) ?? null,
    [layout.properties, selectedId]
  );

  const load = useCallback(async () => {
    if (!realEstateEnabled) return;
    setLoading(true);
    setError("");
    try {
      const [nextLayout, nextContracts, nextOffers] = await Promise.all([
        fetchPropertyLayout(classroomId),
        fetchPendingPropertyContracts(classroomId),
        subscriptionEnabled ? fetchSubscriptionOffers(classroomId) : Promise.resolve([])
      ]);
      setLayout(nextLayout);
      setContracts(nextContracts);
      setOffers(nextOffers);
      setRows(String(nextLayout.row_count || 5));
      setColumns(String(nextLayout.column_count || 5));
      setSelectedId((current) => nextLayout.properties.some((property) => property.property_id === current) ? current : null);
      setOfferSeats((current) => new Set([...current].filter((id) => nextLayout.properties.some((property) => property.property_id === id))));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "자리 정보를 불러오지 못했어요.");
    } finally {
      setLoading(false);
    }
  }, [classroomId, realEstateEnabled, subscriptionEnabled]);

  useEffect(() => { void load(); }, [load]);
  useEffect(() => {
    if (!selected) return;
    setSalePrice(String(selected.sale_price));
    setDisplayName(selected.display_name);
    setIsPublic(selected.is_public);
  }, [selected]);

  async function run(action: () => Promise<unknown>, success: string) {
    setBusy(true);
    try {
      await action();
      onNotice(success);
      await load();
    } catch (cause) {
      onNotice(cause instanceof Error ? cause.message : "요청을 처리하지 못했어요.", "error");
    } finally {
      setBusy(false);
    }
  }

  function chooseSeat(property: ClassroomProperty) {
    setSelectedId(property.property_id);
  }

  async function saveSelected() {
    if (!selected) return;
    const price = Number(salePrice);
    if (!Number.isInteger(price) || price < 0) return onNotice("직판 가격은 0 이상의 정수로 입력해 주세요.", "warning");
    await run(
      () => updateClassroomProperty(selected, { display_name: displayName.trim() || selected.coordinate, sale_price: price, is_public: isPublic }),
      `${selected.coordinate} 자리 설정을 저장했어요.`
    );
  }

  async function rebuildLayout() {
    const nextRows = Number(rows); const nextColumns = Number(columns);
    if (!Number.isInteger(nextRows) || !Number.isInteger(nextColumns) || nextRows < 1 || nextColumns < 1 || nextRows * nextColumns > 200) {
      return onNotice("행·열은 1 이상, 전체 자리는 200개 이하로 입력해 주세요.", "warning");
    }
    if (layout.properties.length && !window.confirm("현재 좌석판을 새 크기로 다시 만들까요? 진행 중인 계약이 있으면 서버가 안전하게 거절합니다.")) return;
    await run(() => replacePropertyLayout(classroomId, nextRows, nextColumns, layout.state_version), `${nextRows}×${nextColumns} 좌석판을 만들었어요.`);
  }

  function toggleOfferSeat(property: ClassroomProperty) {
    if (!(property.owner_kind === "TEACHER" && ["AVAILABLE", "TEACHER_OWNED"].includes(property.status))) return;
    setOfferSeats((current) => {
      const next = new Set(current);
      if (next.has(property.property_id)) next.delete(property.property_id); else next.add(property.property_id);
      return next;
    });
  }

  async function createOffer() {
    const price = Number(offerPrice); const count = Number(minimumCount); const balance = Number(minimumBalance);
    if (!offerSeats.size) return onNotice("청약으로 공급할 자리를 하나 이상 선택해 주세요.", "warning");
    if (![price, count, balance].every(Number.isInteger) || price < 1 || count < 0 || balance < 0 || !closesAt) return onNotice("청약 가격·조건·마감일을 확인해 주세요.", "warning");
    await run(() => createSubscriptionOffer(classroomId, {
      title: offerTitle.trim() || "자리 청약", closesAt, salePrice: price,
      minimumDepositCount: count, minimumBalance: balance, propertyIds: [...offerSeats]
    }), "청약 공고를 열었어요.");
    setOfferSeats(new Set());
  }

  if (!realEstateEnabled) {
    return <section className={styles.disabledPanel}><Grid3X3 size={34} /><h2>부동산 모듈이 꺼져 있어요</h2><p>학급 설정에서 부동산 모듈을 활성화하면 좌석판과 직판·재판매 기능이 열립니다. 청약만 단독으로 켜는 구성은 허용되지 않습니다.</p></section>;
  }

  return (
    <div className={styles.workspace}>
      <div className={styles.heading}>
        <div><p>교실 자리 자산</p><h2>부동산·청약 운영</h2><span>좌석판에서 직판 가격을 정하고, 구매 승인과 청약 공급을 한곳에서 관리합니다.</span></div>
        <button type="button" className={styles.secondary} onClick={() => void load()} disabled={loading}><RefreshCw size={16} /> 새로고침</button>
      </div>

      {error ? <div className={styles.error}>{error}</div> : null}

      <div className={styles.layoutControls}>
        <label>행<input inputMode="numeric" value={rows} onChange={(event) => setRows(event.target.value)} /></label>
        <label>열<input inputMode="numeric" value={columns} onChange={(event) => setColumns(event.target.value)} /></label>
        <button type="button" className={styles.primary} disabled={busy} onClick={() => void rebuildLayout()}>좌석판 만들기</button>
        <div className={styles.legend}><i data-tone="direct" />직판 <i data-tone="resale" />재판매 <i data-tone="subscription" />청약 <i data-tone="owned" />학생 소유</div>
      </div>

      {layout.properties.length ? (
        <div className={styles.mainGrid}>
          <section className={styles.mapPanel}>
            <div className={styles.seatStage} style={{ "--columns": layout.column_count } as CSSProperties}>
              <div className={styles.board}>칠판</div>
              <div className={styles.seatGrid}>
                {layout.properties.map((property) => (
                  <button
                    type="button"
                    key={property.property_id}
                    data-status={property.status}
                    data-selected={selectedId === property.property_id}
                    data-offer={offerSeats.has(property.property_id)}
                    className={styles.seat}
                    onClick={() => chooseSeat(property)}
                  >
                    <b>{property.coordinate}</b><span>{statusLabel(property.status)}</span>
                    {property.sale_price > 0 ? <small>{property.sale_price.toLocaleString("ko-KR")} {currencyName}</small> : null}
                  </button>
                ))}
              </div>
            </div>
          </section>

          <aside className={styles.inspector}>
            {selected ? <>
              <div className={styles.inspectorTitle}><div><small>선택한 자리</small><h3>{selected.coordinate}</h3></div><span>{statusLabel(selected.status)}</span></div>
              <label>표시 이름<input value={displayName} onChange={(event) => setDisplayName(event.target.value)} /></label>
              <label>직판 가격 ({currencyName})<input inputMode="numeric" value={salePrice} onChange={(event) => setSalePrice(event.target.value)} /></label>
              <label className={styles.check}><input type="checkbox" checked={isPublic} onChange={(event) => setIsPublic(event.target.checked)} />학생에게 직판 매물 공개</label>
              <button type="button" className={styles.primary} disabled={busy || selected.owner_kind === "STUDENT"} onClick={() => void saveSelected()}>설정 저장</button>
              {subscriptionEnabled && selected.owner_kind === "TEACHER" && ["AVAILABLE", "TEACHER_OWNED"].includes(selected.status) ? (
                <button type="button" className={styles.secondary} onClick={() => toggleOfferSeat(selected)}>{offerSeats.has(selected.property_id) ? <><X size={16} />청약 선택 해제</> : <><Check size={16} />청약 공급에 선택</>}</button>
              ) : null}
              {selected.owner_kind === "STUDENT" ? <p className={styles.help}>학생 소유 자리의 공개·판매 설정은 소유 학생이 관리합니다.</p> : null}
            </> : <p className={styles.empty}>좌석을 선택하면 이름과 직판 가격을 설정할 수 있어요.</p>}
          </aside>
        </div>
      ) : <div className={styles.emptyPanel}>행과 열을 입력한 뒤 좌석판을 만들어 주세요.</div>}

      <section className={styles.section}>
        <div className={styles.sectionTitle}><div><p>구매 요청</p><h3>승인 대기 {contracts.length}건</h3></div></div>
        {contracts.length ? <div className={styles.cardList}>{contracts.map((contract) => (
          <article className={styles.contractCard} key={contract.contract_id}>
            <div><b>{contract.coordinate} · {contract.student_nickname}</b><span>{contract.roster_number}번 학생 · {contract.landlord_kind === "STUDENT" ? "학생 재판매" : "교사 직판"}</span><strong>{contract.sale_price.toLocaleString("ko-KR")} {currencyName}</strong></div>
            <div><button type="button" className={styles.reject} disabled={busy} onClick={() => void run(() => decidePropertyContract(contract.contract_id, false), "구매 요청을 거절했어요.")}>거절</button><button type="button" className={styles.primary} disabled={busy} onClick={() => void run(() => decidePropertyContract(contract.contract_id, true), "구매를 승인하고 소유권을 이전했어요.")}>승인</button></div>
          </article>
        ))}</div> : <p className={styles.empty}>현재 승인할 구매 요청이 없어요.</p>}
      </section>

      {subscriptionEnabled ? <section className={styles.section}>
        <div className={styles.sectionTitle}><div><p>선택 공급 방식</p><h3>청약 운영</h3></div><span>{offerSeats.size}자리 선택됨</span></div>
        <div className={styles.offerForm}>
          <label>공고 제목<input value={offerTitle} onChange={(event) => setOfferTitle(event.target.value)} /></label>
          <label>분양가<input inputMode="numeric" value={offerPrice} onChange={(event) => setOfferPrice(event.target.value)} /></label>
          <label>최소 납입 횟수<input inputMode="numeric" value={minimumCount} onChange={(event) => setMinimumCount(event.target.value)} /></label>
          <label>최소 예치금<input inputMode="numeric" value={minimumBalance} onChange={(event) => setMinimumBalance(event.target.value)} /></label>
          <label>신청 마감<input type="datetime-local" value={closesAt} onChange={(event) => setClosesAt(event.target.value)} /></label>
          <button type="button" className={styles.primary} disabled={busy} onClick={() => void createOffer()}>청약 공고 열기</button>
        </div>
        <div className={styles.cardList}>{offers.map((offer) => (
          <article className={styles.offerCard} key={offer.offer_id}>
            <div><b>{offer.title}</b><span><Clock3 size={14} /> {new Date(offer.closes_at).toLocaleString("ko-KR")} 마감</span><small>공급 {offer.supply_quantity}석 · 신청 {offer.applicant_count}명 · {offer.sale_price.toLocaleString("ko-KR")} {currencyName}</small></div>
            <div><em>{offer.status}</em>{offer.status === "OPEN" ? <><button type="button" className={styles.reject} disabled={busy} onClick={() => void run(() => cancelSubscriptionOffer(offer.offer_id), "청약 공고를 취소했어요.")}>취소</button><button type="button" className={styles.primary} disabled={busy || Date.now() < new Date(offer.closes_at).getTime()} onClick={() => void run(() => drawSubscriptionOffer(offer.offer_id), "청약 추첨을 완료했어요.")}>추첨</button></> : null}</div>
          </article>
        ))}</div>
      </section> : <section className={styles.subscriptionOff}><b>청약 모듈 OFF</b><span>직판과 학생 간 재판매만 운영됩니다. 청약 관련 탭과 API는 호출하지 않습니다.</span></section>}
    </div>
  );
}
