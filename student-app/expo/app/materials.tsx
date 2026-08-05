import { Pressable, StyleSheet, Text, View } from "react-native";
import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { colors, radii } from "@/theme/tokens";
const materials = [{ title: "월급과 실수령액", detail: "월급, 세금, 실수령액의 관계를 알아봐요." }, { title: "합리적인 소비", detail: "필요와 욕구를 구분하고 소비 계획을 세워요." }, { title: "저축과 투자", detail: "예금, 적금, 투자의 차이를 살펴봐요." }];
export default function MaterialsScreen() { return <Screen title="수업 자료" right={<BackButton />}>{materials.map((item) => <Pressable key={item.title} style={styles.item}><Text style={styles.title}>{item.title}</Text><Text style={styles.detail}>{item.detail}</Text></Pressable>)}</Screen>; }
const styles = StyleSheet.create({ item: { paddingVertical: 20, borderBottomWidth: 1, borderBottomColor: colors.lineSoft }, title: { color: colors.ink900, fontSize: 19, fontWeight: "900" }, detail: { marginTop: 7, color: colors.muted600, fontSize: 15 }, });
