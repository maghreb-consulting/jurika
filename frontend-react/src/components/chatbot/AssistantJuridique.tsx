import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  AlertCircle,
  AlertTriangle,
  Archive,
  ArchiveRestore,
  BookOpen,
  Bot,
  List,
  Loader2,
  MessageSquarePlus,
  Pencil,
  Send,
  X,
} from 'lucide-react';
import { Button } from '../ui/Button';
import { InfoBulle, TexteAide } from '../ui/Aide';
import { ChatMarkdown } from '../ChatMarkdown';
import { ListeConversations } from './ListeConversations';
import { RenommerConversation } from './RenommerConversation';
import { chatbotService } from '../../services/chatbot.service';
import { purgerAnciennesConversationsLocales } from '../../lib/chatbotConversationsLocales';
import { extractError } from '../../lib/api';
import { detecterDonneesPersonnelles, messageReformulation } from '../../lib/donneesPersonnelles';
import { useAuthStore } from '../../store/authStore';
import { useAssistantStore } from '../../store/assistantStore';
import { peutUtiliserChatbot } from './rolesChatbot';
import type { ChatbotConversationResume, ChatbotEchange, ChatbotEntry } from '../../types/chat';

/**
 * Lot L5 E8 : chatbot juridique en panneau lateral (CDC 12.1).
 *
 * - RG-COM-01 : bulle flottante en bas a droite, sur toutes les pages de la mise en page
 *   commune, qui ouvre un panneau lateral. Utilisateurs du cabinet (employe, superviseur) ;
 *   le super-admin l'utilise pour eprouver les sources qu'il depose. Pas le client.
 * - RG-COM-02 : les reponses viennent des seules sources deposees par le super-admin et les
 *   citent ; sans source, la reponse le dit.
 * - RG-COM-03 / 04 : la question est analysee avant l'appel ; une donnee personnelle bloque
 *   l'envoi (le serveur rejoue le controle et refuse en 422, sans rien enregistrer).
 * - RG-COM-05 : avertissement permanent dans le panneau.
 *
 * Lot L5b E2 (RG-COM-12) : les conversations sont conservees par le serveur, propres a
 * l'utilisateur connecte : nouvelle conversation, reprise d'une ancienne (liste par date de
 * derniere activite), renommage, archivage (une conversation archivee reste consultable ; on la
 * desarchive pour y poser une question). Plus aucun echange n'est garde dans le navigateur.
 *
 * Lot L6b (E11) : la bulle flottante est remplacee par JURI (components/assistant/Juri), qui
 * ouvre ce panneau ("Poser une question au chatbot juridique"), comme la palette de
 * commandes et l'adresse /chatbot (store/assistantStore). Roles : chatbot/rolesChatbot.
 * Echap ferme le panneau. Logique, conversations, filtre des donnees personnelles et quota
 * de l'essai (serveur) inchanges.
 */
function identifiant(): string {
  return `qa-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function versEntree(e: ChatbotEchange): ChatbotEntry {
  return {
    id: e.id,
    question: e.question,
    createdAt: e.creeLe,
    answer: {
      reponse: e.reponse,
      sources: e.sources ?? [],
      confidence: e.confidence ?? 0,
      ragMode: e.ragMode ?? undefined,
    },
  };
}

export function AssistantJuridique() {
  const role = useAuthStore((s) => s.user?.role);
  const ouvert = useAssistantStore((s) => s.ouvert);
  const fermer = useAssistantStore((s) => s.fermer);

  if (!peutUtiliserChatbot(role) || !ouvert) return null;
  return <PanneauAssistant onFermer={fermer} />;
}

type Vue = 'conversation' | 'liste';

function PanneauAssistant({ onFermer }: { onFermer: () => void }) {
  const [vue, setVue] = useState<Vue>('conversation');
  /** Conversation affichee ; null = nouvelle conversation, pas encore enregistree. */
  const [conversation, setConversation] = useState<ChatbotConversationResume | null>(null);
  const [entrees, setEntrees] = useState<ChatbotEntry[]>([]);
  const [chargement, setChargement] = useState(true);
  const [erreurChargement, setErreurChargement] = useState<string | null>(null);
  const [renommage, setRenommage] = useState(false);
  const [question, setQuestion] = useState('');
  const [envoi, setEnvoi] = useState(false);
  const [blocage, setBlocage] = useState<string | null>(null);
  const [erreurAction, setErreurAction] = useState<string | null>(null);
  const fin = useRef<HTMLDivElement | null>(null);
  const saisie = useRef<HTMLTextAreaElement | null>(null);

  // Analyse au fil de la saisie : la donnee personnelle est signalee avant meme l'envoi.
  const natures = useMemo(() => detecterDonneesPersonnelles(question), [question]);
  const archivee = conversation?.archivee === true;

  const ouvrirConversation = useCallback(async (id: string) => {
    setChargement(true);
    setErreurChargement(null);
    setErreurAction(null);
    setBlocage(null);
    setRenommage(false);
    try {
      const detail = await chatbotService.lireConversation(id);
      const { echanges, ...resume } = detail;
      setConversation(resume);
      setEntrees(echanges.map(versEntree));
      setVue('conversation');
    } catch (err) {
      setErreurChargement(extractError(err).message);
    } finally {
      setChargement(false);
    }
  }, []);

  // A l'ouverture : purge de l'ancienne conservation locale, puis reprise de la conversation
  // active la plus recente (comme le panneau le faisait avant le lot L5b).
  useEffect(() => {
    purgerAnciennesConversationsLocales();
    let actif = true;
    (async () => {
      try {
        const actives = await chatbotService.listerConversations(false);
        if (!actif) return;
        if (actives.length > 0) {
          await ouvrirConversation(actives[0].id);
        } else {
          setChargement(false);
        }
      } catch (err) {
        if (!actif) return;
        setErreurChargement(extractError(err).message);
        setChargement(false);
      }
    })();
    return () => {
      actif = false;
    };
  }, [ouvrirConversation]);

  useEffect(() => {
    fin.current?.scrollIntoView?.({ behavior: 'smooth' });
  }, [entrees]);

  useEffect(() => {
    if (vue === 'conversation') saisie.current?.focus();
  }, [vue]);

  function nouvelleConversation() {
    setConversation(null);
    setEntrees([]);
    setBlocage(null);
    setErreurAction(null);
    setErreurChargement(null);
    setRenommage(false);
    setChargement(false);
    setVue('conversation');
  }

  async function envoyer() {
    const texte = question.trim();
    if (!texte || envoi || archivee) return;
    // RG-COM-04 : blocage AVANT l'appel ; la question reste dans le champ pour etre reformulee.
    const detectees = detecterDonneesPersonnelles(texte);
    if (detectees.length > 0) {
      setBlocage(messageReformulation(detectees));
      return;
    }
    setBlocage(null);
    const id = identifiant();
    setEntrees((prev) => [...prev, { id, question: texte, loading: true, createdAt: new Date().toISOString() }]);
    setQuestion('');
    setEnvoi(true);
    try {
      const resultat = conversation
        ? await chatbotService.poserQuestion(conversation.id, texte)
        : await chatbotService.creerConversation(texte);
      setConversation(resultat.conversation);
      setEntrees((prev) => prev.map((e) => (e.id === id ? versEntree(resultat.echange) : e)));
    } catch (err) {
      const message = extractError(err).message;
      // Question refusee ou en echec : rien n'est enregistre ; elle reste affichee avec l'erreur.
      setEntrees((prev) => prev.map((e) => (e.id === id ? { ...e, loading: false, error: message } : e)));
    } finally {
      setEnvoi(false);
    }
  }

  async function changerArchivage(cible: ChatbotConversationResume, archiver: boolean) {
    setErreurAction(null);
    try {
      const maj = archiver
        ? await chatbotService.archiverConversation(cible.id)
        : await chatbotService.desarchiverConversation(cible.id);
      if (conversation?.id === maj.id) setConversation(maj);
      return maj;
    } catch (err) {
      setErreurAction(extractError(err).message);
      return null;
    }
  }

  async function renommer(cible: ChatbotConversationResume, titre: string) {
    const maj = await chatbotService.renommerConversation(cible.id, titre);
    if (conversation?.id === maj.id) setConversation(maj);
    return maj;
  }

  return (
    <aside
      role="dialog"
      aria-label="Chatbot juridique"
      data-testid="assistant-panneau"
      onKeyDown={(ev) => {
        if (ev.key === 'Escape' && !ev.defaultPrevented) {
          ev.stopPropagation();
          onFermer();
        }
      }}
      className="fixed inset-y-0 right-0 z-50 flex w-full flex-col border-l border-border bg-bg-raised shadow-2xl sm:w-[420px]"
    >
      <header className="flex items-center gap-2 border-b border-border px-4 py-3">
        <Bot className="h-5 w-5 text-accent" aria-hidden="true" />
        <h2 className="flex-1 font-heading text-base font-semibold text-fg">
          Assistant juridique{' '}
          <InfoBulle
            libelle="À propos du chatbot"
            texte="Le chatbot répond à des questions générales de droit des sociétés au Maroc, uniquement à partir des sources fiables déposées par JURIKA, et cite ces sources. Il n’a accès à aucun dossier du cabinet. Vos conversations sont conservées pour vous seul : aucun autre utilisateur, même de votre cabinet, ne les voit."
          />
        </h2>
        <button
          type="button"
          onClick={() => setVue(vue === 'liste' ? 'conversation' : 'liste')}
          className={`rounded-lg p-1.5 hover:bg-bg-overlay hover:text-fg ${vue === 'liste' ? 'text-accent' : 'text-fg-subtle'}`}
          aria-label="Mes conversations"
          aria-pressed={vue === 'liste'}
          title="Mes conversations : reprendre, renommer ou archiver"
        >
          <List className="h-4 w-4" aria-hidden="true" />
        </button>
        <button
          type="button"
          onClick={nouvelleConversation}
          className="rounded-lg p-1.5 text-fg-subtle hover:bg-bg-overlay hover:text-fg"
          aria-label="Nouvelle conversation"
          title="Nouvelle conversation (la conversation affichée reste dans « Mes conversations »)"
        >
          <MessageSquarePlus className="h-4 w-4" aria-hidden="true" />
        </button>
        <button
          type="button"
          onClick={onFermer}
          className="rounded-lg p-1.5 text-fg-subtle hover:bg-bg-overlay hover:text-fg"
          aria-label="Fermer le chatbot"
        >
          <X className="h-4 w-4" aria-hidden="true" />
        </button>
      </header>

      {/* RG-COM-05 : avertissement permanent, non masquable. */}
      <div
        data-testid="assistant-avertissement"
        className="flex items-start gap-2 border-b border-warning/30 bg-warning/10 px-4 py-2 text-xs leading-relaxed text-fg"
      >
        <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-warning" aria-hidden="true" />
        <p>
          <strong>Ne saisissez aucune donnée de client</strong> : ni nom, ni numéro de CIN, ICE, RC,
          identifiant fiscal, téléphone, adresse électronique, RIB ou date de naissance. Une question
          qui en contient n’est pas envoyée.
        </p>
      </div>

      {vue === 'liste' ? (
        <ListeConversations
          conversationCourante={conversation?.id ?? null}
          onOuvrir={(id) => void ouvrirConversation(id)}
          onNouvelle={nouvelleConversation}
          onArchivage={changerArchivage}
          onRenommer={renommer}
        />
      ) : (
        <>
          <div className="flex items-center gap-2 border-b border-border px-4 py-2" data-testid="assistant-entete-conversation">
            {renommage && conversation ? (
              <RenommerConversation
                titre={conversation.titre}
                onValider={async (titre) => {
                  await renommer(conversation, titre);
                  setRenommage(false);
                }}
                onAnnuler={() => setRenommage(false)}
              />
            ) : (
              <>
                <p className="min-w-0 flex-1 truncate text-sm font-medium text-fg" title={conversation?.titre}>
                  {conversation ? conversation.titre : 'Nouvelle conversation'}
                  {archivee && <span className="ml-2 text-xs font-normal text-fg-subtle">(archivée)</span>}
                </p>
                {conversation ? (
                  <>
                    <button
                      type="button"
                      onClick={() => setRenommage(true)}
                      className="rounded-lg p-1 text-fg-subtle hover:bg-bg-overlay hover:text-fg"
                      aria-label="Renommer la conversation"
                      title="Renommer la conversation"
                    >
                      <Pencil className="h-3.5 w-3.5" aria-hidden="true" />
                    </button>
                    <button
                      type="button"
                      onClick={() => void changerArchivage(conversation, !archivee)}
                      className="rounded-lg p-1 text-fg-subtle hover:bg-bg-overlay hover:text-fg"
                      aria-label={archivee ? 'Désarchiver la conversation' : 'Archiver la conversation'}
                      title={
                        archivee
                          ? 'Désarchiver : la conversation revient dans la liste « En cours » et vous pouvez y poser de nouvelles questions'
                          : 'Archiver : la conversation quitte la liste « En cours » mais reste consultable dans « Archivées »'
                      }
                    >
                      {archivee ? (
                        <ArchiveRestore className="h-3.5 w-3.5" aria-hidden="true" />
                      ) : (
                        <Archive className="h-3.5 w-3.5" aria-hidden="true" />
                      )}
                    </button>
                  </>
                ) : (
                  <InfoBulle
                    libelle="Aide : nouvelle conversation"
                    texte="La conversation est enregistrée dès votre première question, qui lui donne son titre. Vous pourrez la renommer, la reprendre plus tard ou l’archiver depuis « Mes conversations »."
                  />
                )}
              </>
            )}
          </div>

          <div className="flex-1 space-y-4 overflow-y-auto px-4 py-4">
            <TexteAide cle="assistant-juridique" titre="Comment utiliser le chatbot">
              <p>
                Posez une question générale, par exemple : « Quelle majorité pour modifier les statuts
                d’une SARL ? ». Chaque réponse cite les sources fiables dont elle est tirée ; si ces
                sources ne traitent pas la question, le chatbot vous le dit au lieu d’inventer une
                réponse. Pour un dossier précis, vérifiez toujours dans les textes cités.
              </p>
              <p className="mt-2">
                Vos échanges sont enregistrés dans « Mes conversations » (icône de liste, en haut) : vous
                y reprenez une conversation, la renommez ou l’archivez. Une question refusée parce
                qu’elle contient une donnée de client n’est jamais enregistrée.
              </p>
            </TexteAide>

            {erreurAction && (
              <p role="alert" className="flex items-start gap-2 text-sm text-danger">
                <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                <span>{erreurAction}</span>
              </p>
            )}

            {chargement && (
              <p className="flex items-center gap-2 text-sm text-fg-subtle">
                <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> Chargement de vos conversations…
              </p>
            )}

            {erreurChargement && (
              <p role="alert" className="flex items-start gap-2 text-sm text-danger">
                <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                <span>
                  Vos conversations n’ont pas pu être chargées : {erreurChargement} Vous pouvez tout de même
                  poser une question : elle ouvrira une nouvelle conversation.
                </span>
              </p>
            )}

            {!chargement && entrees.length === 0 && (
              <p className="text-center text-sm text-fg-subtle">Aucune question pour l’instant.</p>
            )}

            {entrees.map((e) => (
              <div key={e.id} className="space-y-2">
                <div className="ml-8 rounded-2xl bg-accent px-3 py-2 text-sm text-bg">{e.question}</div>
                <div className="mr-4 space-y-2 rounded-2xl border border-border bg-bg px-3 py-2">
                  {e.loading && (
                    <p className="flex items-center gap-2 text-sm text-fg-subtle">
                      <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> Recherche dans les sources…
                    </p>
                  )}
                  {e.error && (
                    <p role="alert" className="flex items-start gap-2 text-sm text-danger">
                      <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                      <span>{e.error}</span>
                    </p>
                  )}
                  {e.answer && (
                    <>
                      <ChatMarkdown content={e.answer.reponse} />
                      {e.answer.sources.length > 0 && (
                        <div className="space-y-1 border-t border-border pt-2">
                          <p className="flex items-center gap-1 text-xs font-semibold text-fg-subtle">
                            <BookOpen className="h-3 w-3" aria-hidden="true" /> Sources citées
                          </p>
                          <ul className="space-y-1">
                            {e.answer.sources.map((s, i) => (
                              <li key={`${e.id}-${i}`} className="text-xs text-fg-muted">
                                <span className="font-medium text-fg">{s.reference}</span>
                                {s.article ? ` — ${s.article}` : ''}
                              </li>
                            ))}
                          </ul>
                        </div>
                      )}
                    </>
                  )}
                </div>
              </div>
            ))}
            <div ref={fin} />
          </div>

          {archivee && conversation ? (
            <div
              data-testid="assistant-archivee"
              className="space-y-2 border-t border-border px-4 py-3 text-xs text-fg-muted"
            >
              <p>
                Cette conversation est archivée : vous pouvez la consulter, mais plus y poser de question.
                Désarchivez-la pour la poursuivre, ou ouvrez une nouvelle conversation.
              </p>
              <div className="flex gap-2">
                <Button size="sm" variant="secondary" onClick={() => void changerArchivage(conversation, false)}>
                  Désarchiver
                </Button>
                <Button size="sm" variant="ghost" onClick={nouvelleConversation}>
                  Nouvelle conversation
                </Button>
              </div>
            </div>
          ) : (
            <div className="space-y-2 border-t border-border px-3 py-3">
              {(blocage || natures.length > 0) && (
                <p
                  role="alert"
                  data-testid="assistant-blocage"
                  className="flex items-start gap-2 rounded-lg bg-danger/10 px-3 py-2 text-xs text-danger"
                >
                  <AlertCircle className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                  <span>{natures.length > 0 ? messageReformulation(natures) : blocage}</span>
                </p>
              )}
              <div className="flex items-end gap-2">
                <label htmlFor="assistant-question" className="sr-only">
                  Votre question juridique
                </label>
                <textarea
                  id="assistant-question"
                  ref={saisie}
                  rows={2}
                  value={question}
                  onChange={(ev) => {
                    setQuestion(ev.target.value);
                    setBlocage(null);
                  }}
                  onKeyDown={(ev) => {
                    if (ev.key === 'Enter' && !ev.shiftKey) {
                      ev.preventDefault();
                      void envoyer();
                    }
                  }}
                  placeholder="Votre question de droit des sociétés, sans donnée de client…"
                  className="max-h-32 min-h-[3rem] flex-1 resize-none rounded-lg border border-border bg-bg px-3 py-2 text-sm text-fg placeholder:text-fg-subtle focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30"
                />
                <Button
                  size="sm"
                  onClick={() => void envoyer()}
                  disabled={!question.trim() || envoi || natures.length > 0}
                  loading={envoi}
                  aria-label="Envoyer la question"
                >
                  <Send className="h-4 w-4" aria-hidden="true" />
                </Button>
              </div>
              <p className="text-[11px] text-fg-subtle">
                Entrée pour envoyer, Maj + Entrée pour aller à la ligne.
              </p>
            </div>
          )}
        </>
      )}
    </aside>
  );
}
