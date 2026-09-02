import { Ionicons } from "@expo/vector-icons";
import { useLocalSearchParams } from "expo-router";
import React, { useCallback, useEffect, useState } from "react";
import {
  ActivityIndicator,
  Alert,
  FlatList,
  Pressable,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Card } from "../../../components/Card";
import { EmptyState } from "../../../components/EmptyState";
import { Header } from "../../../components/Header";
import { dataroomApi } from "../../../lib/api";
import { colors, radius, spacing } from "../../../theme/colors";
import type { DossierDocument, DossierJuridique } from "../../../types";

type Tab = "current" | "history";

export default function DossierDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const [data, setData] = useState<DossierJuridique | null>(null);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState<Tab>("current");

  const load = useCallback(async () => {
    if (!id) return;
    try {
      const res = await dataroomApi.getJuridique(id);
      setData(res);
    } catch (e: any) {
      Alert.alert(
        "Erreur",
        e?.response?.data?.message ?? "Dossier introuvable."
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

  if (loading) {
    return (
      <SafeAreaView style={styles.safe} edges={["top"]}>
        <Header title="Dossier" back />
        <View style={styles.center}>
          <ActivityIndicator color={colors.primary} />
        </View>
      </SafeAreaView>
    );
  }

  if (!data) {
    return (
      <SafeAreaView style={styles.safe} edges={["top"]}>
        <Header title="Dossier" back />
        <View style={styles.center}>
          <EmptyState
            icon="folder-open-outline"
            title="Dossier introuvable"
          />
        </View>
      </SafeAreaView>
    );
  }

  const currentDocs = data.documents ?? [];
  const history = data.history ?? [];

  return (
    <SafeAreaView style={styles.safe} edges={["top"]}>
      <Header
        title={data.dossier.raisonSociale}
        subtitle={data.dossier.formeJuridique ?? "Dossier juridique"}
        back
      />

      <View style={styles.tabs}>
        <Pressable
          onPress={() => setTab("current")}
          style={[styles.tab, tab === "current" ? styles.tabActive : null]}
        >
          <Text
            style={[
              styles.tabText,
              tab === "current" ? styles.tabTextActive : null,
            ]}
          >
            En vigueur ({currentDocs.length})
          </Text>
        </Pressable>
        <Pressable
          onPress={() => setTab("history")}
          style={[styles.tab, tab === "history" ? styles.tabActive : null]}
        >
          <Text
            style={[
              styles.tabText,
              tab === "history" ? styles.tabTextActive : null,
            ]}
          >
            Historique ({history.length})
          </Text>
        </Pressable>
      </View>

      {tab === "current" ? (
        <FlatList
          data={currentDocs}
          keyExtractor={(d) => d.id}
          contentContainerStyle={styles.list}
          renderItem={({ item }) => <DocRow doc={item} />}
          ListEmptyComponent={
            <EmptyState
              icon="document-text-outline"
              title="Aucun document"
              message="Les documents en vigueur apparaitront ici."
            />
          }
        />
      ) : (
        <FlatList
          data={history}
          keyExtractor={(h) => h.ticketId}
          contentContainerStyle={styles.list}
          renderItem={({ item }) => (
            <Card style={styles.card}>
              <View style={styles.historyHead}>
                <Text style={styles.historyType}>{item.type}</Text>
                <Text style={styles.historyDate}>
                  {new Date(item.closedAt).toLocaleDateString("fr-FR")}
                </Text>
              </View>
              {item.documents && item.documents.length > 0 ? (
                item.documents.map((d) => <DocRow key={d.id} doc={d} compact />)
              ) : (
                <Text style={styles.muted}>Aucun document attache.</Text>
              )}
            </Card>
          )}
          ListEmptyComponent={
            <EmptyState
              icon="time-outline"
              title="Aucun historique"
              message="Les operations passees apparaitront ici."
            />
          }
        />
      )}
    </SafeAreaView>
  );
}

function DocRow({
  doc,
  compact,
}: {
  doc: DossierDocument;
  compact?: boolean;
}) {
  return (
    <Card style={[styles.card, compact ? { marginBottom: spacing.sm } : null]}>
      <View style={styles.docRow}>
        <View style={styles.docIcon}>
          <Ionicons
            name="document-text"
            size={20}
            color={colors.primary}
          />
        </View>
        <View style={{ flex: 1 }}>
          <Text style={styles.docName} numberOfLines={1}>
            {doc.name}
          </Text>
          <Text style={styles.docMeta}>
            {[
              doc.category,
              doc.version ? `v${doc.version}` : null,
              doc.createdAt
                ? new Date(doc.createdAt).toLocaleDateString("fr-FR")
                : null,
            ]
              .filter(Boolean)
              .join(" - ")}
          </Text>
        </View>
        <Ionicons
          name="download-outline"
          size={20}
          color={colors.textMuted}
        />
      </View>
    </Card>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  center: { flex: 1, alignItems: "center", justifyContent: "center" },
  tabs: {
    flexDirection: "row",
    marginHorizontal: spacing.lg,
    backgroundColor: colors.surface,
    borderRadius: radius.md,
    padding: 4,
    borderWidth: 1,
    borderColor: colors.border,
  },
  tab: {
    flex: 1,
    alignItems: "center",
    paddingVertical: 8,
    borderRadius: radius.sm,
  },
  tabActive: { backgroundColor: colors.primary },
  tabText: { fontSize: 12, fontWeight: "600", color: colors.slate700 },
  tabTextActive: { color: "#FFFFFF" },
  list: { padding: spacing.lg, paddingBottom: spacing.xxl },
  card: { marginBottom: spacing.md },
  docRow: { flexDirection: "row", alignItems: "center" },
  docIcon: {
    width: 36,
    height: 36,
    borderRadius: radius.md,
    backgroundColor: "#DBEAFE",
    alignItems: "center",
    justifyContent: "center",
    marginRight: spacing.md,
  },
  docName: { fontSize: 14, fontWeight: "600", color: colors.dark },
  docMeta: { fontSize: 12, color: colors.textMuted, marginTop: 2 },
  historyHead: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: spacing.sm,
  },
  historyType: { fontSize: 14, fontWeight: "700", color: colors.primary },
  historyDate: { fontSize: 12, color: colors.textMuted },
  muted: { fontSize: 13, color: colors.textMuted, fontStyle: "italic" },
});
