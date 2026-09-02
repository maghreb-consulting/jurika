import React from "react";
import { Pressable, StyleSheet, View, ViewProps } from "react-native";
import { colors, radius, spacing } from "../theme/colors";

interface CardProps extends ViewProps {
  onPress?: () => void;
  padded?: boolean;
}

export function Card({
  children,
  onPress,
  padded = true,
  style,
  ...rest
}: CardProps) {
  const content = (
    <View
      {...rest}
      style={[styles.card, padded ? styles.padded : null, style]}
    >
      {children}
    </View>
  );
  if (!onPress) return content;
  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => [pressed ? styles.pressed : null]}
    >
      {content}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.surface,
    borderRadius: radius.lg,
    borderWidth: 1,
    borderColor: colors.border,
    shadowColor: "#0F172A",
    shadowOpacity: 0.04,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: 2 },
    elevation: 1,
  },
  padded: { padding: spacing.lg },
  pressed: { opacity: 0.85 },
});
