import type { PropsWithChildren } from "react";
import { StyleSheet, Text, View } from "react-native";

import { colors, radii, spacing } from "@/theme/tokens";

type StateCardProps = PropsWithChildren<{
  title?: string;
  tone?: "white" | "mint" | "cream" | "sky";
}>;

const backgrounds = {
  white: colors.white,
  mint: colors.washMint,
  cream: colors.washCream,
  sky: colors.sky100
};

export function StateCard({ children, title, tone = "white" }: StateCardProps) {
  return (
    <View style={[styles.card, { backgroundColor: backgrounds[tone] }]}>
      {title ? <Text style={styles.title}>{title}</Text> : null}
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: radii.card,
    padding: spacing.card,
    borderWidth: 1,
    borderColor: colors.lineSoft,
    gap: 10
  },
  title: { color: colors.ink900, fontSize: 18, fontWeight: "800" }
});
