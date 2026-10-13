import { useEffect, useState } from 'react';
import { AlertCircle, Archive, ArchiveRestore, Loader2, MessageSquarePlus, Pencil } from 'lucide-react';
import { Button } from '../ui/Button';
import { InfoBulle, TexteAide } from '../ui/Aide';
import { RenommerConversation } from './RenommerConversation';
import { chatbotService } from '../../services/chatbot.service';
import { extractError } from '../../lib/api';
import { formatDateTime } from '../../lib/date';
import type { ChatbotConversationResume } from '../../types/chat';

/**
 * Lot L5b E2 (RG-COM-12) : « Mes conversations » dans le panneau du chatbot. Liste par date de
 * derniere activite, conversations en cours ou archivees ; reprise, renommage, archivage et
 * desarchivage. Le serveur ne rend que les conversations de l'utilisateur connecte.
 */
export function ListeConversations({
  conversationCourante,
  onOuvrir,
  onNouvelle,
  onArchivage,
  onRenommer,
}: {
  conversationCourante: string | null;
  onOuvrir: (id: string) => void;
  onNouvelle: () => void;
  onArchivage: (c: ChatbotConversationResume, archiver: boolean) => Promise<ChatbotConversationResume | null>;
  onRenommer: (c: ChatbotConversationResume, titre: string) => Promise<ChatbotConversationResume>;
}) {
  const [archivees, setArchivees] = useState(false);
  const [liste, setListe] = useState<ChatbotConversationResume[]>([]);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState<string | null>(null);
  const [enRenommage, setEnRenommage] = useState<string | null>(null);

  useEffect(() => {
    let actif = true;
    chatbotService
      .listerConversations(archivees)
      .then((l) => {
        if (!actif) return;
        setListe(l);
        setErreur(null);
      })
      .catch((err: unknown) => {
        if (!actif) return;
        setErreur(extractError(err).message);
        setListe([]);
      })
      .finally(() => {
        if (actif) setChargement(false);
      });
    return () => {
      actif = false;
    };
  }, [archivees]);

  function choisirOnglet(voirArchivees: boolean) {
    if (voirArchivees === archivees) return;
    setChargement(true);
    setArchivees(voirArchivees);
  }

  async function basculerArchivage(c: ChatbotConversationResume) {
    const maj = await onArchivage(c, !c.archivee);
    // La conversation change de liste : elle quitte celle qui est affichee.
    if (maj) setListe((prev) => prev.filter((x) => x.id !== maj.id));
  }

  return (
    <div className="flex flex-1 flex-col overflow-hidden" data-testid="assistant-liste">
      <div className="flex items-center gap-2 border-b border-border px-4 py-2">
        <h3 className="flex-1 text-sm font-semibold text-fg">
          Mes conversations{' '}
          <InfoBulle
            libelle="Aide : mes conversations"
            texte="Vos conversations avec le chatbot, de la plus récente à la plus ancienne. Elles ne sont visibles que par vous. Cliquez sur un titre pour reprendre la conversation."
          />
        </h3>
        <Button size="sm" variant="secondary" onClick={onNouvelle}>
          <MessageSquarePlus className="mr-1 h-3.5 w-3.5" aria-hidden="true" /> Nouvelle
        </Button>
      </div>

      <div role="tablist" aria-label="Conversations en cours ou archivées" className="flex gap-1 border-b border-border px-4 py-2">
        {[
          { valeur: false, libelle: 'En cours' },
          { valeur: true, libelle: 'Archivées' },
        ].map((onglet) => (
          <button
            key={onglet.libelle}
            type="button"
            role="tab"
            aria-selected={archivees === onglet.valeur}
            onClick={() => choisirOnglet(onglet.valeur)}
            className={`rounded-md px-2 py-1 text-xs font-medium ${
              archivees === onglet.valeur ? 'bg-accent text-bg' : 'text-fg-subtle hover:bg-bg-overlay hover:text-fg'
            }`}
          >
            {onglet.libelle}
          </button>
        ))}
      </div>

      <div className="flex-1 space-y-3 overflow-y-auto px-4 py-3">
        <TexteAide cle="assistant-conversations" titre="Retrouver vos conversations">
          <p>
            Chaque question que vous posez est enregistrée dans une conversation, dont le titre est tiré de
            la première question. Le crayon renomme une conversation ; l’icône d’archive la range dans
            « Archivées », où elle reste consultable. Pour y poser une nouvelle question, désarchivez-la.
          </p>
        </TexteAide>

        {chargement && (
          <p className="flex items-center gap-2 text-sm text-fg-subtle">
            <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> Chargement…
          </p>
        )}
        {erreur && (
          <p role="alert" className="flex items-start gap-2 text-sm text-danger">
            <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
            <span>{erreur}</span>
          </p>
        )}
        {!chargement && !erreur && liste.length === 0 && (
          <p className="text-center text-sm text-fg-subtle">
            {archivees
              ? 'Aucune conversation archivée.'
              : 'Aucune conversation en cours. Posez une question pour en ouvrir une.'}
          </p>
        )}

        <ul className="space-y-2">
          {liste.map((c) => (
            <li
              key={c.id}
              data-testid="assistant-conversation"
              className={`rounded-lg border px-3 py-2 ${
                c.id === conversationCourante ? 'border-accent bg-accent/5' : 'border-border bg-bg'
              }`}
            >
              {enRenommage === c.id ? (
                <RenommerConversation
                  titre={c.titre}
                  onValider={async (titre) => {
                    const maj = await onRenommer(c, titre);
                    setListe((prev) => prev.map((x) => (x.id === maj.id ? maj : x)));
                    setEnRenommage(null);
                  }}
                  onAnnuler={() => setEnRenommage(null)}
                />
              ) : (
                <div className="flex items-start gap-2">
                  <button
                    type="button"
                    onClick={() => onOuvrir(c.id)}
                    className="min-w-0 flex-1 text-left"
                    title={`Reprendre « ${c.titre} »`}
                  >
                    <span className="block truncate text-sm font-medium text-fg">{c.titre}</span>
                    <span className="block text-[11px] text-fg-subtle">
                      Dernière question le {formatDateTime(c.derniereActivite)} ·{' '}
                      {c.nombreEchanges} question{c.nombreEchanges > 1 ? 's' : ''}
                    </span>
                  </button>
                  <button
                    type="button"
                    onClick={() => setEnRenommage(c.id)}
                    className="rounded-lg p-1 text-fg-subtle hover:bg-bg-overlay hover:text-fg"
                    aria-label={`Renommer « ${c.titre} »`}
                    title="Renommer"
                  >
                    <Pencil className="h-3.5 w-3.5" aria-hidden="true" />
                  </button>
                  <button
                    type="button"
                    onClick={() => void basculerArchivage(c)}
                    className="rounded-lg p-1 text-fg-subtle hover:bg-bg-overlay hover:text-fg"
                    aria-label={`${c.archivee ? 'Désarchiver' : 'Archiver'} « ${c.titre} »`}
                    title={c.archivee ? 'Désarchiver' : 'Archiver (reste consultable)'}
                  >
                    {c.archivee ? (
                      <ArchiveRestore className="h-3.5 w-3.5" aria-hidden="true" />
                    ) : (
                      <Archive className="h-3.5 w-3.5" aria-hidden="true" />
                    )}
                  </button>
                </div>
              )}
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
