import { Text, View, StyleSheet } from "react-native";
import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { colors } from "@/theme/tokens";

export default function CreditScreen() {
  return <Screen title="신용등급" right={<BackButton />}><View style={styles.wrap}><Text style={styles.title}>금융 약속을 지켜요</Text><Text style={styles.copy}>빌린 돈을 약속한 때에 갚고, 거래를 책임 있게 하면 신용을 쌓을 수 있어요.</Text></View></Screen>;
}
const styles = StyleSheet.create({ wrap: { gap: 14, paddingTop: 20 }, title: { color: colors.ink900, fontSize: 26, fontWeight: "900" }, copy: { maxWidth: 600, color: colors.muted600, fontSize: 17, lineHeight: 26 } });
