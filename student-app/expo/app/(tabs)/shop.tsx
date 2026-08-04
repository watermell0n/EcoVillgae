import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ActivityIndicator, Alert, Platform, Pressable, StyleSheet, Text, View } from "react-native";
import { useState } from "react";

import { useAuth } from "@/auth/AuthProvider";
import { Screen } from "@/components/Screen";
import { ItemSprite } from "@/features/character/components/ItemSprite";
import { fetchStoreCatalog, purchaseStoreProduct, type StoreProduct } from "@/features/store/api";
import { colors, radii } from "@/theme/tokens";

/** 서버 StoreProductKind는 DIGITAL·PHYSICAL·CHARACTER 셋이다. */
const KIND_LABEL: Record<string, string> = {
  CHARACTER: "캐릭터 아이템",
  DIGITAL: "캐릭터 아이템",
  PHYSICAL: "실제 상품"
};

function isWearable(product: StoreProduct) {
  return Boolean(product.characterCode) && product.productKind !== "PHYSICAL";
}

export default function ShopScreen() {
  const { identity } = useAuth();
  const queryClient = useQueryClient();
  const [shopType, setShopType] = useState<"classroom" | "eco">("classroom");
  const catalog = useQuery({
    queryKey: ["store-catalog", identity?.classroomId],
    queryFn: () => fetchStoreCatalog(identity!.classroomId),
    enabled: Boolean(identity?.classroomId)
  });
  const purchase = useMutation({
    mutationFn: (product: StoreProduct) => purchaseStoreProduct(identity!.classroomId, product),
    onSuccess: (order) => {
      showMessage("구매 요청 완료", `${order.totalAmount} 비빔 구매가 접수됐어요.`);
      queryClient.invalidateQueries({ queryKey: ["wallet", identity?.classroomId] });
      queryClient.invalidateQueries({ queryKey: ["store-catalog", identity?.classroomId] });
    }
  });

  const confirmPurchase = (product: StoreProduct) => {
    const message = `${product.label}을(를) ${product.unitPrice} 비빔에 구매할까요?`;
    if (Platform.OS === "web") {
      if (globalThis.confirm(message)) purchase.mutate(product);
      return;
    }
    Alert.alert("구매 확인", message, [
      { text: "취소", style: "cancel" },
      { text: "구매하기", onPress: () => purchase.mutate(product) }
    ]);
  };

  return (
    <Screen title="이코 상점">
      <View style={styles.tabs}><Pressable onPress={() => setShopType("classroom")} style={[styles.tab, shopType === "classroom" && styles.tabActive]}><Text style={styles.tabText}>교실 상점</Text></Pressable><Pressable onPress={() => setShopType("eco")} style={[styles.tab, shopType === "eco" && styles.tabActive]}><Text style={styles.tabText}>이코 상점</Text></Pressable></View>
      {catalog.isPending ? (
        <View style={styles.center}><ActivityIndicator color={colors.orange500} size="large" /><Text style={styles.helper}>상품을 불러오고 있어요.</Text></View>
      ) : catalog.isError ? (
        <View style={styles.center}>
          <Text style={styles.error}>상품을 불러오지 못했어요.</Text>
          <Pressable onPress={() => catalog.refetch()} style={styles.retry}><Text style={styles.retryText}>다시 시도</Text></Pressable>
        </View>
      ) : (
        <View style={styles.grid}>
          {catalog.data?.filter((product) => shopType === "eco" ? product.productKind === "DIGITAL" : product.productKind !== "DIGITAL").map((product) => (
            <View key={product.resourceId} style={styles.product}>
              <View style={styles.productArt}>
                {isWearable(product)
                  ? <ItemSprite spriteKey={product.characterCode!} scale={2} withBase />
                  : <Text style={styles.productIcon}>🎁</Text>}
              </View>
              <Text style={styles.productTitle}>{product.label}</Text>
              <Text style={styles.productKind}>{KIND_LABEL[product.productKind] ?? "교실 상품"}</Text>
              <Text style={styles.price}>{product.unitPrice} 비빔</Text>
              <Pressable accessibilityRole="button" disabled={purchase.isPending} onPress={() => confirmPurchase(product)} style={[styles.buy, purchase.isPending && styles.buyDisabled]}>
                <Text style={styles.buyText}>{purchase.isPending ? "처리 중" : "구매하기"}</Text>
              </Pressable>
            </View>
          ))}
        </View>
      )}
    </Screen>
  );
}

function showMessage(title: string, message: string) {
  if (Platform.OS === "web") {
    globalThis.alert(`${title}\n${message}`);
    return;
  }
  Alert.alert(title, message);
}

const styles = StyleSheet.create({
  center: { minHeight: 260, alignItems: "center", justifyContent: "center", gap: 14 },
  helper: { color: colors.muted600, fontSize: 15 },
  error: { color: colors.red500, fontSize: 16, fontWeight: "800" },
  retry: { paddingHorizontal: 20, paddingVertical: 11, borderRadius: radii.control, backgroundColor: colors.orange500 },
  retryText: { color: colors.white, fontWeight: "900" },
  grid: { flexDirection: "row", flexWrap: "wrap", gap: 16 },
  tabs: { flexDirection: "row", gap: 8, borderBottomWidth: 1, borderBottomColor: colors.lineSoft },
  tab: { paddingHorizontal: 18, paddingVertical: 12, borderBottomWidth: 3, borderBottomColor: "transparent" },
  tabActive: { borderBottomColor: colors.orange500 },
  tabText: { color: colors.ink900, fontSize: 16, fontWeight: "900" },
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
  productArt: { height: 112, alignItems: "center", justifyContent: "center" },
  productIcon: { fontSize: 42 },
  productTitle: { color: colors.ink900, fontSize: 19, fontWeight: "900" },
  productKind: { color: colors.muted600, fontSize: 13 },
  price: { color: colors.orange500, fontSize: 21, fontWeight: "900", marginTop: "auto" },
  buy: { padding: 11, alignItems: "center", borderRadius: radii.control, backgroundColor: colors.orange500 },
  buyDisabled: { opacity: 0.5 },
  buyText: { color: colors.white, fontWeight: "900" }
});
