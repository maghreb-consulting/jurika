import { useEffect, useState } from 'react';
import { Activity, BarChart3, Building2, HardDrive, Layers, ScrollText, ServerCog, ShieldAlert, Users } from 'lucide-react';
import { Page } from '../../components/layout/Page';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeVariant } from '../../components/ui/Badge';
import { Lien } from '../../components/ui/Lien';
import { ErrorState } from '../../components/ui/ErrorState';
import { InfoBulle, TexteAide } from '../../components/ui/Aide';
import { CardSkeleton, SqueletteZone } from '../../components/ui/LoadingSkeleton';
import { KpiCard } from '../../components/dashboard/KpiCard';
import { decimal, longueur } from '../../components/dashboard/valeurs';
import { ChartCard } from '../../components/dashboard/charts/ChartCard';
import { ColonnesChart } from '../../components/dashboard/charts/ColonnesChart';
import { BarresHorizontales, type Barre } from '../../components/dashboard/charts/BarresHorizontales';
import { dashboardService } from '../../services/dashboard.service';
import type { SuperAdminDashboardDto } from '../../types/dashboard';
import { ENTITE_AUDIT_LABELS, FORFAIT_LABELS, SERVICE_LABELS, STATUT_WORKSPACE_LABELS, formatMonth, labelOf } from '../../lib/dashboardLabels';
import { libelleAction } from '../../lib/libelleAction';

/**
 * Console SUPER_ADMIN -- etat de la plateforme en un coup d'oeil, sur donnees
 * 100% reelles (GET /api/v1/dashboards/super-admin). Aucune valeur codee en
 * dur : si une donnee manque, on affiche "--" plutot que d'inventer.
 *
 * Lot L6b (E9) : chaque champ est lu avec garde (un instantane partiel plantait l'ecran,
 * "storage.totalBytes" sur undefined) ; composants de la bibliotheque.
 *
 * Lot L6b (E17) : codes bruts remplaces par des libelles (statut, type d'element, service) ;
 * anneaux sans chiffres remplaces par des barres etiquetees ; "Inscriptions (30 jours)" retire
 * (il comptait les comptes utilisateurs crees, en pics journaliers : le total sur 30 jours passe
 * en indication sous "Utilisateurs") ; nouveaux cabinets par mois en colonnes ; les evenements
 * critiques deviennent des alertes de securite, au ton de leur gravite.
 *
 * Le SUPER_ADMIN n'accede PAS aux tickets/dossiers/documents metier (RG-U07).
 */
export function SuperAdminDashboard() {
  const [data, setData] = useState<Partial<SuperAdminDashboardDto> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [essai, setEssai] = useState(0);

  useEffect(() => {
    let mounted = true;
    dashboardService
      .getSuperAdmin()
      .then((d) => mounted && setData(d ?? {}))
      .catch(() => mounted && setError(true))
      .finally(() => mounted && setLoading(false));
    return () => {
      mounted = false;
    };
  }, [essai]);

  return (
    <Page
      titre="Tableau de bord de la plateforme"
      description="Vue globale des cabinets clients : comptes, stockage, activité, sécurité et santé des services. Pas d’accès aux tickets ni aux dossiers des cabinets (RG-U07)."
      aide={
        <TexteAide cle="tableau-de-bord-super-admin" titre="Que suivre ici ?">
          <p>
            Regardez d’abord les alertes de sécurité des dernières 24 heures et la santé des services : un service
            hors ligne ou une série de connexions échouées demande une action. Les graphiques suivent ensuite la
            croissance (nouveaux cabinets par mois), la répartition par forfait et par statut, et les cabinets les
            plus actifs. Un indicateur « -- » signale une donnée que le serveur n’a pas rendue. La gestion des
            cabinets, des utilisateurs et le journal d’audit restent accessibles en bas de page, même si les
            indicateurs sont indisponibles.
          </p>
        </TexteAide>
      }
    >
      <div className="space-y-6">
        {loading && (
          <SqueletteZone libelle="Chargement des indicateurs de la plateforme…" className="space-y-4">
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
              {Array.from({ length: 4 }).map((_, i) => (
                <CardSkeleton key={i} className="h-[6.5rem]" />
              ))}
            </div>
            <div className="grid gap-4 lg:grid-cols-2">
              {Array.from({ length: 2 }).map((_, i) => (
                <CardSkeleton key={i} className="h-60" />
              ))}
            </div>
          </SqueletteZone>
        )}

        {!loading && error && (
          <ErrorState
            title="Tableau de bord indisponible"
            message="Le service du tableau de bord n’a pas répondu. Réessayez dans un instant ; la gestion des cabinets reste accessible ci-dessous."
            onRetry={() => {
              setLoading(true);
              setError(false);
              setEssai((n) => n + 1);
            }}
          />
        )}

        {!loading && !error && data && <DashboardContent data={data} />}

        {/* Cartes de gestion : toujours accessibles, meme si les KPIs sont KO. */}
        <ManagementCards />
      </div>
    </Page>
  );
}

/** Ton d'une alerte de securite selon son code (liste fixee par le serveur). */
const TON_ALERTE: Record<string, BadgeVariant> = {
  LOGIN_FAILED: 'danger',
  PASSWORD_CHANGED: 'warning',
  RECOVERY_CODES_REGENERATED: 'warning',
  EXERCICE_UNLOCKED: 'warning',
};

function pluriel(n: number, singulier: string, plurielTexte: string): string {
  return `${n} ${n > 1 ? plurielTexte : singulier}`;
}

function DashboardContent({ data }: { data: Partial<SuperAdminDashboardDto> }) {
  const parStatutBrut = data.workspacesParStatut;
  const compteStatut = (cle: string) => (parStatutBrut ?? []).find((c) => c.label === cle)?.count ?? 0;
  const evenements = data.criticalAuditEvents24h ?? [];
  const nbEvenements = longueur(data.criticalAuditEvents24h);
  const sante = Object.entries(data.servicesHealth ?? {});
  const enLigne = sante.filter(([, s]) => s === 'UP').length;
  const stockage = data.storage;
  const nouveauxUtilisateurs = Array.isArray(data.signups30d) ? data.signups30d.reduce((n, d) => n + (d.count ?? 0), 0) : null;

  const parForfait: Barre[] = (data.workspacesParForfait ?? []).map((c) => ({ cle: c.label, libelle: labelOf(FORFAIT_LABELS, c.label), valeur: c.count }));
  const parStatut: Barre[] = (parStatutBrut ?? []).map((c) => ({
    cle: c.label,
    libelle: labelOf(STATUT_WORKSPACE_LABELS, c.label),
    valeur: c.count,
    ton: c.label === 'ACTIVE' ? 'succes' : c.label === 'ESSAI' ? 'info' : c.label === 'SUSPENDED' ? 'attention' : 'neutre',
  }));
  const inscriptions = (data.signupsMensuels ?? []).map((m) => ({ mois: formatMonth(m.month), cabinets: m.count }));
  const actifs: Barre[] = (data.topWorkspacesByUsage ?? []).map((w) => ({
    cle: w.workspaceId,
    libelle: w.name,
    valeur: w.eventsLast7d,
    lien: `/admin/workspaces/${w.workspaceId}`,
  }));

  const indiceCabinets = parStatutBrut
    ? `${pluriel(compteStatut('ACTIVE'), 'actif', 'actifs')} · ${compteStatut('ESSAI')} en essai`
    : typeof data.activeWorkspaces30d === 'number'
      ? `${data.activeWorkspaces30d} avec une connexion sur 30 jours`
      : undefined;
  const nbFichiers = stockage?.fileCount;

  return (
    <>
      {/* Indicateurs de la plateforme -- toutes valeurs reelles issues du DTO. */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <KpiCard
          label="Cabinets"
          value={data.totalWorkspaces}
          hint={indiceCabinets}
          icon={<Building2 />}
          to="/admin/workspaces"
          aide="Tous les cabinets inscrits, quel que soit leur statut. « En essai » : essai gratuit en cours."
        />
        <KpiCard
          label="Utilisateurs"
          value={data.totalUsers}
          hint={nouveauxUtilisateurs !== null ? `${nouveauxUtilisateurs} créé${nouveauxUtilisateurs > 1 ? 's' : ''} sur 30 jours` : undefined}
          icon={<Users />}
          to="/admin/users"
          aide="Comptes de tous les cabinets, tous rôles confondus (superviseurs, employés, clients)."
        />
        <KpiCard
          label="Stockage utilisé"
          value={typeof stockage?.totalBytes === 'number' ? `${decimal(stockage.totalBytes / (1024 * 1024 * 1024), 2)} Go` : null}
          hint={typeof nbFichiers === 'number' ? pluriel(nbFichiers, 'document', 'documents') : undefined}
          icon={<HardDrive />}
          aide="Documents courants des Data Rooms et dépôts des clients, tous cabinets confondus."
        />
        <KpiCard
          label="Alertes de sécurité (24 h)"
          value={nbEvenements}
          hint="connexions échouées, mots de passe, codes de secours"
          icon={<ShieldAlert />}
          variant={nbEvenements ? 'danger' : 'default'}
          to="/admin/audit"
          aide="Connexions échouées, changements de mot de passe, régénérations des codes de secours et déverrouillages des dernières 24 heures."
        />
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <ChartCard
          title="Nouveaux cabinets par mois"
          icon={<BarChart3 />}
          empty={inscriptions.length === 0 || inscriptions.every((m) => m.cabinets === 0)}
          emptyLabel="Aucun cabinet inscrit sur les douze derniers mois."
        >
          <ColonnesChart
            donnees={inscriptions}
            cleX="mois"
            legende="Nouveaux cabinets par mois, douze derniers mois"
            series={[{ cle: 'cabinets', nom: 'Nouveaux cabinets', ton: 'info' }]}
          />
        </ChartCard>
        <ChartCard title="Répartition des cabinets" icon={<Layers />} empty={parForfait.length === 0 && parStatut.length === 0}>
          <div className="grid gap-6 sm:grid-cols-2">
            <div>
              <h4 className="mb-3 text-xs font-semibold uppercase tracking-wide text-fg-muted">Par forfait</h4>
              {parForfait.length === 0 ? (
                <p className="text-sm text-fg-muted">Non rendue par le serveur.</p>
              ) : (
                <BarresHorizontales barres={parForfait} legende="Cabinets par forfait" ton="accent" part />
              )}
            </div>
            <div>
              <h4 className="mb-3 text-xs font-semibold uppercase tracking-wide text-fg-muted">Par statut</h4>
              {parStatut.length === 0 ? (
                <p className="text-sm text-fg-muted">Non rendue par le serveur.</p>
              ) : (
                <BarresHorizontales barres={parStatut} legende="Cabinets par statut" part />
              )}
            </div>
          </div>
        </ChartCard>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <ChartCard
          title="Cabinets les plus actifs (7 jours)"
          icon={<Activity />}
          empty={actifs.length === 0}
          emptyLabel="Aucune action tracée sur les sept derniers jours."
          actions={
            <InfoBulle
              libelle="Aide : cabinets les plus actifs"
              texte="Nombre d’actions tracées au journal d’audit par cabinet sur les sept derniers jours (connexions, tickets, documents…)."
            />
          }
        >
          <BarresHorizontales barres={actifs} legende="Actions tracées par cabinet sur sept jours" ton="accent" />
        </ChartCard>

        <ChartCard
          title="Santé des services"
          icon={<ServerCog />}
          empty={sante.length === 0}
          emptyLabel="État des services non rendu par le serveur."
          actions={
            sante.length > 0 ? (
              <Badge variant={enLigne === sante.length ? 'success' : 'danger'}>
                {enLigne} sur {sante.length} en ligne
              </Badge>
            ) : undefined
          }
        >
          <ul className="divide-y divide-border">
            {sante.map(([svc, status]) => {
              const s = santeService(status);
              return (
                <li key={svc} className="flex items-center justify-between gap-2 py-2 text-sm">
                  <span className="min-w-0">
                    <span className="block truncate font-medium text-fg">{labelOf(SERVICE_LABELS, svc)}</span>
                    <span className="block font-mono text-xs text-fg-subtle">{svc}</span>
                  </span>
                  <Badge variant={s.ton} dot>
                    {s.libelle}
                  </Badge>
                </li>
              );
            })}
          </ul>
        </ChartCard>
      </div>

      <ChartCard
        title="Alertes de sécurité (24 h)"
        icon={<ShieldAlert />}
        empty={evenements.length === 0}
        emptyLabel={nbEvenements === null ? 'Liste non rendue par le serveur.' : 'Aucune alerte de sécurité sur les dernières 24 heures.'}
        actions={<Lien to="/admin/audit">Ouvrir le journal d’audit</Lien>}
      >
        <ul className="divide-y divide-border">
          {evenements.slice(0, 12).map((e) => (
            <li key={e.id} className="flex flex-wrap items-center gap-3 py-2 text-sm">
              <Badge variant={TON_ALERTE[e.action] ?? 'neutral'}>{libelleAction(e.actionLabel, e.action)}</Badge>
              {e.entityType && <span className="text-fg-muted">{labelOf(ENTITE_AUDIT_LABELS, e.entityType.toLowerCase())}</span>}
              <span className="ml-auto text-xs tabular-nums text-fg-subtle">
                {new Date(e.createdAt).toLocaleString('fr-FR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })}
              </span>
            </li>
          ))}
        </ul>
      </ChartCard>
    </>
  );
}

function ManagementCards() {
  const cards = [
    { icon: Building2, title: 'Cabinets', desc: 'Gérer les cabinets : activer, suspendre, désactiver.', to: '/admin/workspaces', cta: 'Voir les cabinets' },
    {
      icon: Users,
      title: 'Utilisateurs',
      desc: 'Les utilisateurs de tous les cabinets : réinitialisation du mot de passe, suspension.',
      to: '/admin/users',
      cta: 'Voir les utilisateurs',
    },
    { icon: ScrollText, title: 'Journal d’audit', desc: 'Toutes les actions de la plateforme (connexions, transferts).', to: '/admin/audit', cta: 'Consulter l’audit' },
  ];
  return (
    <section aria-labelledby="titre-gestion">
      <h2 id="titre-gestion" className="mb-3 text-lg font-semibold text-heading">
        Gestion de la plateforme
      </h2>
      <div className="grid gap-4 lg:grid-cols-3">
        {cards.map((c) => (
          <Card key={c.to} className="flex flex-col p-5">
            <span aria-hidden="true" className="mb-3 flex h-10 w-10 items-center justify-center rounded-lg bg-bg-overlay text-fg-muted">
              <c.icon className="h-5 w-5" />
            </span>
            <h3 className="font-semibold text-heading">{c.title}</h3>
            <p className="mb-4 mt-1 flex-1 text-sm text-fg-muted">{c.desc}</p>
            <Lien to={c.to} commeBouton={{ variant: 'secondary' }} className="w-full">
              {c.cta}
            </Lien>
          </Card>
        ))}
      </div>
    </section>
  );
}

function santeService(status: string): { libelle: string; ton: BadgeVariant } {
  switch (status) {
    case 'UP':
      return { libelle: 'En ligne', ton: 'success' };
    case 'DEGRADED':
      return { libelle: 'Dégradé', ton: 'warning' };
    case 'DOWN':
      return { libelle: 'Hors ligne', ton: 'danger' };
    default:
      return { libelle: 'Inconnu', ton: 'neutral' };
  }
}
