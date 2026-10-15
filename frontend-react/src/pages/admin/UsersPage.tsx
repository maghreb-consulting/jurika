import { useEffect, useMemo, useState } from 'react';
import { KeyRound, Search, ShieldCheck, Users } from 'lucide-react';
import { Page } from '../../components/layout/Page';
import { TextField } from '../../components/ui/TextField';
import { Select } from '../../components/ui/Select';
import { EmptyState } from '../../components/ui/EmptyState';
import { ErrorState } from '../../components/ui/ErrorState';
import { Tableau, type ColonneTableau } from '../../components/ui/Tableau';
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

  const colonnes: ColonneTableau<AdminUserRow>[] = [
    {
      cle: 'utilisateur',
      titre: 'Utilisateur',
      rendu: (u) => (
        <>
          <div className="font-medium text-fg">
            {u.firstName} {u.lastName}
          </div>
          <div className="text-xs text-fg-muted">{u.loginEmail}</div>
        </>
      ),
    },
    {
      cle: 'role',
      titre: 'Rôle',
      rendu: (u) => <Badge variant={u.role === 'SUPER_ADMIN' ? 'info' : 'neutral'}>{ROLE_LABELS[u.role] ?? u.role}</Badge>,
    },
    {
      cle: 'cabinet',
      titre: 'Cabinet',
      masquerSous: 'md',
      rendu: (u) => (
        <>
          <div className="font-mono text-xs text-fg-muted">{u.workspaceCode ?? '--'}</div>
          {u.workspaceName && <div className="text-xs text-fg-subtle">{u.workspaceName}</div>}
        </>
      ),
    },
    { cle: 'statut', titre: 'Statut', rendu: (u) => <UserStatusBadge status={u.status} /> },
    {
      cle: 'totp',
      titre: (
        <span className="inline-flex items-center gap-1">
          Double authentification
          <InfoBulle
            libelle="Aide : double authentification"
            texte="« Activée » : l’utilisateur confirme chaque connexion par un code à 6 chiffres. Un tiret indique qu’elle n’est pas encore configurée : elle lui sera demandée à sa prochaine connexion."
          />
        </span>
      ),
      masquerSous: 'lg',
      rendu: (u) =>
        u.totpEnabled ? (
          <span className="inline-flex items-center gap-1 text-xs text-success">
            <ShieldCheck className="h-3.5 w-3.5" aria-hidden="true" /> Activé
          </span>
        ) : (
          <span className="text-xs text-fg-subtle">--</span>
        ),
    },
    {
      cle: 'connexion',
      titre: 'Dernière connexion',
      masquerSous: 'lg',
      rendu: (u) => (
        <span className="tabular-nums text-fg-muted">
          {u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit', year: '2-digit' }) : 'Jamais'}
        </span>
      ),
    },
    {
      cle: 'actions',
      titre: 'Actions',
      alignement: 'droite',
      rendu: (u) =>
        u.role !== 'SUPER_ADMIN' ? (
          <div className="flex justify-end gap-1">
            <Button type="button" variant="ghost" size="sm" icon={<KeyRound />} onClick={() => setPending({ type: 'reset', user: u })}>
              Nouveau mot de passe
            </Button>
            {u.status === 'INACTIVE' ? (
              <Button type="button" variant="secondary" size="sm" onClick={() => setPending({ type: 'reactivate', user: u })}>
                Réactiver
              </Button>
            ) : (
              <Button type="button" variant="danger" size="sm" onClick={() => setPending({ type: 'suspend', user: u })}>
                Suspendre
              </Button>
            )}
          </div>
        ) : null,
    },
  ];

  return (
    <Page
      titre="Utilisateurs de la plateforme"
      description="Les comptes de tous les cabinets : identité, rôle, cabinet et statut. Le contenu des dossiers n’est jamais visible ici."
      aide={
        <TexteAide cle="admin-utilisateurs" titre="Que faire sur cet écran ?">
          <p>
            Retrouvez un compte par son nom, son adresse, son rôle ou son cabinet. Vous pouvez envoyer un
            nouveau mot de passe temporaire (l’utilisateur devra le changer à sa connexion), suspendre un
            compte (il ne peut plus se connecter) ou le réactiver. Les comptes de super-administrateur ne
            se gèrent pas ici.
          </p>
        </TexteAide>
      }
    >
      <div className="space-y-5">
        {/* Filtres */}
        <div className="grid gap-3 sm:grid-cols-[1fr_auto_auto]">
          <div className="relative min-w-0">
            <Search className="pointer-events-none absolute left-3 top-2.5 z-10 h-4 w-4 text-fg-subtle" aria-hidden="true" />
            <TextField
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Rechercher un nom ou une adresse…"
              aria-label="Rechercher un utilisateur"
              className="pl-9"
            />
          </div>
          <Select
            value={role}
            onChange={(e) => {
              setRole(e.target.value);
              setOffset(0);
            }}
            aria-label="Filtrer par rôle"
            options={[
              { value: '', label: 'Tous les rôles' },
              { value: 'SUPERVISEUR', label: 'Superviseur' },
              { value: 'EMPLOYE', label: 'Employé' },
              { value: 'CLIENT', label: 'Client' },
              { value: 'SUPER_ADMIN', label: 'Super-administrateur' },
            ]}
          />
          <Select
            value={workspaceId}
            onChange={(e) => {
              setWorkspaceId(e.target.value);
              setOffset(0);
            }}
            aria-label="Filtrer par cabinet"
            containerClassName="sm:max-w-[16rem]"
            options={[{ value: '', label: 'Tous les cabinets' }, ...workspaces.map((w) => ({ value: w.id, label: `${w.code} — ${w.name}` }))]}
          />
        </div>

        {error && !loading ? (
          <ErrorState
            title="Chargement impossible"
            message="La liste des utilisateurs n’a pas pu être récupérée. Rechargez la page ; si le problème persiste, vérifiez l’état des services sur le tableau de bord de la plateforme."
          />
        ) : (
          <Tableau
            legende="Utilisateurs de la plateforme"
            colonnes={colonnes}
            lignes={page?.items ?? []}
            cleLigne={(u) => u.id}
            chargement={loading}
            lignesChargement={6}
            enTeteCollant={false}
            vide={
              <EmptyState
                compact
                icon={<Users />}
                title="Aucun utilisateur"
                description="Aucun utilisateur ne correspond à ces filtres. Modifiez la recherche ou choisissez « Tous les cabinets »."
              />
            }
          />
        )}

        {!error && !loading && page && page.items.length > 0 && (
          <div className="flex flex-wrap items-center justify-between gap-2 text-sm text-fg-muted">
            <span>
              {total} utilisateur{total > 1 ? 's' : ''} · page {currentPage}/{totalPages}
            </span>
            <div className="flex gap-2">
              <Button type="button" variant="secondary" size="sm" disabled={offset === 0} onClick={() => setOffset(Math.max(0, offset - PAGE_SIZE))}>
                Précédent
              </Button>
              <Button type="button" variant="secondary" size="sm" disabled={currentPage >= totalPages} onClick={() => setOffset(offset + PAGE_SIZE)}>
                Suivant
              </Button>
            </div>
          </div>
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
    </Page>
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
