import { useCallback, useEffect, useMemo, useRef, useState, type KeyboardEvent } from 'react';
import { MessageSquarePlus, Send, Search, Loader2, AlertCircle, Plug, PlugZap, UserPlus } from 'lucide-react';
import type { Socket } from 'socket.io-client';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { TextField } from '../../components/ui/TextField';
import { Badge } from '../../components/ui/Badge';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import { chatService } from '../../services/chat.service';
import { authService } from '../../services/auth.service';
import { getSocket } from '../../lib/realtimeSocket';
import { useCurrentUser } from '../../store/authStore';
import { refreshChatUnread } from '../../store/chatUnreadStore';
import { extractError } from '../../lib/api';
import { useForfait } from '../../lib/useForfait';
import { FonctionHorsForfait } from '../../components/billing/FonctionHorsForfait';
import type { ChatMessage, Conversation } from '../../types/chat';
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

/**
 * Lot L7 (E5), CDC 4.5 : la messagerie interne est une fonction des forfaits Professionnel, Max et
 * Sur Devis. Hors forfait, l'ecran l'explique (le serveur la refuse aussi : FORFAIT_SANS_MESSAGERIE).
 */
export function ChatPage() {
  const forfait = useForfait();
  if (!forfait.inclut('MESSAGERIE_INTERNE')) {
    return (
      <div className="p-6">
        <FonctionHorsForfait fonction="Messagerie interne" disponibleDes={forfait.disponibleDes('MESSAGERIE_INTERNE')} />
      </div>
    );
  }
  return <MessagerieInterne />;
}

function MessagerieInterne() {
  const user = useCurrentUser();
  // 2026-06-26 — Le chat reste OUVERT au superviseur : c'est l'exception voulue
  // à la règle « superviseur oversight-only » (Lot G). Lot L5 (E9, RG-COM-06) :
  // le realtime-service n'accepte que le superviseur et les employés d'un même
  // cabinet (refus par défaut) ; son message de refus s'affiche tel quel.

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
  const [connected, setConnected] = useState(() => getSocket()?.connected === true);

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
    return c.role === 'SUPERVISEUR' ? 'Superviseur' : 'Employé';
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
    // Chargement par promesse : aucun etat pose de facon synchrone dans l'effet.
    void Promise.resolve().then(() => {
      void loadConversations();
      void loadContacts();
    });
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
          (m) => m.id !== msg.id && !(m.pending && m.body === msg.body && m.to === msg.to),
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

  // Lot L5b (E1, RG-COM-06) : sans temps reel, la liste et la conversation ouverte sont
  // relues toutes les 15 s : un message recu est remis meme sans connexion temps reel.
  useEffect(() => {
    if (connected) return;
    const t = setInterval(() => {
      void chatService.listConversations().then(setConversations).catch(() => undefined);
      const peer = activePeerRef.current;
      const conv = peer ? conversationsRef.current.find((c) => c.peerId === peer) : undefined;
      if (peer && conv) {
        void chatService.listMessages(conv.id)
          .then((items) => setMessagesByPeer((prev) => ({ ...prev, [peer]: items })))
          .catch(() => undefined);
      }
    }, 15_000);
    return () => clearInterval(t);
  }, [connected]);

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
      setError('Vous ne pouvez pas démarrer une conversation avec vous-même.');
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
    if (!body || !activePeer) return;
    const peer = activePeer;

    setSending(true);
    setError(null);

    const optimistic: ChatMessage = {
      id: `tmp-${Date.now()}`,
      conversationId: `pending:${peer}`,
      from: user?.userId ?? 'me',
      to: peer,
      body,
      createdAt: new Date().toISOString(),
      pending: true,
    };
    setMessagesByPeer((prev) => ({
      ...prev,
      [peer]: [...(prev[peer] ?? []), optimistic],
    }));
    setDraftBody('');

    try {
      // Lot L5b (E1, RG-COM-06) : envoi par le serveur, enregistre des l'envoi, que la
      // connexion temps reel soit active ou non ; le temps reel ne sert qu'a la remise
      // immediate (l'evenement chat:message du meme message est dedoublonne par son id).
      const saved = await chatService.envoyer(peer, body);
      setMessagesByPeer((prev) => {
        const sans = (prev[peer] ?? []).filter((m) => m.id !== optimistic.id && m.id !== saved.id);
        return { ...prev, [peer]: [...sans, saved] };
      });
      setConversations((prev) => {
        const idx = prev.findIndex((c) => c.peerId === peer);
        const maj: Conversation = {
          id: saved.conversationId,
          peerId: peer,
          lastMessage: saved.body,
          lastMessageAt: saved.createdAt,
          unreadCount: 0,
        };
        if (idx >= 0) return [{ ...prev[idx], ...maj }, ...prev.filter((_, i) => i !== idx)];
        return [maj, ...prev];
      });
      if (draftPeer && draftPeer.peerId === peer) setDraftPeer(null);
    } catch (e) {
      const message = e instanceof Error ? e.message : 'Le message n’a pas pu être envoyé.';
      setError(message);
      setMessagesByPeer((prev) => ({
        ...prev,
        [peer]: (prev[peer] ?? []).filter((m) => m.id !== optimistic.id),
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
            Conversations internes du cabinet, en temps réel
          </p>
        </div>
        <div className="flex items-center gap-2 text-xs">
          {/* Lot L5b (E1, RG-COM-06) : indicateur seulement ; il ne bloque jamais l'envoi. */}
          {connected ? (
            <Badge variant="success">
              <PlugZap className="mr-1 h-3 w-3" /> Temps réel actif
            </Badge>
          ) : (
            <Badge variant="warning">
              <Plug className="mr-1 h-3 w-3" /> Temps réel interrompu
            </Badge>
          )}
          <InfoBulle
            libelle="Que signifie l’indicateur de connexion ?"
            texte="« Temps réel actif » : les messages de vos collègues s’affichent dès leur envoi. « Temps réel interrompu » : vos messages partent et sont enregistrés quand même ; ceux de vos collègues apparaissent à la prochaine actualisation, faite automatiquement toutes les 15 secondes."
          />
        </div>
      </header>

      <TexteAide cle="messagerie-interne" titre="Messagerie interne du cabinet">
        Échangez ici avec le superviseur et les employés de votre cabinet, et uniquement eux :
        vos clients n’ont pas accès à la messagerie (ils vous écrivent par leurs demandes, dans
        la Data Room). Choisissez « Nouvelle conversation » pour écrire à un collègue ; une
        conversation non lue porte un compteur, et le destinataire reçoit une notification
        dans la cloche.
      </TexteAide>

      <div className="grid flex-1 grid-cols-1 gap-4 overflow-hidden md:grid-cols-[320px_1fr]">
        {/* Sidebar */}
        <Card className="flex min-h-0 flex-col overflow-hidden">
          <div className="space-y-3 border-b border-border p-3">
            <div className="relative">
              <Search className="pointer-events-none absolute left-3 top-2.5 h-4 w-4 text-fg-subtle" />
              <TextField
                placeholder="Rechercher un correspondant…"
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
            <p className="flex items-center gap-1 text-[11px] text-fg-subtle">
              Collègues de votre cabinet
              <InfoBulle
                libelle="Aide sur la liste des collègues"
                texte="La liste ne montre que les comptes actifs du superviseur et des employés de votre cabinet. Un collègue absent de la liste doit d’abord être invité depuis Paramètres > Équipe, ou son compte réactivé."
              />
            </p>

            {showContacts && (
              <div
                className="max-h-72 overflow-y-auto rounded-lg border border-border bg-bg-overlay"
                data-testid="chat-contacts-list"
              >
                {contacts.length === 0 ? (
                  <p className="px-3 py-4 text-center text-xs text-fg-subtle">
                    Aucun collègue disponible.<br />
                    Invitez un collègue depuis Paramètres &gt; Équipe.
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
                              {c.role === 'SUPERVISEUR' ? 'Superviseur' : 'Employé'} · {c.email}
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
                Aucune conversation pour le moment. Cliquez sur « Nouvelle conversation » pour écrire à un collègue.
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
                Sélectionnez une conversation.
              </p>
              <p className="mt-1 text-xs text-fg-subtle">
                Ou démarrez-en une nouvelle avec « Nouvelle conversation », dans la colonne de gauche.
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
                  <span className="text-xs italic text-accent">est en train d’écrire…</span>
                )}
              </div>

              <div className="flex-1 space-y-2 overflow-y-auto bg-bg-overlay px-4 py-4">
                {loadingMessages && activeMessages.length === 0 ? (
                  <div className="flex h-24 items-center justify-center">
                    <Loader2 className="h-5 w-5 animate-spin text-fg-subtle" />
                  </div>
                ) : activeMessages.length === 0 ? (
                  <p className="mt-8 text-center text-sm text-fg-subtle">
                    Aucun message échangé. Écrivez le premier ci-dessous.
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
                            {m.pending && ' • envoi…'}
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
                  placeholder="Écrire un message…"
                  value={draftBody}
                  onChange={(e) => setDraftBody(e.target.value)}
                  onInput={(e) => autoGrow(e.currentTarget)}
                  onKeyDown={handleKeyDown}
                  rows={1}
                  className="max-h-40 min-h-[3rem] flex-1 resize-none rounded-lg border border-border bg-bg-raised px-3 py-2 text-sm text-fg placeholder:text-fg-subtle transition-colors focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30"
                />
                <Button
                  onClick={() => void handleSend()}
                  disabled={!draftBody.trim() || sending}
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
