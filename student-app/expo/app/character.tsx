import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { StateCard } from "@/components/StateCard";
import { CharacterStage } from "@/features/character/components/CharacterStage";
import { ItemSprite } from "@/features/character/components/ItemSprite";
import { hasSprite } from "@/features/character/layerProfile";
import { CATALOG_BY_ID } from "@/features/character/catalog";
import { EMPTY_EQUIPPED, type EquippedMap } from "@/features/character/types";
import { commandInventoryItem, fetchInventory, type InventoryItem } from "@/features/student/api";
import { colors, radii } from "@/theme/tokens";

/**
 * 인벤토리 응답의 character_code는 카탈로그 spriteKey와 같은 값이라 그대로 슬롯에 꽂는다.
 * 카탈로그에 없는 코드는 무시해서 서버가 새 아이템을 먼저 배포해도 화면이 깨지지 않게 한다.
 */
function equippedFrom(items: readonly InventoryItem[]): EquippedMap {
  const equipped: EquippedMap = { ...EMPTY_EQUIPPED };
  for (const item of items) {
    if (!item.equipped) continue;
    const cosmetic = CATALOG_BY_ID[item.characterCode];
    if (!cosmetic) continue;
    equipped[cosmetic.slot] = cosmetic.id;
  }
  return equipped;
}

export default function CharacterScreen() {
  const { identity } = useAuth();
  const queryClient = useQueryClient();
  const inventoryKey = ["character-inventory", identity?.classroomId];
  const inventory = useQuery({
    queryKey: inventoryKey,
    queryFn: () => fetchInventory(identity!.classroomId),
    enabled: Boolean(identity)
  });

  const toggle = useMutation({
    mutationFn: (item: InventoryItem) =>
      commandInventoryItem(identity!.classroomId, item, item.equipped ? "UNEQUIP" : "EQUIP"),
    onSettled: () => queryClient.invalidateQueries({ queryKey: inventoryKey })
  });

  const items = inventory.data ?? [];
  const equipped = equippedFrom(items);

  return (
    <Screen title="내 캐릭터" subtitle="SCR-STU-008 · 리그 좌표에 48×56 투명 레이어를 겹쳐 그립니다." right={<BackButton />}>
      <View style={styles.columns}>
        <StateCard title="캐릭터 미리보기" tone="sky">
          <View style={styles.stageBox}>
            <CharacterStage equipped={equipped} scale={5} />
          </View>
          <Text style={styles.helper}>
            장착한 아이템이 리그 레이어 순서대로 겹칩니다.
          </Text>
        </StateCard>

        <StateCard title="내 아이템">
          {inventory.isPending ? (
            <ActivityIndicator color={colors.purple500} />
          ) : items.length === 0 ? (
            <Text style={styles.helper}>아직 가진 아이템이 없어요. 이코 상점에서 먼저 사보세요.</Text>
          ) : (
            items.map((item) => {
              const busy = toggle.isPending && toggle.variables?.resourceId === item.resourceId;
              return (
                <View key={item.resourceId} style={styles.item}>
                  <ItemSprite spriteKey={item.characterCode} scale={1} />
                  <View style={styles.itemBody}>
                    <Text style={styles.itemTitle}>{item.label}</Text>
                    <Text style={styles.helper}>
                      {item.equipped ? "장착 중" : item.status}
                      {hasSprite(item.characterCode) ? "" : " · 도트 준비 중"}
                    </Text>
                  </View>
                  <Pressable
                    accessibilityRole="button"
                    disabled={busy}
                    onPress={() => toggle.mutate(item)}
                    style={({ pressed }) => [styles.action, (pressed || busy) && styles.actionBusy]}
                  >
                    <Text style={styles.actionText}>
                      {busy ? "처리 중" : item.equipped ? "벗기" : "장착"}
                    </Text>
                  </Pressable>
                </View>
              );
            })
          )}
          {toggle.isError ? (
            <Text style={styles.error}>장착 상태를 바꾸지 못했어요. 잠시 후 다시 시도해 주세요.</Text>
          ) : null}
        </StateCard>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  columns: { flexDirection: "row", gap: 18 },
  stageBox: {
    width: 260,
    height: 320,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: radii.panel,
    backgroundColor: colors.white
  },
  helper: { maxWidth: 400, color: colors.muted600, lineHeight: 21 },
  item: {
    minWidth: 420,
    flexDirection: "row",
    alignItems: "center",
    gap: 14,
    padding: 15,
    borderRadius: radii.control,
    backgroundColor: colors.washBlue
  },
  itemBody: { flex: 1 },
  itemTitle: { color: colors.ink900, fontSize: 17, fontWeight: "900" },
  action: {
    paddingHorizontal: 16,
    paddingVertical: 9,
    borderRadius: radii.control,
    backgroundColor: colors.purple500
  },
  actionBusy: { opacity: 0.6 },
  actionText: { color: colors.white, fontWeight: "900" },
  error: { color: colors.red500, fontSize: 14, fontWeight: "800", marginTop: 10 }
});
