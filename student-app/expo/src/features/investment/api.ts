import { apiRequest } from "@/api/client";

export type InvestmentNews = {
  eventId: string;
  title: string;
  content: string;
};

export type InvestmentAsset = {
  assetId: string;
  symbol: string;
  name: string;
  companyProfile: string;
  currentPrice: number;
  priceVersion: number;
  news: InvestmentNews[];
};

export type InvestmentMarket = {
  status: "DRAFT" | "OPEN" | "PAUSED" | "CLOSED";
  investmentMode: "SHORT_TERM" | "LONG_TERM";
  scenarioSource: "AI_GENERATED" | "HISTORICAL_INDEX";
  settlementRule: "NEXT_DAY_MANDATORY" | "COURSE_END_FINAL";
  currentStep: number;
  assets: InvestmentAsset[];
};

type MarketResponse = {
  status: InvestmentMarket["status"];
  investment_mode: InvestmentMarket["investmentMode"];
  scenario_source: InvestmentMarket["scenarioSource"];
  settlement_rule: InvestmentMarket["settlementRule"];
  current_step: number;
  assets: Array<{
    asset_id: string;
    symbol: string;
    name: string;
    company_profile: string;
    current_price: number | null;
    price_version: number | null;
    news: Array<{
      event_id: string;
      title: string;
      content: string;
      status: string;
    }>;
  }>;
};

export async function fetchInvestmentMarket(classroomId: string): Promise<InvestmentMarket> {
  const response = await apiRequest<MarketResponse>(`/classrooms/${classroomId}/investment-market`);
  return {
    status: response.status,
    investmentMode: response.investment_mode,
    scenarioSource: response.scenario_source,
    settlementRule: response.settlement_rule,
    currentStep: response.current_step,
    assets: response.assets
      .filter((asset) => asset.current_price !== null && asset.price_version !== null)
      .map((asset) => ({
        assetId: asset.asset_id,
        symbol: asset.symbol,
        name: asset.name,
        companyProfile: asset.company_profile,
        currentPrice: asset.current_price!,
        priceVersion: asset.price_version!,
        news: asset.news
          .filter((news) => ["APPROVED", "APPLIED"].includes(news.status))
          .map((news) => ({ eventId: news.event_id, title: news.title, content: news.content }))
      }))
  };
}

export async function buyInvestment(
  classroomId: string,
  asset: InvestmentAsset,
  quantity: number
) {
  return apiRequest<{
    order_id: string;
    status: string;
    total_amount: number;
    position_quantity: number;
    average_price: number;
  }>(`/classrooms/${classroomId}/investment-orders`, {
    method: "POST",
    retryOnNetworkFailure: true,
    body: JSON.stringify({
      side: "BUY",
      asset_id: asset.assetId,
      quantity,
      expected_price_version: asset.priceVersion
    })
  });
}
