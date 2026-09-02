import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Building2, ChevronRight, RefreshCw, Search } from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { useToast } from '../../components/ui/Toast';
import { adminService } from '../../services/admin.service';
import type { AdminWorkspaceRow, WorkspaceStatusAction } from '../../types/admin';
import { FORFAIT_LABELS, labelOf } from '../../lib/dashboardLabels';

const ACTION_META: Record<
  WorkspaceStatusAction,
  { label: string; verb: string; targetStatus: string; variant: 'primary' | 'danger' }
> = {
  activate: { label: 'Activer', verb: 'activer', targetStatus: 'ACTIVE', variant: 'primary' },
  suspend: { label: 'Suspendre', verb: 'suspendre', targetStatus: 'SUSPENDED', variant: 'danger' },
  deactivate: { label: 'Désactiver', verb: 'désactiver', targetStatus: 'DEACTIVATED', variant: 'danger' },
};

export function WorkspacesPage() {
  const toast = useToast();
  const navigate = useNavigate();
  const [rows, setRows] = useState<AdminWorkspaceRow[] | null>(null);
  const [error, setError] = useState(false);
  const [query, setQuery] = useState('');
  const [pending, setPending] = useState<{ ws: AdminWorkspaceRow; action: WorkspaceStatusAction } | null>(null);
  const [acting, setActing] = useState(false);

  async function load() {
    setError(false);
    setRows(null);
    try {
      const data = await adminService.listWorkspaces();
      setRows(data);
    } catch {
      setError(true);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  const filtered = useMemo(() => {
    if (!rows) return [];
    const q = query.trim().toLowerCase();
    if (!q) return rows;
    return rows.filter(
      (w) =>
        w.code.toLowerCase().includes(q) ||
        w.name.toLowerCase().includes(q) ||
        (w.contactEmail ?? '').toLowerCase().includes(q),
    );
  }, [rows, query]);

  async function confirmAction() {
    if (!pending) return;
    setActing(true);
    try {
      await adminService.setWorkspaceStatus(pending.ws.id, pending.action);
      toast.success(`« ${pending.ws.name} » — ${ACTION_META[pending.action].verb} effectué.`);
      // Mise a jour locale du statut (evite un rechargement complet).
      setRows((prev) =>
        (prev ?? []).map((w) =>
          w.id === pending.ws.id ? { ...w, statut: ACTION_META[pending.action].targetStatus } : w,
        ),
      );
      setPending(null);
    } catch {
      toast.error("L'action a échoué. Réessayez.");
    } finally {
      setActing(false);
    }
  }

  return (
    <div className="space-y-5">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="flex items-center gap-2 font-heading text-2xl font-semibold text-fg">
            <Building2 className="h-6 w-6 text-accent" /> Workspaces
          </h1>
          <p className="mt-1 text-sm text-fg-subtle">
            Tous les cabinets clients de la plateforme. Activez, suspendez ou désactivez un workspace.
          </p>
        </div>
        <Button variant="secondary" size="sm" onClick={() => void load()}>
          <RefreshCw className="mr-2 h-4 w-4" /> Actualiser
        </Button>
      </header>

      <div className="relative max-w-sm">
        <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-fg-subtle" />
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Rechercher par code, nom ou contact…"
          className="w-full rounded-lg border border-border bg-bg-raised py-2 pl-9 pr-3 text-sm text-fg placeholder:text-fg-subtle focus:border-accent focus:outline-none"
        />
      </div>

      {error && (
        <Card className="p-8 text-center">
          <p className="font-medium text-fg">Chargement impossible</p>
          <p className="mt-1 text-sm text-fg-subtle">La liste des workspaces n'a pas pu être récupérée.</p>
          <Button variant="secondary" size="sm" className="mt-4" onClick={() => void load()}>
            Réessayer
          </Button>
        </Card>
      )}

      {!error && rows === null && (
        <div className="space-y-2">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="h-14 animate-pulse rounded-lg border border-border bg-bg-raised" />
          ))}
        </div>
      )}

      {!error && rows !== null && filtered.length === 0 && (
        <Card className="p-10 text-center">
          <p className="text-sm text-fg-subtle">
            {rows.length === 0 ? 'Aucun workspace enregistré.' : 'Aucun workspace ne correspond à la recherche.'}
          </p>
        </Card>
      )}

      {!error && filtered.length > 0 && (
        <Card className="overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border text-left text-xs uppercase tracking-wide text-fg-subtle">
                  <th className="px-4 py-3 font-medium">Cabinet</th>
                  <th className="px-4 py-3 font-medium">Forfait</th>
                  <th className="px-4 py-3 font-medium">Statut</th>
                  <th className="px-4 py-3 text-right font-medium">Équipe</th>
                  <th className="px-4 py-3 text-right font-medium">Clients</th>
                  <th className="px-4 py-3 text-right font-medium">Stockage</th>
                  <th className="px-4 py-3 font-medium">Créé le</th>
                  <th className="px-4 py-3 text-right font-medium">Actions</th>
                  <th className="w-8 px-2 py-3" aria-label="Détails" />
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {filtered.map((w) => (
                  <tr
                    key={w.id}
                    onClick={() => navigate(`/admin/workspaces/${w.id}`)}
                    className="cursor-pointer hover:bg-bg-overlay/50"
                  >
                    <td className="px-4 py-3">
                      <div className="font-medium text-fg">{w.name}</div>
                      <div className="font-mono text-[11px] text-fg-subtle">{w.code}</div>
                      {w.contactEmail && (
                        <div className="text-[11px] text-fg-subtle">{w.contactEmail}</div>
                      )}
                    </td>
                    <td className="px-4 py-3 text-fg-muted">
                      {w.forfait ? labelOf(FORFAIT_LABELS, w.forfait) : '—'}
                    </td>
                    <td className="px-4 py-3">
                      <StatusBadge statut={w.statut} />
                    </td>
                    <td className="px-4 py-3 text-right tabular-nums text-fg-muted">{w.employes}</td>
                    <td className="px-4 py-3 text-right tabular-nums text-fg-muted">{w.clients}</td>
                    <td className="px-4 py-3 text-right text-fg-muted">{formatStorage(w)}</td>
                    <td className="px-4 py-3 text-fg-subtle">
                      {new Date(w.createdAt).toLocaleDateString('fr-FR')}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex justify-end gap-1">
                        {availableActions(w.statut).map((action) => (
                          <button
                            key={action}
                            type="button"
                            onClick={(e) => {
                              e.stopPropagation();
                              setPending({ ws: w, action });
                            }}
                            className={`rounded-md px-2 py-1 text-xs font-medium transition ${
                              ACTION_META[action].variant === 'danger'
                                ? 'text-danger hover:bg-danger/10'
                                : 'text-success hover:bg-success/10'
                            }`}
                          >
                            {ACTION_META[action].label}
                          </button>
                        ))}
                      </div>
                    </td>
                    <td className="px-2 py-3 text-fg-subtle">
                      <ChevronRight className="h-4 w-4" />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      <ConfirmDialog
        open={pending !== null}
        onOpenChange={(o) => !o && setPending(null)}
        title={pending ? `${ACTION_META[pending.action].label} le workspace` : ''}
        description={
          pending
            ? `Confirmer ${ACTION_META[pending.action].verb} « ${pending.ws.name} » (${pending.ws.code}) ?`
            : undefined
        }
        confirmLabel={pending ? ACTION_META[pending.action].label : 'Confirmer'}
        variant={pending?.action === 'activate' ? 'primary' : 'danger'}
        loading={acting}
        onConfirm={() => void confirmAction()}
      />
    </div>
  );
}

function availableActions(statut: string): WorkspaceStatusAction[] {
  switch (statut) {
    case 'ACTIVE':
    case 'ESSAI':
      return ['suspend', 'deactivate'];
    case 'SUSPENDED':
      return ['activate', 'deactivate'];
    case 'DEACTIVATED':
      return ['activate'];
    default: // PENDING_VERIFICATION
      return ['activate', 'deactivate'];
  }
}

function StatusBadge({ statut }: { statut: string }) {
  switch (statut) {
    case 'ACTIVE':
      return <Badge variant="success">Actif</Badge>;
    case 'ESSAI':
      return <Badge variant="info">Essai</Badge>;
    case 'SUSPENDED':
      return <Badge variant="warning">Suspendu</Badge>;
    case 'DEACTIVATED':
      return <Badge variant="danger">Désactivé</Badge>;
    case 'PENDING_VERIFICATION':
      return <Badge variant="neutral">En attente</Badge>;
    default:
      return <Badge variant="neutral">{statut}</Badge>;
  }
}

function formatStorage(w: AdminWorkspaceRow): string {
  if (w.storageBytes === null || w.storageBytes === undefined) return '—';
  const gb = w.storageBytes / (1024 * 1024 * 1024);
  const size = gb >= 0.01 ? `${gb.toFixed(2)} Go` : `${Math.round(w.storageBytes / (1024 * 1024))} Mo`;
  return `${size} · ${w.storageFiles ?? 0} doc`;
}
