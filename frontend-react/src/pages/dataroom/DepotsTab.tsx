import { useCallback, useEffect, useState } from 'react';
import { Download, Eye, FileText, Loader2, Upload } from 'lucide-react';
import { PdfPreviewModal } from '../../components/ui/PdfPreviewModal';
import { dataroomService } from '../../services/dataroom.service';
import { extractError } from '../../lib/api';
import type { DepotSummary } from '../../types/dataroom';
import { DataroomReadOnlyHint } from './components/DataroomReadOnlyHint';

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
    reload();
  }, [reload]);

  return (
    <div className="rounded-xl border border-border bg-bg-raised p-4 md:p-6">
      <div className="mb-4 flex items-center gap-2">
        <Upload className="h-5 w-5 text-warning" />
        <h3 className="text-sm font-semibold text-fg">Depots du client</h3>
      </div>

      {readOnly && (
        <DataroomReadOnlyHint
          statut={readOnlyStatut}
          testId="depots-readonly-hint"
          className="mb-4"
        />
      )}

      {error && (
        <div className="mb-4 rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
          {error}
        </div>
      )}

      {loading ? (
        <div className="flex justify-center py-10">
          <Loader2 className="h-6 w-6 animate-spin text-warning" />
        </div>
      ) : depots.length === 0 ? (
        <div className="py-12 text-center">
          <Upload className="mx-auto mb-3 h-10 w-10 text-fg-subtle" />
          <p className="text-sm text-fg-subtle">
            Aucun depot du client pour le moment.
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {depots.map((dep) => (
            <DepotRow key={dep.id} depot={dep} />
          ))}
        </div>
      )}

      {!loading && depots.length > 0 && (
        <div className="mt-6 border-t border-border pt-5 text-center">
          <p className="text-xs text-fg-subtle">
            {depots.length} depot{depots.length > 1 ? 's' : ''}
          </p>
        </div>
      )}
    </div>
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

  async function download() {
    setDownloading(true);
    try {
      await dataroomService.downloadDepot(depot.id, depot.filename);
    } finally {
      setDownloading(false);
    }
  }

  return (
    <div className="flex items-center gap-4 rounded-lg bg-bg-overlay p-3 md:p-4">
      <div className="flex h-10 w-10 flex-shrink-0 items-center justify-center">
        <FileText className="h-6 w-6 text-warning" />
      </div>
      <div className="min-w-0 flex-1">
        <h4 className="truncate text-sm font-bold text-fg">{depot.title}</h4>
        <p className="mt-0.5 text-[10px] text-fg-subtle">
          Depose par le client • {formatBytes(depot.sizeBytes)} •{' '}
          {new Date(depot.createdAt).toLocaleDateString('fr-FR')}
        </p>
      </div>
      <div className="flex flex-shrink-0 items-center gap-1">
        <button
          type="button"
          onClick={() => setPreviewOpen(true)}
          className="flex h-8 items-center gap-1 rounded border border-border bg-bg-raised px-3 text-xs text-fg transition hover:border-accent"
          title="Voir le document"
        >
          <Eye className="h-3.5 w-3.5" /> Voir
        </button>
        <button
          type="button"
          onClick={download}
          disabled={downloading}
          className="flex h-8 items-center gap-1 rounded border border-border bg-bg-raised px-3 text-xs text-fg transition hover:border-accent disabled:opacity-50"
        >
          <Download className="h-3.5 w-3.5" /> {downloading ? '...' : 'Telecharger'}
        </button>
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
      />
    </div>
  );
}
