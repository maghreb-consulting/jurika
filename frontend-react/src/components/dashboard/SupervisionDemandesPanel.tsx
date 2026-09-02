import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Building2, Inbox, MessageSquare, Send, User } from 'lucide-react';
import { dataroomService } from '../../services/dataroom.service';
import {
  DEMANDE_STATUT_LABELS,
  REQUETE_STATUT_LABELS,
  type DemandeSupervisionRow,
} from '../../types/dataroom';

/**
 * Vue SUPERVISEUR (workspace-wide) : toutes les demandes des clients ET requetes
 * aux clients du cabinet, chaque ligne montrant le nom du dataroom (raison
 * sociale) et l'employe responsable (noms resolus par jointure cote back —
 * jamais d'UUID). Lecture seule (RG-U02-04) : aucune action de mutation ici.
 */

const DEMANDE_STATUT_ORDER = ['NON_TRAITEE', 'EN_COURS', 'TRAITEE'];
const REQUETE_STATUT_ORDER = ['OUVERTE', 'A_COMPLETER', 'REPONDUE', 'CLOTUREE'];

const STATUT_BADGE: Record<string, string> = {
  NON_TRAITEE: 'bg-danger/10 text-danger',
  EN_COURS: 'bg-accent/10 text-accent',
  TRAITEE: 'bg-emerald-100 text-emerald-700',
  OUVERTE: 'bg-amber-100 text-amber-700',
  A_COMPLETER: 'bg-amber-100 text-amber-700',
  REPONDUE: 'bg-accent/10 text-accent',
  CLOTUREE: 'bg-emerald-100 text-emerald-700',
};

function statutLabel(direction: 'CLIENT_TO_EMPLOYE' | 'EMPLOYE_TO_CLIENT', statut: string): string {
  if (direction === 'EMPLOYE_TO_CLIENT') {
    return REQUETE_STATUT_LABELS[statut as keyof typeof REQUETE_STATUT_LABELS] ?? statut;
  }
  return DEMANDE_STATUT_LABELS[statut as keyof typeof DEMANDE_STATUT_LABELS] ?? statut;
}

export function SupervisionDemandesPanel() {
  const [demandes, setDemandes] = useState<DemandeSupervisionRow[]>([]);
  const [requetes, setRequetes] = useState<DemandeSupervisionRow[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let mounted = true;
    Promise.all([
      dataroomService.listSupervisionDemandes('CLIENT_TO_EMPLOYE').catch(() => [] as DemandeSupervisionRow[]),
      dataroomService.listSupervisionDemandes('EMPLOYE_TO_CLIENT').catch(() => [] as DemandeSupervisionRow[]),
    ]).then(([dem, req]) => {
      if (!mounted) return;
      setDemandes(dem);
      setRequetes(req);
      setLoading(false);
    });
    return () => {
      mounted = false;
    };
  }, []);

  return (
    <div className="mt-6 grid gap-6 lg:grid-cols-2">
      <SupervisionBlock
        title="Demandes des clients"
        icon={MessageSquare}
        iconColor="text-violet-600"
        direction="CLIENT_TO_EMPLOYE"
        order={DEMANDE_STATUT_ORDER}
        rows={demandes}
        loading={loading}
        emptyLabel="Aucune demande client dans le cabinet."
      />
      <SupervisionBlock
        title="Requetes aux clients"
        icon={Send}
        iconColor="text-accent"
        direction="EMPLOYE_TO_CLIENT"
        order={REQUETE_STATUT_ORDER}
        rows={requetes}
        loading={loading}
        emptyLabel="Aucune requete envoyee aux clients."
      />
    </div>
  );
}

function SupervisionBlock({
  title,
  icon: Icon,
  iconColor,
  direction,
  order,
  rows,
  loading,
  emptyLabel,
}: {
  title: string;
  icon: typeof MessageSquare;
  iconColor: string;
  direction: 'CLIENT_TO_EMPLOYE' | 'EMPLOYE_TO_CLIENT';
  order: string[];
  rows: DemandeSupervisionRow[];
  loading: boolean;
  emptyLabel: string;
}) {
  const navigate = useNavigate();

  // Compteurs par statut (repartition) + tri des lignes par statut connu.
  const counts = useMemo(() => {
    const map: Record<string, number> = {};
    for (const r of rows) map[r.statut] = (map[r.statut] ?? 0) + 1;
    return map;
  }, [rows]);

  const sorted = useMemo(() => {
    const rank = (s: string) => {
      const i = order.indexOf(s);
      return i === -1 ? order.length : i;
    };
    return [...rows].sort(
      (a, b) =>
        rank(a.statut) - rank(b.statut) ||
        new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
    );
  }, [rows, order]);

  return (
    <div className="rounded-xl border border-border bg-bg-raised shadow-sm">
      <div className="flex items-center justify-between border-b border-border px-6 py-4">
        <h3 className="flex items-center gap-2 text-base font-semibold text-fg">
          <Icon className={`h-5 w-5 ${iconColor}`} /> {title}
        </h3>
        <span className="text-xs text-fg-subtle">{rows.length} au total</span>
      </div>

      {/* Repartition par statut (chips de comptage). */}
      {rows.length > 0 && (
        <div className="flex flex-wrap gap-2 border-b border-border px-6 py-2.5">
          {order
            .filter((s) => counts[s])
            .map((s) => (
              <span
                key={s}
                className={`rounded-full px-2.5 py-0.5 text-[11px] font-semibold ${STATUT_BADGE[s] ?? 'bg-bg-overlay text-fg-muted'}`}
              >
                {statutLabel(direction, s)} · {counts[s]}
              </span>
            ))}
        </div>
      )}

      {loading ? (
        <p className="px-6 py-10 text-center text-sm text-fg-subtle">Chargement…</p>
      ) : rows.length === 0 ? (
        <div className="flex flex-col items-center justify-center px-6 py-10 text-center">
          <Inbox className="mb-2 h-8 w-8 text-fg-subtle" />
          <p className="text-sm text-fg-subtle">{emptyLabel}</p>
        </div>
      ) : (
        <div className="max-h-96 overflow-y-auto">
          <table className="w-full">
            <thead className="sticky top-0 bg-bg-overlay">
              <tr>
                <th className="px-6 py-2.5 text-left text-xs font-semibold uppercase text-fg-subtle">Sujet</th>
                <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase text-fg-subtle">Dataroom</th>
                <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase text-fg-subtle">Employe</th>
                <th className="px-4 py-2.5 text-left text-xs font-semibold uppercase text-fg-subtle">Statut</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {sorted.map((r) => (
                <tr
                  key={r.id}
                  onClick={() => navigate(`/data-rooms?dossier=${r.dossierId}&tab=demandes`)}
                  className="cursor-pointer hover:bg-bg-overlay"
                  title="Ouvrir dans le Data Room"
                >
                  <td className="max-w-[14rem] px-6 py-3">
                    <p className="truncate text-sm font-medium text-fg">{r.sujet}</p>
                    <p className="text-[10px] text-fg-subtle">
                      {new Date(r.createdAt).toLocaleDateString('fr-FR')}
                    </p>
                  </td>
                  <td className="px-4 py-3">
                    <span className="flex items-center gap-1 text-xs text-fg" title={r.raisonSociale ?? undefined}>
                      <Building2 className="h-3.5 w-3.5 shrink-0 text-fg-subtle" />
                      <span className="max-w-[9rem] truncate">
                        {r.raisonSociale ?? r.dossierId.slice(0, 8).toUpperCase()}
                      </span>
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <span className="flex items-center gap-1 text-xs text-fg-muted">
                      <User className="h-3.5 w-3.5 shrink-0 text-fg-subtle" />
                      <span className="max-w-[9rem] truncate">{r.responsableNom ?? '—'}</span>
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <span
                      className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${STATUT_BADGE[r.statut] ?? 'bg-bg-overlay text-fg-muted'}`}
                    >
                      {statutLabel(direction, r.statut)}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
