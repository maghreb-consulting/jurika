import { useEffect, useState } from 'react';
import { Activity, Building2, HardDrive, Layers, ScrollText, ServerCog, ShieldAlert, TrendingUp, Users } from 'lucide-react';
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
import { ColonnesChart } from '../../components/dashboard/charts/ColonnesChart';
import { BarresHorizontales, type Barre } from '../../components/dashboard/charts/BarresHorizontales';
import { BarresEmpilees, type SerieEmpilee } from '../../components/dashboard/charts/BarresEmpilees';
import { DonutChart } from '../../components/dashboard/charts/DonutChart';
import { Sparkline } from '../../components/dashboard/charts/Sparkline';
import type { TonSerie } from '../../components/dashboard/charts/apparence';
import { adminService } from '../../services/admin.service';
import { cumuls } from '../../components/dashboard/series';
import type { AdminWorkspaceRow } from '../../types/admin';
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
 * Lot L6b (E18, demande du 2026-10-17 : des diagrammes propres a chaque role) : courbes de
 * tendance dans les indicateurs, croissance (nouveaux cabinets et total), forfaits en anneau avec
 * total et statuts en barre empilee, activite et stockage par cabinet.
 *
 * Le SUPER_ADMIN n'accede PAS aux tickets/dossiers/documents metier (RG-U07).
 */
export function SuperAdminDashboard() {
  const [data, setData] = useState<Partial<SuperAdminDashboardDto> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [essai, setEssai] = useState(0);
  // L6b (E18) : stockage par cabinet (liste d'administration des cabinets) ; null si en echec.
  const [cabinets, setCabinets] = useState<AdminWorkspaceRow[] | null>(null);

  useEffect(() => {
    let mounted = true;
    adminService
      .listWorkspaces()
      .then((l) => mounted && setCabinets(Array.isArray(l) ? l : null))
      .catch(() => mounted && setCabinets(null));
    return () => {
      mounted = false;
    };
  }, [essai]);

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

        {!loading && !error && data && <DashboardContent data={data} cabinets={cabinets} />}

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

function DashboardContent({ data, cabinets }: { data: Partial<SuperAdminDashboardDto>; cabinets: AdminWorkspaceRow[] | null }) {
  const parStatutBrut = data.workspacesParStatut;
  const compteStatut = (cle: string) => (parStatutBrut ?? []).find((c) => c.label === cle)?.count ?? 0;
  const evenements = data.criticalAuditEvents24h ?? [];
  const nbEvenements = longueur(data.criticalAuditEvents24h);
  const sante = Object.entries(data.servicesHealth ?? {});
  const enLigne = sante.filter(([, s]) => s === 'UP').length;
  const stockage = data.storage;
  const nouveauxUtilisateurs = Array.isArray(data.signups30d) ? data.signups30d.reduce((n, d) => n + (d.count ?? 0), 0) : null;

  // Croissance : nouveaux cabinets par mois et cumul. Le cumul part du total actuel moins les
  // inscriptions des douze mois (aucun chiffre suppose) ; incoherent (negatif) : pas de cumul.
  const mensuel = data.signupsMensuels ?? [];
  const sommeMois = mensuel.reduce((n, m) => n + m.count, 0);
  const baseCabinets = typeof data.totalWorkspaces === 'number' ? data.totalWorkspaces - sommeMois : null;
  const cumulOk = baseCabinets !== null && baseCabinets >= 0;
  const totaux = cumuls(baseCabinets ?? 0, mensuel.map((m) => m.count));
  const croissance: Array<Record<string, string | number>> = mensuel.map((m, i) => ({
    mois: formatMonth(m.month),
    nouveaux: m.count,
    ...(cumulOk ? { total: totaux[i] } : {}),
  }));

  // Comptes crees par jour sur 30 jours, cumules a partir du total actuel (meme regle).
  const parJour = new Map((data.signups30d ?? []).map((d) => [d.day.slice(0, 10), d.count]));
  const baseUtilisateurs = typeof data.totalUsers === 'number' && nouveauxUtilisateurs !== null ? data.totalUsers - nouveauxUtilisateurs : null;
  const aujourdHui = new Date();
  const creesParJour = Array.from({ length: 30 }, (_, i) => {
    const d = new Date(aujourdHui.getFullYear(), aujourdHui.getMonth(), aujourdHui.getDate() - 29 + i);
    return parJour.get(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`) ?? 0;
  });
  const courbeUtilisateurs = baseUtilisateurs !== null && baseUtilisateurs >= 0 ? cumuls(baseUtilisateurs, creesParJour) : [];

  const parForfait = (data.workspacesParForfait ?? []).map((c) => ({ key: c.label, label: labelOf(FORFAIT_LABELS, c.label), count: c.count }));
  const tonStatut = (cle: string): TonSerie => (cle === 'ACTIVE' ? 'succes' : cle === 'ESSAI' ? 'info' : cle === 'SUSPENDED' ? 'attention' : 'neutre');
  const seriesStatut: SerieEmpilee[] = (parStatutBrut ?? []).map((c) => ({ cle: c.label, nom: labelOf(STATUT_WORKSPACE_LABELS, c.label), ton: tonStatut(c.label) }));
  const actifs: Barre[] = (data.topWorkspacesByUsage ?? []).map((w) => ({
    cle: w.workspaceId,
    libelle: w.name,
    valeur: w.eventsLast7d,
    lien: `/admin/workspaces/${w.workspaceId}`,
  }));
  const GO = 1024 * 1024 * 1024;
  const parStockage: Barre[] = (cabinets ?? [])
    .filter((c) => typeof c.storageBytes === 'number')
    .sort((a, b) => (b.storageBytes ?? 0) - (a.storageBytes ?? 0))
    .slice(0, 8)
    .map((c) => ({ cle: c.id, libelle: c.name, valeur: c.storageBytes ?? 0, lien: `/admin/workspaces/${c.id}` }));

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
          aide="Tous les cabinets inscrits, quel que soit leur statut. « En essai » : essai gratuit en cours. La petite courbe suit le nombre de cabinets sur douze mois."
          tendance={cumulOk ? <Sparkline valeurs={croissance.map((c) => Number(c.total))} libelle="Nombre de cabinets sur douze mois" /> : undefined}
        />
        <KpiCard
          label="Utilisateurs"
          value={data.totalUsers}
          hint={nouveauxUtilisateurs !== null ? `${nouveauxUtilisateurs} créé${nouveauxUtilisateurs > 1 ? 's' : ''} sur 30 jours` : undefined}
          icon={<Users />}
          to="/admin/users"
          aide="Comptes de tous les cabinets, tous rôles confondus (superviseurs, employés, clients). La petite courbe suit le nombre de comptes sur 30 jours."
          tendance={<Sparkline valeurs={courbeUtilisateurs} libelle="Nombre de comptes sur 30 jours" ton="succes" />}
        />
        <KpiCard
          label="Stockage utilisé"
          value={typeof stockage?.totalBytes === 'number' ? `${decimal(stockage.totalBytes / GO, 2)} Go` : null}
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

      <div className="grid gap-4 lg:grid-cols-5">
        <ChartCard
          className="lg:col-span-3"
          title="Croissance des cabinets"
          icon={<TrendingUp />}
          description="Nouveaux cabinets inscrits chaque mois (colonnes) et nombre total de cabinets (ligne), sur douze mois."
          empty={croissance.length === 0}
          emptyLabel="Inscriptions mensuelles non rendues par le serveur."
        >
          <ColonnesChart
            donnees={croissance}
            cleX="mois"
            legende="Nouveaux cabinets par mois et total des cabinets, douze derniers mois"
            series={[{ cle: 'nouveaux', nom: 'Nouveaux cabinets', ton: 'info' }]}
            ligne={cumulOk ? { cle: 'total', nom: 'Cabinets au total', ton: 'accent' } : undefined}
            hauteur={240}
          />
        </ChartCard>
        <ChartCard
          className="lg:col-span-2"
          title="Cabinets par forfait et par statut"
          icon={<Layers />}
          description="Quels forfaits sont choisis, et combien de cabinets sont actifs, en essai ou suspendus."
          empty={parForfait.length === 0 && seriesStatut.length === 0}
        >
          {parForfait.length > 0 ? (
            <DonutChart data={parForfait} height={210} legende="Cabinets par forfait" />
          ) : (
            <p className="text-sm text-fg-muted">Répartition par forfait non rendue par le serveur.</p>
          )}
          {seriesStatut.length > 0 && (
            <div className="mt-4 border-t border-border pt-4">
              <BarresEmpilees
                echelle="part"
                legende="Cabinets par statut"
                series={seriesStatut}
                lignes={[{ cle: 'statut', libelle: 'Statut des cabinets', valeurs: Object.fromEntries((parStatutBrut ?? []).map((c) => [c.label, c.count])) }]}
              />
            </div>
          )}
        </ChartCard>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <ChartCard
          title="Cabinets les plus actifs (7 jours)"
          icon={<Activity />}
          description="Actions tracées au journal d’audit par cabinet : qui utilise vraiment la plateforme."
          empty={actifs.length === 0}
          emptyLabel="Aucune action tracée sur les sept derniers jours."
        >
          <BarresHorizontales barres={actifs} legende="Actions tracées par cabinet sur sept jours" ton="accent" />
        </ChartCard>
        <ChartCard
          title="Stockage par cabinet"
          icon={<HardDrive />}
          description="Volume des documents et dépôts de chaque cabinet (les huit plus gros) : qui approche de son quota."
          empty={parStockage.length === 0}
          emptyLabel={cabinets === null ? 'La liste des cabinets n’a pas pu être chargée.' : 'Stockage des cabinets non rendu par le serveur.'}
        >
          <BarresHorizontales barres={parStockage} legende="Stockage par cabinet" ton="info" format={(o) => `${decimal(o / GO, 2)} Go`} />
        </ChartCard>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <ChartCard
          title="Santé des services"
          icon={<ServerCog />}
          description="Services inscrits au registre : un service hors ligne demande une intervention."
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

        <ChartCard
          title="Alertes de sécurité (24 h)"
          icon={<ShieldAlert />}
          description="Les plus récentes d’abord ; le journal d’audit donne le détail."
          empty={evenements.length === 0}
          emptyLabel={nbEvenements === null ? 'Liste non rendue par le serveur.' : 'Aucune alerte de sécurité sur les dernières 24 heures.'}
          actions={<Lien to="/admin/audit" className="shrink-0 whitespace-nowrap">Journal d’audit</Lien>}
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
      </div>
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
