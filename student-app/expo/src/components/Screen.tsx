import { useQuery } from "@tanstack/react-query";
import { router, usePathname } from "expo-router";
import { useState, type PropsWithChildren, type ReactNode } from "react";
import { Pressable, ScrollView, StyleSheet, Text, useWindowDimensions, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";

import { useAuth } from "@/auth/AuthProvider";
import { colors, spacing } from "@/theme/tokens";
import { useClassroom } from "@/features/classroom/ClassroomProvider";
import type { ModuleGroup } from "@/features/classroom/api";
import { fetchWallet } from "@/features/student/api";

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
  const { identity, logout } = useAuth();
  const [profileOpen, setProfileOpen] = useState(false);
  const wallet = useQuery({
    queryKey: ["wallet", identity?.classroomId],
    queryFn: () => fetchWallet(identity!.classroomId),
    enabled: Boolean(identity?.classroomId && tablet)
  });

  const moveFromProfile = (path: "/character" | "/wallet" | "/paystub" | "/profile") => {
    setProfileOpen(false);
    router.push(path);
  };

  const handleLogout = async () => {
    setProfileOpen(false);
    await logout();
    router.replace("/login");
  };

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
                  accessibilityState={{ expanded: profileOpen }}
                  onPress={() => setProfileOpen((open) => !open)}
                  style={({ pressed }) => [styles.profileButton, pressed && styles.pressed]}
                >
                  <View style={styles.avatarHead} />
                  <View style={styles.avatarBody} />
                </Pressable>
                {profileOpen ? (
                  <View style={styles.profileMenu}>
                    <Text style={styles.profileBalance}>
                      {wallet.data ? `${wallet.data.balance} ${identity?.currencyName ?? "비빔"}` : "잔액 확인 중"}
                    </Text>
                    <ProfileMenuItem icon="🙂" label="내 캐릭터" onPress={() => moveFromProfile("/character")} />
                    <ProfileMenuItem icon="👛" label="내 지갑" onPress={() => moveFromProfile("/wallet")} />
                    <ProfileMenuItem icon="💼" label="월급명세서" onPress={() => moveFromProfile("/paystub")} />
                    <View style={styles.profileMenuFooter}>
                      <Pressable accessibilityRole="button" onPress={() => moveFromProfile("/profile")} style={styles.profileFooterButton}>
                        <Text style={styles.profileFooterText}>프로필</Text>
                      </Pressable>
                      <View style={styles.profileFooterDivider} />
                      <Pressable accessibilityRole="button" onPress={handleLogout} style={styles.profileFooterButton}>
                        <Text style={styles.profileFooterText}>로그아웃</Text>
                      </Pressable>
                    </View>
                  </View>
                ) : null}
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

function ProfileMenuItem({ icon, label, onPress }: { icon: string; label: string; onPress(): void }) {
  return (
    <Pressable accessibilityRole="button" onPress={onPress} style={({ pressed }) => [styles.profileMenuItem, pressed && styles.profileMenuItemPressed]}>
      <Text style={styles.profileMenuIcon}>{icon}</Text>
      <Text style={styles.profileMenuLabel}>{label}</Text>
    </Pressable>
  );
}

type MenuPath = "/" | "/wallet" | "/shop" | "/bank" | "/investment" | "/trade" | "/quiz" | "/classroom";

const menuItems: Array<{ label: string; icon: string; path: MenuPath; group?: ModuleGroup }> = [
  { label: "마을 광장", icon: "🏘️", path: "/" },
  { label: "이코 상점", icon: "🏪", path: "/shop", group: "shop" },
  { label: "이코 은행", icon: "🏦", path: "/bank", group: "bank" },
  { label: "투자 광장", icon: "📈", path: "/investment", group: "investment" },
  { label: "거래소", icon: "🤝", path: "/trade", group: "trade" },
  { label: "퀴즈", icon: "📝", path: "/quiz", group: "quiz" },
  { label: "선생님 수업", icon: "📚", path: "/classroom", group: "meeting" }
];

function TabletSidebar({ pathname }: { pathname: string }) {
  const { enabled } = useClassroom();
  const visibleItems = menuItems.filter((item) => !item.group || enabled(item.group));
  return (
    <View style={styles.sidebar}>
      <View style={styles.brand}>
        <Text style={styles.brandIcon}>🏘️</Text>
        <View>
          <Text style={styles.brandName}>이코빌리지</Text>
          <Text style={styles.brandCaption}>학생 앱</Text>
        </View>
      </View>
      <Text style={styles.menuSectionLabel}>마을 메뉴</Text>
      <View style={styles.menu}>
        {visibleItems.map((item) => {
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
    width: 232,
    paddingLeft: 18,
    paddingRight: 0,
    paddingTop: 22,
    paddingBottom: 24,
    backgroundColor: "#68B6CD"
  },
  brand: { flexDirection: "row", alignItems: "center", gap: 11, marginBottom: 30, paddingRight: 18 },
  brandIcon: { fontSize: 30 },
  brandName: { color: colors.white, fontSize: 21, fontWeight: "900" },
  brandCaption: { color: "rgba(255,255,255,0.76)", fontSize: 12, fontWeight: "700" },
  menuSectionLabel: {
    marginBottom: 8,
    paddingHorizontal: 14,
    color: "rgba(255,255,255,0.7)",
    fontSize: 12,
    fontWeight: "800"
  },
  menu: { paddingBottom: 8 },
  menuItem: {
    minHeight: 54,
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    paddingLeft: 14,
    paddingRight: 20,
    borderBottomWidth: 1,
    borderBottomColor: "rgba(255,255,255,0.2)",
    borderStyle: "dashed"
  },
  menuItemActive: {
    marginLeft: -6,
    minHeight: 58,
    paddingLeft: 20,
    borderBottomColor: "transparent",
    borderTopLeftRadius: 28,
    borderBottomLeftRadius: 28,
    backgroundColor: colors.white
  },
  menuIcon: { width: 28, fontSize: 22, textAlign: "center" },
  menuLabel: { color: "rgba(255,255,255,0.86)", fontSize: 15, fontWeight: "800" },
  menuLabelActive: { color: "#31758A" },
  tabletMain: { flex: 1, minWidth: 0 },
  appHeader: {
    position: "relative",
    zIndex: 30,
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
  headerActions: { position: "relative", flexDirection: "row", alignItems: "center", gap: 16, zIndex: 20 },
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
  profileMenu: {
    position: "absolute",
    top: 68,
    right: 0,
    width: 282,
    paddingHorizontal: 18,
    paddingTop: 20,
    overflow: "hidden",
    borderWidth: 1,
    borderColor: colors.lineSoft,
    borderRadius: 16,
    backgroundColor: "rgba(255,255,255,0.98)",
    shadowColor: colors.ink900,
    shadowOffset: { width: 0, height: 12 },
    shadowOpacity: 0.16,
    shadowRadius: 24,
    elevation: 12
  },
  profileBalance: { paddingBottom: 17, color: colors.green500, fontSize: 28, fontWeight: "900" },
  profileMenuItem: {
    minHeight: 58,
    flexDirection: "row",
    alignItems: "center",
    gap: 13,
    borderTopWidth: 1,
    borderTopColor: colors.lineSoft
  },
  profileMenuItemPressed: { opacity: 0.6 },
  profileMenuIcon: { width: 30, fontSize: 23, textAlign: "center" },
  profileMenuLabel: { color: colors.ink900, fontSize: 16, fontWeight: "800" },
  profileMenuFooter: {
    minHeight: 50,
    flexDirection: "row",
    alignItems: "stretch",
    marginHorizontal: -18,
    borderTopWidth: 1,
    borderTopColor: colors.lineSoft
  },
  profileFooterButton: { flex: 1, alignItems: "center", justifyContent: "center" },
  profileFooterDivider: { width: 1, backgroundColor: colors.lineSoft },
  profileFooterText: { color: colors.muted600, fontSize: 14, fontWeight: "800" },
  tabletScroll: { flexGrow: 1, zIndex: 0 },
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
