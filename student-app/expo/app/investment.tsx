import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { ActivityIndicator, Pressable, StyleSheet, Text, TextInput, View } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { StateCard } from "@/components/StateCard";
import {
  buyInvestment,
  fetchInvestmentMarket,
  type InvestmentAsset
} from "@/features/investment/api";
import { colors, radii } from "@/theme/tokens";

export default function InvestmentScreen() {
  const { identity } = useAuth();
  const queryClient = useQueryClient();
  const [amounts, setAmounts] = useState<Record<string, string>>({});
  const [resultMessage, setResultMessage] = useState("");
  const market = useQuery({
    queryKey: ["investment-market", identity?.classroomId],
    queryFn: () => fetchInvestmentMarket(identity!.classroomId),
    enabled: Boolean(identity)
  });
  const buy = useMutation({
    mutationFn: ({ asset, quantity }: { asset: InvestmentAsset; quantity: number }) =>
      buyInvestment(identity!.classroomId, asset, quantity),
    onSuccess: async (result) => {
      setResultMessage(`${result.total_amount.toLocaleString("ko-KR")} 비를 투자했습니다. 현재 ${result.position_quantity}주를 보유하고 있어요.`);
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["investment-market", identity?.classroomId] }),
        queryClient.invalidateQueries({ queryKey: ["wallet", identity?.classroomId] })
      ]);
    }
  });
  const modeDescription = useMemo(() => {
    if (!market.data) return "";
    return market.data.investmentMode === "SHORT_TERM"
      ? "오늘 넣은 투자금은 다음 날 결과가 공개되면 전량 정산돼요. 이야기 속 단서를 잘 읽어 보세요."
      : "매일 새 이야기를 보고 추가 투자·보유·매도를 결정할 수 있어요. 마지막 날 남은 보유분은 모두 정산돼요.";
  }, [market.data]);

  function quantityFor(asset: InvestmentAsset) {
    const amount = Number(amounts[asset.assetId] ?? "0");
    return Number.isFinite(amount) ? Math.floor(amount / asset.currentPrice) : 0;
  }

  return (
    <Screen title="투자 광장" right={<BackButton />}>
      {market.isPending ? <ActivityIndicator color={colors.purple500} /> : null}
      {market.isError ? <StateCard title="투자 시장을 열 수 없어요" tone="cream"><Text style={styles.error}>{market.error.message}</Text></StateCard> : null}
      {market.data ? (
        <>
          <StateCard title={market.data.investmentMode === "SHORT_TERM" ? "단기 투자" : "장기 투자"} tone="mint">
            <Text style={styles.mode}>{modeDescription}</Text>
            <Text style={styles.meta}>현재 {market.data.currentStep}일차 · {market.data.status === "OPEN" ? "매수 가능" : "시장 대기"}</Text>
          </StateCard>
          {resultMessage ? <Text style={styles.success}>{resultMessage}</Text> : null}
          <View style={styles.grid}>
            {market.data.assets.map((asset) => {
              const quantity = quantityFor(asset);
              const amount = Number(amounts[asset.assetId] ?? "0");
              const news = asset.news[0];
              return (
                <StateCard key={asset.assetId} title={`${asset.name} · ${asset.symbol}`} tone="cream">
                  <Text style={styles.profile}>{asset.companyProfile}</Text>
                  {news ? <View style={styles.news}><Text style={styles.newsTitle}>{news.title}</Text><Text style={styles.newsBody}>{news.content}</Text></View>
                    : <Text style={styles.waiting}>교사가 오늘의 AI 이야기를 검토하고 있어요.</Text>}
                  <Text style={styles.price}>현재가 {asset.currentPrice.toLocaleString("ko-KR")} 비</Text>
                  <TextInput
                    accessibilityLabel={`${asset.name} 투자금`}
                    keyboardType="number-pad"
                    value={amounts[asset.assetId] ?? ""}
                    onChangeText={(value) => setAmounts((current) => ({ ...current, [asset.assetId]: value.replace(/[^0-9]/g, "") }))}
                    placeholder="투자할 금액을 입력하세요"
                    style={styles.input}
                  />
                  <Text style={styles.calculation}>{quantity > 0 ? `${quantity}주 · 실제 사용 ${(quantity * asset.currentPrice).toLocaleString("ko-KR")} 비` : "현재가 이상의 금액을 입력해 주세요."}</Text>
                  <Pressable
                    disabled={market.data.status !== "OPEN" || !news || quantity < 1 || quantity > 100 || buy.isPending || amount < asset.currentPrice}
                    onPress={() => buy.mutate({ asset, quantity })}
                    style={({ pressed }) => [styles.action, pressed && styles.pressed, (quantity < 1 || !news || buy.isPending) && styles.disabled]}
                  >
                    <Text style={styles.actionText}>{buy.isPending ? "매수 처리 중" : "이 종목 매수하기"}</Text>
                  </Pressable>
                </StateCard>
              );
            })}
          </View>
          {buy.isError ? <Text style={styles.error}>{buy.error.message}</Text> : null}
        </>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  grid: { gap: 16 },
  mode: { color: colors.ink900, fontSize: 16, lineHeight: 24, fontWeight: "800" },
  meta: { color: colors.purple500, fontSize: 14, fontWeight: "900" },
  profile: { color: colors.muted600, fontSize: 14, lineHeight: 21 },
  news: { gap: 7, padding: 14, borderRadius: radii.control, backgroundColor: colors.washBlue },
  newsTitle: { color: colors.ink900, fontSize: 17, fontWeight: "900" },
  newsBody: { color: colors.ink900, fontSize: 15, lineHeight: 23 },
  waiting: { color: colors.muted600, fontWeight: "700" },
  price: { color: colors.purple500, fontSize: 20, fontWeight: "900" },
  input: { paddingHorizontal: 15, paddingVertical: 12, borderWidth: 1, borderColor: colors.muted600, borderRadius: radii.control, color: colors.ink900, backgroundColor: colors.white },
  calculation: { color: colors.muted600, fontSize: 13, fontWeight: "700" },
  action: { alignItems: "center", padding: 13, borderRadius: radii.control, backgroundColor: colors.purple500 },
  actionText: { color: colors.white, fontWeight: "900" },
  pressed: { opacity: 0.75 },
  disabled: { opacity: 0.4 },
  success: { padding: 14, borderRadius: radii.control, color: colors.ink900, backgroundColor: colors.washBlue, fontWeight: "800" },
  error: { color: colors.red500, fontWeight: "700" }
});
