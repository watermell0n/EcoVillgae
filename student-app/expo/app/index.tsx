import { Redirect } from "expo-router";
import { ActivityIndicator, StyleSheet, Text, View } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { colors } from "@/theme/tokens";

export default function IndexRoute() {
  const { identity, ready } = useAuth();

  if (!ready) {
    return (
      <View style={styles.loading}>
        <ActivityIndicator color={colors.purple500} size="large" />
        <Text style={styles.loadingText}>안전하게 로그인 정보를 확인하고 있어요.</Text>
      </View>
    );
  }

  return <Redirect href={identity ? "/(tabs)" : "/login"} />;
}

const styles = StyleSheet.create({
  loading: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    gap: 14,
    backgroundColor: colors.washBlue
  },
  loadingText: { color: colors.muted600, fontSize: 16 }
});
