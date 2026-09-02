import { useCallback, useEffect, useMemo, useRef, useState, type KeyboardEvent } from 'react';
import { MessageSquarePlus, Send, Search, Loader2, AlertCircle, Plug, PlugZap, UserPlus } from 'lucide-react';
import type { Socket } from 'socket.io-client';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { TextField } from '../../components/ui/TextField';
import { Badge } from '../../components/ui/Badge';
import { chatService } from '../../services/chat.service';
import { authService } from '../../services/auth.service';
import { getSocket } from '../../lib/realtimeSocket';
import { useCurrentUser } from '../../store/authStore';
import { refreshChatUnread } from '../../store/chatUnreadStore';
import { extractError } from '../../lib/api';
import type { ChatMessage, Conversation, ChatSendAck } from '../../types/chat';
import type { WorkspaceUser } from '../../types/auth';

interface DraftConversation {
  peerId: string;
}

function shortId(id: string): string {
  if (!id) return '';
  if (id.length <= 10) return id;
  return `${id.slice(0, 4)}…${id.slice(-4)}`;
}

function formatTime(iso?: string | null): string {
  if (!iso) return '';
  try {
    const d = new Date(iso);
    return d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
  } catch {
    return '';
  }
}

function formatDate(iso?: string | null): string {
  if (!iso) return '';
  try {
    const d = new Date(iso);
    return d.toLocaleDateString('fr-FR', { day: '2-digit', month: 'short' });
  } catch {
    return '';
  }
}

export function ChatPage() {
  const user = useCurrentUser();
  // 2026-06-26 — Le chat reste OUVERT au superviseur : c'est l'exception voulue
  // à la règle « superviseur oversight-only » (Lot G). Aucune restriction front
  // sur l'envoi ; le backend (realtime-service chat:send) n'impose aucun rôle.

  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [contacts, setContacts] = useState<WorkspaceUser[]>([]);
  const [draftPeer, setDraftPeer] = useState<DraftConversation | null>(null);
  const [activePeer, setActivePeer] = useState<string | null>(null);
  const [messagesByPeer, setMessagesByPeer] = useState<Record<string, ChatMessage[]>>({});
  const [loadingList, setLoadingList] = useState(false);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [searchPeer, setSearchPeer] = useState('');
  const [showContacts, setShowContacts] = useState(false);
  const [draftBody, setDraftBody] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [typingFrom, setTypingFrom] = useState<string | null>(null);
  const [connected, setConnected] = useState(false);

  // BUG 12 (2026-06-08) — Index userId -> nom complet/role, alimente par la
  // liste des contacts. Sert a afficher des libelles humains a la place des
  // UUID dans la sidebar/header (les anciennes conversations conservaient
  // l'ID brut puisque l'UI demandait de TAPER un ID — on patche au render).
  const contactsById = useMemo(() => {
    const m = new Map<string, WorkspaceUser>();
    for (const c of contacts) m.set(c.userId, c);
    return m;
  }, [contacts]);

  function peerLabel(peerId: string | null | undefined): string {
    if (!peerId) return 'Conversation';
    const c = contactsById.get(peerId);
    if (c) return `${c.firstName} ${c.lastName}`.trim() || c.email;
    return shortId(peerId);
  }
  function peerSubtitle(peerId: string | null | undefined): string {
    if (!peerId) return '';
    const c = contactsById.get(peerId);
    if (!c) return peerId;
    return c.role === 'SUPERVISEUR' ? 'Superviseur' : 'Employe';
  }
  function peerInitials(peerId: string | null | undefined): string {
    if (!peerId) return '??';
    const c = contactsById.get(peerId);
    if (c) {
      const a = (c.firstName?.[0] ?? c.email[0] ?? '?').toUpperCase();
      const b = (c.lastName?.[0] ?? '').toUpperCase();
      return `${a}${b}`.trim() || '??';
    }
    return peerId.slice(0, 2).toUpperCase();
  }

  const typingTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const scrollAnchor = useRef<HTMLDivElement | null>(null);
  const socketRef = useRef<Socket | null>(null);
  const inputRef = useRef<HTMLTextAreaElement | null>(null);

  // 2026-06-30 — `activePeer` lu via ref dans le handler socket : permet de
  // n'enregistrer les listeners QU'UNE fois (deps [user?.userId]) sans churn
  // (avant : l'effet socket se re-bindait à chaque sélection de conversation).
  const activePeerRef = useRef<string | null>(activePeer);
  useEffect(() => {
    activePeerRef.current = activePeer;
  }, [activePeer]);

  // 2026-07-03 — `conversations` et `messagesByPeer` lus via ref dans l'effet
  // « ouverture de conversation » : l'intention est de se déclencher QUAND
  // l'utilisateur ouvre une conversation (activePeer change), pas à chaque
  // render de la liste. Sans ces refs, dépendre de `conversations` + appeler
  // `setConversations` dans le même effet créait une boucle infinie (chaque
  // render produisait un nouveau tableau → nouvelle réf → re-run → « Maximum
  // update depth exceeded »).
  const conversationsRef = useRef(conversations);
  useEffect(() => {
    conversationsRef.current = conversations;
  }, [conversations]);
  const messagesByPeerRef = useRef(messagesByPeer);
  useEffect(() => {
    messagesByPeerRef.current = messagesByPeer;
  }, [messagesByPeer]);

  // Build a derived list that includes any draft (new) conversation.
  const visibleConversations = useMemo(() => {
    // Defense en profondeur (fix 2026-06-09) : ignorer toute conversation
    // sans peerId valide (donnees legacy / migration / backend qui changerait
    // de shape) plutot que de crasher `peerInitials` dans un render.
    const base = conversations
      .filter((c) => typeof c.peerId === 'string' && c.peerId.length > 0)
      .filter((c) =>
        searchPeer ? c.peerId.toLowerCase().includes(searchPeer.toLowerCase()) : true,
      );
    if (draftPeer && !base.some((c) => c.peerId === draftPeer.peerId)) {
      return [
        {
          id: `draft:${draftPeer.peerId}`,
          peerId: draftPeer.peerId,
          lastMessage: 'Nouvelle conversation',
          lastMessageAt: null,
          unreadCount: 0,
        } satisfies Conversation,
        ...base,
      ];
    }
    return base;
  }, [conversations, draftPeer, searchPeer]);

  const activeMessages = useMemo(
    () => (activePeer ? messagesByPeer[activePeer] ?? [] : []),
    [activePeer, messagesByPeer],
  );

  const loadConversations = useCallback(async () => {
    setLoadingList(true);
    try {
      const list = await chatService.listConversations();
      setConversations(list);
    } catch (e) {
      setError(extractError(e).message);
    } finally {
      setLoadingList(false);
    }
  }, []);

  // BUG 12 — charge la liste des contacts du workspace (ACTIVE, hors CLIENT,
  // hors soi). On le fait en best-effort : un fail laisse l'UI fonctionnelle
  // mais retombe sur l'affichage shortId pour les conversations existantes.
  const loadContacts = useCallback(async () => {
    try {
      const list = await authService.listChatContacts();
      // RG-U09 : les CLIENT ne doivent jamais apparaitre dans cette liste.
      // Le typage WorkspaceUser restreint deja role a EMPLOYE/SUPERVISEUR,
      // le backend filtre status='ACTIVE' et exclut le caller.
      setContacts(list);
    } catch {
      /* swallow : pas bloquant pour l'UX */
    }
  }, []);

  const loadMessages = useCallback(async (conversationId: string, peerId: string) => {
    setLoadingMessages(true);
    try {
      const items = await chatService.listMessages(conversationId);
      setMessagesByPeer((prev) => ({ ...prev, [peerId]: items }));
    } catch (e) {
      setError(extractError(e).message);
    } finally {
      setLoadingMessages(false);
    }
  }, []);

  // Initial load
  useEffect(() => {
    void loadConversations();
    void loadContacts();
  }, [loadConversations, loadContacts]);

  // Socket lifecycle
  useEffect(() => {
    const s = getSocket();
    if (!s) return;
    socketRef.current = s;

    const onConnect = () => setConnected(true);
    const onDisconnect = () => setConnected(false);

    const onMessage = (msg: ChatMessage) => {
      // Défense (2026-06-30) : un payload socket malformé (champs from/to/body
      // manquants) ne doit pas se propager dans le state — sinon une entrée à
      // peerId `undefined` finit par alimenter le render (peerInitials/map) et
      // peut casser l'arbre React. On ignore silencieusement les messages KO.
      if (!msg || typeof msg.from !== 'string' || typeof msg.to !== 'string') return;
      const peerId = msg.from === user?.userId ? msg.to : msg.from;
      if (!peerId) return;
      const isActive = activePeerRef.current === peerId;
      // Si la conversation est ouverte à l'écran, on la marque lue côté serveur
      // immédiatement (sinon read_at reste NULL et le badge de nav resterait
      // gonflé alors que l'utilisateur regarde le message).
      if (isActive && msg.from !== user?.userId && msg.conversationId) {
        socketRef.current?.emit('chat:read', { conversationId: msg.conversationId });
      }
      setMessagesByPeer((prev) => {
        const existing = prev[peerId] ?? [];
        // Replace optimistic match if any
        const filtered = existing.filter(
          (m) => !(m.pending && m.body === msg.body && m.to === msg.to),
        );
        return { ...prev, [peerId]: [...filtered, msg] };
      });
      setConversations((prev) => {
        const idx = prev.findIndex((c) => c.peerId === peerId);
        if (idx >= 0) {
          const updated: Conversation = {
            ...prev[idx],
            lastMessage: msg.body,
            lastMessageAt: msg.createdAt,
            unreadCount:
              isActive || msg.from === user?.userId
                ? 0
                : (prev[idx].unreadCount ?? 0) + 1,
          };
          return [updated, ...prev.filter((_, i) => i !== idx)];
        }
        return [
          {
            id: msg.conversationId,
            peerId,
            lastMessage: msg.body,
            lastMessageAt: msg.createdAt,
            unreadCount: msg.from === user?.userId ? 0 : 1,
          },
          ...prev,
        ];
      });
    };

    const onTyping = (evt: { from: string }) => {
      if (!evt?.from) return;
      setTypingFrom(evt.from);
      if (typingTimer.current) clearTimeout(typingTimer.current);
      typingTimer.current = setTimeout(() => setTypingFrom(null), 2500);
    };

    s.on('connect', onConnect);
    s.on('disconnect', onDisconnect);
    s.on('chat:message', onMessage);
    s.on('chat:typing', onTyping);

    if (s.connected) setConnected(true);

    return () => {
      s.off('connect', onConnect);
      s.off('disconnect', onDisconnect);
      s.off('chat:message', onMessage);
      s.off('chat:typing', onTyping);
      if (typingTimer.current) clearTimeout(typingTimer.current);
    };
    // 2026-06-30 — deps réduites à [user?.userId] : on enregistre les listeners
    // une seule fois par session utilisateur. La dépendance à `activePeer` passe
    // par `activePeerRef`, évitant un re-bind complet du socket à chaque
    // changement de conversation (churn + risque de rater des messages).
  }, [user?.userId]);

  // Auto-scroll on new messages
  useEffect(() => {
    // behavior:'auto' (2026-06-30) — le scroll lissé provoquait du jank à chaque
    // message dans un conteneur à hauteur fixe ; le saut direct est plus net.
    scrollAnchor.current?.scrollIntoView({ behavior: 'auto' });
  }, [activeMessages, typingFrom]);

  // When user opens a conversation, fetch history + emit read.
  // 2026-07-03 — deps réduites à [activePeer] (+ loadMessages, stable via
  // useCallback []) : l'effet ne se déclenche QUE lorsqu'une conversation est
  // ouverte, pas à chaque changement de `conversations`/`messagesByPeer` (lus
  // via ref). `setConversations` bail-out sur `prev` inchangé quand il n'y a
  // rien à nettoyer → React ne re-render pas → plus de boucle infinie.
  useEffect(() => {
    if (!activePeer) return;
    const conv = conversationsRef.current.find((c) => c.peerId === activePeer);
    if (!conv) return;
    if (!messagesByPeerRef.current[activePeer]) {
      loadMessages(conv.id, activePeer);
    }
    socketRef.current?.emit('chat:read', { conversationId: conv.id });
    setConversations((prev) => {
      const target = prev.find((c) => c.peerId === activePeer);
      // Même référence si rien à nettoyer → bail-out React, stoppe la boucle.
      if (!target || (target.unreadCount ?? 0) === 0) return prev;
      return prev.map((c) => (c.peerId === activePeer ? { ...c, unreadCount: 0 } : c));
    });
    // Le badge de nav (Sidebar/AppTopNav) suit le total serveur : on le rafraîchit
    // après que chat:read a eu le temps d'être persisté (read_at = NOW()).
    setTimeout(() => refreshChatUnread(), 350);
  }, [activePeer, loadMessages]);

  function handleSelectConversation(peerId: string) {
    setActivePeer(peerId);
    setTypingFrom(null);
    setError(null);
  }

  function handleStartConversationWith(peerId: string) {
    if (!peerId) return;
    if (peerId === user?.userId) {
      setError('Vous ne pouvez pas demarrer une conversation avec vous-meme.');
      return;
    }
    // Si une conversation existe deja avec ce peer, on bascule simplement
    // dessus. Sinon on cree un draft jusqu'au 1er ack server.
    const existing = conversations.find((c) => c.peerId === peerId);
    if (!existing) {
      setDraftPeer({ peerId });
    }
    setActivePeer(peerId);
    setShowContacts(false);
    setError(null);
  }

  function handleTyping() {
    if (!activePeer) return;
    socketRef.current?.emit('chat:typing', { to: activePeer });
  }

  async function handleSend() {
    const body = draftBody.trim();
    const s = socketRef.current;
    if (!body || !activePeer || !s) return;

    setSending(true);
    setError(null);

    const optimistic: ChatMessage = {
      id: `tmp-${Date.now()}`,
      conversationId: `pending:${activePeer}`,
      from: user?.userId ?? 'me',
      to: activePeer,
      body,
      createdAt: new Date().toISOString(),
      pending: true,
    };
    setMessagesByPeer((prev) => ({
      ...prev,
      [activePeer]: [...(prev[activePeer] ?? []), optimistic],
    }));
    setDraftBody('');

    try {
      const ack = await new Promise<ChatSendAck>((resolve) => {
        let settled = false;
        const timeout = setTimeout(() => {
          if (!settled) resolve({ ok: false, error: 'Timeout' });
        }, 8000);
        s.emit('chat:send', { to: activePeer, body }, (response: ChatSendAck) => {
          settled = true;
          clearTimeout(timeout);
          resolve(response ?? { ok: false, error: 'Reponse invalide' });
        });
      });

      if (!ack.ok) {
        throw new Error(ack.error ?? 'Echec de l\'envoi');
      }

      // Promote draft conversation to real one once the server confirms it
      if (draftPeer && draftPeer.peerId === activePeer && ack.message) {
        setDraftPeer(null);
        setConversations((prev) => {
          if (prev.some((c) => c.peerId === activePeer)) return prev;
          return [
            {
              id: ack.message!.conversationId,
              peerId: activePeer,
              lastMessage: ack.message!.body,
              lastMessageAt: ack.message!.createdAt,
              unreadCount: 0,
            },
            ...prev,
          ];
        });
      }
    } catch (e) {
      const message = e instanceof Error ? e.message : 'Echec de l\'envoi';
      setError(message);
      setMessagesByPeer((prev) => ({
        ...prev,
        [activePeer]: (prev[activePeer] ?? []).filter((m) => m.id !== optimistic.id),
      }));
      setDraftBody(body);
    } finally {
      setSending(false);
    }
  }

  function handleKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    // Enter = envoyer ; Shift+Enter = nouvelle ligne (textarea multi-lignes).
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      void handleSend();
    } else {
      handleTyping();
    }
  }

  // Auto-grow du textarea : on reset la hauteur puis on l'ajuste au contenu
  // (borné par max-h-40 via CSS overflow). Appelé à chaque saisie + au reset
  // de `draftBody` après envoi.
  function autoGrow(el: HTMLTextAreaElement | null) {
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = `${el.scrollHeight}px`;
  }
  useEffect(() => {
    if (!draftBody) autoGrow(inputRef.current);
  }, [draftBody]);

  const activeConversationLabel = activePeer ? peerLabel(activePeer) : null;
  const activeConversationSubtitle = activePeer ? peerSubtitle(activePeer) : null;

  return (
    <div className="flex h-[calc(100vh-9rem)] flex-col gap-4">
      <header className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-fg">Messagerie</h1>
          <p className="text-sm text-fg-subtle">
            Conversations internes en temps reel
          </p>
        </div>
        <div className="flex items-center gap-2 text-xs">
          {connected ? (
            <Badge variant="success">
              <PlugZap className="mr-1 h-3 w-3" /> Connecte
            </Badge>
          ) : (
            <Badge variant="warning">
              <Plug className="mr-1 h-3 w-3" /> Hors-ligne
            </Badge>
          )}
        </div>
      </header>

      <div className="grid flex-1 grid-cols-1 gap-4 overflow-hidden md:grid-cols-[320px_1fr]">
        {/* Sidebar */}
        <Card className="flex min-h-0 flex-col overflow-hidden">
          <div className="space-y-3 border-b border-border p-3">
            <div className="relative">
              <Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-fg-subtle" />
              <TextField
                placeholder="Rechercher un correspondant..."
                value={searchPeer}
                onChange={(e) => setSearchPeer(e.target.value)}
                className="pl-9"
              />
            </div>
            {/* BUG 12 (2026-06-08) — bouton "Nouveau" deroule la liste des
                contacts du workspace au lieu de demander l'UUID a la main. */}
            <Button
              onClick={() => setShowContacts((v) => !v)}
              size="sm"
              variant="secondary"
              data-testid="chat-new-conversation"
              className="w-full"
            >
              <UserPlus className="mr-1.5 h-4 w-4" />
              {showContacts ? 'Fermer la liste' : 'Nouvelle conversation'}
            </Button>

            {showContacts && (
              <div
                className="max-h-72 overflow-y-auto rounded-lg border border-border bg-bg-overlay"
                data-testid="chat-contacts-list"
              >
                {contacts.length === 0 ? (
                  <p className="px-3 py-4 text-center text-xs text-fg-subtle">
                    Aucun contact disponible.<br />
                    Invitez un collegue depuis Parametres &gt; Equipe.
                  </p>
                ) : (
                  <ul className="divide-y divide-border">
                    {contacts.map((c) => (
                      <li key={c.userId}>
                        <button
                          type="button"
                          onClick={() => handleStartConversationWith(c.userId)}
                          className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm transition hover:bg-bg-raised"
                          data-testid="chat-contact-item"
                        >
                          <span className="flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-full bg-accent/15 text-[10px] font-bold text-accent">
                            {(c.firstName?.[0] ?? c.email[0] ?? '?').toUpperCase()}
                            {(c.lastName?.[0] ?? '').toUpperCase()}
                          </span>
                          <span className="min-w-0 flex-1">
                            <span className="block truncate text-fg">
                              {c.firstName} {c.lastName}
                            </span>
                            <span className="block truncate text-[10px] text-fg-subtle">
                              {c.role === 'SUPERVISEUR' ? 'Superviseur' : 'Employe'} · {c.email}
                            </span>
                          </span>
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            )}
          </div>

          <div className="flex-1 overflow-y-auto">
            {loadingList && conversations.length === 0 ? (
              <div className="flex h-32 items-center justify-center">
                <Loader2 className="h-5 w-5 animate-spin text-fg-subtle" />
              </div>
            ) : visibleConversations.length === 0 ? (
              <div className="p-6 text-center text-sm text-fg-subtle">
                Aucune conversation pour le moment.
              </div>
            ) : (
              <ul className="divide-y divide-border">
                {visibleConversations.map((c) => {
                  const selected = activePeer === c.peerId;
                  return (
                    <li key={c.id}>
                      <button
                        type="button"
                        onClick={() => handleSelectConversation(c.peerId)}
                        className={`flex w-full items-start gap-3 px-3 py-3 text-left transition-colors ${
                          selected ? 'bg-accent/10' : 'hover:bg-bg-overlay'
                        }`}
                      >
                        <div
                          className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-sm font-semibold ${
                            selected
                              ? 'bg-accent text-bg-raised'
                              : 'bg-bg-overlay text-fg-muted'
                          }`}
                        >
                          {peerInitials(c.peerId)}
                        </div>
                        <div className="min-w-0 flex-1">
                          <div className="flex items-center justify-between gap-2">
                            <span className="truncate text-sm font-medium text-fg">
                              {peerLabel(c.peerId)}
                            </span>
                            <span className="shrink-0 text-[10px] text-fg-subtle">
                              {formatDate(c.lastMessageAt)}
                            </span>
                          </div>
                          <div className="flex items-center justify-between gap-2">
                            <span className="truncate text-xs text-fg-subtle">
                              {c.lastMessage ?? 'Pas encore de message'}
                            </span>
                            {(c.unreadCount ?? 0) > 0 && (
                              <Badge variant="default" className="ml-2">
                                {c.unreadCount}
                              </Badge>
                            )}
                          </div>
                        </div>
                      </button>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
        </Card>

        {/* Conversation panel */}
        <Card className="flex min-h-0 flex-col overflow-hidden">
          {!activePeer ? (
            <div className="flex flex-1 flex-col items-center justify-center px-6 text-center text-fg-subtle">
              <MessageSquarePlus className="mb-3 h-10 w-10 text-fg-subtle" />
              <p className="text-sm font-medium text-fg-muted">
                Selectionne une conversation...
              </p>
              <p className="mt-1 text-xs text-fg-subtle">
                Ou demarre-en une nouvelle depuis la colonne de gauche.
              </p>
            </div>
          ) : (
            <>
              <div className="flex items-center justify-between border-b border-border px-4 py-3">
                <div>
                  <p className="text-sm font-semibold text-fg">
                    {activeConversationLabel}
                  </p>
                  <p className="text-xs text-fg-subtle">{activeConversationSubtitle}</p>
                </div>
                {typingFrom === activePeer && (
                  <span className="text-xs italic text-accent">en train d'ecrire...</span>
                )}
              </div>

              <div className="flex-1 space-y-2 overflow-y-auto bg-bg-overlay px-4 py-4">
                {loadingMessages && activeMessages.length === 0 ? (
                  <div className="flex h-24 items-center justify-center">
                    <Loader2 className="h-5 w-5 animate-spin text-fg-subtle" />
                  </div>
                ) : activeMessages.length === 0 ? (
                  <p className="mt-8 text-center text-sm text-fg-subtle">
                    Aucun message echange. Envoie le premier.
                  </p>
                ) : (
                  activeMessages.map((m) => {
                    const mine = m.from === user?.userId;
                    return (
                      <div
                        key={m.id}
                        className={`flex ${mine ? 'justify-end' : 'justify-start'}`}
                      >
                        <div
                          className={`max-w-[75%] rounded-2xl px-3 py-2 shadow-sm ${
                            mine
                              ? 'bg-accent text-bg-raised'
                              : 'bg-bg-raised text-fg ring-1 ring-slate-200'
                          } ${m.pending ? 'opacity-60' : ''}`}
                        >
                          <p className="whitespace-pre-wrap text-sm">{m.body}</p>
                          <p
                            className={`mt-1 text-right text-[10px] ${
                              mine ? 'text-bg' : 'text-fg-subtle'
                            }`}
                          >
                            {formatTime(m.createdAt)}
                            {m.pending && ' • envoi...'}
                          </p>
                        </div>
                      </div>
                    );
                  })
                )}
                <div ref={scrollAnchor} />
              </div>

              {error && (
                <div className="flex items-center gap-2 border-t border-rose-100 bg-danger/10 px-4 py-2 text-xs text-danger">
                  <AlertCircle className="h-3.5 w-3.5" />
                  {error}
                </div>
              )}

              <div className="flex items-end gap-2 border-t border-border bg-bg-raised px-3 py-3">
                <textarea
                  ref={inputRef}
                  data-testid="chat-message-input"
                  placeholder="Ecrire un message..."
                  value={draftBody}
                  onChange={(e) => setDraftBody(e.target.value)}
                  onInput={(e) => autoGrow(e.currentTarget)}
                  onKeyDown={handleKeyDown}
                  rows={1}
                  className="max-h-40 min-h-[3rem] flex-1 resize-none rounded-lg border border-border bg-bg-raised px-3 py-2 text-sm text-fg placeholder:text-fg-subtle transition-colors focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30"
                />
                <Button
                  onClick={() => void handleSend()}
                  disabled={!draftBody.trim() || sending || !connected}
                  loading={sending}
                  size="sm"
                >
                  <Send className="mr-1 h-4 w-4" /> Envoyer
                </Button>
              </div>
            </>
          )}
        </Card>
      </div>
    </div>
  );
}
