import { useMemo, useState } from 'react';
import { CalendarClock, ChevronRight, Info } from 'lucide-react';

interface Props {
  existing?: Record<string, unknown>;
  /** Etapes amont (notamment step3 financier) pour detecter les annees. */
  data: Record<string, Record<string, unknown>>;
  saving: boolean;
  onSubmit: (payload: Record<string, unknown>) => Promise<void>;
}

/**
 * Lit la couche metier d'une step en tolerant le nicheage backend
 * ("step3.financier") et l'acces direct. Identique au helper unwrap des
 * autres etapes Import (fix 2026-06-06).
 */
function unwrap(
  data: Record<string, Record<string, unknown>>,
  stepKey: string,
  innerKey: string,
): Record<string, unknown> {
  const step = (data[stepKey] as Record<string, unknown> | undefined) ?? {};
  const inner = step[innerKey];
  if (inner && typeof inner === 'object' && !Array.isArray(inner)) {
    return inner as Record<string, unknown>;
  }
  return step;
}

/**
 * IMPORT — Etape 5 « Suivi » (2026-06-24).
 *
 * <p>Une societe importee = un SUIVI a integrer. Le coeur du suivi fiscal est
 * l'ouverture d'un exercice : elle genere automatiquement ~10 echeances (TVA,
 * acomptes IS, IR...) via {@code EcheancesGenerator}, qui alimentent le
 * calendrier et le dashboard « Echeances a venir ».
 *
 * <p>Cette etape collecte uniquement les CHOIX (regime TVA + annee(s)).
 * L'OUVERTURE EFFECTIVE est orchestree a la finalisation cote
 * {@code ImportWorkflowPage} (workflow-service n'a pas de client cross-service
 * vers dataroom-service).
 */
export function StepSuiviExercices({ existing, data, saving, onSubmit }: Props) {
  const e =
    (existing?.suivi as
      | {
          regimeTvaMensuel?: boolean;
          anneeExercice?: number;
          anneesAnterieuresSelectionnees?: number[];
        }
      | undefined) ?? {};

  const currentYear = new Date().getFullYear();
  const [regimeTvaMensuel, setRegimeTvaMensuel] = useState<boolean>(
    e.regimeTvaMensuel ?? true,
  );
  const [anneeExercice, setAnneeExercice] = useState<number>(
    Number(e.anneeExercice ?? currentYear),
  );
  const [selectedAnterieures, setSelectedAnterieures] = useState<Set<number>>(
    new Set((e.anneesAnterieuresSelectionnees ?? []).map((y) => Number(y))),
  );

  // Annees detectees depuis les documents comptables (step8) / fiscaux (step9)
  // importes. Refonte 2026-06-25 : les uploads financiers sont desormais 2
  // etapes distinctes ; on lit aussi step3 en compat ascendante.
  const detectedYears = useMemo(() => {
    const comptable = unwrap(data, 'step8', 'comptable');
    const fiscal = unwrap(data, 'step9', 'fiscal');
    const legacy = unwrap(data, 'step3', 'financier');
    const compta = (
      (comptable.documentsFinanciers ?? legacy.documentsFinanciers) as
        | Array<{ annee?: number }>
        | undefined
    ) ?? [];
    const fisc = (
      (fiscal.documentsFiscaux ?? legacy.documentsFiscaux) as
        | Array<{ annee?: number }>
        | undefined
    ) ?? [];
    const set = new Set<number>();
    [...compta, ...fisc].forEach((d) => {
      const y = Number(d?.annee);
      if (Number.isFinite(y) && y >= 1990 && y <= currentYear + 1) set.add(y);
    });
    return Array.from(set).sort((a, b) => b - a);
  }, [data, currentYear]);

  const priorYears = detectedYears.filter((y) => y !== anneeExercice);

  function toggleAnterieure(year: number) {
    setSelectedAnterieures((prev) => {
      const next = new Set(prev);
      if (next.has(year)) next.delete(year);
      else next.add(year);
      return next;
    });
  }

  const canSubmit = anneeExercice >= 1990 && anneeExercice <= currentYear + 1;

  return (
    <form
      noValidate
      onSubmit={(ev) => {
        ev.preventDefault();
        // Garde-fou JS (annee d'exercice dans la plage valide) depuis que
        // `noValidate` neutralise la validation native du champ number.
        if (!canSubmit) return;
        const anterieures = Array.from(selectedAnterieures).filter((y) => y < anneeExercice);
        // Payload top-level (lu tel quel par ImportWorkflow.handleSuivi) + miroir
        // niche `suivi` pour la rehydratation a la reouverture du wizard.
        void onSubmit({
          regimeTvaMensuel,
          anneeExercice,
          anneesAnterieuresSelectionnees: anterieures,
          suivi: {
            regimeTvaMensuel,
            anneeExercice,
            anneesAnterieuresSelectionnees: anterieures,
          },
        });
      }}
      className="mx-auto max-w-[820px] space-y-6"
    >
      <div className="rounded-xl border border-accent/20 bg-accent/10 p-4 text-xs text-fg">
        <p className="flex items-center gap-2 font-semibold text-accent">
          <CalendarClock className="h-4 w-4" /> Suivi fiscal de la societe importee
        </p>
        <p className="mt-1">
          Ouvrir l'exercice courant generera automatiquement les echeances
          fiscales (TVA, acomptes IS, IR...) — elles apparaitront dans le
          calendrier du dossier et le dashboard « Echeances a venir ».
        </p>
      </div>

      {/* Regime TVA */}
      <div className="rounded-xl border border-border bg-bg-raised p-6 shadow-sm">
        <h3 className="mb-3 text-base font-bold text-fg">Regime de TVA</h3>
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          <label
            className={`flex cursor-pointer items-center gap-3 rounded-lg border-2 px-4 py-3 text-sm transition ${
              regimeTvaMensuel
                ? 'border-accent bg-accent/5'
                : 'border-border bg-bg-overlay hover:border-accent/40'
            }`}
          >
            <input
              type="radio"
              name="regimeTva"
              checked={regimeTvaMensuel}
              onChange={() => setRegimeTvaMensuel(true)}
            />
            <div>
              <p className="font-bold text-fg">Mensuel</p>
              <p className="text-[11px] text-fg-subtle">12 declarations TVA / an</p>
            </div>
          </label>
          <label
            className={`flex cursor-pointer items-center gap-3 rounded-lg border-2 px-4 py-3 text-sm transition ${
              !regimeTvaMensuel
                ? 'border-accent bg-accent/5'
                : 'border-border bg-bg-overlay hover:border-accent/40'
            }`}
          >
            <input
              type="radio"
              name="regimeTva"
              checked={!regimeTvaMensuel}
              onChange={() => setRegimeTvaMensuel(false)}
            />
            <div>
              <p className="font-bold text-fg">Trimestriel</p>
              <p className="text-[11px] text-fg-subtle">4 declarations TVA / an</p>
            </div>
          </label>
        </div>
      </div>

      {/* Exercice en cours */}
      <div className="rounded-xl border border-border bg-bg-raised p-6 shadow-sm">
        <h3 className="mb-3 text-base font-bold text-fg">Exercice en cours</h3>
        <label className="mb-1 block text-xs font-medium text-fg">
          Annee de l'exercice a ouvrir
        </label>
        <input aria-label="Annee de l'exercice a ouvrir"
          type="number"
          min={1990}
          max={currentYear + 1}
          value={anneeExercice}
          onChange={(ev) => setAnneeExercice(Number(ev.target.value) || currentYear)}
          className="h-10 w-40 rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
        />
        <p className="mt-2 flex items-center gap-1 text-[11px] text-fg-subtle">
          <Info className="h-3 w-3" />
          A la finalisation, cet exercice sera ouvert et ses echeances generees.
        </p>
      </div>

      {/* Exercices anterieurs detectes */}
      {priorYears.length > 0 && (
        <div className="rounded-xl border border-border bg-bg-raised p-6 shadow-sm">
          <h3 className="mb-1 text-base font-bold text-fg">
            Exercices anterieurs detectes
          </h3>
          <p className="mb-3 text-[11px] text-fg-subtle">
            Annees reperees dans les documents importes a l'etape 3. Cochez celles
            dont vous souhaitez aussi ouvrir l'exercice (et generer les echeances).
          </p>
          <div className="flex flex-wrap gap-2">
            {priorYears.map((year) => {
              const checked = selectedAnterieures.has(year);
              return (
                <label
                  key={year}
                  className={`flex cursor-pointer items-center gap-2 rounded-lg border-2 px-3 py-2 text-sm transition ${
                    checked
                      ? 'border-accent bg-accent/5'
                      : 'border-border bg-bg-overlay hover:border-accent/40'
                  }`}
                >
                  <input
                    type="checkbox"
                    checked={checked}
                    onChange={() => toggleAnterieure(year)}
                    className="h-4 w-4 rounded"
                  />
                  <span className="font-semibold text-fg">{year}</span>
                </label>
              );
            })}
          </div>
        </div>
      )}

      <div className="flex items-center justify-end pt-2">
        <button
          type="submit"
          disabled={!canSubmit || saving}
          className={`flex items-center gap-2 rounded-lg px-8 h-12 font-medium transition ${
            canSubmit && !saving
              ? 'bg-accent text-bg-raised hover:bg-accent-hover'
              : 'cursor-not-allowed bg-border text-fg-subtle'
          }`}
        >
          {saving ? 'Validation en cours...' : 'Valider et continuer'}
          <ChevronRight className="h-4 w-4" />
        </button>
      </div>
    </form>
  );
}
