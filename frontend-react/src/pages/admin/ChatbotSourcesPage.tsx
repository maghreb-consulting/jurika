import { useCallback, useEffect, useRef, useState } from 'react';
import { FileText, Library, Trash2, Upload } from 'lucide-react';
import { Page } from '../../components/layout/Page';
import { Message } from '../../components/ui/Message';
import { EmptyState } from '../../components/ui/EmptyState';
import { Skeleton, SqueletteZone } from '../../components/ui/LoadingSkeleton';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { TexteAide } from '../../components/ui/Aide';
import { useToast } from '../../components/ui/useToast';
import { chatbotService } from '../../services/chatbot.service';
import { extractError } from '../../lib/api';
import { useAssistantStore } from '../../store/assistantStore';
import type { ChatbotSourceDoc } from '../../types/chat';

function dateCourte(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '' : d.toLocaleDateString('fr-FR');
}

/**
 * Lot L5 E8 (CDC 3.1, RG-COM-02, RG-ADM-03) : ecran du super-admin pour deposer et retirer
 * les sources fiables du chatbot. Ces sources forment le corpus commun, lu par tous les
 * cabinets ; l'ajout et le retrait sont traces au journal d'audit (CHATBOT_SOURCE_ADDED,
 * CHATBOT_SOURCE_DELETED). Reprend la gestion des sources de l'ancienne page /chatbot.
 */
export function ChatbotSourcesPage() {
  const toast = useToast();
  const ouvrirAssistant = useAssistantStore((s) => s.ouvrir);
  const [sources, setSources] = useState<ChatbotSourceDoc[]>([]);
  const [chargement, setChargement] = useState(true);
  const [erreur, setErreur] = useState<string | null>(null);
  const [fichiers, setFichiers] = useState<File[] | null>(null);
  const [depot, setDepot] = useState(false);
  const [aRetirer, setARetirer] = useState<ChatbotSourceDoc | null>(null);
  const [retrait, setRetrait] = useState(false);
  const champFichier = useRef<HTMLInputElement | null>(null);

  // Les mises a jour d'etat suivent la reponse (aucune mise a jour synchrone dans l'effet).
  const charger = useCallback(
    () =>
      chatbotService
        .listSources()
        .then((liste) => {
          setSources(liste);
          setErreur(null);
        })
        .catch((e: unknown) => setErreur(extractError(e).message))
        .finally(() => setChargement(false)),
    [],
  );

  useEffect(() => {
    void charger();
  }, [charger]);

  function choisir(liste: FileList | null) {
    const choisis = liste ? Array.from(liste) : [];
    if (champFichier.current) champFichier.current.value = '';
    if (choisis.length > 0) {
      setErreur(null);
      setFichiers(choisis);
    }
  }

  async function deposer() {
    if (!fichiers || fichiers.length === 0) return;
    const n = fichiers.length;
    setDepot(true);
    setErreur(null);
    try {
      for (const f of fichiers) {
        await chatbotService.addSource(f);
      }
      await charger();
      toast.enregistre(n > 1 ? `${n} sources déposées` : 'Source déposée');
    } catch (e) {
      setErreur(extractError(e).message);
    } finally {
      setFichiers(null);
      setDepot(false);
    }
  }

  async function retirer() {
    const cible = aRetirer;
    if (!cible) return;
    setRetrait(true);
    setErreur(null);
    try {
      await chatbotService.deleteSource(cible.doc_id);
      setSources((prev) => prev.filter((s) => s.doc_id !== cible.doc_id));
      toast.enregistre('Source retirée');
    } catch (e) {
      setErreur(extractError(e).message);
    } finally {
      setARetirer(null);
      setRetrait(false);
    }
  }

  return (
    <Page
      titre="Sources du chatbot"
      largeur="etroite"
      infoBulle="Les sources déposées ici sont communes : le chatbot de tous les cabinets y puise ses réponses, et seulement là. Aucun cabinet ne peut en déposer ni en voir la liste."
      description="Sources fiables communes à tous les cabinets."
      actions={
        <Button type="button" variant="secondary" onClick={ouvrirAssistant}>
          Vérifier dans le chatbot
        </Button>
      }
      action={
        <Button type="button" onClick={() => champFichier.current?.click()} loading={depot} disabled={depot} icon={<Upload />}>
          Déposer une source
        </Button>
      }
      aide={
      <TexteAide cle="admin-sources-chatbot" titre="Déposer et retirer les sources du chatbot">
        <p>
          Déposez des textes fiables (PDF, DOCX ou TXT) : lois, décrets, circulaires, notes de doctrine. Chaque
          document est découpé en passages ; le chatbot ne répond qu’à partir de ces passages et les cite. Ne déposez
          aucun document contenant des données de clients. Un dépôt ou un retrait est inscrit au journal d’audit.
          Ensuite, ouvrez le chatbot (bouton « Vérifier dans le chatbot » en haut de cet écran, JURI sur le bord droit de l’écran, ou Ctrl+K puis « Chatbot ») et posez une question pour vérifier que la source est
          bien retrouvée.
        </p>
      </TexteAide>
      }
    >
      <div className="flex flex-col gap-4">
        <input
          ref={champFichier}
          type="file"
          accept=".pdf,.docx,.txt,.md,application/pdf,text/plain"
          multiple
          className="hidden"
          data-testid="sources-champ-fichier"
          onChange={(e) => choisir(e.target.files)}
        />

      {erreur && <Message ton="erreur">{erreur}</Message>}

      <Card className="p-4">
        {chargement ? (
          <SqueletteZone libelle="Chargement des sources…" className="space-y-2">
            {Array.from({ length: 3 }).map((_, i) => (
              <Skeleton key={i} className="h-10 w-full" />
            ))}
          </SqueletteZone>
        ) : sources.length === 0 ? (
          <EmptyState
            compact
            icon={<Library />}
            title="Aucune source déposée"
            description="Le chatbot répond à chaque question qu’il n’a pas de source. Déposez un premier document avec le bouton « Déposer une source », en haut à droite."
          />
        ) : (
          <ul className="space-y-1.5" aria-label="Sources communes">
            {sources.map((s) => (
              <li
                key={s.doc_id}
                className="flex items-center gap-2 rounded-lg border border-border bg-bg-raised px-3 py-2 text-sm"
              >
                <FileText className="h-4 w-4 shrink-0 text-fg-subtle" aria-hidden="true" />
                <span className="flex-1 truncate text-fg" title={s.source}>
                  {s.source}
                </span>
                <span className="text-xs text-fg-subtle">{dateCourte(s.created_at)}</span>
                <Badge variant="neutral">
                  {s.chunks} passage{s.chunks > 1 ? 's' : ''}
                </Badge>
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  iconOnly
                  onClick={() => setARetirer(s)}
                  aria-label={`Retirer ${s.source}`}
                  icon={<Trash2 />}
                />
              </li>
            ))}
          </ul>
        )}
      </Card>

      <ConfirmDialog
        open={fichiers !== null}
        onOpenChange={(o) => {
          if (!o && !depot) setFichiers(null);
        }}
        title="Déposer comme source commune ?"
        description="Ces documents seront lus par le chatbot de tous les cabinets. Vérifiez qu’ils ne contiennent aucune donnée de client."
        confirmLabel="Déposer"
        variant="primary"
        loading={depot}
        onConfirm={() => void deposer()}
      >
        {fichiers && (
          <ul className="max-h-40 space-y-1.5 overflow-y-auto rounded-lg border border-border bg-bg-raised px-3 py-2">
            {fichiers.map((f, i) => (
              <li key={`${f.name}-${i}`} className="flex items-center gap-2 text-sm text-fg">
                <FileText className="h-4 w-4 shrink-0 text-fg-subtle" aria-hidden="true" />
                <span className="truncate" title={f.name}>
                  {f.name}
                </span>
              </li>
            ))}
          </ul>
        )}
      </ConfirmDialog>

      <ConfirmDialog
        open={aRetirer !== null}
        onOpenChange={(o) => {
          if (!o && !retrait) setARetirer(null);
        }}
        title="Retirer la source ?"
        description={
          aRetirer
            ? `Retirer « ${aRetirer.source} » ? Le chatbot de tous les cabinets cessera aussitôt de s’en servir.`
            : undefined
        }
        confirmLabel="Retirer"
        variant="danger"
        loading={retrait}
        onConfirm={() => void retirer()}
      />
      </div>
    </Page>
  );
}
