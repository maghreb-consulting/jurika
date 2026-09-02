import React from "react";
import {
  ActivityIndicator,
  Pressable,
  StyleSheet,
  Text,
  ViewStyle,
} from "react-native";
import { colors, radius, spacing } from "../theme/colors";

export type ButtonVariant = "primary" | "secondary" | "ghost" | "danger";

interface ButtonProps {
  label: string;
  onPress: () => void;
  variant?: ButtonVariant;
  loading?: boolean;
  disabled?: boolean;
  fullWidth?: boolean;
  style?: ViewStyle;
}

export function Button({
  label,
  onPress,
  variant = "primary",
  loading = false,
  disabled = false,
  fullWidth = true,
  style,
}: ButtonProps) {
  const isDisabled = disabled || loading;

  const containerStyle: ViewStyle[] = [
    styles.base,
    fullWidth ? styles.fullWidth : null,
    variantContainer[variant],
    isDisabled ? styles.disabled : null,
    style ?? null,
  ].filter(Boolean) as ViewStyle[];

  return (
    <Pressable
      onPress={isDisabled ? undefined : onPress}
      style={({ pressed }) => [
        ...containerStyle,
        pressed && !isDisabled ? styles.pressed : null,
      ]}
      accessibilityRole="button"
      accessibilityState={{ disabled: isDisabled, busy: loading }}
    >
      {loading ? (
        <ActivityIndicator color={variantText[variant].color} />
      ) : (
        <Text style={[styles.label, variantText[variant]]}>{label}</Text>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    height: 48,
    paddingHorizontal: spacing.lg,
    borderRadius: radius.md,
    alignItems: "center",
    justifyContent: "center",
    flexDirection: "row",
  },
  fullWidth: { alignSelf: "stretch" },
  disabled: { opacity: 0.55 },
  pressed: { opacity: 0.85 },
  label: { fontSize: 15, fontWeight: "600" },
});

const variantContainer: Record<ButtonVariant, ViewStyle> = {
  primary: { backgroundColor: colors.primary },
  secondary: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
  },
  ghost: { backgroundColor: "transparent" },
  danger: { backgroundColor: colors.danger },
};

const variantText: Record<ButtonVariant, { color: string }> = {
  primary: { color: "#FFFFFF" },
  secondary: { color: colors.dark },
  ghost: { color: colors.primary },
  danger: { color: "#FFFFFF" },
};
