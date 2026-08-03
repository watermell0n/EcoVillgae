import { useQuery } from "@tanstack/react-query";
import { ActivityIndicator, StyleSheet, Text, View } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { StateCard } from "@/components/StateCard";
import { fetchInventory } from "@/features/student/api";
import { colors, radii } from "@/theme/tokens";

export default function CharacterScreen() {
  const { identity } = useAuth();
  const inventory = useQuery({ queryKey: ["character-inventory", identity?.classroomId], queryFn: () => fetchInventory(identity!.classroomId), enabled: Boolean(identity) });

  return (
    <Screen title="내 캐릭터" subtitle="SCR-STU-008 · 웹 CSS 대신 Expo용 투명 PNG 에셋 규칙을 사용합니다." right={<BackButton />}>
      <View style={styles.columns}>
        <StateCard title="캐릭터 미리보기" tone="sky">
          <View style={styles.avatar}><Text style={styles.avatarText}>🙂</Text></View>
          <Text style={styles.helper}>최종 캐릭터 레이어 에셋을 연결할 자리예요.</Text>
        </StateCard>
        <StateCard title="내 아이템">
          {inventory.isPending ? <ActivityIndicator color={colors.purple500} /> : inventory.data?.map((item) => (
            <View key={item.resourceId} style={styles.item}>
              <Text style={styles.itemIcon}>🧢</Text>
              <View><Text style={styles.itemTitle}>{item.label}</Text><Text style={styles.helper}>{item.equipped ? "장착 중" : item.status}</Text></View>
            </View>
          ))}
          <Text style={styles.notice}>장착 명령은 Expo 에셋 규격 검증 후 연결합니다.</Text>
        </StateCard>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  columns: { flexDirection: "row", gap: 18 },
  avatar: { width: 260, height: 260, alignItems: "center", justifyContent: "center", borderRadius: radii.panel, backgroundColor: colors.white },
  avatarText: { fontSize: 112 },
  helper: { maxWidth: 400, color: colors.muted600, lineHeight: 21 },
  item: { minWidth: 420, flexDirection: "row", alignItems: "center", gap: 14, padding: 15, borderRadius: radii.control, backgroundColor: colors.washBlue },
  itemIcon: { fontSize: 34 },
  itemTitle: { color: colors.ink900, fontSize: 17, fontWeight: "900" },
  notice: { color: colors.orange500, fontSize: 14, fontWeight: "800", marginTop: 10 }
});
