import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import React, { useCallback, useEffect, useMemo, useState } from "react";
import {
  ActivityIndicator,
  FlatList,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Badge, statusLabel, statusToTone } from "../../../components/Badge";
import { Card } from "../../../components/Card";
import { EmptyState } from "../../../components/EmptyState";
import { Header } from "../../../components/Header";
import { ticketsApi } from "../../../lib/api";
import { colors, radius, spacing } from "../../../theme/colors";
import type { Ticket, TicketStatus } from "../../../types";

type Filter = "ALL" | TicketStatus;

const FILTERS: { key: Filter; label: string }[] = [
  { key: "ALL", label: "Tous" },
  { key: "NOUVEAU", label: "Nouveau" },
  { key: "EN_COURS", label: "En cours" },
  { key: "CLOTURE", label: "Cloture" },
  { key: "ANNULE", label: "Annule" },
];

export default function TicketsScreen() {
  const router = useRouter();
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [filter, setFilter] = useState<Filter>("ALL");

  const load = useCallback(async () => {
    try {
      const res = await ticketsApi.list(50);
      setTickets(res?.items ?? []);
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

  const filtered = useMemo(() => {
    if (filter === "ALL") return tickets;
    return tickets.filter((t) => (t.status ?? t.statut) === filter);
  }, [tickets, filter]);

  async function onRefresh() {
    setRefreshing(true);
    await load();
    setRefreshing(false);
  }

  return (
    <SafeAreaView style={styles.safe} edges={["top"]}>
      <Header
        title="Tickets"
        subtitle={`${tickets.length} ticket(s)`}
        right={
          <Pressable
            onPress={onRefresh}
            hitSlop={10}
            style={styles.iconBtn}
          >
            <Ionicons name="refresh" size={20} color={colors.dark} />
          </Pressable>
        }
      />

      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.filterRow}
      >
        {FILTERS.map((f) => {
          const active = filter === f.key;
          return (
            <Pressable
              key={f.key}
              onPress={() => setFilter(f.key)}
              style={[styles.chip, active ? styles.chipActive : null]}
            >
              <Text
                style={[
                  styles.chipText,
                  active ? styles.chipTextActive : null,
                ]}
              >
                {f.label}
              </Text>
            </Pressable>
          );
        })}
      </ScrollView>

      <FlatList
        data={filtered}
        keyExtractor={(t) => t.id}
        contentContainerStyle={styles.list}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
        }
        renderItem={({ item }) => {
          const status = (item.status ?? item.statut) as string;
          return (
            <Card
              onPress={() => router.push(`/(tabs)/tickets/${item.id}`)}
              style={styles.card}
            >
              <View style={styles.cardHead}>
                <Text style={styles.ref}>{item.reference ?? item.id}</Text>
                <Badge label={statusLabel(status)} tone={statusToTone(status)} />
              </View>
              <Text style={styles.title} numberOfLines={1}>
                {item.titre ?? item.title ?? "Sans titre"}
              </Text>
              {item.workflowType || item.type ? (
                <Text style={styles.meta}>
                  {item.workflowType ?? item.type}
                </Text>
              ) : null}
              {item.description ? (
                <Text style={styles.desc} numberOfLines={2}>
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
              message="Aucun ticket dans cette categorie."
            />
          )
        }
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  iconBtn: { padding: 6 },
  filterRow: {
    paddingHorizontal: spacing.lg,
    paddingVertical: spacing.sm,
    gap: spacing.sm,
  },
  chip: {
    paddingHorizontal: spacing.md,
    paddingVertical: 8,
    borderRadius: radius.pill,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    marginRight: spacing.sm,
  },
  chipActive: {
    backgroundColor: colors.primary,
    borderColor: colors.primary,
  },
  chipText: { fontSize: 13, fontWeight: "600", color: colors.slate700 },
  chipTextActive: { color: "#FFFFFF" },
  list: { padding: spacing.lg, paddingBottom: spacing.xxl },
  card: { marginBottom: spacing.md },
  cardHead: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: spacing.sm,
  },
  ref: { fontSize: 12, color: colors.textMuted, fontWeight: "600" },
  title: { fontSize: 15, fontWeight: "700", color: colors.dark },
  meta: { fontSize: 12, color: colors.primary, marginTop: 4 },
  desc: { fontSize: 13, color: colors.textMuted, marginTop: 6 },
  loader: { paddingVertical: spacing.xxl, alignItems: "center" },
});
