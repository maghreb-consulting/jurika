import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import React from "react";
import {
  Alert,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Button } from "../../components/Button";
import { Card } from "../../components/Card";
import { Header } from "../../components/Header";
import { API_URL } from "../../lib/api";
import { useAuthStore } from "../../store/authStore";
import { colors, radius, spacing } from "../../theme/colors";

const ROLE_LABEL: Record<string, string> = {
  SUPER_ADMIN: "Super Admin",
  SUPERVISEUR: "Superviseur",
  EMPLOYE: "Employe",
  CLIENT: "Client",
};

export default function ProfileScreen() {
  const router = useRouter();
  const user = useAuthStore((s) => s.user);
  const workspaceCode = useAuthStore((s) => s.workspaceCode);
  const logout = useAuthStore((s) => s.logout);

  function confirmLogout() {
    Alert.alert(
      "Deconnexion",
      "Voulez-vous vraiment vous deconnecter ?",
      [
        { text: "Annuler", style: "cancel" },
        {
          text: "Deconnexion",
          style: "destructive",
          onPress: async () => {
            await logout();
            router.replace("/(auth)/workspace");
          },
        },
      ],
      { cancelable: true }
    );
  }

  const initials = (() => {
    const fn = user?.firstName ?? "";
    const ln = user?.lastName ?? "";
    const fromName = (fn[0] ?? "") + (ln[0] ?? "");
    return (fromName || user?.email?.[0] || "?").toUpperCase();
  })();

  return (
    <SafeAreaView style={styles.safe} edges={["top"]}>
      <Header title="Profil" />
      <ScrollView contentContainerStyle={styles.container}>
        <Card style={styles.card}>
          <View style={styles.row}>
            <View style={styles.avatar}>
              <Text style={styles.avatarText}>{initials}</Text>
            </View>
            <View style={{ flex: 1 }}>
              <Text style={styles.name}>
                {user
                  ? `${user.firstName ?? ""} ${user.lastName ?? ""}`.trim() ||
                    user.email
                  : "Utilisateur"}
              </Text>
              <Text style={styles.role}>
                {user?.role ? ROLE_LABEL[user.role] ?? user.role : "-"}
              </Text>
            </View>
          </View>
        </Card>

        <Card style={styles.card}>
          <InfoRow icon="mail-outline" label="Email" value={user?.email ?? "-"} />
          <View style={styles.divider} />
          <InfoRow
            icon="business-outline"
            label="Workspace"
            value={workspaceCode ?? "-"}
          />
          <View style={styles.divider} />
          <InfoRow
            icon="shield-checkmark-outline"
            label="2FA"
            value={user?.twoFactorEnabled ? "Active" : "Inactive"}
          />
          <View style={styles.divider} />
          <InfoRow
            icon="cloud-outline"
            label="API"
            value={API_URL}
          />
        </Card>

        <Button
          label="Se deconnecter"
          variant="danger"
          onPress={confirmLogout}
        />

        <Text style={styles.footer}>JURIKA Mobile - v1.0.0</Text>
      </ScrollView>
    </SafeAreaView>
  );
}

function InfoRow({
  icon,
  label,
  value,
}: {
  icon: keyof typeof Ionicons.glyphMap;
  label: string;
  value: string;
}) {
  return (
    <View style={styles.infoRow}>
      <Ionicons name={icon} size={18} color={colors.textMuted} />
      <View style={{ flex: 1, marginLeft: spacing.md }}>
        <Text style={styles.infoLabel}>{label}</Text>
        <Text style={styles.infoValue} numberOfLines={1}>
          {value}
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  container: { padding: spacing.lg, paddingBottom: spacing.xxl },
  card: { marginBottom: spacing.md },
  row: { flexDirection: "row", alignItems: "center" },
  avatar: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: colors.primary,
    alignItems: "center",
    justifyContent: "center",
    marginRight: spacing.md,
  },
  avatarText: { fontSize: 20, fontWeight: "800", color: "#FFFFFF" },
  name: { fontSize: 17, fontWeight: "700", color: colors.dark },
  role: { fontSize: 13, color: colors.primary, fontWeight: "600", marginTop: 2 },
  infoRow: {
    flexDirection: "row",
    alignItems: "center",
    paddingVertical: spacing.sm,
  },
  infoLabel: { fontSize: 12, color: colors.textMuted },
  infoValue: { fontSize: 14, color: colors.dark, fontWeight: "600", marginTop: 2 },
  divider: { height: 1, backgroundColor: colors.border },
  footer: {
    textAlign: "center",
    fontSize: 12,
    color: colors.textMuted,
    marginTop: spacing.lg,
  },
});
