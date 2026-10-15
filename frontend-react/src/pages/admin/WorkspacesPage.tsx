import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Building2, ChevronRight, RefreshCw, Search } from 'lucide-react';
import { Page } from '../../components/layout/Page';
import { TextField } from '../../components/ui/TextField';
import { EmptyState } from '../../components/ui/EmptyState';
import { ErrorState } from '../../components/ui/ErrorState';
import { Tableau, type ColonneTableau } from '../../components/ui/Tableau';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { useToast } from '../../components/ui/useToast';
import { adminService } from '../../services/admin.service';
import type { AdminWorkspaceRow, WorkspaceStatusAction } from '../../types/admin';
import { FORFAIT_LABELS, labelOf } from '../../lib/dashboardLabels';
import { extractError } from '../../lib/api';
import { TexteAide } from '../../components/ui/Aide';

const ACTION_META: Record<
  WorkspaceStatusAction,
  { label: string; verb: string; done: string; targetStatus: string; variant: 'primary' | 'danger' }
> = {
  activate: { label: 'Activer', verb: 'activer', done: 'activé', targetStatus: 'ACTIVE', variant: 'primary' },
  suspend: { label: 'Suspendre', verb: 'suspendre', done: 'suspendu', targetStatus: 'SUSPENDED', variant: 'danger' },
  deactivate: { label: 'Désactiver', verb: 'désactiver', done: 'désactivé', targetStatus: 'DEACTIVATED', variant: 'danger' },
};

export function WorkspacesPage() {
  const toast = useToast();
  const navigate = useNavigate();
  const [rows, setRows] = useState<AdminWorkspaceRow[] | null>(null);
  const [error, setError] = useState(false);
  const [query, setQuery] = useState('');
  const [pending, setPending] = useState<{ ws: AdminWorkspaceRow; action: WorkspaceStatusAction } | null>(null);
  const [acting, setActing] = useState(false);

  // Recuperation seule (setState dans la promesse) : appelee au montage et par « Actualiser ».
  const recuperer = useCallback(
    () =>
      adminService
        .listWorkspaces()
        .then((data) => {
          setRows(data);
          setError(false);
        })
        .catch(() => setError(true)),
    [],
  );

  useEffect(() => {
    void recuperer();
  }, [recuperer]);

  function load() {
    setError(false);
    setRows(null);
    void recuperer();
  }

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
      toast.success(`Cabinet « ${pending.ws.name} » ${ACTION_META[pending.action].done}.`);
      // Mise a jour locale du statut (evite un rechargement complet).
      setRows((prev) =>
        (prev ?? []).map((w) =>
          w.id === pending.ws.id ? { ...w, statut: ACTION_META[pending.action].targetStatus } : w,
        ),
      );
      setPending(null);
    } catch (e) {
      toast.error(`L’action a échoué : ${extractError(e).message}`);
    } finally {
      setActing(false);
    }
  }

  const colonnes: ColonneTableau<AdminWorkspaceRow>[] = [
    {
      cle: 'cabinet',
      titre: 'Cabinet',
      rendu: (w) => (
        <>
          <Link
            to={`/admin/workspaces/${w.id}`}
            onClick={(e) => e.stopPropagation()}
            className="rounded-sm font-medium text-fg underline decoration-border-hi underline-offset-2 hover:decoration-2"
          >
            {w.name}
          </Link>
          <div className="font-mono text-xs text-fg-subtle">{w.code}</div>
          {w.contactEmail && <div className="text-xs text-fg-muted">{w.contactEmail}</div>}
        </>
      ),
    },
    { cle: 'forfait', titre: 'Forfait', rendu: (w) => <span className="text-fg-muted">{w.forfait ? labelOf(FORFAIT_LABELS, w.forfait) : '--'}</span> },
    { cle: 'statut', titre: 'Statut', rendu: (w) => <StatusBadge statut={w.statut} /> },
    { cle: 'equipe', titre: 'Équipe', alignement: 'droite', masquerSous: 'md', rendu: (w) => <span className="tabular-nums text-fg-muted">{w.employes}</span> },
    { cle: 'clients', titre: 'Clients', alignement: 'droite', masquerSous: 'md', rendu: (w) => <span className="tabular-nums text-fg-muted">{w.clients}</span> },
    { cle: 'stockage', titre: 'Stockage', alignement: 'droite', masquerSous: 'lg', rendu: (w) => <span className="text-fg-muted">{formatStorage(w)}</span> },
    { cle: 'cree', titre: 'Créé le', masquerSous: 'lg', rendu: (w) => <span className="tabular-nums text-fg-muted">{new Date(w.createdAt).toLocaleDateString('fr-FR')}</span> },
    {
      cle: 'actions',
      titre: 'Actions',
      alignement: 'droite',
      rendu: (w) => (
        <div className="flex justify-end gap-1">
          {availableActions(w.statut).map((action) => (
            <Button
              key={action}
              type="button"
              size="sm"
              variant={ACTION_META[action].variant === 'danger' ? 'danger' : 'secondary'}
              onClick={(e) => {
                e.stopPropagation();
                setPending({ ws: w, action });
              }}
            >
              {ACTION_META[action].label}
            </Button>
          ))}
        </div>
      ),
    },
    { cle: 'details', titre: <span className="sr-only">Détails</span>, largeur: 'w-8', rendu: () => <ChevronRight className="h-4 w-4 text-fg-subtle" aria-hidden="true" /> },
  ];

  return (
    <Page
      titre="Cabinets"
      description="Tous les cabinets clients de la plateforme. Activez, suspendez ou désactivez un cabinet."
      actions={
        <Button type="button" variant="secondary" icon={<RefreshCw />} onClick={load}>
          Actualiser
        </Button>
      }
      aide={
        <TexteAide cle="admin-cabinets" titre="Que faire sur cet écran ?">
          <p>
            Cliquez sur une ligne pour ouvrir la fiche du cabinet (abonnement, équipe, utilisateurs). Les
            boutons de la colonne « Actions » changent son statut : « Suspendre » et « Désactiver » coupent
            l’accès au cabinet sans effacer aucune de ses données ; « Activer » le rétablit. Une
            confirmation vous est toujours demandée.
          </p>
        </TexteAide>
      }
    >
      <div className="space-y-5">
        <div className="relative max-w-sm">
          <Search className="pointer-events-none absolute left-3 top-2.5 z-10 h-4 w-4 text-fg-subtle" aria-hidden="true" />
          <TextField
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Rechercher par code, nom ou contact…"
            aria-label="Rechercher un cabinet"
            className="pl-9"
          />
        </div>

        {error ? (
          <ErrorState title="Chargement impossible" message="La liste des cabinets n’a pas pu être récupérée." onRetry={load} />
        ) : (
          <Tableau
            legende="Cabinets de la plateforme"
            colonnes={colonnes}
            lignes={filtered}
            cleLigne={(w) => w.id}
            chargement={rows === null}
            onLigne={(w) => navigate(`/admin/workspaces/${w.id}`)}
            enTeteCollant={false}
            vide={
              <EmptyState
                compact
                icon={<Building2 />}
                title={rows && rows.length === 0 ? 'Aucun cabinet inscrit' : 'Aucun cabinet trouvé'}
                description={
                  rows && rows.length === 0
                    ? 'Aucun cabinet inscrit pour le moment : les cabinets apparaissent ici après leur inscription.'
                    : 'Aucun cabinet ne correspond à la recherche. Vérifiez le code ou le nom saisi.'
                }
              />
            }
          />
        )}

        <ConfirmDialog
          open={pending !== null}
          onOpenChange={(o) => !o && setPending(null)}
          title={pending ? `${ACTION_META[pending.action].label} le cabinet` : ''}
          description={
            pending
              ? `Voulez-vous vraiment ${ACTION_META[pending.action].verb} le cabinet « ${pending.ws.name} » (${pending.ws.code}) ?`
              : undefined
          }
          confirmLabel={pending ? ACTION_META[pending.action].label : 'Confirmer'}
          variant={pending?.action === 'activate' ? 'primary' : 'danger'}
          loading={acting}
          onConfirm={() => void confirmAction()}
        />
      </div>
    </Page>
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
  if (typeof w.storageBytes !== 'number' || !Number.isFinite(w.storageBytes)) return '--';
  const gb = w.storageBytes / (1024 * 1024 * 1024);
  const size = gb >= 0.01 ? `${gb.toFixed(2)} Go` : `${Math.round(w.storageBytes / (1024 * 1024))} Mo`;
  const n = w.storageFiles ?? 0;
  return `${size} · ${n} document${n > 1 ? 's' : ''}`;
}
