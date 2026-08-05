import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ActivityIndicator, Image, type ImageSourcePropType, Pressable, StyleSheet, Text, View } from "react-native";

import { useAuth } from "@/auth/AuthProvider";
import { BackButton } from "@/components/BackButton";
import { Screen } from "@/components/Screen";
import { StateCard } from "@/components/StateCard";
import { fetchInventory, setInventoryEquipped, type InventoryItem } from "@/features/student/api";
import { colors, radii } from "@/theme/tokens";

const base = require("../assets/character/base_char_base.png");
const layers: Record<string, ImageSourcePropType> = {
  bottom_overalls: require("../assets/character/bottom_overalls.png"),
  top_yellow_hoodie: require("../assets/character/top_yellow_hoodie.png"),
  top_blue_tshirt: require("../assets/character/top_blue_tshirt.png"),
  top_green_vest: require("../assets/character/top_green_vest.png"),
  shoes_sneakers: require("../assets/character/shoes_sneakers.png"),
  back_backpack: require("../assets/character/back_backpack.png"),
  face_sunglasses: require("../assets/character/face_sunglasses.png"),
  hat_red_cap: require("../assets/character/hat_red_cap.png"),
  hat_teal_beanie: require("../assets/character/hat_teal_beanie.png")
};

function slotFor(code: string) {
  if (code.startsWith("hat_")) return "모자";
  if (code.startsWith("top_")) return "상의";
  if (code.startsWith("bottom_")) return "하의";
  if (code.startsWith("shoes_")) return "신발";
  if (code.startsWith("back_")) return "등 장식";
  if (code.startsWith("face_")) return "얼굴 장식";
  return "캐릭터 아이템";
}

export default function CharacterScreen() {
  const { identity } = useAuth();
  const queryClient = useQueryClient();
  const inventory = useQuery({
    queryKey: ["character-inventory", identity?.classroomId],
    queryFn: () => fetchInventory(identity!.classroomId),
    enabled: Boolean(identity)
  });
  const equip = useMutation({
    mutationFn: ({ item, equipped }: { item: InventoryItem; equipped: boolean }) =>
      setInventoryEquipped(identity!.classroomId, item, equipped),
    onSuccess: (_result, { item, equipped }) => {
      queryClient.setQueryData<InventoryItem[]>(["character-inventory", identity?.classroomId], (current = []) =>
        current.map((candidate) => ({
          ...candidate,
          equipped: candidate.resourceId === item.resourceId
            ? equipped
            : equipped && slotFor(candidate.characterCode) === slotFor(item.characterCode)
              ? false
              : candidate.equipped
        }))
      );
    }
  });
  const equippedItems = inventory.data?.filter((item) => item.equipped && layers[item.characterCode]) ?? [];

  return (
    <Screen title="내 캐릭터" right={<BackButton />}>
      <View style={styles.columns}>
        <StateCard title="캐릭터 미리보기" tone="sky">
          <View style={styles.stage}>
            <Image source={base} style={styles.layer} resizeMode="contain" />
            {equippedItems.map((item) => (
              <Image key={item.resourceId} source={layers[item.characterCode]} style={styles.layer} resizeMode="contain" />
            ))}
          </View>
          <Text style={styles.helper}>이코 상점에서 산 아이템을 눌러 착용해요.</Text>
        </StateCard>

        <StateCard title="보유 아이템">
          {inventory.isPending ? <ActivityIndicator color={colors.purple500} /> : null}
          {inventory.data?.map((item) => (
            <View key={item.resourceId} style={styles.item}>
              <View style={styles.itemCopy}>
                <Text style={styles.itemTitle}>{item.label}</Text>
                <Text style={styles.helper}>{slotFor(item.characterCode)}</Text>
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
          {!inventory.isPending && inventory.data?.length === 0 ? <Text style={styles.helper}>아직 보유한 캐릭터 아이템이 없어요.</Text> : null}
        </StateCard>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  columns: { flexDirection: "row", gap: 18 },
  stage: { width: 280, height: 300, alignItems: "center", justifyContent: "center", borderRadius: radii.panel, backgroundColor: colors.white },
  layer: { position: "absolute", width: 220, height: 260 },
  helper: { color: colors.muted600, lineHeight: 21 },
  item: { minWidth: 380, flexDirection: "row", alignItems: "center", gap: 16, paddingVertical: 14, borderBottomWidth: 1, borderBottomColor: colors.lineSoft },
  itemCopy: { flex: 1 },
  itemTitle: { color: colors.ink900, fontSize: 17, fontWeight: "900" },
  equipButton: { minWidth: 76, alignItems: "center", paddingHorizontal: 14, paddingVertical: 10, borderRadius: radii.control, borderWidth: 1, borderColor: colors.lineSoft },
  equipButtonActive: { backgroundColor: colors.mint100, borderColor: colors.green500 },
  equipButtonText: { color: colors.ink900, fontWeight: "900" },
  equipButtonTextActive: { color: colors.green500 }
});
