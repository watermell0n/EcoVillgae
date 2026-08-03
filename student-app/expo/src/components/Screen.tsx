import { router, usePathname } from "expo-router";
import type { PropsWithChildren, ReactNode } from "react";
import { Pressable, ScrollView, StyleSheet, Text, useWindowDimensions, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { colors, spacing } from "@/theme/tokens";

type ScreenProps = PropsWithChildren<{
  title: string;
  subtitle?: string;
  right?: ReactNode;
  scroll?: boolean;
}>;

export function Screen({ children, title, subtitle, right, scroll = true }: ScreenProps) {
  const { width } = useWindowDimensions();
  const pathname = usePathname();
  const tablet = width >= 768;

  if (tablet) {
    const content = <View style={styles.tabletContent}>{children}</View>;

    return (
      <SafeAreaView edges={["top", "left", "right"]} style={styles.safeArea}>
        <View style={styles.tabletShell}>
          <TabletSidebar pathname={pathname} />
          <View style={styles.tabletMain}>
            <View style={styles.appHeader}>
              <Text accessibilityRole="header" style={styles.appHeaderTitle}>{title}</Text>
              <View style={styles.headerActions}>
                {right}
                <Pressable accessibilityLabel="알림" style={styles.notificationButton}>
                  <Text style={styles.notificationIcon}>🔔</Text>
                </Pressable>
                <Pressable
                  accessibilityLabel="프로필 메뉴"
                  onPress={() => router.push("/(tabs)/profile")}
                  style={({ pressed }) => [styles.profileButton, pressed && styles.pressed]}
                >
                  <View style={styles.avatarHead} />
                  <View style={styles.avatarBody} />
                </Pressable>
              </View>
            </View>
            {scroll ? <ScrollView contentContainerStyle={styles.tabletScroll}>{content}</ScrollView> : content}
          </View>
        </View>
      </SafeAreaView>
    );
  }

  const content = (
    <View style={styles.mobileContent}>
      <View style={styles.header}>
        <View style={styles.heading}>
          <Text accessibilityRole="header" style={styles.title}>{title}</Text>
          {subtitle ? <Text style={styles.subtitle}>{subtitle}</Text> : null}
        </View>
        {right}
      </View>
      {children}
    </View>
  );

  return (
    <SafeAreaView edges={["top", "left", "right"]} style={styles.safeArea}>
      {scroll ? (
        <ScrollView contentContainerStyle={styles.mobileScroll}>{content}</ScrollView>
      ) : (
        content
      )}
    </SafeAreaView>
  );
}

const menuItems = [
  { label: "마을 광장", icon: "🏘️", path: "/" },
  { label: "이코 상점", icon: "🏪", path: "/shop" },
  { label: "이코 은행", icon: "🏦", path: "/bank" },
  { label: "투자 광장", icon: "📈", path: "/investment" },
  { label: "거래소", icon: "🤝", path: "/trade" },
  { label: "퀴즈", icon: "📝", path: "/quiz" },
  { label: "선생님 수업", icon: "📚", path: "/classroom" }
] as const;

function TabletSidebar({ pathname }: { pathname: string }) {
  return (
    <View style={styles.sidebar}>
      <View style={styles.brand}>
        <Text style={styles.brandIcon}>🏘️</Text>
        <View>
          <Text style={styles.brandName}>이코빌리지</Text>
          <Text style={styles.brandCaption}>학생 앱</Text>
        </View>
      </View>
      <View style={styles.menu}>
        {menuItems.map((item) => {
          const active = pathname === item.path || (item.path === "/" && pathname === "/index");
          return (
            <Pressable
              accessibilityRole="button"
              key={item.path}
              onPress={() => router.push(item.path)}
              style={({ pressed }) => [
                styles.menuItem,
                active && styles.menuItemActive,
                pressed && styles.pressed
              ]}
            >
              <View style={[styles.activeBar, !active && styles.activeBarHidden]} />
              <Text style={styles.menuIcon}>{item.icon}</Text>
              <Text style={[styles.menuLabel, active && styles.menuLabelActive]}>{item.label}</Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  safeArea: { flex: 1, backgroundColor: colors.sky100 },
  tabletShell: { flex: 1, flexDirection: "row", backgroundColor: colors.sky100 },
  sidebar: {
    width: 248,
    paddingHorizontal: 18,
    paddingTop: 18,
    paddingBottom: 24,
    borderRightWidth: 1,
    borderRightColor: colors.lineSoft,
    backgroundColor: "rgba(255,255,255,0.92)"
  },
  brand: { flexDirection: "row", alignItems: "center", gap: 12, marginBottom: 26 },
  brandIcon: { fontSize: 32 },
  brandName: { color: colors.ink900, fontSize: 22, fontWeight: "900" },
  brandCaption: { color: colors.muted600, fontSize: 12, fontWeight: "700" },
  menu: { gap: 8 },
  menuItem: {
    position: "relative",
    minHeight: 50,
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    paddingHorizontal: 14,
    borderRadius: 12
  },
  menuItemActive: { backgroundColor: "#EEF8FB" },
  activeBar: {
    position: "absolute",
    left: 0,
    top: 9,
    bottom: 9,
    width: 3,
    borderRadius: 99,
    backgroundColor: colors.orange500
  },
  activeBarHidden: { opacity: 0 },
  menuIcon: { fontSize: 23 },
  menuLabel: { color: colors.muted600, fontSize: 15, fontWeight: "800" },
  menuLabelActive: { color: colors.ink900 },
  tabletMain: { flex: 1, minWidth: 0 },
  appHeader: {
    minHeight: 96,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    gap: 24,
    paddingHorizontal: 34,
    paddingVertical: 20,
    borderBottomWidth: 1,
    borderBottomColor: "rgba(255,255,255,0.7)",
    backgroundColor: "rgba(215,243,232,0.72)"
  },
  appHeaderTitle: { color: colors.ink900, fontSize: 30, fontWeight: "900" },
  headerActions: { flexDirection: "row", alignItems: "center", gap: 16 },
  notificationButton: { width: 44, height: 44, alignItems: "center", justifyContent: "center" },
  notificationIcon: { fontSize: 27 },
  profileButton: {
    position: "relative",
    width: 58,
    height: 58,
    overflow: "hidden",
    borderRadius: 29,
    backgroundColor: "#64A9B5"
  },
  avatarHead: {
    position: "absolute",
    top: 13,
    left: 20,
    width: 18,
    height: 18,
    borderRadius: 9,
    backgroundColor: "#D4EDF0"
  },
  avatarBody: {
    position: "absolute",
    bottom: -2,
    left: 6,
    width: 46,
    height: 30,
    borderTopLeftRadius: 23,
    borderTopRightRadius: 23,
    backgroundColor: "#D4EDF0"
  },
  tabletScroll: { flexGrow: 1 },
  tabletContent: {
    flexGrow: 1,
    width: "100%",
    maxWidth: 1380,
    alignSelf: "center",
    paddingHorizontal: 34,
    paddingTop: 30,
    paddingBottom: 46,
    gap: spacing.section
  },
  mobileScroll: { flexGrow: 1 },
  mobileContent: {
    flexGrow: 1,
    width: "100%",
    maxWidth: 1280,
    alignSelf: "center",
    padding: spacing.page,
    gap: spacing.section
  },
  header: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    gap: 16
  },
  heading: { flex: 1, gap: 4 },
  title: { color: colors.ink900, fontSize: 30, fontWeight: "800" },
  subtitle: { color: colors.muted600, fontSize: 16, lineHeight: 23 },
  pressed: { opacity: 0.78 }
});
