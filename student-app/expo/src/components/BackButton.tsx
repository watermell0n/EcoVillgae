import { router } from "expo-router";
import { Pressable, StyleSheet, Text } from "react-native";

import { colors, radii } from "@/theme/tokens";

export function BackButton() {
  return (
    <Pressable accessibilityRole="button" onPress={() => router.back()} style={styles.button}>
      <Text style={styles.text}>← 돌아가기</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: { paddingHorizontal: 16, paddingVertical: 10, borderRadius: radii.chip, backgroundColor: colors.white },
  text: { color: colors.ink900, fontSize: 14, fontWeight: "800" }
});
