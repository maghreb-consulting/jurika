import { useEffect, useMemo, useRef, useState } from 'react';
import { AlertCircle, AlertTriangle, BookOpen, Bot, Loader2, MessageSquarePlus, Send, X } from 'lucide-react';
import { Button } from '../ui/Button';
import { InfoBulle, TexteAide } from '../ui/Aide';
import { ChatMarkdown } from '../ChatMarkdown';
import { chatbotService } from '../../services/chatbot.service';
import { extractError } from '../../lib/api';
import { detecterDonneesPersonnelles, messageReformulation } from '../../lib/donneesPersonnelles';
import { deriveTitle, loadConversations, saveConversations } from '../../lib/chatbotConversations';
import { useAuthStore } from '../../store/authStore';
import { useAssistantStore } from '../../store/assistantStore';
import type { Role } from '../../types/auth';
import type { ChatbotEntry } from '../../types/chat';

/**
 * Lot L5 E8 : chatbot juridique en panneau lateral (CDC 12.1).
 *
 * - RG-COM-01 : bulle flottante en bas a droite, sur toutes les pages de la mise en page
 *   commune, qui ouvre un panneau lateral. Utilisateurs du cabinet (employe, superviseur) ;
 *   le super-admin l'utilise pour eprouver les sources qu'il depose. Pas le client.
 * - RG-COM-02 : les reponses viennent des seules sources deposees par le super-admin et les
 *   citent ; sans source, la reponse le dit.
 * - RG-COM-03 / 04 : la question est analysee avant l'appel ; une donnee personnelle bloque
 *   l'envoi (le serveur rejoue le controle et refuse en 422).
 * - RG-COM-05 : avertissement permanent dans le panneau.
 */
const ROLES_ASSISTANT: Role[] = ['EMPLOYE', 'SUPERVISEUR', 'SUPER_ADMIN'];

function identifiant(): string {
  return `qa-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

export function AssistantJuridique() {
  const user = useAuthStore((s) => s.user);
  const ouvert = useAssistantStore((s) => s.ouvert);
  const basculer = useAssistantStore((s) => s.basculer);
  const fermer = useAssistantStore((s) => s.fermer);

  if (!user || !ROLES_ASSISTANT.includes(user.role)) return null;

  return (
    <>
      {!ouvert && (
        <button
          type="button"
          onClick={basculer}
          aria-label="Ouvrir le chatbot juridique"
          title="Chatbot juridique : une question de droit des sociétés ?"
          data-testid="assistant-bulle"
          className="fixed bottom-6 right-6 z-50 flex h-14 w-14 items-center justify-center rounded-full bg-accent text-bg shadow-lg transition-transform hover:scale-105 focus:outline-none focus-visible:ring-2 focus-visible:ring-accent focus-visible:ring-offset-2"
        >
          <Bot className="h-6 w-6" aria-hidden="true" />
        </button>
      )}
      {ouvert && <PanneauAssistant userId={user.userId ?? null} onFermer={fermer} />}
    </>
  );
}

function PanneauAssistant({ userId, onFermer }: { userId: string | null; onFermer: () => void }) {
  const [entrees, setEntrees] = useState<ChatbotEntry[]>(() => loadConversations(userId)[0]?.entries ?? []);
  const [question, setQuestion] = useState('');
  const [envoi, setEnvoi] = useState(false);
  const [blocage, setBlocage] = useState<string | null>(null);
  const fin = useRef<HTMLDivElement | null>(null);
  const saisie = useRef<HTMLTextAreaElement | null>(null);

  // Analyse au fil de la saisie : la donnee personnelle est signalee avant meme l'envoi.
  const natures = useMemo(() => detecterDonneesPersonnelles(question), [question]);

  useEffect(() => {
    const now = new Date().toISOString();
    saveConversations(userId, [
      {
        id: 'assistant-panneau',
        title: entrees[0] ? deriveTitle(entrees[0].question) : '',
        entries: entrees,
        createdAt: entrees[0]?.createdAt ?? now,
        updatedAt: now,
      },
    ]);
    fin.current?.scrollIntoView?.({ behavior: 'smooth' });
  }, [entrees, userId]);

  useEffect(() => {
    saisie.current?.focus();
  }, []);

  async function envoyer() {
    const texte = question.trim();
    if (!texte || envoi) return;
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
      const answer = await chatbotService.ask(texte);
      setEntrees((prev) => prev.map((e) => (e.id === id ? { ...e, loading: false, answer } : e)));
    } catch (err) {
      const message = extractError(err).message;
      setEntrees((prev) => prev.map((e) => (e.id === id ? { ...e, loading: false, error: message } : e)));
    } finally {
      setEnvoi(false);
    }
  }

  return (
    <aside
      role="dialog"
      aria-label="Chatbot juridique"
      data-testid="assistant-panneau"
      className="fixed inset-y-0 right-0 z-50 flex w-full flex-col border-l border-border bg-bg-raised shadow-2xl sm:w-[420px]"
    >
      <header className="flex items-center gap-2 border-b border-border px-4 py-3">
        <Bot className="h-5 w-5 text-accent" aria-hidden="true" />
        <h2 className="flex-1 font-heading text-base font-semibold text-fg">
          Assistant juridique{' '}
          <InfoBulle
            libelle="À propos du chatbot"
            texte="Le chatbot répond à des questions générales de droit des sociétés au Maroc, uniquement à partir des sources fiables déposées par JURIKA, et cite ces sources. Il n’a accès à aucun dossier du cabinet."
          />
        </h2>
        <button
          type="button"
          onClick={() => {
            setEntrees([]);
            setBlocage(null);
          }}
          className="rounded-lg p-1.5 text-fg-subtle hover:bg-bg-overlay hover:text-fg"
          aria-label="Nouvelle conversation"
          title="Nouvelle conversation (efface l’échange affiché)"
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

      <div className="flex-1 space-y-4 overflow-y-auto px-4 py-4">
        <TexteAide cle="assistant-juridique" titre="Comment utiliser le chatbot">
          <p>
            Posez une question générale, par exemple : « Quelle majorité pour modifier les statuts d’une
            SARL ? ». Chaque réponse cite les sources fiables dont elle est tirée ; si ces sources ne
            traitent pas la question, le chatbot vous le dit au lieu d’inventer une réponse. Pour un
            dossier précis, vérifiez toujours dans les textes cités.
          </p>
        </TexteAide>

        {entrees.length === 0 && (
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

      <div className="space-y-2 border-t border-border px-3 py-3">
        {(blocage || natures.length > 0) && (
          <p role="alert" data-testid="assistant-blocage" className="flex items-start gap-2 rounded-lg bg-danger/10 px-3 py-2 text-xs text-danger">
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
    </aside>
  );
}
