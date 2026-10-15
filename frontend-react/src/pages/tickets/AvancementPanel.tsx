import { AlertTriangle, CalendarClock, CircleCheck, CircleDot } from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { formatDate } from '../../lib/date';
import { PARCOURS_STATUTS, STATUT_LABELS, STATUT_LABELS_COURTS } from '../../types/ticket';
import type { AvancementTicket, PhaseDemarches, SeveriteEcheance } from '../../types/demarche';

/**
 * D.4 — L'avancement du ticket, visible SANS ouvrir le workflow.
 *
 * Pas de graphique décoratif : une barre par phase et une liste de points
 * d'attention, exactes. L'information la plus utile de l'écran est la dernière :
 * un délai manqué a des conséquences réelles.
 */

// Lot L6b (E7) : memes couleurs que la frise des demarches. Rouge = depassee ; orange =
// echeance proche (CRITIQUE J-3 et APPROCHE J-15 du serveur). Le libelle dit l'etat.
const COULEUR_SEVERITE: Record<SeveriteEcheance, string> = {
  DEPASSE: 'border-danger/30 bg-danger-soft text-danger',
  CRITIQUE: 'border-warning/30 bg-warning-soft text-warning',
  APPROCHE: 'border-warning/30 bg-warning-soft text-warning',
};

const LIBELLE_SEVERITE: Record<SeveriteEcheance, string> = {
  DEPASSE: 'Dépassée',
  CRITIQUE: 'Échéance imminente',
  APPROCHE: 'Échéance proche',
};

function libelleDelai(joursRestants: number): string {
  if (joursRestants < 0) {
    const retard = Math.abs(joursRestants);
    return `dépassée de ${retard} jour${retard > 1 ? 's' : ''}`;
  }
  if (joursRestants === 0) return "aujourd'hui";
  return `dans ${joursRestants} jour${joursRestants > 1 ? 's' : ''}`;
}

export function AvancementPanel({
  avancement,
  phases,
}: {
  avancement: AvancementTicket;
  phases: PhaseDemarches[];
}) {
  const annule = avancement.statutCourant === 'ANNULE';
  // Seules les phases du statut courant sont en cours de traitement : afficher
  // une barre pour les 9 phases mélangerait ce qui est fait et ce qui n'a pas
  // encore commencé.
  const phasesDuStatut = phases.filter((p) =>
    p.demarches.some((d) => d.statutTicket === avancement.statutCourant),
  );

  return (
    <Card>
      <header className="border-b border-border px-5 py-3">
        <h3 className="text-base font-semibold text-heading">Avancement</h3>
      </header>

      <div className="space-y-5 px-5 py-4">
        {/* ── Position dans le parcours ─────────────────────────────── */}
        <div>
          <ol className="flex flex-wrap items-center gap-x-1 gap-y-2">
            {PARCOURS_STATUTS.map((s, i) => {
              const rang = i + 1;
              const atteint = !annule && rang <= avancement.position;
              const courant = !annule && rang === avancement.position;
              return (
                <li key={s} className="flex items-center gap-1">
                  <span
                    className={`flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ${
                      courant
                        ? 'bg-accent text-accent-fg'
                        : atteint
                          ? 'bg-success-soft text-success'
                          : 'bg-bg-overlay text-fg-muted'
                    }`}
                    aria-current={courant ? 'step' : undefined}
                  >
                    {atteint && !courant ? (
                      <CircleCheck className="h-3.5 w-3.5" aria-hidden="true" />
                    ) : (
                      <CircleDot className="h-3.5 w-3.5" aria-hidden="true" />
                    )}
                    {STATUT_LABELS_COURTS[s]}
                  </span>
                  {i < PARCOURS_STATUTS.length - 1 && (
                    <span className="text-fg-subtle" aria-hidden>
                      ›
                    </span>
                  )}
                </li>
              );
            })}
          </ol>
          <p className="mt-2 text-xs text-fg-subtle">
            {annule ? (
              <>Ticket annulé — hors parcours.</>
            ) : (
              <>
                Statut {avancement.position} sur {avancement.totalStatuts} :{' '}
                <strong className="text-fg">{STATUT_LABELS[avancement.statutCourant]}</strong>
              </>
            )}
          </p>
        </div>

        {/* ── Démarches cochées, par phase ──────────────────────────── */}
        {phasesDuStatut.length > 0 && (
          <div>
            <p className="mb-2 text-xs font-semibold text-fg-muted">
              Démarches — {avancement.traitees} / {avancement.applicables}
            </p>
            <ul className="space-y-2">
              {phasesDuStatut.map((phase) => {
                const applicables = phase.demarches.filter(
                  (d) => d.statutTicket === avancement.statutCourant,
                );
                const traitees = applicables.filter((d) => d.etat !== 'A_FAIRE').length;
                const pourcent =
                  applicables.length === 0 ? 0 : (traitees / applicables.length) * 100;
                return (
                  <li key={phase.code}>
                    <div className="flex items-baseline justify-between gap-2 text-xs">
                      <span className="text-fg">{phase.libelle}</span>
                      <span className="tabular-nums text-fg-subtle">
                        {traitees} / {applicables.length}
                      </span>
                    </div>
                    <div
                      className="mt-1 h-1.5 overflow-hidden rounded-full bg-bg-overlay"
                      role="progressbar"
                      aria-valuenow={traitees}
                      aria-valuemin={0}
                      aria-valuemax={applicables.length}
                      aria-label={phase.libelle}
                    >
                      <div
                        className={`h-full rounded-full ${
                          pourcent === 100 ? 'bg-success' : 'bg-accent'
                        }`}
                        style={{ width: `${pourcent}%` }}
                      />
                    </div>
                  </li>
                );
              })}
            </ul>
          </div>
        )}

        {/* ── Prochaine démarche ────────────────────────────────────── */}
        {avancement.prochainOrdre != null && (
          <p className="rounded-lg border border-border bg-bg-overlay px-3 py-2 text-sm text-fg">
            <span className="text-fg-muted">Prochaine démarche : </span>
            <strong>
              {avancement.prochainOrdre}. {avancement.prochainLibelle}
            </strong>
          </p>
        )}

        {/* ── Points d'attention : délais légaux ────────────────────── */}
        <div>
          <p className="mb-2 flex items-center gap-1.5 text-xs font-semibold text-fg-muted">
            <CalendarClock className="h-3.5 w-3.5" aria-hidden="true" />
            Délais légaux
          </p>
          {avancement.pointsAttention.length === 0 ? (
            <p className="text-xs text-fg-muted">
              Aucun délai légal en approche. Une échéance n&apos;apparaît qu&apos;une fois son
              point de départ posé — c&apos;est-à-dire l&apos;étape dont elle dépend cochée.
            </p>
          ) : (
            <ul className="space-y-1.5">
              {avancement.pointsAttention.map((p) => (
                <li
                  key={p.ordre}
                  className={`flex items-start gap-2 rounded-lg border px-3 py-2 text-sm ${
                    COULEUR_SEVERITE[p.severite]
                  }`}
                >
                  <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                  <span className="min-w-0">
                    <span className="block text-xs font-semibold">{LIBELLE_SEVERITE[p.severite]}</span>
                    <strong className="font-semibold text-fg">
                      {p.ordre}. {p.libelle}
                    </strong>
                    <span className="block text-xs text-fg-muted">
                      Échéance au {formatDate(p.echeance)} — {libelleDelai(p.joursRestants)}
                      {p.delai ? ` · ${p.delai}` : ''}
                    </span>
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </Card>
  );
}
