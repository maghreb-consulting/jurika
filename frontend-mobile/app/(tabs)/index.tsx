import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import React, { useCallback, useEffect, useState } from "react";
import {
  ActivityIndicator,
  FlatList,
  Pressable,
  RefreshControl,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Badge, statusLabel, statusToTone } from "../../components/Badge";
import { Card } from "../../components/Card";
import { EmptyState } from "../../components/EmptyState";
import { Header } from "../../components/Header";
import { supervisionApi, ticketsApi } from "../../lib/api";
import { useAuthStore } from "../../store/authStore";
import { colors, spacing } from "../../theme/colors";
import type { KpisResponse, Ticket } from "../../types";

interface KpiCardProps {
  label: string;
  value: number | string;
  icon: keyof typeof Ionicons.glyphMap;
  tint: string;
}

function KpiCard({ label, value, icon, tint }: KpiCardProps) {
  return (
    <Card style={styles.kpiCard}>
      <View style={[styles.kpiIcon, { backgroundColor: tint + "22" }]}>
        <Ionicons name={icon} size={20} color={tint} />
      </View>
      <Text style={styles.kpiValue}>{value}</Text>
      <Text style={styles.kpiLabel}>{label}</Text>
    </Card>
  );
}

export default function DashboardScreen() {
  const router = useRouter();
  const user = useAuthStore((s) => s.user);
  const [kpis, setKpis] = useState<KpisResponse>({});
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async () => {
    try {
      const [kpisRes, ticketsRes] = await Promise.all([
        supervisionApi.kpis(),
        ticketsApi.list(5),
      ]);
      setKpis(kpisRes ?? {});
      setTickets(ticketsRes?.items ?? []);
    } catch {
      setTickets([]);
    }
  }, []);

  useEffect(() => {
    (async () => {
      setLoading(true);
      await load();
      setLoading(false);
    })();
  }, [load]);

  async function onRefresh() {
    setRefreshing(true);
    await load();
    setRefreshing(false);
  }

  const greeting = (() => {
    const h = new Date().getHours();
    if (h < 6) return "Bonne nuit";
    if (h < 12) return "Bonjour";
    if (h < 18) return "Bon apres-midi";
    return "Bonsoir";
  })();

  return (
    <SafeAreaView style={styles.safe} edges={["top"]}>
      <FlatList
        data={tickets}
        keyExtractor={(t) => t.id}
        contentContainerStyle={styles.scroll}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
        }
        ListHeaderComponent={
          <>
            <Header
              title={`${greeting}${user?.firstName ? ", " + user.firstName : ""}`}
              subtitle={user?.workspaceCode ?? "JURIKA"}
            />

            <View style={styles.kpisRow}>
              <KpiCard
                label="Tickets ouverts"
                value={kpis.ticketsOpen ?? tickets.filter((t) => (t.status ?? t.statut) !== "CLOTURE").length}
                icon="ticket-outline"
                tint={colors.primary}
              />
              <KpiCard
                label="En cours"
                value={kpis.workflowsActive ?? "-"}
                icon="time-outline"
                tint={colors.warning}
              />
            </View>
            <View style={styles.kpisRow}>
              <KpiCard
                label="Dossiers"
                value={kpis.dossiersTotal ?? "-"}
                icon="folder-outline"
                tint={colors.violet}
              />
              <KpiCard
                label="Aujourd'hui"
                value={kpis.ticketsToday ?? "-"}
                icon="calendar-outline"
                tint={colors.success}
              />
            </View>

            <View style={styles.sectionHead}>
              <Text style={styles.sectionTitle}>Tickets recents</Text>
              <Pressable onPress={() => router.push("/(tabs)/tickets")}>
                <Text style={styles.sectionLink}>Voir tout</Text>
              </Pressable>
            </View>
          </>
        }
        renderItem={({ item }) => {
          const status = (item.status ?? item.statut) as string;
          return (
            <Card
              onPress={() => router.push(`/(tabs)/tickets/${item.id}`)}
              style={styles.ticketCard}
            >
              <View style={styles.ticketRow}>
                <Text style={styles.ticketTitle} numberOfLines={1}>
                  {item.titre ?? item.title ?? item.reference ?? item.id}
                </Text>
                <Badge label={statusLabel(status)} tone={statusToTone(status)} />
              </View>
              {item.description ? (
                <Text style={styles.ticketDesc} numberOfLines={2}>
                  {item.description}
                </Text>
              ) : null}
            </Card>
          );
        }}
        ListEmptyComponent={
          loading ? (
            <View style={styles.loader}>
              <ActivityIndicator color={colors.primary} />
            </View>
          ) : (
            <EmptyState
              icon="ticket-outline"
              title="Aucun ticket"
              message="Vos tickets recents apparaitront ici."
            />
          )
        }
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  scroll: { paddingBottom: spacing.xxl },
  kpisRow: {
    flexDirection: "row",
    gap: spacing.md,
    paddingHorizontal: spacing.lg,
    marginBottom: spacing.md,
  },
  kpiCard: { flex: 1, padding: spacing.lg },
  kpiIcon: {
    width: 36,
    height: 36,
    borderRadius: 10,
    alignItems: "center",
    justifyContent: "center",
    marginBottom: spacing.sm,
  },
  kpiValue: { fontSize: 22, fontWeight: "800", color: colors.dark },
  kpiLabel: { fontSize: 12, color: colors.textMuted, marginTop: 2 },
  sectionHead: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    paddingHorizontal: spacing.lg,
    marginTop: spacing.md,
    marginBottom: spacing.sm,
  },
  sectionTitle: { fontSize: 16, fontWeight: "700", color: colors.dark },
  sectionLink: { fontSize: 13, fontWeight: "600", color: colors.primary },
  ticketCard: { marginHorizontal: spacing.lg, marginBottom: spacing.md },
  ticketRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  ticketTitle: {
    flex: 1,
    fontSize: 14,
    fontWeight: "600",
    color: colors.dark,
    marginRight: spacing.sm,
  },
  ticketDesc: {
    marginTop: 6,
    fontSize: 12,
    color: colors.textMuted,
  },
  loader: { paddingVertical: spacing.xxl, alignItems: "center" },
});
