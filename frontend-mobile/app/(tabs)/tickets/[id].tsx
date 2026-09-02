import { Ionicons } from "@expo/vector-icons";
import { useLocalSearchParams, useRouter } from "expo-router";
import React, { useCallback, useEffect, useState } from "react";
import {
  ActivityIndicator,
  Alert,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Badge, statusLabel, statusToTone } from "../../../components/Badge";
import { Button } from "../../../components/Button";
import { Card } from "../../../components/Card";
import { Header } from "../../../components/Header";
import { ticketsApi } from "../../../lib/api";
import { colors, radius, spacing } from "../../../theme/colors";
import type { Ticket, TicketStatus } from "../../../types";

interface TransitionOption {
  target: TicketStatus;
  label: string;
  tone: "primary" | "success" | "danger";
  requiresComment?: boolean;
}

const TRANSITIONS: Record<string, TransitionOption[]> = {
  NOUVEAU: [
    { target: "EN_COURS", label: "Demarrer", tone: "primary" },
    {
      target: "ANNULE",
      label: "Annuler",
      tone: "danger",
      requiresComment: true,
    },
  ],
  EN_COURS: [
    { target: "CLOTURE", label: "Cloturer", tone: "success" },
    {
      target: "ANNULE",
      label: "Annuler",
      tone: "danger",
      requiresComment: true,
    },
  ],
  CLOTURE: [],
  ANNULE: [],
};

export default function TicketDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const router = useRouter();
  const [ticket, setTicket] = useState<Ticket | null>(null);
  const [loading, setLoading] = useState(true);
  const [comment, setComment] = useState("");
  const [submitting, setSubmitting] = useState<TicketStatus | null>(null);

  const load = useCallback(async () => {
    if (!id) return;
    try {
      const res = await ticketsApi.get(id);
      setTicket(res);
    } catch (e: any) {
      Alert.alert(
        "Erreur",
        e?.response?.data?.message ?? "Ticket introuvable."
      );
    }
  }, [id]);

  useEffect(() => {
    (async () => {
      setLoading(true);
      await load();
      setLoading(false);
    })();
  }, [load]);

  async function handleTransition(opt: TransitionOption) {
    if (!ticket) return;
    if (opt.requiresComment && comment.trim().length < 10) {
      Alert.alert(
        "Commentaire requis",
        "Le commentaire doit contenir au moins 10 caracteres."
      );
      return;
    }
    setSubmitting(opt.target);
    try {
      const updated = await ticketsApi.transition(
        ticket.id,
        opt.target,
        opt.requiresComment ? comment.trim() : undefined
      );
      setTicket(updated);
      setComment("");
      Alert.alert("Succes", `Ticket -> ${statusLabel(opt.target)}`);
    } catch (e: any) {
      Alert.alert(
        "Erreur",
        e?.response?.data?.message ?? "Transition impossible."
      );
    } finally {
      setSubmitting(null);
    }
  }

  if (loading) {
    return (
      <SafeAreaView style={styles.safe} edges={["top"]}>
        <Header title="Ticket" back />
        <View style={styles.center}>
          <ActivityIndicator color={colors.primary} />
        </View>
      </SafeAreaView>
    );
  }

  if (!ticket) {
    return (
      <SafeAreaView style={styles.safe} edges={["top"]}>
        <Header title="Ticket" back />
        <View style={styles.center}>
          <Ionicons
            name="alert-circle-outline"
            size={36}
            color={colors.textMuted}
          />
          <Text style={styles.notFoundText}>Ticket introuvable.</Text>
        </View>
      </SafeAreaView>
    );
  }

  const status = (ticket.status ?? ticket.statut) as TicketStatus;
  const options = TRANSITIONS[status] ?? [];

  return (
    <SafeAreaView style={styles.safe} edges={["top"]}>
      <KeyboardAvoidingView
        behavior={Platform.OS === "ios" ? "padding" : undefined}
        style={{ flex: 1 }}
      >
        <Header title="Detail ticket" back />
        <ScrollView
          contentContainerStyle={styles.container}
          keyboardShouldPersistTaps="handled"
        >
          <Card style={styles.card}>
            <View style={styles.row}>
              <Text style={styles.ref}>{ticket.reference ?? ticket.id}</Text>
              <Badge label={statusLabel(status)} tone={statusToTone(status)} />
            </View>
            <Text style={styles.title}>
              {ticket.titre ?? ticket.title ?? "Sans titre"}
            </Text>
            {ticket.workflowType || ticket.type ? (
              <Text style={styles.meta}>
                {ticket.workflowType ?? ticket.type}
              </Text>
            ) : null}
            {ticket.description ? (
              <>
                <Text style={styles.sectionLabel}>Description</Text>
                <Text style={styles.desc}>{ticket.description}</Text>
              </>
            ) : null}

            <View style={styles.divider} />

            <View style={styles.infoRow}>
              <Text style={styles.infoLabel}>Client</Text>
              <Text style={styles.infoValue}>
                {ticket.clientName ?? "-"}
              </Text>
            </View>
            <View style={styles.infoRow}>
              <Text style={styles.infoLabel}>Assigne</Text>
              <Text style={styles.infoValue}>
                {ticket.assigneeName ?? "-"}
              </Text>
            </View>
            <View style={styles.infoRow}>
              <Text style={styles.infoLabel}>Cree</Text>
              <Text style={styles.infoValue}>
                {ticket.createdAt
                  ? new Date(ticket.createdAt).toLocaleString("fr-FR")
                  : "-"}
              </Text>
            </View>
          </Card>

          {options.length > 0 ? (
            <Card style={styles.card}>
              <Text style={styles.sectionTitle}>Actions</Text>
              {options.some((o) => o.requiresComment) ? (
                <View>
                  <Text style={styles.commentLabel}>
                    Commentaire (requis pour annulation, min 10 caracteres)
                  </Text>
                  <TextInput
                    style={styles.commentInput}
                    placeholder="Motif d'annulation..."
                    placeholderTextColor={colors.textSubtle}
                    multiline
                    value={comment}
                    onChangeText={setComment}
                  />
                </View>
              ) : null}

              <View style={styles.actions}>
                {options.map((o) => (
                  <Button
                    key={o.target}
                    label={o.label}
                    onPress={() => handleTransition(o)}
                    variant={
                      o.tone === "danger"
                        ? "danger"
                        : o.tone === "success"
                          ? "primary"
                          : "primary"
                    }
                    loading={submitting === o.target}
                    disabled={submitting !== null}
                    style={
                      o.tone === "success"
                        ? { backgroundColor: colors.success }
                        : undefined
                    }
                  />
                ))}
              </View>
            </Card>
          ) : (
            <Card style={styles.card}>
              <Text style={styles.terminalText}>
                Ce ticket est dans un etat terminal. Aucune action disponible.
              </Text>
            </Card>
          )}
        </ScrollView>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  container: { padding: spacing.lg, paddingBottom: spacing.xxl },
  center: { flex: 1, alignItems: "center", justifyContent: "center" },
  card: { marginBottom: spacing.md },
  row: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    marginBottom: spacing.sm,
  },
  ref: { fontSize: 12, color: colors.textMuted, fontWeight: "600" },
  title: { fontSize: 18, fontWeight: "700", color: colors.dark },
  meta: { fontSize: 12, color: colors.primary, marginTop: 4 },
  sectionLabel: {
    marginTop: spacing.md,
    fontSize: 12,
    color: colors.textMuted,
    fontWeight: "600",
  },
  desc: { fontSize: 14, color: colors.slate700, marginTop: 4, lineHeight: 20 },
  divider: {
    height: 1,
    backgroundColor: colors.border,
    marginVertical: spacing.md,
  },
  infoRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    paddingVertical: 4,
  },
  infoLabel: { fontSize: 13, color: colors.textMuted },
  infoValue: {
    fontSize: 13,
    color: colors.dark,
    fontWeight: "600",
    maxWidth: "60%",
    textAlign: "right",
  },
  sectionTitle: {
    fontSize: 15,
    fontWeight: "700",
    color: colors.dark,
    marginBottom: spacing.md,
  },
  commentLabel: {
    fontSize: 12,
    color: colors.textMuted,
    marginBottom: 6,
  },
  commentInput: {
    minHeight: 80,
    padding: spacing.md,
    borderRadius: radius.md,
    borderWidth: 1,
    borderColor: colors.border,
    color: colors.dark,
    fontSize: 14,
    textAlignVertical: "top",
    marginBottom: spacing.md,
  },
  actions: { gap: spacing.sm },
  terminalText: {
    fontSize: 13,
    color: colors.textMuted,
    textAlign: "center",
  },
  notFoundText: { marginTop: spacing.md, fontSize: 14, color: colors.textMuted },
});
