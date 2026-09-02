import { Ionicons } from "@expo/vector-icons";
import { useRouter } from "expo-router";
import React, { useCallback, useEffect, useState } from "react";
import {
  ActivityIndicator,
  FlatList,
  RefreshControl,
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
import type { DossierBrief } from "../../../types";

export default function DataRoomScreen() {
  const router = useRouter();
  const [dossiers, setDossiers] = useState<DossierBrief[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async () => {
    try {
      const res = await dataroomApi.listDossiers();
      setDossiers(Array.isArray(res) ? res : []);
    } catch {
      setDossiers([]);
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

  return (
    <SafeAreaView style={styles.safe} edges={["top"]}>
      <Header
        title="Data Room"
        subtitle={`${dossiers.length} dossier(s)`}
      />
      <FlatList
        data={dossiers}
        keyExtractor={(d) => d.id}
        contentContainerStyle={styles.list}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
        }
        renderItem={({ item }) => (
          <Card
            onPress={() => router.push(`/(tabs)/dataroom/${item.id}`)}
            style={styles.card}
          >
            <View style={styles.row}>
              <View style={styles.iconWrap}>
                <Ionicons
                  name="folder"
                  size={22}
                  color={colors.violet}
                />
              </View>
              <View style={{ flex: 1 }}>
                <Text style={styles.name} numberOfLines={1}>
                  {item.raisonSociale}
                </Text>
                <Text style={styles.meta} numberOfLines={1}>
                  {[item.formeJuridique, item.rc ? `RC ${item.rc}` : null]
                    .filter(Boolean)
                    .join(" - ")}
                </Text>
              </View>
              <Ionicons
                name="chevron-forward"
                size={20}
                color={colors.textMuted}
              />
            </View>
          </Card>
        )}
        ListEmptyComponent={
          loading ? (
            <View style={styles.loader}>
              <ActivityIndicator color={colors.primary} />
            </View>
          ) : (
            <EmptyState
              icon="folder-open-outline"
              title="Aucun dossier"
              message="Les dossiers de vos clients apparaitront ici."
            />
          )
        }
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  list: { padding: spacing.lg, paddingBottom: spacing.xxl },
  card: { marginBottom: spacing.md },
  row: { flexDirection: "row", alignItems: "center" },
  iconWrap: {
    width: 40,
    height: 40,
    borderRadius: radius.md,
    backgroundColor: "#EDE9FE",
    alignItems: "center",
    justifyContent: "center",
    marginRight: spacing.md,
  },
  name: { fontSize: 15, fontWeight: "700", color: colors.dark },
  meta: { fontSize: 12, color: colors.textMuted, marginTop: 2 },
  loader: { paddingVertical: spacing.xxl, alignItems: "center" },
});
