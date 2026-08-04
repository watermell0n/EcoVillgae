"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { AlertTriangle, Check, LineChart, Play, Sparkles } from "lucide-react";

import { ApiError, apiRequest } from "../api";
import type { AiModuleRow } from "../types";

type InvestmentNews = {
  event_id: string;
  title: string;
  content: string;
  status: "DRAFT" | "APPROVED" | "APPLIED";
  state_version: number;
  revealed_at: string | null;
};

type InvestmentAsset = {
  asset_id: string;
  symbol: string;
  name: string;
  company_profile: string;
  current_price: number | null;
  price_version: number | null;
  price_history: Array<{ step: number; price: number; change_rate_bps: number }>;
  news: InvestmentNews[];
};

type InvestmentMarket = {
  market_id: string;
  scenario_version_id: string;
  status: "DRAFT" | "OPEN" | "PAUSED" | "CLOSED";
  investment_mode: "SHORT_TERM" | "LONG_TERM";
  scenario_source: "AI_GENERATED" | "HISTORICAL_INDEX";
  settlement_rule: "NEXT_DAY_MANDATORY" | "COURSE_END_FINAL";
  current_step: number;
  state_version: number;
  assets: InvestmentAsset[];
};

type ScenarioResult = {
  market_id: string;
  market_status: string;
  state_version: number;
  scenario_source: string;
};

function messageOf(error: unknown) {
  if (error instanceof ApiError && error.code === "RESOURCE_NOT_FOUND") return "아직 생성된 투자 시나리오가 없습니다.";
  return error instanceof Error ? error.message : "투자 시나리오를 처리하지 못했습니다.";
}

export function InvestmentScenarioPanel({
  classroomId,
  modules,
  aiEnabled,
  canManage
}: Readonly<{
  classroomId: string;
  modules: readonly AiModuleRow[];
  aiEnabled: boolean;
  canManage: boolean;
}>) {
  const investmentModule = modules.find((module) => module.code === "INVESTMENT");
  const configuredMode = investmentModule?.config?.investment_mode === "SHORT_TERM" ? "SHORT_TERM" : "LONG_TERM";
  const moduleActive = Boolean(investmentModule?.config?.investment_mode) || investmentModule?.status === "ACTIVE";
  const [market, setMarket] = useState<InvestmentMarket | null>(null);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState("");
  const [message, setMessage] = useState("");

  const load = useCallback(async () => {
    if (!moduleActive) return;
    setLoading(true);
    try {
      setMarket(await apiRequest<InvestmentMarket>(`/classrooms/${classroomId}/investment-market`));
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
  }, [classroomId, moduleActive]);

  useEffect(() => { void load(); }, [load]);

  const reviewNews = useMemo(
    () => market?.assets.flatMap((asset) => asset.news.map((news) => ({ asset, news }))) ?? [],
    [market]
  );
  const missingNewsCount = market?.status === "OPEN"
    ? Math.max(0, market.assets.length - market.assets.filter((asset) => asset.news.length > 0).length)
    : 0;

  async function generateScenario() {
    setBusy("scenario");
    setMessage("");
    try {
      const result = await apiRequest<ScenarioResult>(`/classrooms/${classroomId}/investment-market/scenario`, {
        method: "PUT",
        body: JSON.stringify({
          investment_mode: configuredMode,
          prefer_historical_index: configuredMode === "LONG_TERM"
        })
      });
      await load();
      setMessage(result.scenario_source === "HISTORICAL_INDEX"
        ? "FRED 역사 지수 흐름을 고정한 장기 시나리오를 생성했습니다."
        : "AI 생성 가격 흐름으로 투자 시나리오를 만들었습니다.");
    } catch (error) {
      setMessage(messageOf(error));
    } finally {
      setBusy("");
    }
  }

  async function startMarket() {
    if (!market) return;
    setBusy("start");
    setMessage("");
    try {
      await apiRequest(`/classrooms/${classroomId}/investment-market/commands`, {
        method: "POST",
        body: JSON.stringify({ command: "START", expected_state_version: market.state_version })
      });
      await load();
      setMessage("첫 가격을 공개하고 투자 시장을 열었습니다. 이제 AI 이야기를 생성해 주세요.");
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
            teacher_context: configuredMode === "SHORT_TERM"
              ? "학생이 문맥 단서로 다음 날의 상승 또는 하락을 추론할 수 있는 짧은 이야기"
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

  async function decide(news: InvestmentNews, decision: "APPROVE" | "REJECT") {
    setBusy(news.event_id);
    setMessage("");
    try {
      await apiRequest(`/classrooms/${classroomId}/ai/economic-events/${news.event_id}/decisions`, {
        method: "POST",
        body: JSON.stringify({
          decision,
          edits: {},
          reason: decision === "APPROVE" ? "학생에게 공개할 투자 이야기 검토 완료" : "투자 이야기 재생성 필요",
          expected_event_version: news.state_version
        })
      });
      await load();
      setMessage(decision === "APPROVE" ? "투자 이야기를 학생에게 공개했습니다." : "초안을 거절했습니다.");
    } catch (error) {
      setMessage(messageOf(error));
    } finally {
      setBusy("");
    }
  }

  if (!investmentModule) return null;

  return (
    <section className="plain-section investment-scenario-panel">
      <div className="section-title-line">
        <div><span className="section-kicker">투자 AI</span><h2>단기·장기 투자 시나리오</h2></div>
        <span className={`ai-status ${market?.status === "OPEN" ? "on" : "off"}`}>
          <LineChart size={16} /> {market?.status ?? "미생성"}
        </span>
      </div>
      <p className="section-description">
        {configuredMode === "SHORT_TERM"
          ? "단기 투자는 오늘 매수하고 다음 날 의무 정산되는 이야기형 시나리오입니다."
          : "장기 투자는 역사 지수 흐름을 우선 사용하고 매일 새로운 기업 이야기를 제공합니다."}
      </p>
      {!moduleActive ? <div className="inline-notice"><AlertTriangle size={17} /> 투자 모듈을 먼저 활성화해 주세요.</div> : null}
      {loading ? <p className="empty-copy">투자 시장을 불러오는 중입니다.</p> : null}
      {moduleActive && !market ? (
        <button type="button" className="wide-action" disabled={!canManage || busy === "scenario"} onClick={() => void generateScenario()}>
          <Sparkles size={16} /> {busy === "scenario" ? "시나리오 생성 중" : `${configuredMode === "SHORT_TERM" ? "단기" : "장기"} AI 시나리오 생성`}
        </button>
      ) : null}
      {market?.status === "DRAFT" ? (
        <div className="investment-market-actions">
          <p><b>{market.assets.length}개 가상 기업</b>의 가격 경로가 준비되었습니다.</p>
          <button type="button" disabled={!canManage || busy === "start"} onClick={() => void startMarket()}><Play size={16} /> 시장 시작</button>
        </div>
      ) : null}
      {market?.status === "OPEN" ? (
        <>
          <div className="investment-market-summary">
            <b>{market.investment_mode === "SHORT_TERM" ? "단기 투자" : "장기 투자"}</b>
            <span>{market.scenario_source === "HISTORICAL_INDEX" ? "FRED 역사 지수 흐름" : "AI 생성 가격 흐름"}</span>
            <span>현재 {market.current_step}일차</span>
          </div>
          {missingNewsCount > 0 ? (
            <button type="button" className="wide-action" disabled={!aiEnabled || busy === "news"} onClick={() => void generateNews()}>
              <Sparkles size={16} /> {busy === "news" ? "AI 이야기 생성 중" : `현재 단계 AI 이야기 ${missingNewsCount}개 생성`}
            </button>
          ) : <div className="inline-notice"><Check size={17} /> 모든 종목의 현재 단계 이야기가 준비되었습니다.</div>}
          <div className="investment-review-grid">
            {market.assets.map((asset) => (
              <article key={asset.asset_id}>
                <header><div><strong>{asset.name}</strong><small>{asset.symbol}</small></div><b>{asset.current_price?.toLocaleString("ko-KR")} 비</b></header>
                <p>{asset.company_profile}</p>
                {asset.news.length === 0 ? <small>아직 이야기 초안이 없습니다.</small> : asset.news.map((news) => (
                  <div className="investment-news-review" key={news.event_id}>
                    <span>{news.status}</span><h3>{news.title}</h3><p>{news.content}</p>
                    {news.status === "DRAFT" ? <div>
                      <button type="button" disabled={Boolean(busy)} onClick={() => void decide(news, "APPROVE")}><Check size={15} /> 승인·공개</button>
                      <button type="button" className="ghost-action" disabled={Boolean(busy)} onClick={() => void decide(news, "REJECT")}>거절</button>
                    </div> : null}
                  </div>
                ))}
              </article>
            ))}
          </div>
        </>
      ) : null}
      {reviewNews.length === 0 && market?.status === "OPEN" ? <p className="empty-copy">시장 시작 후 AI 이야기 초안을 생성해 주세요.</p> : null}
      {message ? <div className="inline-notice" role="status">{message}</div> : null}
    </section>
  );
}
