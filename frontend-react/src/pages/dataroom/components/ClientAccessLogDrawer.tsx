import { useCallback, useEffect, useState } from 'react';
import { Activity, RefreshCw } from 'lucide-react';
import { Button } from '../../../components/ui/Button';
import { Drawer } from '../../../components/ui/Drawer';
import { dataroomService } from '../../../services/dataroom.service';
import { extractError } from '../../../lib/api';
import { formatDateTime } from '../../../lib/date';
import {
  ACCESS_LOG_ACTION_LABELS,
  type AccessLogAction,
  type AccessLogEntry,
} from '../../../types/dataroom';

/**
 * Sprint 7 / TASK 5.3 -- Drawer "Activite client" pour le cabinet.
 *
 * Affiche les 50 dernieres actions client (VIEW_DOSSIER, PREVIEW_DOC,
 * DOWNLOAD_DOC, DOWNLOAD_VERSION, PRINT_DOC, DEPOT_DOC, DEMANDE) sur un
 * dossier donne, avec date, action coloree par type, user, document
 * (uuid court), IP. Pagination "Voir plus" cote frontend.
 *
 * Vue admin uniquement : le drawer n'est jamais expose au role CLIENT.
 */
interface Props {
  open: boolean;
  dossierId: string;
  raisonSociale?: string;
  onClose: () => void;
}

const PAGE_SIZE = 50;

// Lot X -- une couleur DISTINCTE par type d'action pour lire l'activite client
// d'un coup d'oeil. Fallback gris pour un type inconnu (cf. rendu plus bas).
const ACTION_BADGE: Record<string, string> = {
  VIEW_DOSSIER: 'bg-bg-overlay text-fg-muted', // gris -- consultation dossier
  PREVIEW_DOC: 'bg-accent/20 text-accent', // bleu -- visualisation
  DOWNLOAD_DOC: 'bg-emerald-100 text-emerald-700', // vert -- telechargement
  DOWNLOAD_VERSION: 'bg-emerald-50 text-emerald-600', // vert clair -- version
  PRINT_DOC: 'bg-amber-100 text-amber-700', // ambre -- impression
  DEPOT_DOC: 'bg-violet-100 text-violet-700', // violet -- import (depot)
  DEMANDE: 'bg-pink-100 text-pink-700', // rose -- demande
};

export function ClientAccessLogDrawer({
  open,
  dossierId,
  raisonSociale,
  onClose,
}: Props) {
  const [items, setItems] = useState<AccessLogEntry[]>([]);
  const [total, setTotal] = useState(0);
  const [offset, setOffset] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(
    async (nextOffset: number, replace = false) => {
      setLoading(true);
      setError(null);
      try {
        const page = await dataroomService.getAccessLog(
          dossierId,
          PAGE_SIZE,
          nextOffset,
        );
        setItems((prev) => (replace ? page.items : [...prev, ...page.items]));
        setTotal(page.total);
        setOffset(nextOffset + page.items.length);
      } catch (err) {
        setError(extractError(err).message);
      } finally {
        setLoading(false);
      }
    },
    [dossierId],
  );

  // Reset + premier chargement a l'ouverture
  useEffect(() => {
    if (!open) return;
    setItems([]);
    setOffset(0);
    setTotal(0);
    load(0, true);
  }, [open, load]);

  return (
    <Drawer
      open={open}
      onClose={onClose}
      title="Activite client"
      subtitle={raisonSociale ?? 'Historique des consultations cote client'}
      width="xl"
      footer={
        <div className="flex items-center justify-between border-t border-border px-6 py-3">
          <p className="text-xs text-fg-subtle">
            {items.length} / {total} entree{total > 1 ? 's' : ''}
          </p>
          <div className="flex gap-2">
            <Button
              variant="secondary"
              size="sm"
              onClick={() => load(0, true)}
              disabled={loading}
            >
              <RefreshCw className="mr-1 h-4 w-4" />
              Rafraichir
            </Button>
            {items.length < total && (
              <Button
                size="sm"
                onClick={() => load(offset)}
                loading={loading}
              >
                Charger plus
              </Button>
            )}
          </div>
        </div>
      }
    >
      {error && (
        <div className="mb-3 rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
          {error}
        </div>
      )}

      {!error && items.length === 0 && !loading && (
        <div className="flex flex-col items-center gap-2 py-12 text-center text-sm text-fg-subtle">
          <Activity className="h-8 w-8 text-fg-subtle" />
          <p>Aucune activite enregistree pour ce dossier.</p>
        </div>
      )}

      {items.length > 0 && (
        <div className="overflow-hidden rounded-lg border border-border">
          <table className="w-full text-sm">
            <thead className="bg-bg-overlay text-left text-xs uppercase tracking-wider text-fg-subtle">
              <tr>
                <th className="px-3 py-2 font-semibold">Date</th>
                <th className="px-3 py-2 font-semibold">Action</th>
                <th className="px-3 py-2 font-semibold">User</th>
                <th className="px-3 py-2 font-semibold">Document</th>
                <th className="px-3 py-2 font-semibold">IP</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {items.map((row) => (
                <tr key={row.id} className="hover:bg-bg-overlay">
                  <td className="px-3 py-2 text-xs text-fg-muted">
                    {formatDateTime(row.createdAt)}
                  </td>
                  <td className="px-3 py-2">
                    <span
                      className={`inline-block rounded-full px-2 py-0.5 text-[10px] font-bold ${
                        ACTION_BADGE[row.action] ??
                        'bg-bg-overlay text-fg-muted'
                      }`}
                    >
                      {ACCESS_LOG_ACTION_LABELS[
                        row.action as AccessLogAction
                      ] ?? row.action}
                    </span>
                  </td>
                  <td className="px-3 py-2 font-mono text-[11px] text-fg-subtle">
                    {row.userId ? row.userId.slice(0, 8) : '—'}
                  </td>
                  <td className="px-3 py-2 font-mono text-[11px] text-fg-subtle">
                    {row.documentId ? row.documentId.slice(0, 8) : '—'}
                  </td>
                  <td className="px-3 py-2 font-mono text-[11px] text-fg-subtle">
                    {row.ipAddress ?? '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Drawer>
  );
}
