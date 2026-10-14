import { useCallback, useEffect, useRef, useState } from 'react';
import { AlertCircle, FileText, Library, Loader2, Trash2, Upload } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
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
      toast.success(n > 1 ? `${n} sources déposées.` : 'Source déposée.');
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
      toast.success('Source retirée.');
    } catch (e) {
      setErreur(extractError(e).message);
    } finally {
      setARetirer(null);
      setRetrait(false);
    }
  }

  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-4">
      <header className="flex flex-wrap items-center gap-3">
        <Library className="h-6 w-6 text-accent" aria-hidden="true" />
        <div className="flex-1">
          <h1 className="font-heading text-2xl font-semibold text-fg">
            Sources du chatbot{' '}
            <InfoBulle
              libelle="À propos des sources"
              texte="Les sources déposées ici sont communes : le chatbot de tous les cabinets y puise ses réponses, et seulement là. Aucun cabinet ne peut en déposer ni en voir la liste."
            />
          </h1>
          <p className="text-sm text-fg-subtle">Sources fiables communes à tous les cabinets.</p>
        </div>
        <input
          ref={champFichier}
          type="file"
          accept=".pdf,.docx,.txt,.md,application/pdf,text/plain"
          multiple
          className="hidden"
          data-testid="sources-champ-fichier"
          onChange={(e) => choisir(e.target.files)}
        />
        <Button onClick={() => champFichier.current?.click()} loading={depot} disabled={depot}>
          <Upload className="mr-1 h-4 w-4" aria-hidden="true" /> Déposer une source
        </Button>
      </header>

      <TexteAide cle="admin-sources-chatbot" titre="Déposer et retirer les sources du chatbot">
        <p>
          Déposez des textes fiables (PDF, DOCX ou TXT) : lois, décrets, circulaires, notes de doctrine. Chaque
          document est découpé en passages ; le chatbot ne répond qu’à partir de ces passages et les cite. Ne déposez
          aucun document contenant des données de clients. Un dépôt ou un retrait est inscrit au journal d’audit.
          Ensuite, ouvrez le chatbot (bulle en bas à droite) et posez une question pour vérifier que la source est
          bien retrouvée.
        </p>
      </TexteAide>

      {erreur && (
        <p role="alert" className="flex items-start gap-2 rounded-lg bg-danger/10 px-3 py-2 text-sm text-danger">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
          <span>{erreur}</span>
        </p>
      )}

      <Card className="p-4">
        {chargement ? (
          <p className="flex items-center justify-center gap-2 py-6 text-sm text-fg-subtle">
            <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> Chargement des sources…
          </p>
        ) : sources.length === 0 ? (
          <p className="py-6 text-center text-sm text-fg-subtle">
            Aucune source déposée : le chatbot répond à chaque question qu’il n’a pas de source. Déposez un premier
            document avec le bouton « Déposer une source ».
          </p>
        ) : (
          <ul className="space-y-1.5" aria-label="Sources communes">
            {sources.map((s) => (
              <li
                key={s.doc_id}
                className="flex items-center gap-2 rounded-lg border border-border bg-bg-raised px-3 py-2 text-sm"
              >
                <FileText className="h-4 w-4 shrink-0 text-accent" aria-hidden="true" />
                <span className="flex-1 truncate text-fg" title={s.source}>
                  {s.source}
                </span>
                <span className="text-xs text-fg-subtle">{dateCourte(s.created_at)}</span>
                <Badge variant="default">
                  {s.chunks} passage{s.chunks > 1 ? 's' : ''}
                </Badge>
                <button
                  type="button"
                  onClick={() => setARetirer(s)}
                  className="rounded-lg p-1 text-fg-subtle transition-colors hover:bg-danger/10 hover:text-danger"
                  aria-label={`Retirer ${s.source}`}
                >
                  <Trash2 className="h-4 w-4" aria-hidden="true" />
                </button>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <div>
        <Button variant="secondary" size="sm" onClick={ouvrirAssistant}>
          Vérifier dans le chatbot
        </Button>
      </div>

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
                <FileText className="h-4 w-4 shrink-0 text-accent" aria-hidden="true" />
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
  );
}
