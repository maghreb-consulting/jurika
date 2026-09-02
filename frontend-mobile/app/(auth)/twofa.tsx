import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import React, { useState } from "react";
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Button } from "../../components/Button";
import { TextField } from "../../components/TextField";
import { useAuthStore } from "../../store/authStore";
import { colors, spacing } from "../../theme/colors";

export default function TwoFAScreen() {
  const router = useRouter();
  const verify2FA = useAuthStore((s) => s.verify2FA);
  const loading = useAuthStore((s) => s.loading);
  const apiError = useAuthStore((s) => s.error);
  const clearError = useAuthStore((s) => s.clearError);

  const [code, setCode] = useState("");
  const [localError, setLocalError] = useState<string | null>(null);

  async function handleVerify() {
    clearError();
    setLocalError(null);
    const trimmed = code.trim();
    if (!/^\d{6}$/.test(trimmed)) {
      setLocalError("Le code doit contenir 6 chiffres.");
      return;
    }
    const ok = await verify2FA(trimmed);
    if (ok) router.replace("/(tabs)");
  }

  return (
    <SafeAreaView style={styles.safe} edges={["top", "bottom"]}>
      <KeyboardAvoidingView
        behavior={Platform.OS === "ios" ? "padding" : undefined}
        style={{ flex: 1 }}
      >
        <ScrollView
          contentContainerStyle={styles.container}
          keyboardShouldPersistTaps="handled"
        >
          <Pressable
            onPress={() => router.back()}
            style={styles.backBtn}
            hitSlop={10}
          >
            <Ionicons name="chevron-back" size={22} color={colors.dark} />
            <Text style={styles.backText}>Retour</Text>
          </Pressable>

          <View style={styles.formCard}>
            <View style={styles.iconCircle}>
              <Ionicons
                name="shield-checkmark"
                size={28}
                color={colors.primary}
              />
            </View>
            <Text style={styles.stepLabel}>Etape 3 / 3</Text>
            <Text style={styles.title}>Verification 2FA</Text>
            <Text style={styles.subtitle}>
              Entrez le code a 6 chiffres genere par votre application
              d'authentification (Google Authenticator, etc.).
            </Text>

            <TextField
              label="Code 2FA"
              placeholder="000000"
              keyboardType="number-pad"
              maxLength={6}
              value={code}
              onChangeText={(t) => setCode(t.replace(/\D/g, ""))}
              error={localError ?? apiError}
              style={styles.codeInput}
            />

            <Button
              label="Verifier"
              onPress={handleVerify}
              loading={loading}
            />
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  container: {
    flexGrow: 1,
    padding: spacing.xl,
    justifyContent: "center",
  },
  backBtn: {
    flexDirection: "row",
    alignItems: "center",
    marginBottom: spacing.lg,
  },
  backText: { marginLeft: 4, fontSize: 14, color: colors.dark },
  formCard: {
    backgroundColor: colors.surface,
    padding: spacing.xl,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: colors.border,
  },
  iconCircle: {
    width: 56,
    height: 56,
    borderRadius: 14,
    backgroundColor: "#DBEAFE",
    alignItems: "center",
    justifyContent: "center",
    marginBottom: spacing.md,
  },
  stepLabel: {
    fontSize: 12,
    fontWeight: "700",
    color: colors.primary,
    marginBottom: 4,
    letterSpacing: 0.5,
  },
  title: {
    fontSize: 22,
    fontWeight: "700",
    color: colors.dark,
    marginBottom: 4,
  },
  subtitle: {
    fontSize: 13,
    color: colors.textMuted,
    marginBottom: spacing.lg,
  },
  codeInput: {
    fontSize: 22,
    letterSpacing: 8,
    textAlign: "center",
    height: 56,
  },
});
