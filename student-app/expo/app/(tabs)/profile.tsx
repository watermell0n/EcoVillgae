import { router } from "expo-router";
import { Pressable, StyleSheet, Text } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { Screen } from "@/components/Screen";
import { StateCard } from "@/components/StateCard";
import { colors, radii } from "@/theme/tokens";

export default function ProfileScreen() {
  const { identity, logout } = useAuth();

  async function handleLogout() {
    await logout();
    router.replace("/login");
  }

  return (
    <Screen title="내 정보" subtitle="이 기기에서는 학생 한 명의 활성 세션만 사용합니다.">
      <StateCard title="학생 정보">
        <Text style={styles.label}>닉네임</Text>
        <Text style={styles.value}>{identity?.nickname}</Text>
        <Text style={styles.label}>학생 ID</Text>
        <Text selectable style={styles.code}>{identity?.studentId}</Text>
        <Text style={styles.security}>🔐 액세스 토큰은 메모리에만, 갱신 토큰은 SecureStore에만 저장돼요.</Text>
      </StateCard>
      <Pressable accessibilityRole="button" onPress={handleLogout} style={styles.logout}>
        <Text style={styles.logoutText}>안전하게 로그아웃</Text>
      </Pressable>
    </Screen>
  );
}

const styles = StyleSheet.create({
  label: { color: colors.muted600, fontSize: 13, fontWeight: "700", marginTop: 4 },
  value: { color: colors.ink900, fontSize: 21, fontWeight: "900" },
  code: { color: colors.ink900, fontSize: 14 },
  security: { color: colors.green500, fontSize: 14, fontWeight: "800", marginTop: 10 },
  logout: { alignSelf: "flex-start", paddingHorizontal: 22, paddingVertical: 14, borderRadius: radii.control, backgroundColor: colors.red500 },
  logoutText: { color: colors.white, fontSize: 15, fontWeight: "900" }
});
