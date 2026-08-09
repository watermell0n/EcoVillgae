import { useMemo, useState } from "react";
import { Pressable, StyleSheet, Text, TextInput, View } from "react-native";
import { useQuery, useQueryClient } from "@tanstack/react-query";

import { ApiError } from "@/api/client";
import { useAuth } from "@/auth/AuthProvider";
import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import {
  buyInvestment,
  fetchInvestmentOrderHistory,
  fetchInvestmentMarkets,
  sellInvestment,
  type InvestmentAsset,
  type InvestmentMarket,
  type InvestmentMode,
  type InvestmentOrderHistory
} from "@/features/investment/api";
import { fetchWallet } from "@/features/student/api";
import { colors } from "@/theme/tokens";

const MODE_LABELS: Record<InvestmentMode, string> = {
  SHORT_TERM: "단기 투자",
  LONG_TERM: "장기 투자"
};

const SPARK_BLOCKS = ["▁", "▂", "▃", "▄", "▅", "▆", "▇", "█"];

// 한 번에 살 수 있는 주 수입니다. 서버도 1~100주만 받습니다.
const MIN_QUANTITY = 1;
const MAX_QUANTITY = 100;

/** 입력칸의 글자를 실제로 주문할 수 있는 주 수로 정리합니다. */
function clampQuantity(text: string) {
  const parsed = Number.parseInt(text.replace(/[^0-9]/g, ""), 10);
  if (!Number.isFinite(parsed)) return MIN_QUANTITY;
  return Math.min(MAX_QUANTITY, Math.max(MIN_QUANTITY, parsed));
}

/** 공개된 가격만으로 흐름을 그립니다. 아직 공개되지 않은 가격은 서버가 보내지 않습니다. */
function sparkline(prices: number[]) {
  if (prices.length === 0) return "";
  const lowest = Math.min(...prices);
  const highest = Math.max(...prices);
  if (highest === lowest) return SPARK_BLOCKS[3].repeat(prices.length);
  return prices
    .map((price) => {
      const ratio = (price - lowest) / (highest - lowest);
      return SPARK_BLOCKS[Math.round(ratio * (SPARK_BLOCKS.length - 1))];
    })
    .join("");
}

/**
 * 이 기업이 하루에 얼마나 오르내릴 수 있는지 알려 줍니다.
 *
 * 학생이 사기 전에 "잘되면 얼마, 잘못되면 얼마"를 스스로 가늠할 수 있어야 합니다.
 */
function returnBandText(asset: InvestmentAsset) {
  if (asset.minReturnBps === 0 && asset.maxReturnBps === 0) return "";
  const low = (asset.minReturnBps / 100).toFixed(1);
  const high = (asset.maxReturnBps / 100).toFixed(1);
  return `하루 ${low}% ~ +${high}%`;
}

function changeText(asset: InvestmentAsset) {
  const latest = asset.priceHistory[asset.priceHistory.length - 1];
  if (!latest) return { text: "-", negative: false };
  const percent = latest.changeRateBps / 100;
  return {
    text: `${percent > 0 ? "+" : ""}${percent.toFixed(1)}%`,
    negative: percent < 0
  };
}

export default function InvestmentScreen() {
  const { identity } = useAuth();
  const classroomId = identity?.classroomId;
  const [selected, setSelected] = useState<InvestmentMode | "HISTORY" | null>(null);
  const marketQuery = useQuery({
    queryKey: ["investment-markets", classroomId],
    queryFn: () => fetchInvestmentMarkets(classroomId!),
    enabled: Boolean(classroomId),
    // 실시간 연결이 막힌 학교 네트워크에서도 교사 조작을 오래 놓치지 않게 보완합니다.
    refetchInterval: 15_000
  });
  const historyQuery = useQuery({
    queryKey: ["investment-order-history", classroomId],
    queryFn: () => fetchInvestmentOrderHistory(classroomId!),
    enabled: Boolean(classroomId),
    refetchInterval: 15_000
  });
  const markets = marketQuery.data;
  const load = async () => { await marketQuery.refetch(); };
  const error = marketQuery.error instanceof Error
    ? marketQuery.error.message
    : "투자 정보를 불러오지 못했어요.";

  // 선생님이 잠근 방식과 아직 공개하지 않은 시나리오는 서버에서 이미 빠져 있습니다.
  const openMarkets = markets ?? [];
  const orderHistory = historyQuery.data ?? [];
  // 투자 내역 탭을 보는 동안에는 종목 탭이 함께 강조되지 않도록 active를 비웁니다.
  const active = useMemo(
    () => selected === "HISTORY"
      ? undefined
      : openMarkets.find((market) => market.investmentMode === selected) ?? openMarkets[0],
    [openMarkets, selected]
  );
  const showHistory = selected === "HISTORY" || (openMarkets.length === 0 && orderHistory.length > 0);

  return (
    <Screen title="투자 광장" right={<BackButton />}>
      {marketQuery.isPending && historyQuery.isPending ? (
        <View style={styles.empty}>
          <Text style={styles.emptyTitle}>투자 정보를 불러오는 중이에요</Text>
        </View>
      ) : openMarkets.length === 0 && orderHistory.length === 0 ? (
        <View style={styles.empty}>
          <Text style={styles.emptyTitle}>지금 열려 있는 투자 수업이 없어요</Text>
          <Text style={styles.description}>
            {marketQuery.isError && historyQuery.isError ? error : "선생님이 단기 투자나 장기 투자를 열면 이곳에 나타나요."}
          </Text>
        </View>
      ) : (
        <>
          <View accessibilityRole="tablist" style={styles.tabs}>
            {openMarkets.map((market) => (
              <Pressable
                accessibilityRole="tab"
                accessibilityState={{ selected: active?.investmentMode === market.investmentMode }}
                key={market.investmentMode}
                onPress={() => setSelected(market.investmentMode)}
                style={[styles.tab, active?.investmentMode === market.investmentMode && styles.tabActive]}
              >
                <Text style={[
                  styles.tabText,
                  active?.investmentMode === market.investmentMode && styles.tabTextActive
                ]}>
                  {MODE_LABELS[market.investmentMode]}
                </Text>
              </Pressable>
            ))}
            <Pressable
              accessibilityRole="tab"
              accessibilityState={{ selected: showHistory }}
              onPress={() => setSelected("HISTORY")}
              style={[styles.tab, showHistory && styles.tabActive]}
            >
              <Text style={[styles.tabText, showHistory && styles.tabTextActive]}>투자 내역</Text>
            </Pressable>
          </View>
          {showHistory ? <InvestmentHistoryView orders={orderHistory} /> : active ? <MarketView market={active} onTraded={load} /> : null}
        </>
      )}
    </Screen>
  );
}

function MarketView({
  market,
  onTraded
}: Readonly<{ market: InvestmentMarket; onTraded: () => Promise<void> }>) {
  const { identity } = useAuth();
  const queryClient = useQueryClient();
  const classroomId = identity?.classroomId;
  const currencyName = identity?.currencyName ?? "비빔";
  const walletQuery = useQuery({
    queryKey: ["wallet", classroomId],
    queryFn: () => fetchWallet(classroomId!),
    enabled: Boolean(classroomId),
    refetchInterval: 10_000
  });
  const [selectedAssetId, setSelectedAssetId] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  // 몇 주를 살지는 학생이 직접 정합니다. 입력 중에는 빈 칸도 있을 수 있으므로 글자로 들고
  // 있다가, 실제로 주문할 때 쓰는 값만 숫자로 정리합니다.
  const [quantityText, setQuantityText] = useState(String(MIN_QUANTITY));
  const quantity = clampQuantity(quantityText);

  const current = market.assets.find((asset) => asset.assetId === selectedAssetId) ?? market.assets[0];
  const tradable = market.status === "OPEN";
  const totalProfitLoss = market.assets.reduce((sum, asset) => sum + asset.profitLoss, 0);
  const buyTotal = current ? current.currentPrice * quantity : 0;
  const insufficientBalance = Boolean(walletQuery.data && walletQuery.data.balance < buyTotal);

  function changeQuantity(step: number) {
    setQuantityText(String(clampQuantity(String(quantity + step))));
  }

  async function trade(asset: InvestmentAsset, quantity: number, side: "BUY" | "SELL") {
    if (!classroomId) return;
    setBusy(true);
    setNotice("");
    try {
      const result = side === "BUY"
        ? await buyInvestment(classroomId, asset, quantity)
        : await sellInvestment(classroomId, asset, quantity);
      await queryClient.invalidateQueries({ queryKey: ["wallet", classroomId] });
      await queryClient.invalidateQueries({ queryKey: ["investment-order-history", classroomId] });
      await onTraded();
      setNotice(
        `${asset.name} ${quantity}주를 ${result.total_amount.toLocaleString("ko-KR")} ${currencyName}에 ${side === "BUY" ? "샀어요" : "팔았어요"}. `
        + `지금 보유 ${result.position_quantity}주 · 평균단가 ${result.average_price.toLocaleString("ko-KR")} ${currencyName}`
      );
    } catch (cause) {
      if (
        cause instanceof ApiError
        && ["PRICE_CHANGED", "MARKET_NOT_OPEN", "NEWS_NOT_PUBLISHED"].includes(cause.code)
      ) {
        await onTraded();
      }
      setNotice(cause instanceof Error ? cause.message : "투자하지 못했어요. 잠시 후 다시 해 주세요.");
    } finally {
      setBusy(false);
    }
  }

  if (market.assets.length === 0) {
    return (
      <View style={styles.empty}>
        <Text style={styles.emptyTitle}>아직 공개된 종목이 없어요</Text>
        <Text style={styles.description}>선생님이 첫 가격을 공개하면 종목이 보여요.</Text>
      </View>
    );
  }

  return (
    <View style={styles.content}>
      <View style={styles.summaryRow}>
        <Text style={styles.sectionTitle}>{MODE_LABELS[market.investmentMode]}</Text>
        <Text style={styles.summaryMeta}>
          {market.currentStep}일차 ·{" "}
          {market.status === "CLOSED"
            ? "정산 완료"
            : market.status === "PAUSED"
              ? "선생님이 잠시 멈춤"
              : market.settlementRule === "NEXT_DAY_MANDATORY" ? "다음 날 결과 확정" : "마지막 날 최종 정산"}
        </Text>
      </View>

      {market.status === "CLOSED" ? (
        <View style={styles.resultCard}>
          <Text style={styles.resultTitle}>최종 투자 결과</Text>
          <Text style={[styles.resultAmount, totalProfitLoss < 0 && styles.loss]}>
            {totalProfitLoss > 0 ? "+" : ""}{totalProfitLoss.toLocaleString("ko-KR")} {currencyName}
          </Text>
          <Text style={styles.stockNews}>정산 금액은 지갑에 반영됐어요.</Text>
        </View>
      ) : null}

      <View style={styles.stockList}>
        {market.assets.map((asset) => {
          const change = changeText(asset);
          return (
            <Pressable
              key={asset.assetId}
              onPress={() => setSelectedAssetId(asset.assetId)}
              style={[styles.stockRow, current?.assetId === asset.assetId && styles.stockRowActive]}
            >
              <View style={styles.stockRowMain}>
                <Text style={styles.company}>{asset.name}</Text>
                <Text style={styles.stockNews}>
                  {asset.news[0]?.title ?? asset.companyProfile}
                </Text>
              </View>
              <View style={styles.holdingRight}>
                <Text style={styles.holdingValue}>
                  {asset.currentPrice.toLocaleString("ko-KR")} {currencyName}
                </Text>
                {/* 1주 금액 바로 밑에 손해·이득 범위를 함께 보여 줍니다. */}
                {returnBandText(asset) ? (
                  <Text style={styles.returnBand}>{returnBandText(asset)}</Text>
                ) : null}
                {asset.positionQuantity > 0 ? (
                  <Text style={styles.positionText}>보유 {asset.positionQuantity}주</Text>
                ) : null}
                {asset.totalBoughtAmount > 0 ? (
                  <Text style={[styles.positionText, asset.profitLoss < 0 && styles.loss]}>
                    손익 {asset.profitLoss > 0 ? "+" : ""}{asset.profitLoss.toLocaleString("ko-KR")} {currencyName}
                  </Text>
                ) : null}
                <Text style={[styles.stockChange, change.negative && styles.loss]}>{change.text}</Text>
              </View>
            </Pressable>
          );
        })}
      </View>

      {current ? (
        <View style={styles.stockDetail}>
          <View style={styles.marketLine}>
            <Text style={styles.company}>{current.name}</Text>
            <Text style={[styles.stockChange, changeText(current).negative && styles.loss]}>
              {changeText(current).text}
            </Text>
          </View>
          <Text style={styles.graph}>{sparkline(current.priceHistory.map((point) => point.price))}</Text>
          <Text style={styles.stockNews}>{current.companyProfile}</Text>
          <View style={styles.positionCard}>
            <Text style={styles.positionHeading}>내 투자</Text>
            <Text style={styles.positionText}>
              보유 {current.positionQuantity}주 · 평균단가 {current.averagePrice.toLocaleString("ko-KR")} {currencyName}
            </Text>
            <Text style={styles.positionText}>
              평가액 {current.currentValue.toLocaleString("ko-KR")} {currencyName}
              {" · "}총 매수 {current.totalBoughtAmount.toLocaleString("ko-KR")} {currencyName}
              {" · "}총 매도·정산 {current.totalSoldAmount.toLocaleString("ko-KR")} {currencyName}
            </Text>
            <Text style={[styles.positionProfit, current.profitLoss < 0 && styles.loss]}>
              손익 {current.profitLoss > 0 ? "+" : ""}{current.profitLoss.toLocaleString("ko-KR")} {currencyName}
            </Text>
          </View>
          {current.news.map((news) => (
            <View key={news.eventId} style={styles.newsCard}>
              <Text style={styles.newsTitle}>{news.title}</Text>
              <Text style={styles.stockNews}>{news.content}</Text>
            </View>
          ))}
          {current.news.length === 0 ? (
            <Text style={styles.stockNews}>아직 오늘의 소식이 없어요.</Text>
          ) : null}
        </View>
      ) : null}

      {notice ? <Text style={styles.notice}>{notice}</Text> : null}

      {current && market.status !== "CLOSED" ? (
        <View style={styles.decisionRow}>
          {!tradable ? <Text style={styles.pausedNotice}>선생님이 투자를 다시 열 때까지 주문할 수 없어요.</Text> : null}
          <View style={styles.stepper}>
            <Pressable
              accessibilityLabel="한 주 줄이기"
              disabled={busy || !tradable || quantity <= MIN_QUANTITY}
              onPress={() => changeQuantity(-1)}
              style={[styles.stepperButton, (busy || !tradable || quantity <= MIN_QUANTITY) && styles.buttonBusy]}
            >
              <Text style={styles.stepperText}>−</Text>
            </Pressable>
            <TextInput
              accessibilityLabel="살 주 수"
              inputMode="numeric"
              keyboardType="number-pad"
              onChangeText={setQuantityText}
              onBlur={() => setQuantityText(String(quantity))}
              selectTextOnFocus
              style={styles.stepperInput}
              value={quantityText}
            />
            <Pressable
              accessibilityLabel="한 주 늘리기"
              disabled={busy || !tradable || quantity >= MAX_QUANTITY}
              onPress={() => changeQuantity(1)}
              style={[styles.stepperButton, (busy || !tradable || quantity >= MAX_QUANTITY) && styles.buttonBusy]}
            >
              <Text style={styles.stepperText}>＋</Text>
            </Pressable>
          </View>
          <Pressable
            disabled={busy || !tradable || walletQuery.isPending || insufficientBalance}
            onPress={() => void trade(current, quantity, "BUY")}
            style={[styles.primaryButton, (busy || !tradable || walletQuery.isPending || insufficientBalance) && styles.buttonBusy]}
          >
            <Text style={styles.primaryText}>{busy ? "처리 중" : `${quantity}주 사기`}</Text>
          </Pressable>
          {market.investmentMode === "LONG_TERM" && current.positionQuantity > 0 ? (
            <Pressable
              disabled={busy || !tradable || quantity > current.positionQuantity}
              onPress={() => void trade(current, quantity, "SELL")}
              style={[
                styles.sellButton,
                (busy || !tradable || quantity > current.positionQuantity) && styles.buttonBusy
              ]}
            >
              <Text style={styles.sellText}>{quantity}주 팔기</Text>
            </Pressable>
          ) : null}
          <Text style={styles.totalPreview}>
            모두 {(current.currentPrice * quantity).toLocaleString("ko-KR")} {currencyName}
          </Text>
          {insufficientBalance ? <Text style={styles.shortageNotice}>잔액이 부족해 살 수 없어요. 현재 지갑에는 {walletQuery.data?.balance.toLocaleString("ko-KR")} {currencyName}이 있어요.</Text> : null}
        </View>
      ) : null}
    </View>
  );
}

function InvestmentHistoryView({ orders }: Readonly<{ orders: InvestmentOrderHistory[] }>) {
  const { identity } = useAuth();
  const currencyName = identity?.currencyName ?? "비빔";
  if (orders.length === 0) {
    return <View style={styles.empty}><Text style={styles.emptyTitle}>아직 투자 내역이 없어요</Text><Text style={styles.description}>종목을 사고팔면 거래 가격과 수량이 여기에 남아요.</Text></View>;
  }
  return <View style={styles.historyList}>
    <View style={styles.summaryRow}><Text style={styles.sectionTitle}>투자 내역</Text><Text style={styles.summaryMeta}>최근 거래부터 표시해요</Text></View>
    {orders.map((order) => <View key={order.orderId} style={styles.historyRow}>
      <View style={styles.historyMain}><Text style={styles.company}>{order.assetName}</Text><Text style={styles.stockNews}>{order.investmentMode === "SHORT_TERM" ? "단기 투자" : order.investmentMode === "LONG_TERM" ? "장기 투자" : "투자"} · {order.executedPriceStep}일차 · {order.executedAt ? new Date(order.executedAt).toLocaleString("ko-KR") : "시간 확인 중"}</Text></View>
      <View style={styles.holdingRight}><Text style={[styles.historySide, order.side === "SELL" && styles.sellHistory]}>{order.side === "BUY" ? "매수" : "매도·정산"} {order.quantity}주</Text><Text style={styles.positionText}>주당 {order.executedPrice.toLocaleString("ko-KR")} · 합계 {order.totalAmount.toLocaleString("ko-KR")} {currencyName}</Text></View>
    </View>)}
  </View>;
}

const styles = StyleSheet.create({
  tabs: { flexDirection: "row", alignSelf: "flex-start", borderBottomWidth: 1, borderBottomColor: colors.lineSoft },
  tab: { minWidth: 132, paddingHorizontal: 18, paddingVertical: 14, borderBottomWidth: 3, borderBottomColor: "transparent" },
  tabActive: { borderBottomColor: colors.orange500 },
  tabText: { color: colors.muted600, fontSize: 16, fontWeight: "800", textAlign: "center" },
  tabTextActive: { color: colors.ink900 },
  content: { width: "100%", maxWidth: 960, gap: 24, paddingTop: 12 },
  summaryRow: { flexDirection: "row", flexWrap: "wrap", alignItems: "baseline", justifyContent: "space-between", gap: 10 },
  summaryMeta: { color: colors.muted600, fontSize: 14, fontWeight: "800" },
  sectionTitle: { color: colors.ink900, fontSize: 23, fontWeight: "900" },
  marketLine: { flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 20 },
  company: { color: colors.ink900, fontSize: 19, fontWeight: "900" },
  stockList: { borderTopWidth: 1, borderTopColor: colors.lineSoft },
  stockRow: { minHeight: 76, flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 18, paddingHorizontal: 12, borderBottomWidth: 1, borderBottomColor: colors.lineSoft },
  stockRowMain: { flexShrink: 1, gap: 0 },
  stockRowActive: { backgroundColor: colors.washMint },
  stockNews: { marginTop: 5, color: colors.muted600, fontSize: 14, fontWeight: "700", lineHeight: 21 },
  stockChange: { color: colors.green500, fontSize: 18, fontWeight: "900" },
  loss: { color: colors.red500 },
  stockDetail: { gap: 10, padding: 18, borderWidth: 1, borderColor: colors.lineSoft, borderRadius: 6, backgroundColor: colors.white },
  graph: { color: colors.green500, fontSize: 34, letterSpacing: 2, fontWeight: "900" },
  newsCard: { gap: 2, paddingTop: 12, borderTopWidth: 1, borderTopColor: colors.lineSoft },
  newsTitle: { color: colors.ink900, fontSize: 16, fontWeight: "900" },
  notice: { color: colors.ink900, fontSize: 15, fontWeight: "800", lineHeight: 23 },
  holdingRight: { alignItems: "flex-end", gap: 4 },
  holdingValue: { color: colors.ink900, fontSize: 18, fontWeight: "900" },
  returnBand: { color: colors.muted600, fontSize: 13, fontWeight: "800" },
  positionText: { color: colors.muted600, fontSize: 13, fontWeight: "800", lineHeight: 19 },
  positionCard: { gap: 5, padding: 13, borderRadius: 8, backgroundColor: colors.washMint },
  positionHeading: { color: colors.ink900, fontSize: 15, fontWeight: "900" },
  positionProfit: { color: colors.green500, fontSize: 17, fontWeight: "900" },
  resultCard: { gap: 5, padding: 18, borderWidth: 1, borderColor: colors.lineSoft, borderRadius: 8, backgroundColor: colors.white },
  resultTitle: { color: colors.ink900, fontSize: 17, fontWeight: "900" },
  resultAmount: { color: colors.green500, fontSize: 26, fontWeight: "900" },
  decisionRow: { flexDirection: "row", flexWrap: "wrap", alignItems: "center", gap: 12 },
  stepper: { flexDirection: "row", alignItems: "center", borderWidth: 1, borderColor: colors.lineSoft, borderRadius: 8, backgroundColor: colors.white, overflow: "hidden" },
  stepperButton: { minWidth: 48, minHeight: 46, alignItems: "center", justifyContent: "center" },
  stepperText: { color: colors.ink900, fontSize: 22, fontWeight: "900" },
  stepperInput: { minWidth: 64, minHeight: 46, paddingHorizontal: 6, borderLeftWidth: 1, borderRightWidth: 1, borderColor: colors.lineSoft, color: colors.ink900, fontSize: 17, fontWeight: "900", textAlign: "center" },
  totalPreview: { color: colors.muted600, fontSize: 15, fontWeight: "800" },
  primaryButton: { alignSelf: "flex-start", minHeight: 46, justifyContent: "center", paddingHorizontal: 22, borderRadius: 8, backgroundColor: colors.orange500 },
  primaryText: { color: colors.white, fontSize: 15, fontWeight: "900" },
  sellButton: { alignSelf: "flex-start", minHeight: 46, justifyContent: "center", paddingHorizontal: 22, borderWidth: 1, borderColor: colors.orange500, borderRadius: 8, backgroundColor: colors.white },
  sellText: { color: colors.orange500, fontSize: 15, fontWeight: "900" },
  pausedNotice: { width: "100%", color: colors.red500, fontSize: 14, fontWeight: "800" },
  shortageNotice: { width: "100%", color: colors.red500, fontSize: 14, fontWeight: "800" },
  historyList: { width: "100%", maxWidth: 960, gap: 0, paddingTop: 12 }, historyRow: { minHeight: 82, flexDirection: "row", alignItems: "center", justifyContent: "space-between", gap: 18, paddingHorizontal: 12, borderBottomWidth: 1, borderBottomColor: colors.lineSoft, backgroundColor: colors.white }, historyMain: { flex: 1, minWidth: 0 }, historySide: { color: colors.orange500, fontSize: 15, fontWeight: "900" }, sellHistory: { color: colors.green500 },
  buttonBusy: { opacity: 0.6 },
  empty: { maxWidth: 760, gap: 10, paddingVertical: 34, borderTopWidth: 1, borderBottomWidth: 1, borderColor: colors.lineSoft },
  emptyTitle: { color: colors.ink900, fontSize: 23, fontWeight: "900" },
  description: { color: colors.muted600, fontSize: 16, lineHeight: 24, fontWeight: "700" }
});
