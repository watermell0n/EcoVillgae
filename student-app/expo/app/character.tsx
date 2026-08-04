import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { StateCard } from "@/components/StateCard";
import { CharacterStage } from "@/features/character/components/CharacterStage";
import { ItemSprite } from "@/features/character/components/ItemSprite";
import {
  CATALOG_BY_ID,
  EMPTY_EQUIPPED,
  SLOT_LABEL,
  hasCharacterAsset,
  type EquippedMap
} from "@/features/character/core";
import { fetchInventory, setInventoryEquipped, type InventoryItem } from "@/features/student/api";
import { colors, radii } from "@/theme/tokens";

/**
 * 슬롯 이름은 카탈로그를 정본으로 쓰고, 카탈로그에 아직 없는 코드는 접두사로 추정한다.
 * 백엔드가 새 아이템을 먼저 배포해도 목록이 "캐릭터 아이템"으로 뭉개지지 않게 한다.
 */
function slotLabelFor(code: string) {
  const cosmetic = CATALOG_BY_ID[code];
  if (cosmetic) return SLOT_LABEL[cosmetic.slot];
  if (code.startsWith("hat_")) return "머리";
  if (code.startsWith("top_")) return "몸통";
  if (code.startsWith("bottom_")) return "하의";
  if (code.startsWith("shoes_")) return "신발";
  if (code.startsWith("back_")) return "등·꼬리";
  if (code.startsWith("face_")) return "얼굴";
  if (code.startsWith("neck_")) return "목 장식";
  if (code.startsWith("hand_")) return "손 소품";
  if (code.startsWith("effect_")) return "이펙트";
  if (code.startsWith("bg_")) return "배경";
  return "캐릭터 아이템";
}

/** 인벤토리의 character_code는 카탈로그 spriteKey와 같은 값이라 그대로 슬롯에 꽂는다. */
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

  const equip = useMutation({
    mutationFn: ({ item, equipped }: { item: InventoryItem; equipped: boolean }) =>
      setInventoryEquipped(identity!.classroomId, item, equipped),
    // 낙관적 반영으로 버튼이 즉시 바뀌게 한다. stateVersion은 여기서 고칠 수 없다.
    onSuccess: (_result, { item, equipped }) => {
      queryClient.setQueryData<InventoryItem[]>(inventoryKey, (current = []) =>
        current.map((candidate) => ({
          ...candidate,
          equipped: candidate.resourceId === item.resourceId
            ? equipped
            : equipped && slotLabelFor(candidate.characterCode) === slotLabelFor(item.characterCode)
              ? false
              : candidate.equipped
        }))
      );
    },
    /**
     * 인벤토리의 state_version은 항목별 값이 아니라 캐릭터 프로필 버전이고, 한 응답의 모든
     * 행이 같은 토큰을 공유한다(CharacterInventoryView 주석). 따라서 아무 항목이든 착용하면
     * 프로필 버전이 올라가 캐시에 있는 모든 항목의 토큰이 한꺼번에 낡는다. 낙관적 반영만 하고
     * 재조회하지 않으면 두 번째 착용부터 STATE_VERSION_CONFLICT로 실패한다.
     * 성공이든 실패든 다시 조회해 서버 기준으로 맞춘다.
     */
    onSettled: () => queryClient.invalidateQueries({ queryKey: inventoryKey })
  });

  const items = inventory.data ?? [];

  return (
    <Screen title="내 캐릭터" right={<BackButton />}>
      <View style={styles.columns}>
        <StateCard title="캐릭터 미리보기" tone="sky">
          <View style={styles.stage}>
            <CharacterStage equipped={equippedFrom(items)} scale={5} />
          </View>
          <Text style={styles.helper}>이코 상점에서 산 아이템을 눌러 착용해요.</Text>
        </StateCard>

        <StateCard title="보유 아이템">
          {inventory.isPending ? <ActivityIndicator color={colors.purple500} /> : null}
          {items.map((item) => (
            <View key={item.resourceId} style={styles.item}>
              <ItemSprite spriteKey={item.characterCode} scale={1} />
              <View style={styles.itemCopy}>
                <Text style={styles.itemTitle}>{item.label}</Text>
                <Text style={styles.helper}>
                  {slotLabelFor(item.characterCode)}
                  {hasCharacterAsset(item.characterCode) ? "" : " · 도트 준비 중"}
                </Text>
              </View>
              <Pressable
                accessibilityRole="button"
                disabled={equip.isPending}
                onPress={() => equip.mutate({ item, equipped: !item.equipped })}
                style={[styles.equipButton, item.equipped && styles.equipButtonActive]}
              >
                <Text style={[styles.equipButtonText, item.equipped && styles.equipButtonTextActive]}>
                  {item.equipped ? "벗기" : "착용"}
                </Text>
              </Pressable>
            </View>
          ))}
          {!inventory.isPending && items.length === 0 ? (
            <Text style={styles.helper}>아직 보유한 캐릭터 아이템이 없어요.</Text>
          ) : null}
          {equip.isError ? (
            <Text style={styles.error}>착용 상태를 바꾸지 못했어요. 잠시 후 다시 시도해 주세요.</Text>
          ) : null}
        </StateCard>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  columns: { flexDirection: "row", gap: 18 },
  stage: {
    width: 280,
    height: 300,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: radii.panel,
    backgroundColor: colors.white
  },
  helper: { color: colors.muted600, lineHeight: 21 },
  item: {
    minWidth: 380,
    flexDirection: "row",
    alignItems: "center",
    gap: 16,
    paddingVertical: 14,
    borderBottomWidth: 1,
    borderBottomColor: colors.lineSoft
  },
  itemCopy: { flex: 1 },
  itemTitle: { color: colors.ink900, fontSize: 17, fontWeight: "900" },
  equipButton: {
    minWidth: 76,
    alignItems: "center",
    paddingHorizontal: 14,
    paddingVertical: 10,
    borderRadius: radii.control,
    borderWidth: 1,
    borderColor: colors.lineSoft
  },
  equipButtonActive: { backgroundColor: colors.mint100, borderColor: colors.green500 },
  equipButtonText: { color: colors.ink900, fontWeight: "900" },
  equipButtonTextActive: { color: colors.green500 },
  error: { color: colors.red500, fontSize: 14, fontWeight: "800", marginTop: 10 }
});
