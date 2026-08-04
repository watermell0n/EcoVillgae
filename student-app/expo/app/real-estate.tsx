import { Text, View, StyleSheet } from "react-native";
import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { colors } from "@/theme/tokens";

export default function RealEstateScreen() {
  return <Screen title="부동산" right={<BackButton />}><View style={styles.wrap}><Text style={styles.title}>집과 땅의 가치</Text><Text style={styles.copy}>집을 고를 때는 가격뿐 아니라 위치, 필요한 비용, 생활에 필요한 조건을 함께 살펴봐요.</Text></View></Screen>;
}
const styles = StyleSheet.create({ wrap: { gap: 14, paddingTop: 20 }, title: { color: colors.ink900, fontSize: 26, fontWeight: "900" }, copy: { maxWidth: 600, color: colors.muted600, fontSize: 17, lineHeight: 26 } });
