import { useCallback, useEffect, useMemo, useState } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import {
  buyInvestment,
  fetchInvestmentMarkets,
  type InvestmentAsset,
  type InvestmentMarket,
  type InvestmentMode
} from "@/features/investment/api";
import { colors } from "@/theme/tokens";

const MODE_LABELS: Record<InvestmentMode, string> = {
  SHORT_TERM: "단기 투자",
  LONG_TERM: "장기 투자"
};

const SPARK_BLOCKS = ["▁", "▂", "▃", "▄", "▅", "▆", "▇", "█"];

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
  const [markets, setMarkets] = useState<InvestmentMarket[] | null>(null);
  const [selected, setSelected] = useState<InvestmentMode | null>(null);
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    if (!classroomId) return;
    try {
      setMarkets(await fetchInvestmentMarkets(classroomId));
      setError("");
    } catch (cause) {
      setMarkets([]);
      setError(cause instanceof Error ? cause.message : "투자 정보를 불러오지 못했어요.");
    }
  }, [classroomId]);

  useEffect(() => { void load(); }, [load]);

  // 선생님이 잠근 방식과 아직 공개하지 않은 시나리오는 서버에서 이미 빠져 있습니다.
  const openMarkets = markets ?? [];
  const active = useMemo(
    () => openMarkets.find((market) => market.investmentMode === selected) ?? openMarkets[0],
    [openMarkets, selected]
  );

  return (
    <Screen title="투자 광장" right={<BackButton />}>
      {markets === null ? (
        <View style={styles.empty}>
          <Text style={styles.emptyTitle}>투자 정보를 불러오는 중이에요</Text>
        </View>
      ) : openMarkets.length === 0 ? (
        <View style={styles.empty}>
          <Text style={styles.emptyTitle}>지금 열려 있는 투자 수업이 없어요</Text>
          <Text style={styles.description}>
            {error || "선생님이 단기 투자나 장기 투자를 열면 이곳에 나타나요."}
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
          </View>
          {active ? <MarketView market={active} onTraded={load} /> : null}
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
  const classroomId = identity?.classroomId;
  const currencyName = identity?.currencyName ?? "비빔";
  const [selectedAssetId, setSelectedAssetId] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");

  const current = market.assets.find((asset) => asset.assetId === selectedAssetId) ?? market.assets[0];

  async function buy(asset: InvestmentAsset, quantity: number) {
    if (!classroomId) return;
    setBusy(true);
    setNotice("");
    try {
      const result = await buyInvestment(classroomId, asset, quantity);
      await onTraded();
      setNotice(
        `${asset.name} ${quantity}주를 ${result.total_amount.toLocaleString("ko-KR")} ${currencyName}에 샀어요. `
        + `지금 보유 ${result.position_quantity}주 · 평균단가 ${result.average_price.toLocaleString("ko-KR")} ${currencyName}`
      );
    } catch (cause) {
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
          {market.settlementRule === "NEXT_DAY_MANDATORY" ? "다음 날 결과 확정" : "마지막 날 최종 정산"}
        </Text>
      </View>

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

      {current ? (
        <View style={styles.decisionRow}>
          <Pressable
            disabled={busy}
            onPress={() => void buy(current, 1)}
            style={[styles.outlineButton, busy && styles.buttonBusy]}
          >
            <Text style={styles.outlineText}>{busy ? "처리 중" : "1주 사기"}</Text>
          </Pressable>
          <Pressable
            disabled={busy}
            onPress={() => void buy(current, 5)}
            style={[styles.primaryButton, busy && styles.buttonBusy]}
          >
            <Text style={styles.primaryText}>{busy ? "처리 중" : "5주 사기"}</Text>
          </Pressable>
        </View>
      ) : null}
    </View>
  );
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
  decisionRow: { flexDirection: "row", gap: 12 },
  primaryButton: { alignSelf: "flex-start", minHeight: 46, justifyContent: "center", paddingHorizontal: 22, borderRadius: 8, backgroundColor: colors.orange500 },
  primaryText: { color: colors.white, fontSize: 15, fontWeight: "900" },
  outlineButton: { minHeight: 46, justifyContent: "center", paddingHorizontal: 22, borderWidth: 1, borderColor: colors.lineSoft, borderRadius: 8, backgroundColor: colors.white },
  outlineText: { color: colors.ink900, fontSize: 15, fontWeight: "900" },
  buttonBusy: { opacity: 0.6 },
  empty: { maxWidth: 760, gap: 10, paddingVertical: 34, borderTopWidth: 1, borderBottomWidth: 1, borderColor: colors.lineSoft },
  emptyTitle: { color: colors.ink900, fontSize: 23, fontWeight: "900" },
  description: { color: colors.muted600, fontSize: 16, lineHeight: 24, fontWeight: "700" }
});
