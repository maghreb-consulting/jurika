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

export default function LoginScreen() {
  const router = useRouter();
  const login = useAuthStore((s) => s.login);
  const workspaceCode = useAuthStore((s) => s.workspaceCode);
  const loading = useAuthStore((s) => s.loading);
  const apiError = useAuthStore((s) => s.error);
  const clearError = useAuthStore((s) => s.clearError);

  const [email, setEmail] = useState("karim@jurika.ma");
  const [password, setPassword] = useState("Admin@2026");
  const [showPwd, setShowPwd] = useState(false);
  const [localError, setLocalError] = useState<string | null>(null);

  async function handleLogin() {
    clearError();
    setLocalError(null);
    if (!email || !password) {
      setLocalError("Email et mot de passe requis.");
      return;
    }
    try {
      const res = await login(email.trim(), password);
      if (res.requires2FA) {
        router.push("/(auth)/twofa");
      } else {
        router.replace("/(tabs)");
      }
    } catch {
      /* error stored in state */
    }
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
            <Text style={styles.stepLabel}>Etape 2 / 3</Text>
            <Text style={styles.title}>Connexion</Text>
            <Text style={styles.subtitle}>
              Workspace :{" "}
              <Text style={{ color: colors.dark, fontWeight: "600" }}>
                {workspaceCode ?? "-"}
              </Text>
            </Text>

            <TextField
              label="Email pro"
              placeholder="nom@cabinet.ma"
              keyboardType="email-address"
              autoCapitalize="none"
              autoCorrect={false}
              value={email}
              onChangeText={setEmail}
            />

            <View style={{ position: "relative" }}>
              <TextField
                label="Mot de passe"
                placeholder="Votre mot de passe"
                value={password}
                onChangeText={setPassword}
                secureTextEntry={!showPwd}
                autoCapitalize="none"
              />
              <Pressable
                style={styles.eye}
                onPress={() => setShowPwd((v) => !v)}
                hitSlop={10}
              >
                <Ionicons
                  name={showPwd ? "eye-off-outline" : "eye-outline"}
                  size={20}
                  color={colors.textMuted}
                />
              </Pressable>
            </View>

            {localError || apiError ? (
              <Text style={styles.errorBox}>{localError ?? apiError}</Text>
            ) : null}

            <Button
              label="Se connecter"
              onPress={handleLogin}
              loading={loading}
            />

            <Pressable style={styles.linkRow} onPress={() => router.back()}>
              <Text style={styles.linkText}>Changer de workspace</Text>
            </Pressable>
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
  eye: {
    position: "absolute",
    right: spacing.md,
    top: 32,
    height: 48,
    justifyContent: "center",
  },
  errorBox: {
    backgroundColor: "#FEE2E2",
    color: "#B91C1C",
    padding: spacing.md,
    borderRadius: 8,
    marginBottom: spacing.md,
    fontSize: 13,
  },
  linkRow: { alignItems: "center", marginTop: spacing.md },
  linkText: { color: colors.primary, fontSize: 13, fontWeight: "600" },
});
