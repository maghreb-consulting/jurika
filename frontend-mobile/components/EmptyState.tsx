import { Ionicons } from "@expo/vector-icons";
import React from "react";
import { StyleSheet, Text, View } from "react-native";
import { colors, spacing } from "../theme/colors";

interface EmptyStateProps {
  icon?: keyof typeof Ionicons.glyphMap;
  title: string;
  message?: string;
}

export function EmptyState({
  icon = "file-tray-outline",
  title,
  message,
}: EmptyStateProps) {
  return (
    <View style={styles.wrapper}>
      <Ionicons name={icon} size={36} color={colors.textSubtle} />
      <Text style={styles.title}>{title}</Text>
      {message ? <Text style={styles.message}>{message}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    alignItems: "center",
    justifyContent: "center",
    paddingVertical: spacing.xxl,
    paddingHorizontal: spacing.lg,
  },
  title: {
    marginTop: spacing.md,
    fontSize: 15,
    fontWeight: "600",
    color: colors.dark,
  },
  message: {
    marginTop: 6,
    fontSize: 13,
    color: colors.textMuted,
    textAlign: "center",
  },
});
