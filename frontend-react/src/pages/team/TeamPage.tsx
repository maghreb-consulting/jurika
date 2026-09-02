import { useEffect, useMemo, useState } from 'react';
import { Loader2, Power, RefreshCw, UserPlus, Users } from 'lucide-react';
import { Button } from '../../components/ui/Button';
import { authService } from '../../services/auth.service';
import { billingService } from '../../services/billing.service';
import { extractError } from '../../lib/api';
import { useAuthStore } from '../../store/authStore';
import type { WorkspaceUser, UserStatus } from '../../types/auth';
import type { UsageSnapshot } from '../../types/billing';
import { InviteEmployeeDrawer } from './InviteEmployeeDrawer';
import { Link } from 'react-router-dom';

/**
 * BUG 6 (2026-06-07) — Page Equipe (superviseur uniquement). Permet :
 *  - de voir tous les membres internes (EMPLOYE + SUPERVISEUR) avec leur statut
 *    (En attente / Actif / Desactive) + derniere connexion ;
 *  - d'inviter un nouvel employe (formulaire prenom/nom/email/telephone/role) ;
 *  - d'activer / desactiver un compte (refus du self pour anti-lockout) ;
 *  - de visualiser le quota EMPLOYE du plan en haut + d'upseller vers /app/billing
 *    quand on atteint la limite.
 *
 * Les CLIENT ne sont PAS listes ici : ils se gerent depuis la Data Room
 * (DELETE /auth/dossiers/{id}/client). Voir memoire fix-workflows-dataroom-2026-06-07.
 */
export function TeamPage() {
  const me = useAuthStore((s) => s.user);
  const [users, setUsers] = useState<WorkspaceUser[] | null>(null);
  const [usage, setUsage] = useState<UsageSnapshot | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actingOn, setActingOn] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);

  async function reload() {
    setError(null);
    try {
      const [u, usageSnap] = await Promise.all([
        authService.listWorkspaceUsers(),
        billingService.getUsage(),
      ]);
      setUsers(u);
      setUsage(usageSnap);
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    reload();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function handleToggle(target: WorkspaceUser) {
    if (target.userId === me?.userId) return; // garde-fou UI (le back refuse 403)
    setActingOn(target.userId);
    setActionError(null);
    try {
      const nextActive = target.status === 'INACTIVE';
      await authService.setUserActive(target.userId, nextActive);
      await reload();
    } catch (err) {
      setActionError(extractError(err).message);
    } finally {
      setActingOn(null);
    }
  }

  // Compteur EMPLOYE actifs+pending (cohérent avec le back qui exclut INACTIVE).
  const employeUsage = useMemo(() => {
    if (!users) return { active: 0 };
    const active = users.filter(
      (u) => u.role === 'EMPLOYE' && u.status !== 'INACTIVE',
    ).length;
    return { active };
  }, [users]);

  // Session 5 (2026-06-08) — 1 seul SUPERVISEUR par cabinet (PENDING ou
  // ACTIVE consomme le siege ; INACTIVE le libere). Sert a desactiver le
  // choix SUPERVISEUR dans le drawer d'invitation.
  const supervisorTaken = useMemo(() => {
    if (!users) return false;
    return users.some(
      (u) => u.role === 'SUPERVISEUR' && u.status !== 'INACTIVE',
    );
  }, [users]);

  const max = usage?.maxUsers ?? null;
  const unlimited = usage?.usersUnlimited === true;
  const quotaFull = !unlimited && max !== null && employeUsage.active >= max;

  if (loading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-accent" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="flex items-center gap-2 font-heading text-2xl font-semibold text-fg">
            <Users className="h-6 w-6 text-accent" />
            Equipe
          </h1>
          <p className="mt-1 text-sm text-fg-muted">
            Gerez les membres internes de votre cabinet : inviter, activer, desactiver.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="secondary" onClick={reload}>
            <RefreshCw className="mr-1 h-3.5 w-3.5" /> Actualiser
          </Button>
          <Button
            onClick={() => setDrawerOpen(true)}
            disabled={quotaFull}
            title={quotaFull ? 'Quota EMPLOYE atteint — upgradez le plan' : undefined}
          >
            <UserPlus className="mr-1 h-3.5 w-3.5" /> Inviter un membre
          </Button>
        </div>
      </div>

      {/* Quota indicator */}
      {usage && (
        <div className="flex flex-col gap-3 rounded-2xl border border-border bg-bg-raised p-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="text-xs uppercase tracking-wide text-fg-subtle">
              Plan {usage.planLabel}
            </p>
            <p className="mt-1 text-lg font-semibold text-fg">
              {employeUsage.active}
              <span className="text-fg-subtle"> / {unlimited ? '∞' : max} </span>
              <span className="text-sm font-normal text-fg-muted">
                employes actifs (SUPERVISEUR hors quota)
              </span>
            </p>
          </div>
          {quotaFull && (
            <Link
              to="/app/billing"
              className="rounded-lg bg-accent px-3 py-2 text-sm font-semibold text-bg hover:bg-accent-hover"
            >
              Quota atteint — Upgrader le plan
            </Link>
          )}
        </div>
      )}

      {(error || actionError) && (
        <div className="rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
          {actionError || error}
        </div>
      )}

      {/* Members table */}
      <div className="overflow-hidden rounded-2xl border border-border bg-bg-raised">
        <table className="min-w-full divide-y divide-border">
          <thead className="bg-bg-overlay">
            <tr>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-fg-subtle">
                Membre
              </th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-fg-subtle">
                Role
              </th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-fg-subtle">
                Statut
              </th>
              <th className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-fg-subtle">
                Derniere connexion
              </th>
              <th className="px-4 py-3 text-right text-xs font-medium uppercase tracking-wider text-fg-subtle">
                Action
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {(users ?? []).length === 0 ? (
              <tr>
                <td colSpan={5} className="px-4 py-12 text-center text-sm text-fg-muted">
                  Aucun membre — invitez votre premier employe.
                </td>
              </tr>
            ) : (
              (users ?? []).map((u) => {
                const isSelf = u.userId === me?.userId;
                const isInactive = u.status === 'INACTIVE';
                return (
                  <tr key={u.userId} className="hover:bg-bg-overlay/50">
                    <td className="px-4 py-3">
                      <div className="text-sm font-medium text-fg">
                        {u.firstName} {u.lastName}
                        {isSelf && (
                          <span className="ml-2 rounded bg-accent/10 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-accent">
                            Vous
                          </span>
                        )}
                      </div>
                      <div className="text-xs text-fg-muted">{u.email}</div>
                    </td>
                    <td className="px-4 py-3 text-sm text-fg-subtle">
                      {u.role === 'SUPERVISEUR' ? 'Superviseur' : 'Employe'}
                    </td>
                    <td className="px-4 py-3">
                      <StatusBadge status={u.status} />
                    </td>
                    <td className="px-4 py-3 text-xs text-fg-muted">
                      {u.lastLoginAt
                        ? new Date(u.lastLoginAt).toLocaleString('fr-FR')
                        : 'Jamais'}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <Button
                        size="sm"
                        variant={isInactive ? 'primary' : 'secondary'}
                        disabled={isSelf || actingOn === u.userId}
                        loading={actingOn === u.userId}
                        onClick={() => handleToggle(u)}
                        title={
                          isSelf
                            ? 'Vous ne pouvez pas modifier votre propre statut.'
                            : undefined
                        }
                      >
                        <Power className="mr-1 h-3.5 w-3.5" />
                        {isInactive ? 'Reactiver' : 'Desactiver'}
                      </Button>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      <InviteEmployeeDrawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        onInvited={reload}
        supervisorTaken={supervisorTaken}
      />
    </div>
  );
}

function StatusBadge({ status }: { status: UserStatus }) {
  const config: Record<UserStatus, { label: string; cls: string }> = {
    PENDING: {
      label: 'En attente',
      cls: 'border-amber-300 bg-warning/10 text-amber-800',
    },
    ACTIVE: {
      label: 'Actif',
      cls: 'border-emerald-300 bg-emerald-50 text-emerald-700',
    },
    INACTIVE: {
      label: 'Desactive',
      cls: 'border-border bg-bg-overlay text-fg-subtle',
    },
  };
  const c = config[status];
  return (
    <span
      className={`inline-flex items-center rounded-full border px-2 py-0.5 text-[11px] font-semibold ${c.cls}`}
    >
      {c.label}
    </span>
  );
}
