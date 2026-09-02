import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import React from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import { colors, spacing } from "../theme/colors";

interface HeaderProps {
  title: string;
  subtitle?: string;
  back?: boolean;
  right?: React.ReactNode;
}

export function Header({ title, subtitle, back, right }: HeaderProps) {
  const router = useRouter();
  return (
    <View style={styles.wrapper}>
      <View style={styles.left}>
        {back ? (
          <Pressable
            onPress={() => router.back()}
            hitSlop={12}
            style={styles.backBtn}
            accessibilityLabel="Retour"
          >
            <Ionicons name="chevron-back" size={24} color={colors.dark} />
          </Pressable>
        ) : null}
        <View style={{ flexShrink: 1 }}>
          <Text style={styles.title} numberOfLines={1}>
            {title}
          </Text>
          {subtitle ? (
            <Text style={styles.subtitle} numberOfLines={1}>
              {subtitle}
            </Text>
          ) : null}
        </View>
      </View>
      {right ? <View>{right}</View> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.md,
    backgroundColor: colors.bg,
  },
  left: { flexDirection: "row", alignItems: "center", flex: 1 },
  backBtn: { marginRight: spacing.sm },
  title: { fontSize: 20, fontWeight: "700", color: colors.dark },
  subtitle: { fontSize: 12, color: colors.textMuted, marginTop: 2 },
});
