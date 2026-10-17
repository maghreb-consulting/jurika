import { useCallback, useEffect, useMemo, useState } from 'react';
import { Power, RefreshCw, Trash2, UserPlus, Users } from 'lucide-react';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { EmptyState } from '../../components/ui/EmptyState';
import { Lien } from '../../components/ui/Lien';
import { Skeleton } from '../../components/ui/LoadingSkeleton';
import { Message } from '../../components/ui/Message';
import { Tableau, type ColonneTableau } from '../../components/ui/Tableau';
import { EcranOuOnglet } from '../settings/EcranOuOnglet';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import { authService } from '../../services/auth.service';
import { billingService } from '../../services/billing.service';
import { extractError } from '../../lib/api';
import { useAuthStore } from '../../store/authStore';
import type { WorkspaceUser, UserStatus } from '../../types/auth';
import type { UsageSnapshot } from '../../types/billing';
import { InviteEmployeeDrawer } from './InviteEmployeeDrawer';

/**
 * BUG 6 (2026-06-07) — Page Equipe (superviseur uniquement). Permet :
 *  - de voir tous les membres internes (EMPLOYE + SUPERVISEUR) avec leur statut
 *    (En attente / Actif / Desactive) + derniere connexion ;
 *  - d'inviter un nouvel employe (formulaire prenom/nom/email/telephone/role) ;
 *  - d'activer / desactiver un compte (refus du self pour anti-lockout) ;
 *  - de visualiser le quota EMPLOYE du plan en haut + d'upseller vers /app/billing
 *    quand on atteint la limite.
 *
 * Lot L1 (CDC 3.2, RG-DR-06) : le superviseur y accorde ou retire a chaque employe le
 * droit de suppression en Data Room ; l'etat de chacun est affiche.
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
  // Lot L1 : employes qui ont le droit de suppression en Data Room.
  const [droitsSuppression, setDroitsSuppression] = useState<Set<string>>(new Set());

  // Recuperation seule : les mises a jour d'etat suivent la reponse (appelee au montage).
  const recuperer = useCallback(
    () =>
      Promise.all([
        authService.listWorkspaceUsers(),
        billingService.getUsage(),
        authService.listDroitsSuppressionDataroom(),
      ])
        .then(([u, usageSnap, droits]) => {
          setUsers(u);
          setUsage(usageSnap);
          setDroitsSuppression(new Set(droits));
          setError(null);
        })
        .catch((err) => setError(extractError(err).message))
        .finally(() => setLoading(false)),
    [],
  );

  useEffect(() => {
    void recuperer();
  }, [recuperer]);

  async function reload() {
    setError(null);
    await recuperer();
  }

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

  async function handleDroitSuppression(target: WorkspaceUser, accorde: boolean) {
    setActingOn(`droit-${target.userId}`);
    setActionError(null);
    try {
      await authService.setDroitSuppressionDataroom(target.userId, accorde);
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

  const colonnes: ColonneTableau<WorkspaceUser>[] = [
    {
      cle: 'membre',
      titre: 'Membre',
      rendu: (u) => (
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2 text-sm font-medium text-fg">
            {u.firstName} {u.lastName}
            {u.userId === me?.userId && <Badge variant="default">Vous</Badge>}
          </div>
          <div className="break-all text-xs text-fg-muted">{u.email}</div>
        </div>
      ),
    },
    {
      cle: 'role',
      titre: 'Rôle',
      rendu: (u) => <span className="text-sm text-fg-muted">{u.role === 'SUPERVISEUR' ? 'Superviseur' : 'Employé'}</span>,
      masquerSous: 'md',
    },
    { cle: 'statut', titre: 'Statut', rendu: (u) => <StatusBadge status={u.status} /> },
    {
      cle: 'connexion',
      titre: 'Dernière connexion',
      rendu: (u) => (
        <span className="text-xs text-fg-muted">
          {u.lastLoginAt ? new Date(u.lastLoginAt).toLocaleString('fr-FR') : 'Jamais'}
        </span>
      ),
      masquerSous: 'lg',
    },
    {
      cle: 'suppression',
      titre: (
        <span className="inline-flex items-center gap-1 normal-case">
          Suppression en Data Room
          <InfoBulle
            libelle="Que permet le droit de suppression ?"
            texte="L’employé qui a ce droit peut supprimer des documents dans la Data Room des dossiers dont il est responsable. Sans ce droit, il peut seulement en ajouter. Le superviseur l’accorde ou le retire à tout moment."
          />
        </span>
      ),
      rendu: (u) => {
        if (u.role !== 'EMPLOYE') {
          return (
            <span className="text-xs text-fg-muted" title="Le superviseur ne supprime pas de documents.">
              Sans objet
            </span>
          );
        }
        const aLeDroit = droitsSuppression.has(u.userId);
        return (
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant={aLeDroit ? 'success' : 'neutral'}>{aLeDroit ? 'Accordé' : 'Non accordé'}</Badge>
            <Button
              type="button"
              size="sm"
              variant="secondary"
              loading={actingOn === `droit-${u.userId}`}
              onClick={() => handleDroitSuppression(u, !aLeDroit)}
              aria-label={`${aLeDroit ? 'Retirer' : 'Accorder'} le droit de suppression à ${u.firstName} ${u.lastName}`}
              icon={<Trash2 />}
            >
              {aLeDroit ? 'Retirer' : 'Accorder'}
            </Button>
          </div>
        );
      },
    },
    {
      cle: 'action',
      titre: 'Action',
      alignement: 'droite',
      rendu: (u) => {
        const isSelf = u.userId === me?.userId;
        const isInactive = u.status === 'INACTIVE';
        return (
          <Button
            type="button"
            size="sm"
            variant="secondary"
            disabled={isSelf}
            loading={actingOn === u.userId}
            onClick={() => handleToggle(u)}
            title={isSelf ? 'Vous ne pouvez pas modifier votre propre statut.' : undefined}
            icon={<Power />}
          >
            {isInactive ? 'Réactiver' : 'Désactiver'}
          </Button>
        );
      },
    },
  ];

  return (
    <EcranOuOnglet
      titre="Équipe"
      description="Gérez les membres internes de votre cabinet : inviter, activer, désactiver, et accorder le droit de supprimer des documents en Data Room."
      actions={
        <Button type="button" variant="secondary" onClick={reload} icon={<RefreshCw />} disabled={loading}>
          Actualiser
        </Button>
      }
      action={
        <Button
          type="button"
          onClick={() => setDrawerOpen(true)}
          disabled={quotaFull || loading}
          title={quotaFull ? 'Quota d’employés atteint : passez à un forfait supérieur.' : undefined}
          icon={<UserPlus />}
        >
          Inviter un membre
        </Button>
      }
      aide={
        <div className="space-y-3">
          <TexteAide cle="equipe-invitation" titre="Inviter et gérer les membres">
            <p>
              Cliquez sur « Inviter un membre » : il reçoit par courriel ses identifiants et un mot de
              passe temporaire, et apparaît « En attente » jusqu’à sa première connexion. « Désactiver »
              coupe son accès sans rien effacer ; « Réactiver » le lui rend. Les clients ne figurent pas
              ici : ils se gèrent depuis la Data Room de leur dossier.
            </p>
          </TexteAide>
          <TexteAide cle="equipe-droit-suppression" titre="Le droit de supprimer des documents">
            <p>
              Par défaut, un employé ajoute des documents dans la Data Room de ses dossiers mais ne peut
              pas en supprimer. Cliquez sur « Accorder » pour lui donner ce droit, ou sur « Retirer » pour
              le lui reprendre : le changement vaut aussitôt pour tous ses dossiers et il est tracé. Un
              document supprimé reste en archive.
            </p>
          </TexteAide>
        </div>
      }
    >
      <div className="space-y-4">
        {/* Quota du forfait (emplacement reserve pendant le chargement : aucun decalage). */}
        {loading ? (
          <Skeleton className="h-[4.5rem] w-full rounded-xl" />
        ) : (
          usage && (
            <Card padding={false}>
              <div className="flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <p className="text-xs uppercase tracking-wide text-fg-muted">
                    {/* Recette R1, RG-ESS-07 : pendant l'essai, le libelle de l'essai seul. */}
                    {usage.essai ? usage.planLabel : `Forfait ${usage.planLabel}`}
                  </p>
                  <p className="mt-1 text-lg font-semibold text-fg">
                    {employeUsage.active}
                    <span className="text-fg-muted"> / {unlimited ? '∞' : max} </span>
                    <span className="text-sm font-normal text-fg-muted">employés actifs (superviseur hors quota)</span>
                  </p>
                </div>
                {quotaFull && (
                  <Lien to="/app/billing/change-plan" commeBouton={{ variant: 'secondary', size: 'sm' }}>
                    Quota atteint : changer de forfait
                  </Lien>
                )}
              </div>
            </Card>
          )
        )}

        {(error || actionError) && <Message ton="erreur">{actionError || error}</Message>}

        <Tableau
          legende="Membres internes du cabinet"
          colonnes={colonnes}
          lignes={users ?? []}
          cleLigne={(u) => u.userId}
          chargement={loading}
          lignesChargement={3}
          enTeteCollant={false}
          vide={
            <EmptyState
              icon={<Users />}
              title="Aucun membre pour le moment"
              description="Invitez votre premier employé avec « Inviter un membre » : il reçoit ses identifiants par courriel."
              compact
            />
          }
        />
      </div>

      <InviteEmployeeDrawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        onInvited={reload}
        supervisorTaken={supervisorTaken}
      />
    </EcranOuOnglet>
  );
}

function StatusBadge({ status }: { status: UserStatus }) {
  const config: Record<UserStatus, { label: string; variant: 'warning' | 'success' | 'neutral' }> = {
    PENDING: { label: 'En attente', variant: 'warning' },
    ACTIVE: { label: 'Actif', variant: 'success' },
    INACTIVE: { label: 'Désactivé', variant: 'neutral' },
  };
  const c = config[status];
  return (
    <Badge variant={c.variant} dot>
      {c.label}
    </Badge>
  );
}
