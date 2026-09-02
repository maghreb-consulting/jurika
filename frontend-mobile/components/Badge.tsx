import React from "react";
import { StyleSheet, Text, View, ViewStyle } from "react-native";
import { colors, radius } from "../theme/colors";

export type BadgeTone =
  | "primary"
  | "success"
  | "danger"
  | "warning"
  | "violet"
  | "muted";

interface BadgeProps {
  label: string;
  tone?: BadgeTone;
  style?: ViewStyle;
}

const tones: Record<BadgeTone, { bg: string; fg: string }> = {
  primary: { bg: "#DBEAFE", fg: colors.primary },
  success: { bg: "#D1FAE5", fg: "#047857" },
  danger: { bg: "#FEE2E2", fg: "#B91C1C" },
  warning: { bg: "#FEF3C7", fg: "#92400E" },
  violet: { bg: "#EDE9FE", fg: colors.violet },
  muted: { bg: colors.slate100, fg: colors.slate700 },
};

export function Badge({ label, tone = "muted", style }: BadgeProps) {
  const t = tones[tone];
  return (
    <View style={[styles.badge, { backgroundColor: t.bg }, style]}>
      <Text style={[styles.label, { color: t.fg }]}>{label}</Text>
    </View>
  );
}

export function statusToTone(
  status: string | undefined | null
): BadgeTone {
  switch ((status ?? "").toUpperCase()) {
    case "NOUVEAU":
      return "primary";
    case "EN_COURS":
      return "warning";
    case "CLOTURE":
    case "CLOSED":
      return "success";
    case "ANNULE":
    case "CANCELED":
      return "danger";
    default:
      return "muted";
  }
}

export function statusLabel(status: string | undefined | null): string {
  switch ((status ?? "").toUpperCase()) {
    case "NOUVEAU":
      return "Nouveau";
    case "EN_COURS":
      return "En cours";
    case "CLOTURE":
      return "Cloture";
    case "ANNULE":
      return "Annule";
    default:
      return status ?? "-";
  }
}

const styles = StyleSheet.create({
  badge: {
    alignSelf: "flex-start",
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: radius.pill,
  },
  label: { fontSize: 11, fontWeight: "700", letterSpacing: 0.4 },
});
