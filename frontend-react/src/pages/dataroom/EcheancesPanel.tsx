import { useCallback, useEffect, useState } from 'react';
import { Calendar, CheckCircle, Loader2 } from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { useToast } from '../../components/ui/Toast';
import { dataroomService } from '../../services/dataroom.service';
import { extractError } from '../../lib/api';
import {
  TYPE_ECHEANCE_LABELS,
  type EcheanceSummary,
} from '../../types/dataroom';

interface Props {
  dossierId: string;
  canManage: boolean;
  /**
   * Lot DIVERS §A (2026-08-13) — societe dissoute / liquidee / radiee : elle ne
   * declare plus. Les echeances restent CONSULTABLES (historique fiscal) mais
   * sont marquees suspendues et ne sont plus depilees par le scheduler
   * (`AlertesEcheancesScheduler` les exclut). Aucune nouvelle n'est generee.
   */
  suspended?: boolean;
}

// Retard « encore ouvert » = non traite (aligne sur le Copilote qui ne remonte que
// les alertes PLANIFIEE/ENVOYEE). Ces echeances doivent rester traitables ici.
const STATUTS_OUVERTS = ['PLANIFIEE', 'ENVOYEE'];
// Fenetre passee chargee pour le retard (borne raisonnable, evite un fetch sans borne).
const RETARD_LOOKBACK_MOIS = 24;

export function EcheancesPanel({ dossierId, canManage, suspended = false }: Props) {
  const toast = useToast();
  const [items, setItems] = useState<EcheanceSummary[]>([]);
  const [overdue, setOverdue] = useState<EcheanceSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Confirmation « marquer traitee » (remplace window.confirm natif).
  const [toMark, setToMark] = useState<string | null>(null);
  const [marking, setMarking] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const iso = (d: Date) => d.toISOString().slice(0, 10);
      const today = new Date();
      const horizon = new Date();
      horizon.setMonth(horizon.getMonth() + 3);
      const pastFrom = new Date();
      pastFrom.setMonth(pastFrom.getMonth() - RETARD_LOOKBACK_MOIS);
      const yesterday = new Date();
      yesterday.setDate(yesterday.getDate() - 1);

      // 1) A venir : aujourd'hui -> +3 mois (inchange).
      // 2) Retard non traite : passe -> hier, filtre statuts ouverts (traitable).
      const [upcoming, past] = await Promise.all([
        dataroomService.listEcheances(dossierId, { from: iso(today), to: iso(horizon) }),
        dataroomService.listEcheances(dossierId, { from: iso(pastFrom), to: iso(yesterday) }),
      ]);
      setItems(upcoming);
      setOverdue(
        past
          .filter((e) => STATUTS_OUVERTS.includes(e.statut))
          .sort((a, b) => a.dateEcheance.localeCompare(b.dateEcheance)), // le plus ancien d'abord
      );
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }, [dossierId]);

  useEffect(() => {
    load();
  }, [load]);

  async function confirmMark() {
    if (!toMark) return;
    setMarking(true);
    try {
      await dataroomService.marquerEcheanceTraitee(toMark);
      setToMark(null);
      await load();
    } catch (err) {
      toast.error(extractError(err).message);
    } finally {
      setMarking(false);
    }
  }

  function daysUntil(date: string): number {
    const d = new Date(date);
    const today = new Date();
    return Math.ceil((d.getTime() - today.getTime()) / (1000 * 60 * 60 * 24));
  }

  function renderRow(e: EcheanceSummary, isOverdue: boolean) {
    const d = daysUntil(e.dateEcheance);
    const badgeClass = isOverdue
      ? 'bg-danger/20 text-danger'
      : e.statut === 'TRAITEE'
        ? 'bg-emerald-100 text-emerald-700'
        : d <= 3
          ? 'bg-danger/20 text-danger'
          : d <= 15
            ? 'bg-amber-100 text-amber-700'
            : 'bg-bg-overlay text-fg-muted';
    const badgeLabel = isOverdue ? `Depassee · ${Math.abs(d)} j` : e.statut;
    return (
      <li key={e.id} className="flex items-center justify-between gap-3 py-3">
        <div>
          <p className="text-sm font-medium text-fg">
            {TYPE_ECHEANCE_LABELS[e.typeEcheance] ?? e.typeEcheance}
          </p>
          <p className="text-xs text-fg-subtle">
            {new Date(e.dateEcheance).toLocaleDateString('fr-FR')}
            {isOverdue
              ? ` · en retard de ${Math.abs(d)} j`
              : e.statut !== 'TRAITEE' && d >= 0 && ` · J-${d}`}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <span className={`rounded-full px-2 py-0.5 text-[10px] font-bold ${badgeClass}`}>
            {badgeLabel}
          </span>
          {canManage && e.statut !== 'TRAITEE' && (
            <button
              type="button"
              onClick={() => setToMark(e.id)}
              className="inline-flex items-center gap-1 rounded-lg border border-emerald-300 bg-emerald-50 px-2 py-1 text-xs font-medium text-emerald-700 hover:bg-emerald-100"
            >
              <CheckCircle className="h-3.5 w-3.5" /> Traite
            </button>
          )}
        </div>
      </li>
    );
  }

  return (
    <>
    <Card>
      <header className="flex items-center gap-2 border-b border-border px-5 py-3">
        <Calendar className="h-4 w-4 text-amber-500" />
        <h4 className="text-sm font-semibold text-fg">
          Echeances DGI — en retard &amp; 3 prochains mois
        </h4>
        {suspended && (
          <span
            className="ml-auto rounded-full bg-warning/15 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wide text-warning"
            title="La societe n'est plus en activite : plus aucune alerte n'est envoyee et aucune nouvelle echeance n'est generee."
            data-testid="echeances-suspendues-badge"
          >
            Suspendues
          </span>
        )}
      </header>
      <div className="p-5">
        {loading ? (
          <Loader2 className="h-5 w-5 animate-spin text-fg-subtle" />
        ) : error ? (
          <p className="text-sm text-danger">{error}</p>
        ) : overdue.length === 0 && items.length === 0 ? (
          <p className="text-sm text-fg-subtle">Aucune echeance en retard ni dans les 3 prochains mois.</p>
        ) : (
          <div className="space-y-6">
            {/* En retard : non traite, le plus ancien d'abord. */}
            {overdue.length > 0 && (
              <section>
                <h5 className="mb-2 text-xs font-semibold uppercase tracking-wide text-danger">
                  En retard
                </h5>
                <ul className="divide-y divide-border">
                  {overdue.map((e) => renderRow(e, true))}
                </ul>
              </section>
            )}

            {/* 3 prochains mois (inchange). */}
            <section>
              <h5 className="mb-2 text-xs font-semibold uppercase tracking-wide text-fg-subtle">
                3 prochains mois
              </h5>
              {items.length === 0 ? (
                <p className="text-sm text-fg-subtle">Aucune echeance dans les 3 prochains mois.</p>
              ) : (
                <ul className="divide-y divide-border">
                  {items.map((e) => renderRow(e, false))}
                </ul>
              )}
            </section>
          </div>
        )}
      </div>
    </Card>

    <ConfirmDialog
      open={!!toMark}
      onOpenChange={(o) => !o && setToMark(null)}
      title="Marquer l'echeance"
      description="Marquer cette echeance comme traitee ?"
      confirmLabel="Marquer traitee"
      loading={marking}
      onConfirm={confirmMark}
    />
    </>
  );
}
