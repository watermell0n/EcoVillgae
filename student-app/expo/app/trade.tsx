import { StyleSheet, Text } from "react-native";

import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { StateCard } from "@/components/StateCard";
import { colors } from "@/theme/tokens";

export default function TradeScreen() {
  return (
    <Screen title="친구 거래소" right={<BackButton />}>
      <StateCard title="거래할 친구를 선택해요">
        <Text style={styles.description}>친구 목록 조회 API가 연결되면 이곳에서 상대를 고르고 거래를 제안할 수 있어요.</Text>
      </StateCard>
    </Screen>
  );
}

const styles = StyleSheet.create({
  description: { color: colors.muted600, fontSize: 16, lineHeight: 24 }
});
