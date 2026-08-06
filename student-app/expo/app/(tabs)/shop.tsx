import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ActivityIndicator, Alert, Platform, Pressable, StyleSheet, Text, View } from "react-native";
import { useState } from "react";

import { ApiError } from "@/api/client";
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

/** 구매 실패 사유를 초등학생이 읽을 수 있는 말로 바꾼다. 서버 원문은 그대로 쓰기 어렵다. */
const PURCHASE_ERROR_MESSAGE: Record<string, string> = {
  INSUFFICIENT_BALANCE: "가진 돈이 모자라요.",
  ALREADY_OWNED: "이미 가지고 있는 아이템이에요.",
  OUT_OF_STOCK: "이 상품은 다 팔렸어요.",
  PRODUCT_NOT_AVAILABLE: "지금은 살 수 없는 상품이에요.",
  PRODUCT_VERSION_CONFLICT: "상품 정보가 바뀌었어요. 잠시 뒤 다시 사 주세요.",
  CLASS_NOT_ACTIVE: "지금은 상점을 이용할 수 없어요."
};

function isWearable(product: StoreProduct) {
  return Boolean(product.characterCode) && product.productKind !== "PHYSICAL";
}

/**
 * 이코 상점은 시스템이 제공하는 캐릭터 아이템을, 교실 상점은 교사가 등록한 상품을 판다.
 * 교사 웹은 상품을 PHYSICAL로만 만들므로(UIA-STORE-PRODUCT-CREATE) PHYSICAL만 교실 상점으로 보낸다.
 * 캐릭터는 서버가 CHARACTER로 내려주고 목 데이터만 DIGITAL이라 둘 다 이코 상점에 둔다.
 */
function shopAreaOf(product: StoreProduct) {
  return product.productKind === "PHYSICAL" ? "classroom" : "eco";
}

export default function ShopScreen() {
  const { identity } = useAuth();
  const currencyName = identity?.currencyName ?? "비빔";
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
      showMessage("구매 완료", `${order.totalAmount} ${currencyName} 결제가 완료됐어요.`);
      queryClient.invalidateQueries({ queryKey: ["wallet", identity?.classroomId] });
      queryClient.invalidateQueries({ queryKey: ["store-catalog", identity?.classroomId] });
      // 캐릭터를 사면 보유 아이템이 늘어난다. staleTime이 30초라 무효화하지 않으면
      // 방금 산 아이템이 "내 캐릭터"의 보유 목록에 바로 뜨지 않는다.
      queryClient.invalidateQueries({ queryKey: ["character-inventory", identity?.classroomId] });
    },
    onError: (error) => {
      const code = error instanceof ApiError ? error.code : "";
      showMessage("구매하지 못했어요", PURCHASE_ERROR_MESSAGE[code] ?? "잠시 후 다시 시도해 주세요.");
      // 품절·버전 충돌은 목록이 낡아서 생기므로 최신 목록을 다시 받는다.
      queryClient.invalidateQueries({ queryKey: ["store-catalog", identity?.classroomId] });
    }
  });

  const visibleProducts = (catalog.data ?? []).filter((product) => shopAreaOf(product) === shopType);

  const confirmPurchase = (product: StoreProduct) => {
    const message = `${product.label}을(를) ${product.unitPrice} ${currencyName}에 구매할까요?`;
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
      <Text style={styles.purchasePolicy}>구매를 확인하면 교사 승인 없이 바로 결제되고 재고에 반영돼요.</Text>
      {catalog.isPending ? (
        <View style={styles.center}><ActivityIndicator color={colors.orange500} size="large" /><Text style={styles.helper}>상품을 불러오고 있어요.</Text></View>
      ) : catalog.isError ? (
        <View style={styles.center}>
          <Text style={styles.error}>상품을 불러오지 못했어요.</Text>
          <Pressable onPress={() => catalog.refetch()} style={styles.retry}><Text style={styles.retryText}>다시 시도</Text></Pressable>
        </View>
      ) : visibleProducts.length === 0 ? (
        <View style={styles.center}>
          <Text style={styles.helper}>
            {shopType === "eco" ? "지금은 살 수 있는 캐릭터 아이템이 없어요." : "선생님이 등록한 상품이 아직 없어요."}
          </Text>
        </View>
      ) : (
        <View style={styles.grid}>
          {visibleProducts.map((product) => (
            <View key={product.resourceId} style={styles.product}>
              <View style={styles.productArt}>
                {isWearable(product)
                  ? <ItemSprite spriteKey={product.characterCode!} scale={2} withBase />
                  : <Text style={styles.productIcon}>🎁</Text>}
              </View>
              <Text style={styles.productTitle}>{product.label}</Text>
              <Text style={styles.productKind}>{KIND_LABEL[product.productKind] ?? "교실 상품"}</Text>
              <Text style={styles.price}>{product.unitPrice} {currencyName}</Text>
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
  purchasePolicy: { color: colors.muted600, fontSize: 13, fontWeight: "700" },
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
