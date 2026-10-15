import { useCallback, useEffect, useState } from 'react';
import { Download, Eye, FileText, Upload } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { Card } from '../../components/ui/Card';
import { Message } from '../../components/ui/Message';
import { EmptyState } from '../../components/ui/EmptyState';
import { Skeleton } from '../../components/ui/LoadingSkeleton';
import { PdfPreviewModal } from '../../components/ui/PdfPreviewModal';
import { dataroomService } from '../../services/dataroom.service';
import { extractError } from '../../lib/api';
import type { DepotSummary } from '../../types/dataroom';
import { DataroomReadOnlyHint } from './components/DataroomReadOnlyHint';
import { useEnEssai } from '../../store/essaiStore';
import { InfoBulle } from '../../components/ui/Aide';

/**
 * Lot V -- onglet « Depots » cote EMPLOYE (lecture seule).
 *
 * L'employe responsable (et SUPERVISEUR / SUPER_ADMIN) consulte les fichiers
 * deposes librement par le client : Voir (apercu inline PDF + images) et
 * Telecharger. Le scoping responsable est applique cote backend (403 si
 * l'employe n'est pas responsable du dossier).
 */
export function DepotsTab({
  dossierId,
  readOnly = false,
  readOnlyStatut = null,
}: {
  dossierId: string;
  role: string | null;
  /**
   * Lot DIVERS §A (2026-08-13) — societe dissoute / liquidee / radiee : le client
   * ne peut plus rien deposer (le backend refuse l'upload). L'onglet employe
   * etant deja en lecture seule, on se contente de l'expliciter.
   */
  readOnly?: boolean;
  readOnlyStatut?: string | null;
}) {
  const [depots, setDepots] = useState<DepotSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setDepots(await dataroomService.listDepots(dossierId));
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }, [dossierId]);

  useEffect(() => {
    // Chargement par promesse : aucun etat pose de facon synchrone dans l'effet.
    void Promise.resolve().then(reload);
  }, [reload]);

  return (
    <Card
      padding={false}
      titleAs="h3"
      title={
        <span className="flex items-center gap-2">
          <Upload aria-hidden="true" className="h-4 w-4 text-fg-muted" />
          Dépôts du client
          {!loading && <Badge variant="neutral">{depots.length}</Badge>}
        </span>
      }
      description="Consultation seulement : le client dépose ses pièces depuis son espace."
    >
      <div className="space-y-3 px-5 pb-5 pt-3">
        {readOnly && (
          <DataroomReadOnlyHint
            statut={readOnlyStatut}
            testId="depots-readonly-hint"
          />
        )}

        {error && <Message ton="erreur">{error}</Message>}

        {loading ? (
          <div className="space-y-2" aria-busy="true" aria-label="Chargement des dépôts">
            <Skeleton className="h-14 w-full" />
            <Skeleton className="h-14 w-full" />
          </div>
        ) : depots.length === 0 ? (
          !error && (
            <EmptyState
              compact
              icon={<Upload />}
              title="Aucun dépôt du client pour le moment"
              description="Les pièces qu’il dépose depuis son espace apparaîtront ici. Pour lui en demander une, créez une requête dans « Requêtes du cabinet »."
            />
          )
        ) : (
          <ul className="divide-y divide-border rounded-lg border border-border">
            {depots.map((dep) => (
              <DepotRow key={dep.id} depot={dep} />
            ))}
          </ul>
        )}
      </div>
    </Card>
  );
}

function formatBytes(n: number): string {
  if (!n || n <= 0) return '0 o';
  const units = ['o', 'Ko', 'Mo', 'Go'];
  const i = Math.min(Math.floor(Math.log(n) / Math.log(1024)), units.length - 1);
  return `${(n / Math.pow(1024, i)).toFixed(i === 0 ? 0 : 1)} ${units[i]}`;
}

function DepotRow({ depot }: { depot: DepotSummary }) {
  const [downloading, setDownloading] = useState(false);
  const [previewOpen, setPreviewOpen] = useState(false);
  // Decision du 2026-10-14 (RG-ESS-05) : aucun telechargement propose pendant l'essai.
  const enEssai = useEnEssai();

  async function download() {
    setDownloading(true);
    try {
      await dataroomService.downloadDepot(depot.id, depot.filename);
    } finally {
      setDownloading(false);
    }
  }

  return (
    <li className="flex flex-wrap items-center gap-3 px-4 py-3">
      <div aria-hidden="true" className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-bg-overlay">
        <FileText className="h-4 w-4 text-fg-muted" />
      </div>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium text-fg">{depot.title}</p>
        <p className="mt-0.5 text-xs text-fg-muted">
          Déposé par le client · {formatBytes(depot.sizeBytes)} ·{' '}
          {new Date(depot.createdAt).toLocaleDateString('fr-FR')}
        </p>
      </div>
      <div className="ml-auto flex shrink-0 items-center gap-1">
        <Button type="button" size="sm" variant="ghost" icon={<Eye />} onClick={() => setPreviewOpen(true)} title="Voir le document">
          Voir
        </Button>
        {enEssai !== false ? (
          <InfoBulle libelle="Téléchargement indisponible" texte="Téléchargement indisponible pendant l’essai gratuit : le document se consulte en aperçu, avec la mention d’évaluation. Il sera téléchargeable après la souscription d’un forfait." />
        ) : (
          <Button type="button" size="sm" variant="secondary" icon={<Download />} onClick={download} loading={downloading}>
            Télécharger
          </Button>
        )}
      </div>
      <PdfPreviewModal
        open={previewOpen}
        documentId={previewOpen ? depot.id : null}
        filename={depot.filename}
        canDownload
        canPrint
        onClose={() => setPreviewOpen(false)}
        onDownload={download}
        fetchPreview={dataroomService.previewDepot}
        fetchApercu={dataroomService.apercuDepot}
      />
    </li>
  );
}
