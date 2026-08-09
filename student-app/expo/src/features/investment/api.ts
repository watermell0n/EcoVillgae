import { apiRequest } from "@/api/client";

export type InvestmentMode = "SHORT_TERM" | "LONG_TERM";

export type InvestmentNews = {
  eventId: string;
  title: string;
  content: string;
};

export type InvestmentPricePoint = {
  step: number;
  price: number;
  changeRateBps: number;
};

export type InvestmentAsset = {
  assetId: string;
  symbol: string;
  name: string;
  companyProfile: string;
  /** 이 기업이 하루에 오르내릴 수 있는 범위(bps)입니다. 손해와 이득의 크기를 가늠할 수 있게 보여 줍니다. */
  minReturnBps: number;
  maxReturnBps: number;
  currentPrice: number;
  priceVersion: number;
  priceHistory: InvestmentPricePoint[];
  news: InvestmentNews[];
  positionQuantity: number;
  averagePrice: number;
  currentValue: number;
  totalBoughtAmount: number;
  totalSoldAmount: number;
  profitLoss: number;
};

export type InvestmentMarket = {
  status: "DRAFT" | "OPEN" | "PAUSED" | "CLOSED";
  investmentMode: InvestmentMode;
  scenarioSource: "AI_GENERATED" | "HISTORICAL_INDEX";
  settlementRule: "NEXT_DAY_MANDATORY" | "COURSE_END_FINAL";
  currentStep: number;
  assets: InvestmentAsset[];
};

export type InvestmentOrderHistory = {
  orderId: string;
  assetId: string;
  symbol: string;
  assetName: string;
  investmentMode: InvestmentMode | "UNKNOWN";
  side: "BUY" | "SELL";
  quantity: number;
  executedPrice: number;
  totalAmount: number;
  executedPriceStep: number;
  status: string;
  executedAt: string;
};

type MarketResponse = {
  status: InvestmentMarket["status"];
  investment_mode: InvestmentMode;
  scenario_source: InvestmentMarket["scenarioSource"];
  settlement_rule: InvestmentMarket["settlementRule"];
  current_step: number;
  assets: Array<{
    asset_id: string;
    symbol: string;
    name: string;
    company_profile: string;
    min_return_bps: number | null;
    max_return_bps: number | null;
    current_price: number | null;
    price_version: number | null;
    price_history: Array<{ step: number; price: number; change_rate_bps: number }>;
    news: Array<{
      event_id: string;
      title: string;
      content: string;
      status: string;
    }>;
    position_quantity?: number;
    average_price?: number;
    current_value?: number;
    total_bought_amount?: number;
    total_sold_amount?: number;
    profit_loss?: number;
  }>;
};

/**
 * 서버 응답을 화면이 기대하는 모양으로 맞춥니다.
 *
 * 빠진 목록 필드는 빈 배열로 둡니다. 서버가 화면보다 오래된 빌드일 때 없는 필드를 그대로
 * 읽으면 투자 화면 전체가 죽습니다.
 */
function toMarket(response: MarketResponse): InvestmentMarket {
  return {
    status: response.status,
    investmentMode: response.investment_mode === "SHORT_TERM" ? "SHORT_TERM" : "LONG_TERM",
    scenarioSource: response.scenario_source,
    settlementRule: response.settlement_rule,
    currentStep: response.current_step ?? 0,
    assets: (Array.isArray(response.assets) ? response.assets : [])
      .filter((asset) => asset?.current_price != null && asset?.price_version != null)
      .map((asset) => ({
        assetId: asset.asset_id,
        symbol: asset.symbol,
        name: asset.name,
        companyProfile: asset.company_profile,
        minReturnBps: asset.min_return_bps ?? 0,
        maxReturnBps: asset.max_return_bps ?? 0,
        currentPrice: asset.current_price!,
        priceVersion: asset.price_version!,
        priceHistory: (Array.isArray(asset.price_history) ? asset.price_history : []).map((point) => ({
          step: point.step,
          price: point.price,
          changeRateBps: point.change_rate_bps
        })),
        news: (Array.isArray(asset.news) ? asset.news : [])
          .filter((news) => ["APPROVED", "APPLIED"].includes(news.status))
          .map((news) => ({ eventId: news.event_id, title: news.title, content: news.content })),
        positionQuantity: asset.position_quantity ?? 0,
        averagePrice: asset.average_price ?? 0,
        currentValue: asset.current_value ?? 0,
        totalBoughtAmount: asset.total_bought_amount ?? 0,
        totalSoldAmount: asset.total_sold_amount ?? 0,
        profitLoss: asset.profit_loss ?? 0
      }))
  };
}

/**
 * 학급이 열어 둔 투자 시장을 모두 받아옵니다.
 *
 * 선생님은 단기와 장기를 함께 운영할 수 있으므로 결과는 0~2개입니다. 잠긴 방식과 아직
 * 공개하지 않은 시나리오는 서버에서 빠진 채로 옵니다.
 */
export async function fetchInvestmentMarkets(classroomId: string): Promise<InvestmentMarket[]> {
  const response = await apiRequest<{ markets: MarketResponse[] }>(
    `/classrooms/${classroomId}/investment-markets`
  );
  return (response.markets ?? []).map(toMarket);
}

export async function fetchInvestmentOrderHistory(classroomId: string): Promise<InvestmentOrderHistory[]> {
  const response = await apiRequest<{
    orders?: Array<{
      order_id?: string;
      asset_id?: string;
      symbol?: string;
      asset_name?: string;
      investment_mode?: InvestmentMode | "UNKNOWN";
      side?: "BUY" | "SELL";
      quantity?: number;
      executed_price?: number;
      total_amount?: number;
      executed_price_step?: number;
      status?: string;
      executed_at?: string;
    }>;
  }>(`/classrooms/${classroomId}/students/me/investment-portfolio`);
  return (response.orders ?? []).flatMap((order) => order.order_id && order.asset_id ? [{
    orderId: order.order_id,
    assetId: order.asset_id,
    symbol: order.symbol ?? "-",
    assetName: order.asset_name ?? "종목 정보 없음",
    investmentMode: order.investment_mode ?? "UNKNOWN",
    side: order.side === "SELL" ? "SELL" : "BUY",
    quantity: order.quantity ?? 0,
    executedPrice: order.executed_price ?? 0,
    totalAmount: order.total_amount ?? 0,
    executedPriceStep: order.executed_price_step ?? 0,
    status: order.status ?? "EXECUTED",
    executedAt: order.executed_at ?? ""
  }] : []);
}

export async function fetchInvestmentMarket(
  classroomId: string,
  investmentMode: InvestmentMode
): Promise<InvestmentMarket> {
  const response = await apiRequest<MarketResponse>(
    `/classrooms/${classroomId}/investment-market?investment_mode=${investmentMode}`
  );
  return toMarket(response);
}

export async function buyInvestment(
  classroomId: string,
  asset: InvestmentAsset,
  quantity: number
) {
  return placeInvestmentOrder(classroomId, asset, quantity, "BUY");
}

export async function sellInvestment(
  classroomId: string,
  asset: InvestmentAsset,
  quantity: number
) {
  return placeInvestmentOrder(classroomId, asset, quantity, "SELL");
}

async function placeInvestmentOrder(
  classroomId: string,
  asset: InvestmentAsset,
  quantity: number,
  side: "BUY" | "SELL"
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
      side,
      asset_id: asset.assetId,
      quantity,
      expected_price_version: asset.priceVersion
    })
  });
}
