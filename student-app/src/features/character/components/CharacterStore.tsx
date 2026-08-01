import { useEffect, useMemo, useState } from "react";
import { computeBodyState } from "../bodyState";
import {
  acquireMockCharacter,
  fetchCharacterStore,
  fetchOwnedCharacters,
  setCharacterEquipped,
  type CharacterStoreItem
} from "../characterApi";
import { BODY_LABEL, SLOT_LABEL, THEME_LABEL, type BodyState, type Theme } from "../types";
import { CatalogCharacterPreview, ProductPreview } from "./ProductPreview";

const THEMES: Array<Theme | "ALL"> = ["ALL", "BASIC", "SEASONAL", "SPORTS", "CAREER", "FANTASY", "EVENT"];
const PREVIEW_BODY_STATES: BodyState[] = ["CHUBBY", "BASE", "SLIM", "VERY_SLIM"];

type CharacterShopProps = {
  classroomId: string;
  walletBalance: number;
  savedBalance: number;
  spendTotal: number;
  onBuy: (item: CharacterStoreItem) => Promise<boolean>;
};

function useBodyState(savedBalance: number, spendTotal: number) {
  const [bodyState, setBodyState] = useState<BodyState>("BASE");
  useEffect(() => {
    setBodyState((previous) => computeBodyState(previous, savedBalance, spendTotal));
  }, [savedBalance, spendTotal]);
  return bodyState;
}

function BodyPreviewPicker({
  actual,
  value,
  onChange
}: {
  actual: BodyState;
  value: BodyState;
  onChange: (bodyState: BodyState) => void;
}) {
  return (
    <div className="body-preview-picker" aria-label="체형별 상품 핏 미리보기">
      <div>
        <b>체형별 핏 확인</b>
        <span>현재 체형은 {BODY_LABEL[actual]}이에요.</span>
      </div>
      <div className="body-preview-options">
        {PREVIEW_BODY_STATES.map((bodyState) => (
          <button
            className={value === bodyState ? "active" : ""}
            key={bodyState}
            onClick={() => onChange(bodyState)}
            type="button"
          >
            {BODY_LABEL[bodyState]}
          </button>
        ))}
      </div>
    </div>
  );
}

export function CharacterShop({
  classroomId,
  walletBalance,
  savedBalance,
  spendTotal,
  onBuy
}: CharacterShopProps) {
  const [items, setItems] = useState<CharacterStoreItem[]>([]);
  const [theme, setTheme] = useState<Theme | "ALL">("ALL");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(true);
  const bodyState = useBodyState(savedBalance, spendTotal);
  const [previewBodyState, setPreviewBodyState] = useState<BodyState>(bodyState);

  useEffect(() => {
    setPreviewBodyState(bodyState);
  }, [bodyState]);

  async function refresh() {
    const next = await fetchCharacterStore(classroomId);
    setItems(next);
    setSelectedId((current) => current ?? next[0]?.id ?? null);
  }

  useEffect(() => {
    let active = true;
    setLoading(true);
    fetchCharacterStore(classroomId)
      .then((next) => {
        if (!active) return;
        setItems(next);
        setSelectedId(next[0]?.id ?? null);
      })
      .catch((error: unknown) => {
        if (active) setMessage(error instanceof Error ? error.message : "캐릭터 상품을 불러오지 못했어요.");
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [classroomId]);

  const visibleItems = useMemo(
    () => items.filter((item) => theme === "ALL" || item.theme === theme),
    [items, theme]
  );
  const selected = items.find((item) => item.id === selectedId) ?? visibleItems[0] ?? null;

  async function buy(item: CharacterStoreItem) {
    if (item.owned) {
      await toggle(item);
      return;
    }
    try {
      const ok = await onBuy(item);
      if (!ok) {
        setMessage("비빔이 부족해요.");
        return;
      }
      acquireMockCharacter(item.id);
      await refresh();
      setMessage(`${item.name}을 샀어요. 이제 캐릭터 보관함에서 착용할 수 있어요.`);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "구매하지 못했어요.");
    }
  }

  async function toggle(item: CharacterStoreItem) {
    try {
      await setCharacterEquipped(classroomId, item, !item.equipped);
      await refresh();
      setMessage(item.equipped ? `${item.name}을 해제했어요.` : `${item.name}을 착용했어요.`);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "착용 상태를 바꾸지 못했어요.");
    }
  }

  return (
    <section className="character-store">
      <aside className="character-preview-panel">
        <div className="character-preview-heading">
          <div>
            <p className="eyebrow">착용 미리보기</p>
            <h2>{selected?.name ?? "기본 캐릭터"}</h2>
          </div>
          <span className="body-pill">{BODY_LABEL[bodyState]}</span>
        </div>
        {selected ? (
          <CatalogCharacterPreview item={selected} bodyState={previewBodyState} />
        ) : (
          <div className="catalog-character-stage catalog-character-stage--empty">상품을 선택해 주세요.</div>
        )}
        <BodyPreviewPicker
          actual={bodyState}
          value={previewBodyState}
          onChange={setPreviewBodyState}
        />
        <p className="character-preview-help">
          오른쪽 상품과 같은 원본 도트로 체형별 착용 모습을 확인할 수 있어요.
        </p>
      </aside>

      <div className="character-catalog-panel">
        <div className="character-theme-tabs" aria-label="캐릭터 상품 테마">
          {THEMES.map((value) => (
            <button
              className={theme === value ? "active" : ""}
              key={value}
              onClick={() => setTheme(value)}
            >
              {value === "ALL" ? "전체" : THEME_LABEL[value]}
            </button>
          ))}
        </div>
        {message ? <p className="shop-message">{message}</p> : null}
        {loading ? <p className="character-empty">캐릭터 상품을 불러오는 중이에요.</p> : null}
        {!loading && visibleItems.length === 0 ? (
          <p className="character-empty">현재 판매 중인 캐릭터 상품이 없어요.</p>
        ) : null}
        <div className="character-product-grid">
          {visibleItems.map((item) => {
            const insufficient = walletBalance < item.price;
            return (
              <article
                className={`character-product-card ${selected?.id === item.id ? "selected" : ""}`}
                key={item.id}
                onClick={() => setSelectedId(item.id)}
              >
                <ProductPreview item={item} bodyState={previewBodyState} />
                <div className="character-product-info">
                  <b>{item.name}</b>
                  <span>{SLOT_LABEL[item.slot]} · {item.price}비빔</span>
                </div>
                <button
                  disabled={!item.owned && insufficient}
                  onClick={(event) => {
                    event.stopPropagation();
                    void buy(item);
                  }}
                >
                  {item.equipped ? "착용 해제" : item.owned ? "착용하기" : insufficient ? "잔액 부족" : "구매하기"}
                </button>
              </article>
            );
          })}
        </div>
      </div>
    </section>
  );
}

export function CharacterWardrobe({
  classroomId,
  savedBalance,
  spendTotal
}: {
  classroomId: string;
  savedBalance: number;
  spendTotal: number;
}) {
  const [items, setItems] = useState<CharacterStoreItem[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [message, setMessage] = useState("");
  const bodyState = useBodyState(savedBalance, spendTotal);
  const [previewBodyState, setPreviewBodyState] = useState<BodyState>(bodyState);

  useEffect(() => {
    setPreviewBodyState(bodyState);
  }, [bodyState]);

  async function refresh() {
    setItems(await fetchOwnedCharacters(classroomId));
  }

  useEffect(() => {
    let active = true;
    fetchOwnedCharacters(classroomId)
      .then((next) => {
        if (!active) return;
        setItems(next);
        setSelectedId((current) => current ?? next.find((item) => item.equipped)?.id ?? next[0]?.id ?? null);
      })
      .catch((error: unknown) => {
        if (active) setMessage(error instanceof Error ? error.message : "보관함을 불러오지 못했어요.");
      });
    return () => {
      active = false;
    };
  }, [classroomId]);

  const selected = items.find((item) => item.id === selectedId)
    ?? items.find((item) => item.equipped)
    ?? items[0]
    ?? null;

  async function toggle(item: CharacterStoreItem) {
    try {
      await setCharacterEquipped(classroomId, item, !item.equipped);
      await refresh();
      setMessage(item.equipped ? `${item.name}을 해제했어요.` : `${item.name}을 착용했어요.`);
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "착용 상태를 바꾸지 못했어요.");
    }
  }

  return (
    <section className="character-store wardrobe">
      <aside className="character-preview-panel">
        <div className="character-preview-heading">
          <div>
            <p className="eyebrow">내 캐릭터</p>
            <h2>{selected?.name ?? "착용 모습"}</h2>
          </div>
          <span className="body-pill">{BODY_LABEL[bodyState]}</span>
        </div>
        {selected ? (
          <CatalogCharacterPreview item={selected} bodyState={previewBodyState} />
        ) : (
          <div className="catalog-character-stage catalog-character-stage--empty">보유한 상품이 없어요.</div>
        )}
        <BodyPreviewPicker
          actual={bodyState}
          value={previewBodyState}
          onChange={setPreviewBodyState}
        />
      </aside>
      <div className="character-catalog-panel">
        <div className="character-preview-heading">
          <div>
            <p className="eyebrow">캐릭터 보관함</p>
            <h2>보유 아이템 {items.length}개</h2>
          </div>
        </div>
        {message ? <p className="shop-message">{message}</p> : null}
        <div className="wardrobe-list">
          {items.map((item) => (
            <button
              className={item.equipped ? "wardrobe-item equipped" : "wardrobe-item"}
              key={item.id}
              onClick={() => {
                setSelectedId(item.id);
                void toggle(item);
              }}
            >
              <ProductPreview item={item} bodyState={previewBodyState} />
              <span>
                <b>{item.name}</b>
                <small>{SLOT_LABEL[item.slot]}</small>
              </span>
              <strong>{item.equipped ? "착용 중" : "착용"}</strong>
            </button>
          ))}
        </div>
      </div>
    </section>
  );
}
