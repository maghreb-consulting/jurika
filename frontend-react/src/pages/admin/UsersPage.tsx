import { useEffect, useMemo, useState } from 'react';
import { KeyRound, Search, ShieldCheck, Users } from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { useToast } from '../../components/ui/useToast';
import { adminService } from '../../services/admin.service';
import { extractError } from '../../lib/api';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import type { AdminUserRow, AdminUsersPage, AdminWorkspaceRow } from '../../types/admin';

const PAGE_SIZE = 25;

const ROLE_LABELS: Record<string, string> = {
  SUPER_ADMIN: 'Super-administrateur',
  SUPERVISEUR: 'Superviseur',
  EMPLOYE: 'Employé',
  CLIENT: 'Client',
};

type PendingAction =
  | { type: 'reset'; user: AdminUserRow }
  | { type: 'suspend'; user: AdminUserRow }
  | { type: 'reactivate'; user: AdminUserRow };

export function UsersPage() {
  const toast = useToast();
  const [search, setSearch] = useState('');
  const [debounced, setDebounced] = useState('');
  const [role, setRole] = useState('');
  const [workspaceId, setWorkspaceId] = useState('');
  const [offset, setOffset] = useState(0);

  const [page, setPage] = useState<AdminUsersPage | null>(null);
  const [error, setError] = useState(false);
  // Parametres de la derniere liste recue : le chargement se deduit pendant le rendu.
  const [chargePour, setChargePour] = useState<string | null>(null);
  const cle = `${debounced}|${role}|${workspaceId}|${offset}`;
  const loading = chargePour !== cle;
  const [workspaces, setWorkspaces] = useState<AdminWorkspaceRow[]>([]);

  const [pending, setPending] = useState<PendingAction | null>(null);
  const [acting, setActing] = useState(false);

  // Debounce recherche ; une recherche nouvelle repart de la premiere page.
  useEffect(() => {
    const t = setTimeout(() => {
      if (search !== debounced) {
        setDebounced(search);
        setOffset(0);
      }
    }, 300);
    return () => clearTimeout(t);
  }, [search, debounced]);

  // Dropdown workspaces (filtre).
  useEffect(() => {
    adminService.listWorkspaces().then(setWorkspaces).catch(() => setWorkspaces([]));
  }, []);

  useEffect(() => {
    let mounted = true;
    adminService
      .listUsers({ search: debounced, role, workspaceId, offset, limit: PAGE_SIZE })
      .then((p) => {
        if (!mounted) return;
        setPage(p);
        setError(false);
        setChargePour(cle);
      })
      .catch(() => {
        if (!mounted) return;
        setError(true);
        setChargePour(cle);
      });
    return () => {
      mounted = false;
    };
  }, [debounced, role, workspaceId, offset, cle]);

  const total = page?.total ?? 0;
  const currentPage = Math.floor(offset / PAGE_SIZE) + 1;
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  const dialog = useMemo(() => buildDialog(pending), [pending]);

  async function confirmAction() {
    if (!pending) return;
    setActing(true);
    try {
      const u = pending.user;
      if (pending.type === 'reset') {
        const r = await adminService.resetUserPassword(u.workspaceId, u.id);
        if (r.emailDelivered) toast.success(`Nouveau mot de passe envoyé à ${u.contactEmail}.`);
        else toast.error(r.message);
      } else if (pending.type === 'suspend') {
        await adminService.suspendUser(u.id);
        toast.success(`Compte de ${u.firstName} ${u.lastName} suspendu.`);
        patchStatus(u.id, 'INACTIVE');
      } else {
        await adminService.reactivateUser(u.id);
        toast.success(`Compte de ${u.firstName} ${u.lastName} réactivé.`);
        patchStatus(u.id, 'ACTIVE');
      }
      setPending(null);
    } catch (e) {
      toast.error(`L’action a échoué : ${extractError(e).message}`);
    } finally {
      setActing(false);
    }
  }

  function patchStatus(userId: string, status: string) {
    setPage((prev) =>
      prev ? { ...prev, items: prev.items.map((it) => (it.id === userId ? { ...it, status } : it)) } : prev,
    );
  }

  return (
    <div className="space-y-5">
      <header>
        <h1 className="flex items-center gap-2 font-heading text-2xl font-semibold text-fg">
          <Users className="h-6 w-6 text-accent" /> Utilisateurs de la plateforme
        </h1>
        <p className="mt-1 text-sm text-fg-subtle">
          Les comptes de tous les cabinets : identité, rôle, cabinet et statut. Le contenu des dossiers
          n’est jamais visible ici.
        </p>
      </header>

      <TexteAide cle="admin-utilisateurs" titre="Que faire sur cet écran ?">
        <p>
          Retrouvez un compte par son nom, son adresse, son rôle ou son cabinet. Vous pouvez envoyer un
          nouveau mot de passe temporaire (l’utilisateur devra le changer à sa connexion), suspendre un
          compte (il ne peut plus se connecter) ou le réactiver. Les comptes de super-administrateur ne
          se gèrent pas ici.
        </p>
      </TexteAide>

      {/* Filtres */}
      <div className="flex flex-wrap items-center gap-3">
        <div className="relative min-w-[220px] flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-fg-subtle" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Rechercher un nom ou une adresse…"
            aria-label="Rechercher un utilisateur"
            className="w-full rounded-lg border border-border bg-bg-raised py-2 pl-9 pr-3 text-sm text-fg placeholder:text-fg-subtle focus:border-accent focus:outline-none"
          />
        </div>
        <select
          value={role}
          onChange={(e) => {
            setRole(e.target.value);
            setOffset(0);
          }}
          aria-label="Filtrer par rôle"
          className="rounded-lg border border-border bg-bg-raised px-3 py-2 text-sm text-fg focus:border-accent focus:outline-none"
        >
          <option value="">Tous les rôles</option>
          <option value="SUPERVISEUR">Superviseur</option>
          <option value="EMPLOYE">Employé</option>
          <option value="CLIENT">Client</option>
          <option value="SUPER_ADMIN">Super-administrateur</option>
        </select>
        <select
          value={workspaceId}
          onChange={(e) => {
            setWorkspaceId(e.target.value);
            setOffset(0);
          }}
          aria-label="Filtrer par cabinet"
          className="max-w-[220px] rounded-lg border border-border bg-bg-raised px-3 py-2 text-sm text-fg focus:border-accent focus:outline-none"
        >
          <option value="">Tous les cabinets</option>
          {workspaces.map((w) => (
            <option key={w.id} value={w.id}>
              {w.code} — {w.name}
            </option>
          ))}
        </select>
      </div>

      {error && !loading && (
        <Card className="p-8 text-center">
          <p className="font-medium text-fg">Chargement impossible</p>
          <p className="mt-1 text-sm text-fg-subtle">
            La liste des utilisateurs n’a pas pu être récupérée. Rechargez la page ; si le problème
            persiste, vérifiez l’état des services sur l’écran de supervision.
          </p>
        </Card>
      )}

      {loading && (
        <div className="space-y-2">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="h-14 animate-pulse rounded-lg border border-border bg-bg-raised" />
          ))}
        </div>
      )}

      {!error && !loading && page && page.items.length === 0 && (
        <Card className="p-10 text-center">
          <p className="text-sm text-fg-subtle">
            Aucun utilisateur ne correspond à ces filtres. Modifiez la recherche ou choisissez « Tous les
            cabinets ».
          </p>
        </Card>
      )}

      {!error && !loading && page && page.items.length > 0 && (
        <Card className="overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-border text-left text-xs uppercase tracking-wide text-fg-subtle">
                  <th className="px-4 py-3 font-medium">Utilisateur</th>
                  <th className="px-4 py-3 font-medium">Rôle</th>
                  <th className="px-4 py-3 font-medium">Cabinet</th>
                  <th className="px-4 py-3 font-medium">Statut</th>
                  <th className="px-4 py-3 font-medium">
                    <span className="inline-flex items-center gap-1">
                      Double authentification
                      <InfoBulle
                        libelle="Aide : double authentification"
                        texte="« Activée » : l’utilisateur confirme chaque connexion par un code à 6 chiffres. Un tiret indique qu’elle n’est pas encore configurée : elle lui sera demandée à sa prochaine connexion."
                      />
                    </span>
                  </th>
                  <th className="px-4 py-3 font-medium">Dernière connexion</th>
                  <th className="px-4 py-3 text-right font-medium">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {page.items.map((u) => (
                  <tr key={u.id} className="hover:bg-bg-overlay/50">
                    <td className="px-4 py-3">
                      <div className="font-medium text-fg">
                        {u.firstName} {u.lastName}
                      </div>
                      <div className="text-[11px] text-fg-subtle">{u.loginEmail}</div>
                    </td>
                    <td className="px-4 py-3">
                      <Badge variant={u.role === 'SUPER_ADMIN' ? 'default' : 'neutral'}>
                        {ROLE_LABELS[u.role] ?? u.role}
                      </Badge>
                    </td>
                    <td className="px-4 py-3">
                      <div className="font-mono text-[11px] text-fg-muted">{u.workspaceCode ?? '—'}</div>
                      {u.workspaceName && <div className="text-[11px] text-fg-subtle">{u.workspaceName}</div>}
                    </td>
                    <td className="px-4 py-3">
                      <UserStatusBadge status={u.status} />
                    </td>
                    <td className="px-4 py-3">
                      {u.totpEnabled ? (
                        <span className="inline-flex items-center gap-1 text-xs text-success">
                          <ShieldCheck className="h-3.5 w-3.5" /> Activé
                        </span>
                      ) : (
                        <span className="text-xs text-fg-subtle">—</span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-fg-subtle">
                      {u.lastLoginAt
                        ? new Date(u.lastLoginAt).toLocaleDateString('fr-FR', {
                            day: '2-digit',
                            month: '2-digit',
                            year: '2-digit',
                          })
                        : 'Jamais'}
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex justify-end gap-1">
                        {u.role !== 'SUPER_ADMIN' && (
                          <>
                            <button
                              type="button"
                              onClick={() => setPending({ type: 'reset', user: u })}
                              className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium text-accent transition hover:bg-accent/10"
                            >
                              <KeyRound className="h-3.5 w-3.5" /> Nouveau mot de passe
                            </button>
                            {u.status === 'INACTIVE' ? (
                              <button
                                type="button"
                                onClick={() => setPending({ type: 'reactivate', user: u })}
                                className="rounded-md px-2 py-1 text-xs font-medium text-success transition hover:bg-success/10"
                              >
                                Réactiver
                              </button>
                            ) : (
                              <button
                                type="button"
                                onClick={() => setPending({ type: 'suspend', user: u })}
                                className="rounded-md px-2 py-1 text-xs font-medium text-danger transition hover:bg-danger/10"
                              >
                                Suspendre
                              </button>
                            )}
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Pagination */}
          <div className="flex items-center justify-between border-t border-border px-4 py-3 text-xs text-fg-subtle">
            <span>
              {total} utilisateur{total > 1 ? 's' : ''} · page {currentPage}/{totalPages}
            </span>
            <div className="flex gap-2">
              <Button
                variant="secondary"
                size="sm"
                disabled={offset === 0}
                onClick={() => setOffset(Math.max(0, offset - PAGE_SIZE))}
              >
                Précédent
              </Button>
              <Button
                variant="secondary"
                size="sm"
                disabled={currentPage >= totalPages}
                onClick={() => setOffset(offset + PAGE_SIZE)}
              >
                Suivant
              </Button>
            </div>
          </div>
        </Card>
      )}

      <ConfirmDialog
        open={pending !== null}
        onOpenChange={(o) => !o && setPending(null)}
        title={dialog.title}
        description={dialog.description}
        confirmLabel={dialog.confirmLabel}
        variant={dialog.variant}
        loading={acting}
        onConfirm={() => void confirmAction()}
      />
    </div>
  );
}

function buildDialog(pending: PendingAction | null): {
  title: string;
  description?: string;
  confirmLabel: string;
  variant: 'primary' | 'danger';
} {
  if (!pending) return { title: '', confirmLabel: 'Confirmer', variant: 'primary' };
  const name = `${pending.user.firstName} ${pending.user.lastName}`;
  switch (pending.type) {
    case 'reset':
      return {
        title: 'Réinitialiser le mot de passe',
        description: `Un nouveau mot de passe temporaire sera généré et envoyé par courriel à ${name} (${pending.user.contactEmail}). Il devra le changer à sa prochaine connexion.`,
        confirmLabel: 'Envoyer',
        variant: 'primary',
      };
    case 'suspend':
      return {
        title: 'Suspendre le compte',
        description: `${name} ne pourra plus se connecter tant que le compte est suspendu. Continuer ?`,
        confirmLabel: 'Suspendre',
        variant: 'danger',
      };
    case 'reactivate':
      return {
        title: 'Réactiver le compte',
        description: `${name} pourra de nouveau se connecter.`,
        confirmLabel: 'Réactiver',
        variant: 'primary',
      };
  }
}

function UserStatusBadge({ status }: { status: string }) {
  switch (status) {
    case 'ACTIVE':
      return <Badge variant="success">Actif</Badge>;
    case 'INACTIVE':
      return <Badge variant="danger">Suspendu</Badge>;
    case 'PENDING':
      return <Badge variant="warning">En attente</Badge>;
    default:
      return <Badge variant="neutral">{status}</Badge>;
  }
}
