import { useQuery } from "@tanstack/react-query";
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { Screen } from "@/components/Screen";
import { fetchStoreCatalog } from "@/features/store/api";
import { colors, radii } from "@/theme/tokens";

export default function ShopScreen() {
  const { identity } = useAuth();
  const catalog = useQuery({
    queryKey: ["store-catalog", identity?.classroomId],
    queryFn: () => fetchStoreCatalog(identity!.classroomId),
    enabled: Boolean(identity?.classroomId)
  });

  return (
    <Screen title="이코 상점" subtitle="SCR-STU-006 · 재고와 가격은 서버의 최신 카탈로그를 사용합니다.">
      {catalog.isPending ? (
        <View style={styles.center}><ActivityIndicator color={colors.orange500} size="large" /><Text style={styles.helper}>상품을 불러오고 있어요.</Text></View>
      ) : catalog.isError ? (
        <View style={styles.center}>
          <Text style={styles.error}>상품을 불러오지 못했어요.</Text>
          <Pressable onPress={() => catalog.refetch()} style={styles.retry}><Text style={styles.retryText}>다시 시도</Text></Pressable>
        </View>
      ) : (
        <View style={styles.grid}>
          {catalog.data?.map((product) => (
            <View key={product.resourceId} style={styles.product}>
              <Text style={styles.productIcon}>{product.productKind === "DIGITAL" ? "✨" : "🎁"}</Text>
              <Text style={styles.productTitle}>{product.label}</Text>
              <Text style={styles.productKind}>{product.productKind === "DIGITAL" ? "캐릭터 아이템" : "교실 상품"}</Text>
              <Text style={styles.price}>{product.unitPrice} 비빔</Text>
              <Pressable disabled style={styles.buyDisabled}>
                <Text style={styles.buyDisabledText}>구매 화면 이전 예정</Text>
              </Pressable>
            </View>
          ))}
        </View>
      )}
    </Screen>
  );
}

const styles = StyleSheet.create({
  center: { minHeight: 260, alignItems: "center", justifyContent: "center", gap: 14 },
  helper: { color: colors.muted600, fontSize: 15 },
  error: { color: colors.red500, fontSize: 16, fontWeight: "800" },
  retry: { paddingHorizontal: 20, paddingVertical: 11, borderRadius: radii.control, backgroundColor: colors.orange500 },
  retryText: { color: colors.white, fontWeight: "900" },
  grid: { flexDirection: "row", flexWrap: "wrap", gap: 16 },
  product: {
    width: "31.8%",
    minHeight: 240,
    padding: 20,
    borderRadius: radii.card,
    borderWidth: 1,
    borderColor: colors.lineSoft,
    backgroundColor: colors.white,
    gap: 8
  },
  productIcon: { fontSize: 42 },
  productTitle: { color: colors.ink900, fontSize: 19, fontWeight: "900" },
  productKind: { color: colors.muted600, fontSize: 13 },
  price: { color: colors.orange500, fontSize: 21, fontWeight: "900", marginTop: "auto" },
  buyDisabled: { padding: 11, alignItems: "center", borderRadius: radii.control, backgroundColor: "#E9EEF4" },
  buyDisabledText: { color: colors.muted600, fontWeight: "800" }
});
