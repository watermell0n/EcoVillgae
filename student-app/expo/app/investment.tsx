import { Pressable, StyleSheet, Text, View } from "react-native";

import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { StateCard } from "@/components/StateCard";
import { colors, radii } from "@/theme/tokens";

const assets = [
  { name: "거북이상사", price: 50, level: "안정형", description: "가격 변화가 작고 결과가 빨리 나와요." },
  { name: "호랑운송", price: 40, level: "변동형", description: "좋은 소식과 나쁜 소식에 따라 크게 움직여요." },
  { name: "독수리테크", price: 30, level: "도전형", description: "성공하면 많이 오르지만 손해도 날 수 있어요." }
];

export default function InvestmentScreen() {
  return (
    <Screen title="투자 광장" subtitle="SCR-STU-016·017 · 주문 API 계약 연결 전에는 조회만 제공합니다." right={<BackButton />}>
      <StateCard tone="cream">
        <Text style={styles.warning}>⚠️ 투자 결과는 화면에서 추정하지 않으며 서버가 확정한 값만 표시합니다.</Text>
      </StateCard>
      <View style={styles.grid}>
        {assets.map((asset) => (
          <StateCard key={asset.name} title={asset.name}>
            <Text style={styles.level}>{asset.level}</Text>
            <Text style={styles.description}>{asset.description}</Text>
            <Text style={styles.price}>1주 {asset.price} 비빔</Text>
            <Pressable disabled style={styles.disabled}><Text style={styles.disabledText}>주문 API 연결 예정</Text></Pressable>
          </StateCard>
        ))}
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  warning: { color: colors.orange500, fontSize: 16, fontWeight: "800" },
  grid: { flexDirection: "row", gap: 16 },
  level: { color: colors.purple500, fontWeight: "900" },
  description: { color: colors.muted600, lineHeight: 21 },
  price: { color: colors.ink900, fontSize: 19, fontWeight: "900" },
  disabled: { padding: 11, borderRadius: radii.control, alignItems: "center", backgroundColor: "#E9EEF4" },
  disabledText: { color: colors.muted600, fontWeight: "800" }
});
