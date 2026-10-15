import { useEffect, useState } from 'react';
import {
  Activity,
  Building2,
  Database,
  HardDrive,
  Layers,
  LineChart as LineChartIcon,
  PieChart as PieChartIcon,
  ScrollText,
  ServerCog,
  TrendingUp,
  Users,
} from 'lucide-react';
import { Page } from '../../components/layout/Page';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeVariant } from '../../components/ui/Badge';
import { Lien } from '../../components/ui/Lien';
import { ErrorState } from '../../components/ui/ErrorState';
import { TexteAide } from '../../components/ui/Aide';
import { CardSkeleton, SqueletteZone } from '../../components/ui/LoadingSkeleton';
import { KpiCard } from '../../components/dashboard/KpiCard';
import { decimal, longueur } from '../../components/dashboard/valeurs';
import { ChartCard } from '../../components/dashboard/charts/ChartCard';
import { DonutChart } from '../../components/dashboard/charts/DonutChart';
import { TrendLineChart } from '../../components/dashboard/charts/TrendLineChart';
import { DistributionChart } from '../../components/dashboard/DistributionChart';
import { EvolutionChart } from '../../components/dashboard/EvolutionChart';
import { dashboardService } from '../../services/dashboard.service';
import type { SuperAdminDashboardDto } from '../../types/dashboard';
import { chartTheme } from '../../lib/chartTheme';
import { FORFAIT_LABELS, STATUT_WORKSPACE_LABELS, labelOf, formatMonth } from '../../lib/dashboardLabels';

/**
 * Console SUPER_ADMIN -- etat de la plateforme en un coup d'oeil, sur donnees
 * 100% reelles (GET /api/v1/dashboards/super-admin). Aucune valeur codee en
 * dur : si une donnee manque, on affiche "--" plutot que d'inventer.
 *
 * Lot L6b (E9) : chaque champ est lu avec garde (un instantane partiel plantait l'ecran,
 * "storage.totalBytes" sur undefined) ; composants de la bibliotheque.
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
      description="Vue globale des cabinets clients : comptes, stockage, activité et santé des services. Pas d’accès aux tickets ni aux dossiers des cabinets (RG-U07)."
      aide={
        <TexteAide cle="tableau-de-bord-super-admin" titre="Que suivre ici ?">
          <p>
            Les chiffres de la plateforme (cabinets, utilisateurs, stockage, événements de sécurité des
            dernières 24 heures) et la santé de chaque service. Un indicateur « -- » signale une donnée
            que le serveur n’a pas rendue. La gestion des cabinets, des utilisateurs et le journal
            d’audit restent accessibles en bas de page, même si les indicateurs sont indisponibles.
          </p>
        </TexteAide>
      }
    >
      <div className="space-y-6">
        {loading && (
          <SqueletteZone libelle="Chargement des indicateurs de la plateforme…" className="space-y-4">
            <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
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

function DashboardContent({ data }: { data: Partial<SuperAdminDashboardDto> }) {
  const actifs = (data.workspacesParStatut ?? []).find((c) => c.label === 'ACTIVE')?.count ?? null;
  const evenements = data.criticalAuditEvents24h ?? [];
  const nbEvenements = longueur(data.criticalAuditEvents24h);
  const signups30d = data.signups30d ?? [];
  const sante = Object.entries(data.servicesHealth ?? {});
  const stockage = data.storage;

  const parForfait = (data.workspacesParForfait ?? []).map((c) => ({ key: c.label, label: labelOf(FORFAIT_LABELS, c.label), count: c.count }));
  const parStatut = (data.workspacesParStatut ?? []).map((c) => ({ key: c.label, label: labelOf(STATUT_WORKSPACE_LABELS, c.label), count: c.count }));
  const inscriptions = (data.signupsMensuels ?? []).map((m) => ({ label: formatMonth(m.month), count: m.count }));
  const topWs = (data.topWorkspacesByUsage ?? []).map((w) => ({ label: w.name, count: w.eventsLast7d }));

  const indiceActifs =
    actifs !== null
      ? `${actifs} actif${actifs > 1 ? 's' : ''}`
      : typeof data.activeWorkspaces30d === 'number'
        ? `${data.activeWorkspaces30d} actifs sur 30 jours`
        : undefined;
  const nbFichiers = stockage?.fileCount;

  return (
    <>
      {/* KPIs plateforme -- toutes valeurs reelles issues du DTO. */}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <KpiCard label="Cabinets" value={data.totalWorkspaces} hint={indiceActifs} icon={<Building2 />} />
        <KpiCard label="Utilisateurs plateforme" value={data.totalUsers} hint="Tous rôles, tous cabinets" icon={<Users />} />
        <KpiCard
          label="Stockage utilisé"
          value={typeof stockage?.totalBytes === 'number' ? `${decimal(stockage.totalBytes / (1024 * 1024 * 1024), 2)} Go` : null}
          hint={typeof nbFichiers === 'number' ? `${nbFichiers} document${nbFichiers > 1 ? 's' : ''}` : undefined}
          icon={<HardDrive />}
        />
        <KpiCard
          label="Événements critiques 24 h"
          value={nbEvenements}
          hint="Connexions échouées, 2FA, déblocages…"
          icon={<Activity />}
          variant={nbEvenements ? 'danger' : 'default'}
        />
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <ChartCard title="Cabinets par forfait" icon={<Layers />} empty={parForfait.length === 0}>
          <DonutChart data={parForfait} />
        </ChartCard>
        <ChartCard title="Cabinets par statut" icon={<PieChartIcon />} empty={parStatut.length === 0}>
          <DonutChart data={parStatut} />
        </ChartCard>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <ChartCard title="Inscriptions (12 mois)" icon={<LineChartIcon />} empty={inscriptions.every((m) => m.count === 0)}>
          <TrendLineChart data={inscriptions} xKey="label" series={[{ key: 'count', name: 'Inscriptions', color: chartTheme.accent }]} />
        </ChartCard>
        <ChartCard title="Inscriptions (30 jours)" icon={<TrendingUp />} empty={signups30d.length === 0}>
          <EvolutionChart data={signups30d} color={chartTheme.success} />
        </ChartCard>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <ChartCard title="Cabinets les plus actifs (7 jours)" icon={<Database />} empty={topWs.length === 0}>
          <DistributionChart data={topWs} color={chartTheme.accent} height={220} />
        </ChartCard>

        <ChartCard
          title="Santé des services"
          icon={<ServerCog />}
          empty={sante.length === 0}
          emptyLabel="État des services non rendu par le serveur."
        >
          <ul className="grid grid-cols-1 gap-2 sm:grid-cols-2">
            {sante.map(([svc, status]) => {
              const s = santeService(status);
              return (
                <li key={svc} className="flex items-center justify-between gap-2 text-sm">
                  <span className="truncate font-medium text-fg">{svc}</span>
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
        title="Événements critiques (24 h)"
        icon={<Activity />}
        empty={evenements.length === 0}
        emptyLabel={nbEvenements === null ? 'Liste non rendue par le serveur.' : 'Aucun événement critique sur les dernières 24 heures.'}
      >
        <ul className="divide-y divide-border">
          {evenements.slice(0, 12).map((e) => (
            <li key={e.id} className="flex flex-wrap items-center gap-3 py-2 text-sm">
              <Badge variant="danger" className="font-mono">
                {e.action}
              </Badge>
              {e.entityType && <span className="text-fg-muted">{e.entityType}</span>}
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
