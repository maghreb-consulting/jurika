import { useEffect, useState } from 'react';
import {
  AlertTriangle,
  Ban,
  CheckCircle,
  FolderOpen,
  TrendingUp,
  Users,
} from 'lucide-react';
import {
  supervisionService,
  type TicketsDaily,
  type TicketsPerEmploye,
  type TicketsPerType,
  type WorkspaceKpis,
} from '../../services/supervision.service';
import { Sprint10SupervisorPanel } from '../../components/dashboard/Sprint10SupervisorPanel';
import { SupervisionDemandesPanel } from '../../components/dashboard/SupervisionDemandesPanel';
import { EvolutionChart } from '../../components/dashboard/EvolutionChart';
import { DistributionChart } from '../../components/dashboard/DistributionChart';
import { TYPE_TICKET_LABELS, labelOf } from '../../lib/dashboardLabels';

export function SupervisorDashboard() {
  const [kpis, setKpis] = useState<WorkspaceKpis | null>(null);
  const [perType, setPerType] = useState<TicketsPerType[]>([]);
  const [perEmploye, setPerEmploye] = useState<TicketsPerEmploye[]>([]);
  const [daily, setDaily] = useState<TicketsDaily[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let mounted = true;
    Promise.all([
      supervisionService.kpis().catch(() => null),
      supervisionService.ticketsPerType().catch(() => [] as TicketsPerType[]),
      supervisionService.ticketsPerEmploye().catch(() => [] as TicketsPerEmploye[]),
      supervisionService.ticketsLast30Days().catch(() => [] as TicketsDaily[]),
    ]).then(([k, t, e, d]) => {
      if (!mounted) return;
      setKpis(k);
      setPerType(t);
      setPerEmploye(e);
      setDaily(d.slice(-14));
      setLoading(false);
    });
    return () => {
      mounted = false;
    };
  }, []);

  // Series pretes pour Recharts (donnees reelles supervision-service).
  const dailySeries = daily.map((d) => ({ day: d.date, count: d.count }));
  const typeSeries = perType.map((t) => ({
    label: labelOf(TYPE_TICKET_LABELS, t.type),
    count: t.count,
  }));

  return (
    <div className="p-6">
      <div className="mb-6">
        <h2 className="font-heading text-2xl font-semibold text-fg">Tableau de bord — Superviseur</h2>
        <p className="text-sm text-fg-subtle">Vue d'ensemble & performances du cabinet</p>
      </div>

      <div className="mb-6 grid grid-cols-2 gap-4 lg:grid-cols-4">
        <KpiCard
          icon={FolderOpen} bg="bg-accent/10" color="text-accent"
          label="Total dossiers" value={kpis?.dossiers ?? 0}
          tag={`${kpis?.dossiersActifs ?? 0} actifs`} tagColor="text-fg-subtle" loading={loading}
        />
        <KpiCard
          icon={Users} bg="bg-accent/10" color="text-accent"
          label="Employes" value={perEmploye.length}
          tag="Actifs" tagColor="text-warning" loading={loading}
        />
        <KpiCard
          icon={Ban} bg="bg-rose-50" color="text-rose-600"
          label="Tickets en cours" value={kpis?.ticketsEnCours ?? 0}
          tag={`${kpis?.ticketsNouveaux ?? 0} nouveau${(kpis?.ticketsNouveaux ?? 0) > 1 ? 'x' : ''}`} tagColor="text-rose-600" loading={loading}
        />
        <KpiCard
          icon={CheckCircle} bg="bg-emerald-50" color="text-success"
          label="Cloturees" value={kpis?.ticketsClotures ?? 0}
          tag={`${kpis?.ticketsAnnules ?? 0} annule${(kpis?.ticketsAnnules ?? 0) > 1 ? 's' : ''}`} tagColor="text-fg-subtle" loading={loading}
        />
      </div>

      <div className="mb-6 grid gap-6 lg:grid-cols-2">
        <div className="rounded-xl border border-border bg-bg-raised p-6 shadow-sm">
          <div className="mb-5 flex items-center justify-between">
            <h3 className="text-base font-semibold text-fg">Activite (14 derniers jours)</h3>
            <TrendingUp className="h-5 w-5 text-emerald-500" />
          </div>
          {dailySeries.length === 0 ? (
            <p className="py-12 text-center text-sm text-fg-subtle">Pas de donnees</p>
          ) : (
            <EvolutionChart data={dailySeries} height={200} />
          )}
        </div>

        <div className="rounded-xl border border-border bg-bg-raised p-6 shadow-sm">
          <h3 className="mb-5 text-base font-semibold text-fg">Tickets par type</h3>
          {typeSeries.length === 0 ? (
            <p className="py-12 text-center text-sm text-fg-subtle">Pas de donnees</p>
          ) : (
            <DistributionChart data={typeSeries} height={200} />
          )}
        </div>
      </div>

      <div className="rounded-xl border border-border bg-bg-raised shadow-sm">
        <div className="border-b border-border px-6 py-4">
          <h3 className="text-base font-semibold text-fg">Performances par employe</h3>
        </div>
        <table className="w-full">
          <thead className="bg-bg-overlay">
            <tr>
              <th className="px-6 py-3 text-left text-xs font-semibold uppercase text-fg-subtle">Employe</th>
              <th className="px-6 py-3 text-center text-xs font-semibold uppercase text-fg-subtle">En cours</th>
              <th className="px-6 py-3 text-center text-xs font-semibold uppercase text-fg-subtle">Cloturees</th>
              <th className="px-6 py-3 text-center text-xs font-semibold uppercase text-fg-subtle">Annulees</th>
              <th className="px-6 py-3 text-center text-xs font-semibold uppercase text-fg-subtle">Score</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {perEmploye.length === 0 && (
              <tr>
                <td colSpan={5} className="px-6 py-12 text-center text-sm text-fg-subtle">
                  Pas d'employes
                </td>
              </tr>
            )}
            {perEmploye.map((e) => {
              const total = e.clotures + e.enCours + e.annules;
              const score = total > 0 ? Math.round((e.clotures / total) * 100) : 0;
              return (
                <tr key={e.userId} className="hover:bg-bg-overlay">
                  <td className="px-6 py-4">
                    <div className="flex items-center gap-3">
                      <div className="flex h-9 w-9 items-center justify-center rounded-full bg-accent text-xs font-bold text-bg-raised">
                        {(e.firstName?.[0] ?? '') + (e.lastName?.[0] ?? '')}
                      </div>
                      <div>
                        <p className="text-sm font-medium text-fg">
                          {e.firstName} {e.lastName}
                        </p>
                      </div>
                    </div>
                  </td>
                  <td className="px-6 py-4 text-center text-sm text-fg">{e.enCours}</td>
                  <td className="px-6 py-4 text-center text-sm text-success">{e.clotures}</td>
                  <td className="px-6 py-4 text-center text-sm text-rose-600">{e.annules}</td>
                  <td className="px-6 py-4 text-center">
                    <span
                      className={`inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-bold ${
                        score >= 80
                          ? 'bg-emerald-50 text-emerald-700'
                          : score >= 50
                            ? 'bg-warning/10 text-amber-700'
                            : 'bg-rose-50 text-rose-700'
                      }`}
                    >
                      {score >= 80 && <CheckCircle className="h-3 w-3" />}
                      {score < 50 && <AlertTriangle className="h-3 w-3" />}
                      {score}%
                    </span>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {/* Demandes des clients + Requetes aux clients (workspace-wide, avec nom du
          dataroom et employe responsable resolus par jointure) */}
      <SupervisionDemandesPanel />

      {/* Sprint 10 -- echeances DGI + dossiers a risque + temps moyen cloture */}
      <Sprint10SupervisorPanel />
    </div>
  );
}

function KpiCard({
  icon: Icon,
  bg,
  color,
  label,
  value,
  tag,
  tagColor,
  loading,
}: {
  icon: typeof FolderOpen;
  bg: string;
  color: string;
  label: string;
  value: number;
  tag: string;
  tagColor: string;
  loading: boolean;
}) {
  return (
    <div className="rounded-xl border border-border bg-bg-raised p-5 shadow-sm">
      <div className={`mb-3 flex h-12 w-12 items-center justify-center rounded-lg ${bg}`}>
        <Icon className={`h-6 w-6 ${color}`} />
      </div>
      <p className="mb-1 text-sm text-fg-subtle">{label}</p>
      <p className="mb-2 text-3xl font-bold text-fg">{loading ? '—' : value}</p>
      <p className={`text-xs ${tagColor}`}>{tag}</p>
    </div>
  );
}
