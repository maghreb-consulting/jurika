import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Bot,
  Send,
  Sparkles,
  AlertCircle,
  Loader2,
  BookOpen,
  Gauge,
  User,
  Library,
  Upload,
  FileText,
  Trash2,
  X,
  Plus,
  MessageSquare,
} from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { useToast } from '../../components/ui/Toast';
import { ChatMarkdown } from '../../components/ChatMarkdown';
import { chatbotService } from '../../services/chatbot.service';
import { extractError } from '../../lib/api';
import { useCurrentUser } from '../../store/authStore';
import {
  deriveTitle,
  loadConversations,
  saveConversations,
  type ChatbotConversation,
} from '../../lib/chatbotConversations';
import type { ChatbotAnswer, ChatbotEntry, ChatbotSourceDoc } from '../../types/chat';

const SUGGESTIONS: string[] = [
  'Comment creer une SARL ?',
  'Capital minimum SARL ?',
  'Procedure de dissolution',
  'Succursale etrangere au Maroc ?',
];

function confidenceLabel(score: number): {
  label: string;
  variant: 'danger' | 'warning' | 'success';
} {
  if (score < 0.4) return { label: 'Faible', variant: 'danger' };
  if (score < 0.7) return { label: 'Moyen', variant: 'warning' };
  return { label: 'Eleve', variant: 'success' };
}

function formatPercent(score: number): string {
  if (Number.isNaN(score)) return '—';
  return `${Math.round(score * 100)}%`;
}

function generateId(): string {
  return `qa-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function newConversation(): ChatbotConversation {
  const now = new Date().toISOString();
  return { id: generateId(), title: '', entries: [], createdAt: now, updatedAt: now };
}

export function ChatbotPage() {
  const user = useCurrentUser();
  const userId = user?.userId ?? null;
  // Le CLIENT peut interroger l'assistant (/ask) mais NE gere PAS le corpus RAG
  // (« Sources fiables ») — cohérent avec la restriction backend (2026-07-25 :
  // POST/GET/DELETE /chatbot/sources réservés EMPLOYE/SUPERVISEUR/SUPER_ADMIN).
  const canManageSources = user?.role !== 'CLIENT';

  // Conversations persistees par utilisateur (localStorage). `activeId` designe
  // le fil courant ; `history` = ses entrees.
  const [conversations, setConversations] = useState<ChatbotConversation[]>([]);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [hydrated, setHydrated] = useState(false);

  const [question, setQuestion] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const scrollAnchor = useRef<HTMLDivElement | null>(null);
  const inputRef = useRef<HTMLTextAreaElement | null>(null);
  const toast = useToast();

  // Sources fiables (corpus RAG du workspace) — independant des conversations.
  const [showSources, setShowSources] = useState(false);
  const [sources, setSources] = useState<ChatbotSourceDoc[]>([]);
  const [loadingSources, setLoadingSources] = useState(false);
  const [uploadBusy, setUploadBusy] = useState(false);
  // Erreur contextuelle de la carte Sources (chargement / action) — bandeau inline.
  const [sourceError, setSourceError] = useState<string | null>(null);
  const fileInput = useRef<HTMLInputElement | null>(null);
  // Confirmations (Lot O) : garde-fous avant ajout / suppression d'une source.
  const [pendingFiles, setPendingFiles] = useState<File[] | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<ChatbotSourceDoc | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);

  const active = useMemo(
    () => conversations.find((c) => c.id === activeId) ?? null,
    [conversations, activeId],
  );
  const history = active?.entries ?? [];
  const empty = history.length === 0;

  // Hydratation au montage / changement d'utilisateur.
  useEffect(() => {
    const loaded = loadConversations(userId);
    setConversations(loaded);
    setActiveId(loaded.length > 0 ? loaded[0].id : null);
    setHydrated(true);
  }, [userId]);

  // Persistance (apres hydratation, pour ne pas ecraser le stockage avec []).
  useEffect(() => {
    if (!hydrated) return;
    saveConversations(userId, conversations);
  }, [conversations, userId, hydrated]);

  useEffect(() => {
    scrollAnchor.current?.scrollIntoView({ behavior: 'smooth' });
  }, [history]);

  // Auto-grow du textarea : reset hauteur puis ajuste au contenu (borne max-h-40).
  function autoGrow(el: HTMLTextAreaElement | null) {
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = `${el.scrollHeight}px`;
  }
  useEffect(() => {
    if (!question) autoGrow(inputRef.current);
  }, [question]);

  const loadSources = useCallback(async () => {
    setLoadingSources(true);
    setSourceError(null);
    try {
      setSources(await chatbotService.listSources());
    } catch (e) {
      setSourceError(extractError(e).message);
    } finally {
      setLoadingSources(false);
    }
  }, []);

  useEffect(() => {
    if (showSources) void loadSources();
  }, [showSources, loadSources]);

  // Selection de fichier(s) : on NE televerse PAS tout de suite. On memorise la
  // selection et on ouvre un recapitulatif de confirmation (evite l'ajout par
  // erreur). L'input est reinitialise pour permettre de re-selectionner ensuite.
  function onFilesSelected(files: FileList | null) {
    const list = files ? Array.from(files) : [];
    if (fileInput.current) fileInput.current.value = '';
    if (list.length === 0) return;
    setSourceError(null);
    setPendingFiles(list);
  }

  // Confirme l'ajout : televerse la selection memorisee (1 appel /sources par
  // fichier, chacun chunke + indexe cote RAG — flux d'ingestion inchange).
  async function confirmAddSources() {
    if (!pendingFiles || pendingFiles.length === 0) return;
    const n = pendingFiles.length;
    setUploadBusy(true);
    setSourceError(null);
    try {
      for (const file of pendingFiles) {
        await chatbotService.addSource(file);
      }
      await loadSources();
      toast.success(n > 1 ? `${n} sources fiables ajoutees.` : 'Source fiable ajoutee.');
    } catch (e) {
      setSourceError(extractError(e).message);
    } finally {
      // Ferme le recapitulatif dans tous les cas : le retour (succes/erreur)
      // s'affiche ensuite dans le bandeau de la carte Sources.
      setPendingFiles(null);
      setUploadBusy(false);
    }
  }

  // Confirme la suppression d'une source (plus utilisee par l'assistant apres coup).
  async function confirmRemoveSource() {
    const target = confirmDelete;
    if (!target) return;
    setDeleteBusy(true);
    setSourceError(null);
    try {
      await chatbotService.deleteSource(target.doc_id);
      setSources((prev) => prev.filter((s) => s.doc_id !== target.doc_id));
      toast.success('Source supprimee.');
    } catch (e) {
      setSourceError(extractError(e).message);
    } finally {
      setConfirmDelete(null);
      setDeleteBusy(false);
    }
  }

  // ---- Gestion des conversations -----------------------------------------

  function startNewConversation() {
    // Si le fil courant est deja vide, on ne cree pas de doublon : on focus.
    if (active && active.entries.length === 0) {
      inputRef.current?.focus();
      return;
    }
    const conv = newConversation();
    setConversations((prev) => [conv, ...prev]);
    setActiveId(conv.id);
    setQuestion('');
    requestAnimationFrame(() => inputRef.current?.focus());
  }

  function selectConversation(id: string) {
    setActiveId(id);
    setQuestion('');
  }

  function deleteConversation(id: string) {
    setConversations((prev) => {
      const next = prev.filter((c) => c.id !== id);
      if (id === activeId) {
        setActiveId(next.length > 0 ? next[0].id : null);
      }
      return next;
    });
  }

  // ---- Envoi d'une question ----------------------------------------------

  async function submit(rawQuestion?: string) {
    const text = (rawQuestion ?? question).trim();
    if (!text || submitting) return;

    const entryId = generateId();
    const nowIso = new Date().toISOString();
    const entry: ChatbotEntry = { id: entryId, question: text, loading: true, createdAt: nowIso };

    // Cible : le fil courant, ou un nouveau si aucun n'est actif.
    const creating = !active;
    const convId = active ? active.id : generateId();

    setConversations((prev) => {
      const list = creating
        ? [{ id: convId, title: '', entries: [], createdAt: nowIso, updatedAt: nowIso }, ...prev]
        : prev;
      return list.map((c) =>
        c.id === convId
          ? {
              ...c,
              title: c.title || deriveTitle(text),
              entries: [...c.entries, entry],
              updatedAt: nowIso,
            }
          : c,
      );
    });
    if (creating) setActiveId(convId);
    setQuestion('');
    setSubmitting(true);

    try {
      const answer: ChatbotAnswer = await chatbotService.ask(text);
      setConversations((prev) =>
        prev.map((c) =>
          c.id === convId
            ? {
                ...c,
                updatedAt: new Date().toISOString(),
                entries: c.entries.map((e) =>
                  e.id === entryId ? { ...e, loading: false, answer } : e,
                ),
              }
            : c,
        ),
      );
    } catch (e) {
      // Etat d'erreur clair (ai-service injoignable, 5xx, timeout...) : la bulle
      // affiche le message, l'ecran n'est jamais fige.
      const message = extractError(e).message;
      setConversations((prev) =>
        prev.map((c) =>
          c.id === convId
            ? {
                ...c,
                entries: c.entries.map((e) =>
                  e.id === entryId ? { ...e, loading: false, error: message } : e,
                ),
              }
            : c,
        ),
      );
    } finally {
      setSubmitting(false);
    }
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    // Enter = envoyer ; Shift+Enter = nouvelle ligne.
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      void submit();
    }
  }

  const remainingSuggestions = useMemo(
    () => SUGGESTIONS.filter((s) => !history.some((h) => h.question === s)),
    [history],
  );

  return (
    <div className="mx-auto flex h-[calc(100vh-9rem)] w-full max-w-6xl flex-col gap-4">
      <header className="flex flex-col gap-1">
        <div className="flex items-center gap-2">
          <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-br from-accent to-accent-hover text-bg shadow-card">
            <Sparkles className="h-5 w-5" />
          </div>
          <div className="flex-1">
            <h1 className="font-heading text-2xl font-semibold text-fg">Assistant juridique</h1>
            <p className="text-sm text-fg-subtle">
              Pose une question sur le droit des societes au Maroc — reponses sourcees.
            </p>
          </div>
          {canManageSources && (
            <Button
              variant="secondary"
              size="sm"
              onClick={() => setShowSources((v) => !v)}
              aria-expanded={showSources}
            >
              <Library className="mr-1 h-4 w-4" />
              Sources fiables
              {sources.length > 0 && (
                <Badge variant="info" className="ml-2">
                  {sources.length}
                </Badge>
              )}
            </Button>
          )}
        </div>
      </header>

      {canManageSources && showSources && (
        <Card className="flex max-h-72 flex-col overflow-hidden">
          <div className="flex items-center justify-between border-b border-border bg-bg-overlay px-4 py-2.5">
            <p className="flex items-center gap-1.5 text-sm font-semibold text-fg">
              <Library className="h-4 w-4 text-accent" /> Sources fiables du RAG
            </p>
            <div className="flex items-center gap-2">
              <input
                ref={fileInput}
                type="file"
                accept=".pdf,.docx,.doc,.txt,.md,application/pdf,text/plain"
                multiple
                className="hidden"
                onChange={(e) => onFilesSelected(e.target.files)}
              />
              <Button
                size="sm"
                onClick={() => fileInput.current?.click()}
                loading={uploadBusy}
                disabled={uploadBusy}
              >
                <Upload className="mr-1 h-4 w-4" /> Ajouter
              </Button>
              <button
                type="button"
                onClick={() => setShowSources(false)}
                className="rounded-lg p-1 text-fg-subtle transition-colors hover:bg-bg-raised hover:text-fg"
                aria-label="Fermer"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
          </div>

          <div className="flex-1 overflow-y-auto px-4 py-3">
            {sourceError && (
              <div className="mb-2 flex items-start gap-2 rounded-lg bg-danger/10 px-3 py-2 text-xs text-danger">
                <AlertCircle className="mt-0.5 h-3.5 w-3.5 shrink-0" />
                <span>{sourceError}</span>
              </div>
            )}
            {loadingSources ? (
              <div className="flex items-center justify-center gap-2 py-6 text-sm text-fg-subtle">
                <Loader2 className="h-4 w-4 animate-spin" /> Chargement des sources...
              </div>
            ) : sources.length === 0 ? (
              <p className="py-6 text-center text-sm text-fg-subtle">
                Aucune source fiable. Importez des documents (PDF, DOCX, TXT) pour enrichir
                les reponses du chatbot.
              </p>
            ) : (
              <ul className="space-y-1.5">
                {sources.map((s) => (
                  <li
                    key={s.doc_id}
                    className="flex items-center gap-2 rounded-lg border border-border bg-bg-raised px-3 py-2 text-sm"
                  >
                    <FileText className="h-4 w-4 shrink-0 text-accent" />
                    <span className="flex-1 truncate text-fg" title={s.source}>
                      {s.source}
                    </span>
                    <Badge variant="default">
                      {s.chunks} passage{s.chunks > 1 ? 's' : ''}
                    </Badge>
                    <button
                      type="button"
                      onClick={() => setConfirmDelete(s)}
                      className="rounded-lg p-1 text-fg-subtle transition-colors hover:bg-danger/10 hover:text-danger"
                      aria-label={`Supprimer ${s.source}`}
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </Card>
      )}

      <div className="grid min-h-0 flex-1 grid-cols-1 gap-4 md:grid-cols-[260px_1fr]">
        {/* Sidebar conversations */}
        <Card className="hidden min-h-0 flex-col overflow-hidden md:flex">
          <div className="border-b border-border p-3">
            <Button
              onClick={startNewConversation}
              size="sm"
              variant="secondary"
              className="w-full"
              data-testid="chatbot-new-conversation"
            >
              <Plus className="mr-1.5 h-4 w-4" /> Nouvelle conversation
            </Button>
          </div>
          <div className="flex-1 overflow-y-auto">
            {conversations.length === 0 ? (
              <p className="px-3 py-6 text-center text-xs text-fg-subtle">
                Aucune conversation. Posez une question pour demarrer.
              </p>
            ) : (
              <ul className="divide-y divide-border">
                {conversations.map((c) => {
                  const selected = c.id === activeId;
                  return (
                    <li key={c.id}>
                      <div
                        className={`group flex items-center gap-2 px-3 py-2.5 transition-colors ${
                          selected ? 'bg-accent/10' : 'hover:bg-bg-overlay'
                        }`}
                      >
                        <button
                          type="button"
                          onClick={() => selectConversation(c.id)}
                          className="flex min-w-0 flex-1 items-center gap-2 text-left"
                        >
                          <MessageSquare
                            className={`h-4 w-4 shrink-0 ${selected ? 'text-accent' : 'text-fg-subtle'}`}
                          />
                          <span className="truncate text-sm text-fg">
                            {c.title || 'Nouvelle conversation'}
                          </span>
                        </button>
                        <button
                          type="button"
                          onClick={() => deleteConversation(c.id)}
                          className="shrink-0 rounded p-1 text-fg-subtle opacity-0 transition-opacity hover:bg-danger/10 hover:text-danger group-hover:opacity-100"
                          aria-label="Supprimer la conversation"
                        >
                          <Trash2 className="h-3.5 w-3.5" />
                        </button>
                      </div>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
        </Card>

        {/* Fil de conversation */}
        <Card className="flex min-h-0 flex-col overflow-hidden">
          <div className="flex-1 space-y-4 overflow-y-auto bg-gradient-to-b from-bg to-bg-overlay px-4 py-5">
            {empty && (
              <div className="flex flex-col items-center justify-center px-6 py-10 text-center">
                <Bot className="mb-3 h-12 w-12 text-accent/70" />
                <p className="text-base font-semibold text-fg">
                  Bonjour, je suis l'assistant JURIKA
                </p>
                <p className="mt-1 max-w-md text-sm text-fg-subtle">
                  Je m'appuie sur les regles de gestion et le code de commerce marocain. Mes
                  reponses citent toujours leur source.
                </p>
                <div className="mt-6 grid w-full max-w-2xl grid-cols-1 gap-2 sm:grid-cols-2">
                  {SUGGESTIONS.map((s) => (
                    <button
                      key={s}
                      type="button"
                      onClick={() => void submit(s)}
                      className="rounded-xl border border-border bg-bg-raised px-3 py-2.5 text-left text-sm text-fg-muted shadow-card transition-colors hover:border-accent/50 hover:bg-accent/10"
                    >
                      <Sparkles className="mr-1 inline h-3.5 w-3.5 text-accent" />
                      {s}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {history.map((entry) => (
              <div key={entry.id} className="space-y-3">
                {/* User question */}
                <div className="flex justify-end">
                  <div className="flex max-w-[80%] items-start gap-2">
                    <div className="rounded-2xl bg-accent px-4 py-2 text-sm text-bg shadow-card">
                      {entry.question}
                    </div>
                    <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-accent/20 text-accent">
                      <User className="h-4 w-4" />
                    </div>
                  </div>
                </div>

                {/* Bot answer */}
                <div className="flex justify-start">
                  <div className="flex max-w-[85%] items-start gap-2">
                    <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-gradient-to-br from-accent to-accent-hover text-bg shadow-card">
                      <Bot className="h-4 w-4" />
                    </div>
                    <div className="flex-1 space-y-2 rounded-2xl border border-border bg-bg-raised px-4 py-3 shadow-sm">
                      {entry.loading && (
                        <div className="flex items-center gap-2 text-sm text-fg-subtle">
                          <Loader2 className="h-4 w-4 animate-spin" />
                          Recherche dans la base juridique...
                        </div>
                      )}
                      {entry.error && (
                        <div className="flex items-start gap-2 text-sm text-danger">
                          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
                          <span>{entry.error}</span>
                        </div>
                      )}
                      {entry.answer && (
                        <>
                          <ChatMarkdown content={entry.answer.reponse} />

                          <div className="flex items-center justify-between gap-2 border-t border-border pt-2">
                            <div className="flex flex-wrap items-center gap-2">
                              <Badge variant={confidenceLabel(entry.answer.confidence).variant}>
                                <Gauge className="mr-1 h-3 w-3" />
                                Confiance : {confidenceLabel(entry.answer.confidence).label} (
                                {formatPercent(entry.answer.confidence)})
                              </Badge>
                              {entry.answer.ragMode === 'llm' && (
                                <Badge variant="success">
                                  <Sparkles className="mr-1 h-3 w-3" />
                                  Réponse IA
                                </Badge>
                              )}
                            </div>
                            {entry.answer.sources.length > 0 && (
                              <span className="text-xs text-fg-subtle">
                                {entry.answer.sources.length} source
                                {entry.answer.sources.length > 1 ? 's' : ''}
                              </span>
                            )}
                          </div>

                          {entry.answer.sources.length > 0 && (
                            <div className="space-y-1.5">
                              <p className="flex items-center gap-1 text-xs font-semibold uppercase tracking-wide text-fg-subtle">
                                <BookOpen className="h-3 w-3" /> Citations
                              </p>
                              <ul className="space-y-1">
                                {entry.answer.sources.map((src, idx) => (
                                  <li
                                    key={`${entry.id}-src-${idx}`}
                                    className="flex flex-wrap items-center gap-2 rounded-lg bg-bg-overlay px-2.5 py-1.5 text-xs text-fg-muted"
                                  >
                                    <Badge variant="info">{src.reference}</Badge>
                                    <span className="font-medium text-fg">{src.article}</span>
                                    {src.extract && (
                                      <span className="text-fg-subtle">— {src.extract}</span>
                                    )}
                                  </li>
                                ))}
                              </ul>
                            </div>
                          )}
                        </>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            ))}

            <div ref={scrollAnchor} />
          </div>

          {!empty && remainingSuggestions.length > 0 && (
            <div className="flex flex-wrap gap-2 border-t border-border bg-bg-overlay px-4 py-2">
              {remainingSuggestions.slice(0, 4).map((s) => (
                <button
                  key={s}
                  type="button"
                  onClick={() => void submit(s)}
                  disabled={submitting}
                  className="rounded-full border border-border bg-bg-raised px-3 py-1 text-xs text-fg-muted transition-colors hover:border-indigo-300 hover:text-accent disabled:opacity-50"
                >
                  {s}
                </button>
              ))}
            </div>
          )}

          <div className="flex items-end gap-2 border-t border-border bg-bg-raised px-3 py-3">
            <textarea
              ref={inputRef}
              data-testid="chatbot-message-input"
              placeholder="Pose une question juridique..."
              value={question}
              onChange={(e) => setQuestion(e.target.value)}
              onInput={(e) => autoGrow(e.currentTarget)}
              onKeyDown={handleKeyDown}
              rows={1}
              className="max-h-40 min-h-[3rem] flex-1 resize-none rounded-lg border border-border bg-bg-raised px-3 py-2 text-sm text-fg placeholder:text-fg-subtle transition-colors focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30"
            />
            <Button
              onClick={() => void submit()}
              disabled={!question.trim() || submitting}
              loading={submitting}
              size="sm"
            >
              <Send className="mr-1 h-4 w-4" /> Envoyer
            </Button>
          </div>
        </Card>
      </div>

      {/* Confirmation AJOUT : recapitulatif des fichiers avant ingestion RAG. */}
      <ConfirmDialog
        open={pendingFiles !== null}
        onOpenChange={(o) => {
          if (!o && !uploadBusy) setPendingFiles(null);
        }}
        title="Ajouter comme source fiable ?"
        description="Ces documents seront indexes et utilises par l'assistant pour ses reponses."
        confirmLabel="Ajouter"
        variant="primary"
        loading={uploadBusy}
        onConfirm={() => void confirmAddSources()}
      >
        {pendingFiles && (
          <ul className="max-h-40 space-y-1.5 overflow-y-auto rounded-lg border border-border bg-bg-raised px-3 py-2">
            {pendingFiles.map((f, i) => (
              <li key={`${f.name}-${i}`} className="flex items-center gap-2 text-sm text-fg">
                <FileText className="h-4 w-4 shrink-0 text-accent" />
                <span className="truncate" title={f.name}>
                  {f.name}
                </span>
              </li>
            ))}
          </ul>
        )}
      </ConfirmDialog>

      {/* Confirmation SUPPRESSION : action non reversible cote assistant. */}
      <ConfirmDialog
        open={confirmDelete !== null}
        onOpenChange={(o) => {
          if (!o && !deleteBusy) setConfirmDelete(null);
        }}
        title="Supprimer la source ?"
        description={
          confirmDelete
            ? `Supprimer la source « ${confirmDelete.source} » ? Elle ne sera plus utilisee par l'assistant.`
            : undefined
        }
        confirmLabel="Supprimer"
        variant="danger"
        loading={deleteBusy}
        onConfirm={() => void confirmRemoveSource()}
      />
    </div>
  );
}
