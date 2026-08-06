"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { AlertTriangle, CalendarClock, Check, Lock, LockOpen, LineChart, Play, RefreshCw, Sparkles } from "lucide-react";

import { ApiError, apiRequest } from "../api";

export type InvestmentMode = "SHORT_TERM" | "LONG_TERM";

type InvestmentNews = {
  event_id: string;
  title: string;
  content: string;
  status: "DRAFT" | "APPROVED" | "APPLIED";
  state_version: number;
  revealed_at: string | null;
};

type PricePoint = { step: number; price: number; change_rate_bps: number };

type InvestmentAsset = {
  asset_id: string;
  symbol: string;
  name: string;
  company_profile: string;
  current_price: number | null;
  price_version: number | null;
  price_history: PricePoint[];
  planned_price_history: PricePoint[];
  news: InvestmentNews[];
};

type InvestmentMarket = {
  market_id: string;
  scenario_version_id: string;
  status: "DRAFT" | "OPEN" | "PAUSED" | "CLOSED";
  investment_mode: InvestmentMode;
  scenario_source: "AI_GENERATED" | "HISTORICAL_INDEX";
  settlement_rule: "NEXT_DAY_MANDATORY" | "COURSE_END_FINAL";
  current_step: number;
  state_version: number;
  assets: InvestmentAsset[];
};

type DayResult = {
  market_status: string;
  current_step: number;
  total_steps: number;
  settled: boolean;
  settled_position_count: number;
  settled_amount: number;
};

type ScenarioResult = {
  market_id: string;
  market_status: string;
  state_version: number;
  scenario_source: string;
};

const MODE_LABELS: Record<InvestmentMode, string> = {
  SHORT_TERM: "단기 투자",
  LONG_TERM: "장기 투자"
};

/**
 * 서버 응답을 화면이 기대하는 모양으로 맞춥니다.
 *
 * 목록 필드가 빠져 있으면 빈 배열로 둡니다. 백엔드를 아직 새로 올리지 않았거나 예전 응답이
 * 캐시된 경우 `planned_price_history` 같은 새 필드가 없는데, 그대로 `.length`를 읽으면
 * 화면 전체가 흰 화면(client-side exception)이 됩니다.
 */
function toMarket(raw: Partial<InvestmentMarket> | null | undefined): InvestmentMarket | null {
  if (!raw || typeof raw !== "object" || !raw.status) return null;
  return {
    market_id: raw.market_id ?? "",
    scenario_version_id: raw.scenario_version_id ?? "",
    status: raw.status,
    investment_mode: raw.investment_mode === "SHORT_TERM" ? "SHORT_TERM" : "LONG_TERM",
    scenario_source: raw.scenario_source === "HISTORICAL_INDEX" ? "HISTORICAL_INDEX" : "AI_GENERATED",
    settlement_rule: raw.settlement_rule ?? "COURSE_END_FINAL",
    current_step: raw.current_step ?? 0,
    state_version: raw.state_version ?? 0,
    assets: (Array.isArray(raw.assets) ? raw.assets : []).map((asset) => ({
      asset_id: asset?.asset_id ?? "",
      symbol: asset?.symbol ?? "",
      name: asset?.name ?? "",
      company_profile: asset?.company_profile ?? "",
      current_price: asset?.current_price ?? null,
      price_version: asset?.price_version ?? null,
      price_history: Array.isArray(asset?.price_history) ? asset.price_history : [],
      planned_price_history: Array.isArray(asset?.planned_price_history)
        ? asset.planned_price_history
        : [],
      news: Array.isArray(asset?.news) ? asset.news : []
    }))
  };
}

function messageOf(error: unknown) {
  if (error instanceof ApiError && error.code === "RESOURCE_NOT_FOUND") return "아직 생성된 투자 시나리오가 없습니다.";
  if (error instanceof ApiError && error.code === "INVESTMENT_MODE_NOT_ENABLED") {
    return "이 방식을 사용하도록 먼저 켜 주세요.";
  }
  // 없는 주소에 대한 404는 오류 코드가 없습니다. 서버가 화면보다 오래된 빌드라는 뜻이므로
  // "시나리오가 없다"가 아니라 서버를 새로 올려야 한다고 알려 줍니다.
  if (error instanceof ApiError && error.status === 404) {
    return "서버에 이 기능이 아직 없습니다. 백엔드를 새로 빌드해 실행한 뒤 다시 시도해 주세요.";
  }
  if (error instanceof ApiError && error.code === "INVESTMENT_MODE_MISMATCH") {
    return "서버가 아직 한 가지 방식만 지원합니다. 백엔드를 새로 빌드해 실행해 주세요.";
  }
  return error instanceof Error ? error.message : "투자 시나리오를 처리하지 못했습니다.";
}

/**
 * 한 가지 투자 방식(단기 또는 장기)의 AI 시나리오를 만들고 검토하는 자리입니다.
 *
 * <p>학급은 단기와 장기를 함께 운영하므로 이 패널은 방식마다 하나씩 놓입니다. 교사가 쓰겠다고
 * 고르지 않은 방식은 시나리오를 만들 수 없으므로 생성 버튼을 누를 수 없습니다.
 */
export function InvestmentScenarioPanel({
  classroomId,
  mode,
  enabledModes,
  moduleActive,
  moduleStateVersion,
  currencyName,
  aiEnabled,
  canManage,
  onModuleChanged
}: Readonly<{
  classroomId: string;
  mode: InvestmentMode;
  enabledModes: readonly InvestmentMode[];
  moduleActive: boolean;
  moduleStateVersion: number;
  // 금액 뒤에 붙는 단위는 학급마다 다르므로 화면에 고정하지 않고 받아 씁니다.
  currencyName: string;
  aiEnabled: boolean;
  canManage: boolean;
  onModuleChanged: () => void | Promise<void>;
}>) {
  const modeEnabled = enabledModes.includes(mode);
  const [market, setMarket] = useState<InvestmentMarket | null>(null);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState("");
  const [message, setMessage] = useState("");

  const load = useCallback(async () => {
    if (!moduleActive || !modeEnabled) {
      setMarket(null);
      return;
    }
    setLoading(true);
    try {
      setMarket(toMarket(await apiRequest<Partial<InvestmentMarket>>(
        `/classrooms/${classroomId}/investment-market?investment_mode=${mode}`
      )));
      setMessage("");
    } catch (error) {
      if (error instanceof ApiError && error.code === "RESOURCE_NOT_FOUND") {
        setMarket(null);
      } else {
        setMessage(messageOf(error));
      }
    } finally {
      setLoading(false);
    }
  }, [classroomId, mode, moduleActive, modeEnabled]);

  useEffect(() => { void load(); }, [load]);

  const missingNewsCount = market && ["OPEN", "PAUSED"].includes(market.status)
    ? Math.max(0, market.assets.length - market.assets.filter((asset) => asset.news.length > 0).length)
    : 0;
  const plannedPath = useMemo(
    () => market?.assets.filter((asset) => asset.planned_price_history.length > 0) ?? [],
    [market]
  );

  async function enableMode() {
    setBusy("enable");
    setMessage("");
    try {
      await apiRequest(`/classrooms/${classroomId}/modules/INVESTMENT/investment-mode-commands`, {
        method: "POST",
        body: JSON.stringify({
          expected_state_version: moduleStateVersion,
          screen_id: "SCR-WEB-003",
          ui_action_id: "UIA-MOD-004",
          client_occurred_at: new Date().toISOString(),
          investment_modes: [...new Set([...enabledModes, mode])]
        })
      });
      await onModuleChanged();
      setMessage(`${MODE_LABELS[mode]}를 사용하도록 켰습니다. 이제 시나리오를 생성할 수 있습니다.`);
    } catch (error) {
      setMessage(messageOf(error));
    } finally {
      setBusy("");
    }
  }

  async function generateScenario(regenerate = false) {
    setBusy("scenario");
    setMessage("");
    try {
      const result = await apiRequest<ScenarioResult>(`/classrooms/${classroomId}/investment-market/scenario`, {
        method: "PUT",
        body: JSON.stringify({
          investment_mode: mode,
          prefer_historical_index: mode === "LONG_TERM"
        })
      });
      await load();
      const source = result.scenario_source === "HISTORICAL_INDEX"
        ? "FRED 역사 지수 흐름을 고정한"
        : "AI가 만든 가격 흐름의";
      setMessage(regenerate
        ? `초안을 거절하고 ${source} 새 시나리오를 만들었습니다.`
        : `${source} ${MODE_LABELS[mode]} 시나리오를 만들었습니다. 가격 흐름을 확인한 뒤 승인해 주세요.`);
    } catch (error) {
      setMessage(messageOf(error));
    } finally {
      setBusy("");
    }
  }

  async function sendCommand(command: "START" | "PAUSE" | "RESUME", done: string) {
    if (!market) return;
    setBusy(command);
    setMessage("");
    try {
      await apiRequest(`/classrooms/${classroomId}/investment-market/commands`, {
        method: "POST",
        body: JSON.stringify({
          command,
          investment_mode: mode,
          expected_state_version: market.state_version
        })
      });
      await load();
      setMessage(done);
    } catch (error) {
      setMessage(messageOf(error));
    } finally {
      setBusy("");
    }
  }

  /**
   * 숨겨 둔 다음 날 가격을 공개합니다. 단기는 이때 의무 정산까지 함께 일어납니다.
   */
  async function advanceDay() {
    if (!market) return;
    setBusy("day");
    setMessage("");
    try {
      const result = await apiRequest<DayResult>(`/classrooms/${classroomId}/investment-market/day-commands`, {
        method: "POST",
        body: JSON.stringify({
          investment_mode: mode,
          expected_state_version: market.state_version
        })
      });
      await load();
      setMessage(result.settled
        ? `정산했습니다. ${result.settled_position_count}명에게 모두 ${result.settled_amount.toLocaleString("ko-KR")} ${currencyName}을 지급하고 투자를 마감했습니다.`
        + (mode === "SHORT_TERM" ? " 다음 날 투자를 열려면 시나리오를 새로 생성해 주세요." : "")
        : `${result.current_step}일차 가격을 공개했습니다(전체 ${result.total_steps}일). 새 이야기를 생성해 주세요.`);
    } catch (error) {
      setMessage(messageOf(error));
    } finally {
      setBusy("");
    }
  }

  async function generateNews() {
    if (!market || missingNewsCount === 0) return;
    setBusy("news");
    setMessage("");
    try {
      for (let index = 0; index < missingNewsCount; index += 1) {
        await apiRequest(`/classrooms/${classroomId}/ai/economic-event-draft-operations`, {
          method: "POST",
          body: JSON.stringify({
            template_type: "INVESTMENT_NEWS",
            investment_mode: mode,
            // 다음 단계의 방향은 서버가 target_direction으로 따로 넘깁니다. 이 자유 입력에
            // "상승"·"하락" 같은 방향 낱말을 쓰면 AI가 미래 정보 유출로 보고 422로 거절합니다.
            teacher_context: mode === "SHORT_TERM"
              ? "학생이 기업의 하루 상황만 보고 다음 날 투자 여부를 스스로 판단할 수 있는 짧은 이야기"
              : "학생이 매일 투자금을 조정할 수 있도록 기업의 하루 상황을 설명하는 이야기"
          })
        });
      }
      await load();
      setMessage("현재 단계의 모든 종목에 AI 이야기 초안을 생성했습니다. 검토 후 승인해 주세요.");
    } catch (error) {
      await load();
      setMessage(messageOf(error));
    } finally {
      setBusy("");
    }
  }

  async function reject(news: InvestmentNews) {
    await apiRequest(`/classrooms/${classroomId}/ai/economic-events/${news.event_id}/decisions`, {
      method: "POST",
      body: JSON.stringify({
        decision: "REJECT",
        edits: {},
        reason: "투자 이야기 재생성 필요",
        expected_event_version: news.state_version
      })
    });
  }

  async function decide(news: InvestmentNews, decision: "APPROVE" | "REJECT") {
    setBusy(news.event_id);
    setMessage("");
    try {
      if (decision === "REJECT") {
        await reject(news);
      } else {
        await apiRequest(`/classrooms/${classroomId}/ai/economic-events/${news.event_id}/decisions`, {
          method: "POST",
          body: JSON.stringify({
            decision,
            edits: {},
            reason: "학생에게 공개할 투자 이야기 검토 완료",
            expected_event_version: news.state_version
          })
        });
      }
      await load();
      setMessage(decision === "APPROVE"
        ? "투자 이야기를 학생에게 공개했습니다."
        : "초안을 거절했습니다. 다시 생성해 주세요.");
    } catch (error) {
      setMessage(messageOf(error));
    } finally {
      setBusy("");
    }
  }

  /**
   * 지금 초안을 물리고 그 자리에서 새 이야기를 요청합니다.
   *
   * <p>AI 생성이 실패해 규칙 엔진 안내문만 들어온 경우, 교사가 거절과 생성을 두 번 나눠
   * 누르지 않고 한 번에 다시 시도할 수 있어야 합니다.
   */
  async function regenerate(news: InvestmentNews) {
    setBusy(news.event_id);
    setMessage("");
    try {
      await reject(news);
      await apiRequest(`/classrooms/${classroomId}/ai/economic-event-draft-operations`, {
        method: "POST",
        body: JSON.stringify({
          template_type: "INVESTMENT_NEWS",
          investment_mode: mode,
          teacher_context: mode === "SHORT_TERM"
            ? "학생이 기업의 하루 상황만 보고 다음 날 투자 여부를 스스로 판단할 수 있는 짧은 이야기"
            : "학생이 매일 투자금을 조정할 수 있도록 기업의 하루 상황을 설명하는 이야기"
        })
      });
      await load();
      setMessage("이야기를 다시 생성했습니다. 내용을 확인한 뒤 승인해 주세요.");
    } catch (error) {
      await load();
      setMessage(messageOf(error));
    } finally {
      setBusy("");
    }
  }

  const statusLabel = !moduleActive
    ? "모듈 미활성"
    : !modeEnabled
      ? "사용 안 함"
      : market?.status === "OPEN"
        ? "학생 이용 중"
        : market?.status === "PAUSED"
          ? "학생 잠금"
          : market?.status === "DRAFT"
            ? "검토 대기"
            : "미생성";

  return (
    <section className="plain-section investment-scenario-panel">
      <div className="section-title-line">
        <div>
          <span className="section-kicker">투자 AI</span>
          <h2>{MODE_LABELS[mode]} 시나리오</h2>
        </div>
        <span className={`ai-status ${market?.status === "OPEN" ? "on" : "off"}`}>
          <LineChart size={16} /> {statusLabel}
        </span>
      </div>
      <p className="section-description">
        {mode === "SHORT_TERM"
          ? "단기 투자는 오늘 매수하고 다음 날 의무 정산되는 이야기형 시나리오입니다."
          : "장기 투자는 역사 지수 흐름을 우선 사용하고 매일 새로운 기업 이야기를 제공합니다."}
      </p>

      {!moduleActive ? (
        <div className="inline-notice">
          <AlertTriangle size={17} /> 투자 모듈을 먼저 활성화해 주세요. 활성화 전에는 시나리오를 만들 수 없습니다.
        </div>
      ) : !modeEnabled ? (
        <>
          <div className="inline-notice">
            <AlertTriangle size={17} /> 이 학급은 {MODE_LABELS[mode]}를 아직 사용하지 않습니다.
          </div>
          <button type="button" className="wide-action" disabled={!canManage || busy === "enable"} onClick={() => void enableMode()}>
            <Check size={16} /> {busy === "enable" ? "켜는 중" : `${MODE_LABELS[mode]} 사용하기`}
          </button>
        </>
      ) : null}

      {/* 활성화하지 않았거나 사용하지 않는 방식은 생성 자체가 막혀 있어야 합니다. */}
      {moduleActive && modeEnabled && !market ? (
        <button type="button" className="wide-action" disabled={!canManage || busy === "scenario"} onClick={() => void generateScenario()}>
          <Sparkles size={16} /> {busy === "scenario" ? "시나리오 생성 중" : `${MODE_LABELS[mode]} AI 시나리오 생성`}
        </button>
      ) : null}
      {!moduleActive || !modeEnabled ? (
        <button type="button" className="wide-action" disabled title="투자 모듈과 이 방식을 먼저 켜 주세요.">
          <Sparkles size={16} /> {MODE_LABELS[mode]} AI 시나리오 생성
        </button>
      ) : null}

      {loading ? <p className="empty-copy">투자 시장을 불러오는 중입니다.</p> : null}

      {market?.status === "DRAFT" ? (
        <div className="investment-market-actions">
          <p>
            <b>{market.assets.length}개 가상 기업</b>의 가격 경로가 준비되었습니다.
            아래 흐름을 확인하고 승인하면 학생에게 첫 가격이 공개됩니다.
          </p>
          <div className="investment-draft-preview">
            {plannedPath.map((asset) => (
              <article key={asset.asset_id}>
                <header><strong>{asset.name}</strong><small>{asset.symbol}</small></header>
                <p>{asset.company_profile}</p>
                <ol>
                  {asset.planned_price_history.map((point) => (
                    <li key={point.step}>
                      {point.step}일차 {point.price.toLocaleString("ko-KR")}
                      <small>{point.change_rate_bps > 0 ? "+" : ""}{(point.change_rate_bps / 100).toFixed(2)}%</small>
                    </li>
                  ))}
                </ol>
              </article>
            ))}
          </div>
          <div className="investment-decision-actions">
            <button type="button" disabled={!canManage || Boolean(busy)} onClick={() => void sendCommand("START", "첫 가격을 공개하고 투자 시장을 열었습니다. 이제 AI 이야기를 생성해 주세요.")}>
              <Play size={16} /> 승인·시장 시작
            </button>
            <button type="button" className="ghost-action" disabled={!canManage || Boolean(busy)} onClick={() => void generateScenario(true)}>
              <RefreshCw size={16} /> {busy === "scenario" ? "다시 생성 중" : "거절·다시 생성"}
            </button>
          </div>
        </div>
      ) : null}

      {market && ["OPEN", "PAUSED"].includes(market.status) ? (
        <>
          <div className="investment-market-summary">
            <b>{MODE_LABELS[market.investment_mode]}</b>
            <span>{market.scenario_source === "HISTORICAL_INDEX" ? "FRED 역사 지수 흐름" : "AI 생성 가격 흐름"}</span>
            {/* 단기는 오늘 투자하고 다음 날 바로 정산하므로 며칠째인지가 의미 없습니다. */}
            {mode === "LONG_TERM" ? <span>현재 {market.current_step}일차</span> : null}
            <button
              type="button"
              className="ghost-action"
              disabled={!canManage || Boolean(busy)}
              onClick={() => void (market.status === "OPEN"
                ? sendCommand("PAUSE", `${MODE_LABELS[mode]}의 학생 이용을 잠갔습니다.`)
                : sendCommand("RESUME", `${MODE_LABELS[mode]}의 학생 이용을 다시 열었습니다.`))}
            >
              {market.status === "OPEN" ? <><Lock size={15} /> 학생 이용 잠금</> : <><LockOpen size={15} /> 학생 이용 열기</>}
            </button>
          </div>
          {market.status === "PAUSED" ? (
            <div className="inline-notice"><Lock size={17} /> 학생 화면에서 이 투자에 들어갈 수 없습니다.</div>
          ) : null}
          <div className="investment-decision-actions">
            <button type="button" disabled={!canManage || Boolean(busy)} onClick={() => void advanceDay()}>
              <CalendarClock size={16} /> {busy === "day"
                ? "처리 중"
                : mode === "SHORT_TERM" ? "다음 날 공개·정산" : "다음 날 공개"}
            </button>
            <small>
              {mode === "SHORT_TERM"
                ? "단기는 다음 날 가격을 공개하는 순간 학생 보유량을 의무 정산하고 마감합니다."
                : `숨겨 둔 다음 날 가격을 공개합니다. 마지막 날에는 최종 정산하고 마감합니다.`}
            </small>
          </div>
          {missingNewsCount > 0 ? (
            <button type="button" className="wide-action" disabled={!aiEnabled || !canManage || busy === "news"} onClick={() => void generateNews()}>
              <Sparkles size={16} /> {busy === "news" ? "AI 이야기 생성 중" : `현재 단계 AI 이야기 ${missingNewsCount}개 생성`}
            </button>
          ) : <div className="inline-notice"><Check size={17} /> 모든 종목의 현재 단계 이야기가 준비되었습니다.</div>}
          <div className="investment-review-grid">
            {market.assets.map((asset) => (
              <article key={asset.asset_id}>
                <header>
                  <div><strong>{asset.name}</strong><small>{asset.symbol}</small></div>
                  <b>{asset.current_price?.toLocaleString("ko-KR")} {currencyName}</b>
                </header>
                <p>{asset.company_profile}</p>
                {asset.news.length === 0 ? <small>아직 이야기 초안이 없습니다.</small> : asset.news.map((news) => {
                  // AI가 실패해 규칙 엔진 안내문만 들어온 초안입니다. 승인해도 학생에게 보여 줄
                  // 이야기가 없으므로 다시 생성부터 권합니다.
                  const aiFailed = news.content.includes("AI 이야기 생성에 실패");
                  return (
                    <div className="investment-news-review" key={news.event_id}>
                      <span>{aiFailed ? "생성 실패" : news.status}</span><h3>{news.title}</h3><p>{news.content}</p>
                      {news.status === "DRAFT" ? <div>
                        {aiFailed ? null : (
                          <button type="button" disabled={!canManage || Boolean(busy)} onClick={() => void decide(news, "APPROVE")}>
                            <Check size={15} /> 승인·공개
                          </button>
                        )}
                        <button type="button" className={aiFailed ? "" : "ghost-action"} disabled={!aiEnabled || !canManage || Boolean(busy)} onClick={() => void regenerate(news)}>
                          <RefreshCw size={15} /> {busy === news.event_id ? "다시 생성 중" : "AI로 다시 생성"}
                        </button>
                        <button type="button" className="ghost-action" disabled={!canManage || Boolean(busy)} onClick={() => void decide(news, "REJECT")}>거절</button>
                      </div> : null}
                    </div>
                  );
                })}
              </article>
            ))}
          </div>
        </>
      ) : null}

      {message ? <div className="inline-notice" role="status">{message}</div> : null}
    </section>
  );
}
