import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import React, { useState } from "react";
import {
  KeyboardAvoidingView,
  Platform,
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

const WORKSPACE_REGEX = /^JUR-[A-Z0-9]{4,8}$/i;

export default function WorkspaceScreen() {
  const router = useRouter();
  const checkWorkspace = useAuthStore((s) => s.checkWorkspace);
  const loading = useAuthStore((s) => s.loading);
  const apiError = useAuthStore((s) => s.error);
  const clearError = useAuthStore((s) => s.clearError);

  const [code, setCode] = useState("JUR-DEMO1");
  const [localError, setLocalError] = useState<string | null>(null);

  async function handleNext() {
    clearError();
    setLocalError(null);
    const trimmed = code.trim().toUpperCase();
    if (!WORKSPACE_REGEX.test(trimmed)) {
      setLocalError("Format invalide. Exemple : JUR-DEMO1");
      return;
    }
    const ok = await checkWorkspace(trimmed);
    if (ok) router.push("/(auth)/login");
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
          <View style={styles.brand}>
            <View style={styles.logoCircle}>
              <Ionicons name="briefcase" size={28} color="#FFFFFF" />
            </View>
            <Text style={styles.brandTitle}>JURIKA</Text>
            <Text style={styles.brandSubtitle}>
              Plateforme juridique des entreprises au Maroc
            </Text>
          </View>

          <View style={styles.formCard}>
            <Text style={styles.stepLabel}>Etape 1 / 3</Text>
            <Text style={styles.title}>Acces a votre cabinet</Text>
            <Text style={styles.subtitle}>
              Entrez le code de votre workspace pour continuer.
            </Text>

            <TextField
              label="Code workspace"
              placeholder="JUR-XXXXX"
              autoCapitalize="characters"
              autoCorrect={false}
              value={code}
              onChangeText={setCode}
              error={localError ?? apiError}
              hint="Demo : JUR-DEMO1"
              returnKeyType="next"
              onSubmitEditing={handleNext}
            />

            <Button label="Continuer" onPress={handleNext} loading={loading} />
          </View>

          <Text style={styles.footer}>
            Vous n'avez pas de cabinet ? Contactez votre administrateur.
          </Text>
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
  brand: { alignItems: "center", marginBottom: spacing.xxl },
  logoCircle: {
    width: 64,
    height: 64,
    borderRadius: 16,
    backgroundColor: colors.primary,
    alignItems: "center",
    justifyContent: "center",
    marginBottom: spacing.md,
  },
  brandTitle: {
    fontSize: 28,
    fontWeight: "800",
    color: colors.dark,
    letterSpacing: 1,
  },
  brandSubtitle: {
    marginTop: spacing.xs,
    fontSize: 13,
    color: colors.textMuted,
    textAlign: "center",
  },
  formCard: {
    backgroundColor: colors.surface,
    padding: spacing.xl,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: colors.border,
  },
  stepLabel: {
    fontSize: 12,
    fontWeight: "700",
    color: colors.primary,
    marginBottom: 4,
    letterSpacing: 0.5,
  },
  title: {
    fontSize: 20,
    fontWeight: "700",
    color: colors.dark,
    marginBottom: 4,
  },
  subtitle: {
    fontSize: 13,
    color: colors.textMuted,
    marginBottom: spacing.lg,
  },
  footer: {
    marginTop: spacing.xl,
    fontSize: 12,
    color: colors.textMuted,
    textAlign: "center",
  },
});
