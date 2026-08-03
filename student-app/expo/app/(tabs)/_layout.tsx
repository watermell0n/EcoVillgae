import { Redirect, Tabs } from "expo-router";
import { Text, useWindowDimensions } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { useClassroom } from "@/features/classroom/ClassroomProvider";
import { colors } from "@/theme/tokens";

const icons: Record<string, string> = {
  index: "🏘️",
  wallet: "👛",
  shop: "🏪",
  profile: "🙂"
};

export default function StudentTabsLayout() {
  const { identity, ready } = useAuth();
  const { enabled } = useClassroom();
  const { width } = useWindowDimensions();
  if (ready && !identity) return <Redirect href="/login" />;

  return (
    <Tabs
      screenOptions={({ route }) => ({
        headerShown: false,
        tabBarActiveTintColor: colors.purple500,
        tabBarInactiveTintColor: colors.muted600,
        tabBarLabelStyle: { fontSize: 13, fontWeight: "800" },
        tabBarStyle: width >= 768
          ? { display: "none" }
          : { height: 72, paddingTop: 8, paddingBottom: 8 },
        tabBarIcon: ({ focused }) => (
          <Text style={{ fontSize: focused ? 25 : 22 }}>{icons[route.name] ?? "•"}</Text>
        )
      })}
    >
      <Tabs.Screen name="index" options={{ title: "마을" }} />
      <Tabs.Screen name="wallet" options={{ title: "지갑", href: enabled("wallet") ? undefined : null }} />
      <Tabs.Screen name="shop" options={{ title: "상점", href: enabled("shop") ? undefined : null }} />
      <Tabs.Screen name="profile" options={{ title: "내 정보" }} />
    </Tabs>
  );
}
