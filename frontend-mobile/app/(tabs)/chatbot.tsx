import { Ionicons } from "@expo/vector-icons";
import React, { useRef, useState } from "react";
import {
  ActivityIndicator,
  FlatList,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { Header } from "../../components/Header";
import { chatbotApi } from "../../lib/api";
import { colors, radius, spacing } from "../../theme/colors";
import type { ChatbotMessage } from "../../types";

const SUGGESTIONS = [
  "Quelle est la procedure de creation d'une SARL au Maroc ?",
  "Quels documents pour une dissolution ?",
  "Comment changer la denomination sociale ?",
  "Quelles sont les obligations comptables annuelles ?",
];

export default function ChatbotScreen() {
  const [messages, setMessages] = useState<ChatbotMessage[]>([
    {
      id: "welcome",
      role: "assistant",
      text:
        "Bonjour. Je suis votre assistant JURIKA. Posez-moi une question sur le droit des societes au Maroc.",
      createdAt: Date.now(),
    },
  ]);
  const [input, setInput] = useState("");
  const [loading, setLoading] = useState(false);
  const listRef = useRef<FlatList<ChatbotMessage>>(null);

  async function send(question?: string) {
    const text = (question ?? input).trim();
    if (!text || loading) return;

    const userMsg: ChatbotMessage = {
      id: `u-${Date.now()}`,
      role: "user",
      text,
      createdAt: Date.now(),
    };
    setMessages((prev) => [...prev, userMsg]);
    setInput("");
    setLoading(true);
    requestAnimationFrame(() =>
      listRef.current?.scrollToEnd({ animated: true })
    );

    try {
      const res = await chatbotApi.ask(text);
      const botMsg: ChatbotMessage = {
        id: `a-${Date.now()}`,
        role: "assistant",
        text: res?.answer ?? "Je n'ai pas pu trouver de reponse.",
        createdAt: Date.now(),
      };
      setMessages((prev) => [...prev, botMsg]);
    } catch (e: any) {
      const errMsg: ChatbotMessage = {
        id: `e-${Date.now()}`,
        role: "assistant",
        text:
          e?.response?.data?.message ??
          "Service indisponible. Reessayez plus tard.",
        createdAt: Date.now(),
      };
      setMessages((prev) => [...prev, errMsg]);
    } finally {
      setLoading(false);
      requestAnimationFrame(() =>
        listRef.current?.scrollToEnd({ animated: true })
      );
    }
  }

  return (
    <SafeAreaView style={styles.safe} edges={["top"]}>
      <Header title="Assistant IA" subtitle="Powered by JURIKA RAG" />
      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === "ios" ? "padding" : undefined}
        keyboardVerticalOffset={Platform.OS === "ios" ? 90 : 0}
      >
        <FlatList
          ref={listRef}
          data={messages}
          keyExtractor={(m) => m.id}
          contentContainerStyle={styles.list}
          renderItem={({ item }) => (
            <View
              style={[
                styles.bubble,
                item.role === "user" ? styles.bubbleUser : styles.bubbleBot,
              ]}
            >
              <Text
                style={[
                  styles.bubbleText,
                  item.role === "user" ? styles.bubbleTextUser : null,
                ]}
              >
                {item.text}
              </Text>
            </View>
          )}
          ListFooterComponent={
            <>
              {loading ? (
                <View style={[styles.bubble, styles.bubbleBot]}>
                  <ActivityIndicator size="small" color={colors.primary} />
                </View>
              ) : null}
              {messages.length <= 1 ? (
                <View style={styles.suggestions}>
                  <Text style={styles.suggTitle}>Questions frequentes</Text>
                  {SUGGESTIONS.map((q) => (
                    <Pressable
                      key={q}
                      style={styles.suggChip}
                      onPress={() => send(q)}
                    >
                      <Ionicons
                        name="sparkles-outline"
                        size={14}
                        color={colors.primary}
                      />
                      <Text style={styles.suggText}>{q}</Text>
                    </Pressable>
                  ))}
                </View>
              ) : null}
            </>
          }
        />

        <View style={styles.inputBar}>
          <TextInput
            style={styles.input}
            placeholder="Posez votre question..."
            placeholderTextColor={colors.textSubtle}
            value={input}
            onChangeText={setInput}
            multiline
            maxLength={500}
            onSubmitEditing={() => send()}
            blurOnSubmit
          />
          <Pressable
            onPress={() => send()}
            disabled={loading || !input.trim()}
            style={[
              styles.sendBtn,
              !input.trim() || loading ? styles.sendBtnDisabled : null,
            ]}
          >
            <Ionicons name="send" size={18} color="#FFFFFF" />
          </Pressable>
        </View>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: colors.bg },
  list: { padding: spacing.lg, paddingBottom: spacing.lg },
  bubble: {
    maxWidth: "85%",
    padding: spacing.md,
    borderRadius: radius.lg,
    marginBottom: spacing.sm,
  },
  bubbleUser: {
    alignSelf: "flex-end",
    backgroundColor: colors.primary,
    borderBottomRightRadius: 4,
  },
  bubbleBot: {
    alignSelf: "flex-start",
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderBottomLeftRadius: 4,
  },
  bubbleText: { fontSize: 14, color: colors.slate700, lineHeight: 20 },
  bubbleTextUser: { color: "#FFFFFF" },
  suggestions: { marginTop: spacing.lg },
  suggTitle: {
    fontSize: 12,
    fontWeight: "600",
    color: colors.textMuted,
    marginBottom: spacing.sm,
    textTransform: "uppercase",
    letterSpacing: 0.5,
  },
  suggChip: {
    flexDirection: "row",
    alignItems: "center",
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
    borderRadius: radius.md,
    marginBottom: spacing.sm,
    gap: 8,
  },
  suggText: { fontSize: 13, color: colors.slate700, flex: 1 },
  inputBar: {
    flexDirection: "row",
    alignItems: "flex-end",
    padding: spacing.md,
    borderTopWidth: 1,
    borderTopColor: colors.border,
    backgroundColor: colors.surface,
    gap: spacing.sm,
  },
  input: {
    flex: 1,
    minHeight: 44,
    maxHeight: 120,
    paddingHorizontal: spacing.md,
    paddingTop: 12,
    paddingBottom: 12,
    borderRadius: radius.lg,
    backgroundColor: colors.bg,
    borderWidth: 1,
    borderColor: colors.border,
    color: colors.dark,
    fontSize: 14,
  },
  sendBtn: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: colors.primary,
    alignItems: "center",
    justifyContent: "center",
  },
  sendBtnDisabled: { backgroundColor: colors.textSubtle },
});
