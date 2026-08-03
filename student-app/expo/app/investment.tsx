import { StyleSheet, Text } from "react-native";

import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { StateCard } from "@/components/StateCard";
import { colors } from "@/theme/tokens";

export default function InvestmentScreen() {
  return (
    <Screen title="투자 광장" right={<BackButton />}>
      <StateCard title="투자 시장을 준비하고 있어요" tone="cream">
        <Text style={styles.description}>서버에서 시장과 투자 상품이 열리면 실제 가격과 내 손익을 이곳에 표시해요.</Text>
        <Text style={styles.notice}>화면에서 임의로 만든 가격이나 결과는 보여주지 않아요.</Text>
      </StateCard>
    </Screen>
  );
}

const styles = StyleSheet.create({
  description: { color: colors.ink900, fontSize: 17, lineHeight: 25, fontWeight: "800" },
  notice: { color: colors.muted600, fontSize: 14, lineHeight: 21 }
});
