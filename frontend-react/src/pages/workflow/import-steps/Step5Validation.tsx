import { useMemo, useState } from 'react';
import {
  CalendarClock,
  Check,
  CheckCircle2,
  ChevronRight,
  ShieldCheck,
  Square,
  Users,
} from 'lucide-react';
import {
  CATEGORIES_FISCALES_LABELS,
  CATEGORIES_FISCALES_ORDER,
  CATEGORIE_COMPTABLE_LABELS,
  CATEGORIE_COMPTABLE_ORDER,
} from '../../../types/dataroom';
import type { ExerciceOpenResult } from '../ImportWorkflowPage';

interface Props {
  existing?: Record<string, unknown>;
  data: Record<string, Record<string, unknown>>;
  /** dossierId canonique du ticket (source fiable, vs step4.dataRoom). */
  dossierId?: string | null;
  /** Resultats d'ouverture des exercices (renseigne apres finalisation). */
  exerciceResults?: ExerciceOpenResult[] | null;
  /** Total d'echeances du dossier recharge apres finalisation. */
  echeancesTotal?: number | null;
  saving: boolean;
  onSubmit: (payload: Record<string, unknown>) => Promise<void>;
}

interface AssocieRow {
  nom?: string;
  prenom?: string;
  nombreParts?: number;
  pourcentageDetention?: number;
}
interface GerantRow {
  nom?: string;
  prenom?: string;
  cin?: string;
}

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
 * IMPORT -- Etape 5 : recap consolide + checkbox de validation + finalisation.
 * Fix 2026-06-06 : utilise {@link unwrap} pour lire correctement
 * stepData.stepN.{info|juridique|financier|dataroom} cote backend.
 */
export function Step5Validation({
  existing,
  data,
  dossierId,
  exerciceResults,
  echeancesTotal,
  saving,
  onSubmit,
}: Props) {
  const e = (existing?.validation as { validated?: boolean } | undefined) ?? {};
  const [validated, setValidated] = useState<boolean>(e.validated ?? false);

  // Refonte 2026-06-25 — meme tronc de saisie que la CREATION : on lit les
  // donnees dans les memes step bags (denomination/capital/dirigeants/associes)
  // + les 3 etapes d'upload (juridique/comptable/fiscal) + le suivi (step10).
  const denom = unwrap(data, 'step1', 'denomination');
  const capital = unwrap(data, 'step3', 'capital');
  const associesBag = unwrap(data, 'step6', 'associes');
  const associes = (associesBag.associes as AssocieRow[] | undefined) ?? [];
  const dirigeantsBag = unwrap(data, 'step5', 'dirigeants');
  const gerants = (dirigeantsBag.dirigeants as GerantRow[] | undefined) ?? [];
  const suivi = unwrap(data, 'step10', 'suivi');
  const plannedAnnee =
    suivi.anneeExercice != null ? Number(suivi.anneeExercice) : undefined;
  const plannedAnterieures = Array.isArray(suivi.anneesAnterieuresSelectionnees)
    ? (suivi.anneesAnterieuresSelectionnees as unknown[]).map((y) => Number(y))
    : [];
  const regimeTvaMensuel = suivi.regimeTvaMensuel !== false;
  const juridique = unwrap(data, 'step7', 'juridique');
  const docs = ((juridique.documents as unknown[]) ?? []) as Array<{
    uploaded?: boolean;
    uiType?: string;
    type?: string;
  }>;
  const comptableBag = unwrap(data, 'step8', 'comptable');
  const fin = ((comptableBag.documentsFinanciers as unknown[]) ?? []) as Array<{
    state?: string;
    category?: string;
  }>;
  const fiscalBag = unwrap(data, 'step9', 'fiscal');
  const fiscalDocs =
    ((fiscalBag.documentsFiscaux as unknown[]) ?? []) as Array<{
      state?: string;
      category?: string;
    }>;
  // dossierId canonique du ticket (auto-create IMPORT).
  const resolvedDossierId = (dossierId && dossierId.trim()) || '';

  const uploadedJuridique = docs.filter((d) => d.uploaded).length;
  const uploadedFin = fin.filter((d) => d.state === 'UPLOADED').length;
  const uploadedFiscal = fiscalDocs.filter((d) => d.state === 'UPLOADED').length;

  // Prompt H (2026-06-23) — guide de complétude par dossier.
  const completionJuridique = useMemo(() => {
    const set = new Set<string>();
    docs.forEach((d) => {
      const t = d.uiType ?? d.type;
      if (d.uploaded && t) set.add(t);
    });
    const cats = ['STATUTS', 'BAIL', 'DOMICILIATION', 'CNIE', 'CN', 'RC', 'IF'];
    return cats.map((c) => ({ value: c, done: set.has(c) }));
  }, [docs]);
  const completionComptable = useMemo(() => {
    const set = new Set<string>();
    fin.forEach((d) => {
      if (d.state === 'UPLOADED' && d.category) set.add(d.category);
    });
    return CATEGORIE_COMPTABLE_ORDER.map((c) => ({
      value: c,
      label: CATEGORIE_COMPTABLE_LABELS[c],
      done: set.has(c),
    }));
  }, [fin]);
  const completionFiscal = useMemo(() => {
    const set = new Set<string>();
    fiscalDocs.forEach((d) => {
      if (d.state === 'UPLOADED' && d.category) set.add(d.category);
    });
    return CATEGORIES_FISCALES_ORDER.filter((c) => c !== 'CONTENTIEUX').map(
      (c) => ({ value: c, label: CATEGORIES_FISCALES_LABELS[c], done: set.has(c) }),
    );
  }, [fiscalDocs]);

  return (
    <form
      noValidate
      onSubmit={(ev) => {
        ev.preventDefault();
        // Garde-fou JS (case de certification cochee) depuis que `noValidate`
        // neutralise la validation native.
        if (!validated) return;
        void onSubmit({ validated, validation: { validated } });
      }}
      className="mx-auto max-w-[820px] space-y-6"
    >
      <div className="rounded-xl border border-border bg-bg-raised p-6 shadow-sm">
        <h3 className="mb-3 flex items-center gap-2 text-base font-bold text-fg">
          <ShieldCheck className="h-5 w-5 text-success" />
          Fiche juridique consolidee
        </h3>
        <div className="grid grid-cols-1 gap-3 text-sm md:grid-cols-2">
          <Item label="Raison sociale" value={String(denom.denomination ?? '—')} />
          <Item label="Forme juridique" value={String(denom.formeJuridique ?? '—')} />
          <Item label="ICE" value={String(denom.ice ?? '—')} />
          <Item label="RC" value={String(denom.rcNumero ?? '—')} />
          <Item label="IF" value={String(denom.ifNumero ?? '—')} />
          <Item
            label="Capital social"
            value={`${Number(capital.capitalSocialMad ?? 0).toLocaleString('fr-MA')} MAD`}
          />
          <Item
            label="Nombre de parts"
            value={String(capital.nombreParts ?? '—')}
          />
          <Item
            label="Dossier rattache"
            value={resolvedDossierId ? resolvedDossierId : 'A definir'}
          />
        </div>
      </div>

      {/* 2026-06-24 — Associes + gerants consolides (modele aligne sur Creation,
          exploitable par une future Modification). */}
      <div className="rounded-xl border border-border bg-bg-raised p-6 shadow-sm">
        <h3 className="mb-3 flex items-center gap-2 text-base font-bold text-fg">
          <Users className="h-5 w-5 text-accent" />
          Associes &amp; gerants
        </h3>
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          <div>
            <p className="mb-1 text-xs font-semibold uppercase text-fg-subtle">
              Associes ({associes.length})
            </p>
            {associes.length === 0 ? (
              <p className="text-xs text-fg-subtle">Aucun associe saisi.</p>
            ) : (
              <ul className="space-y-1 text-sm text-fg">
                {associes.map((a, i) => (
                  <li key={`a-${i}`} className="flex items-center justify-between gap-2">
                    <span>
                      {(a.prenom ?? '').trim()} {(a.nom ?? '').trim() || '—'}
                    </span>
                    <span className="text-xs text-fg-subtle">
                      {a.nombreParts ? `${a.nombreParts} parts` : ''}
                      {a.pourcentageDetention
                        ? ` · ${Number(a.pourcentageDetention).toFixed(2)} %`
                        : ''}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </div>
          <div>
            <p className="mb-1 text-xs font-semibold uppercase text-fg-subtle">
              Gerants ({gerants.length})
            </p>
            {gerants.length === 0 ? (
              <p className="text-xs text-fg-subtle">Aucun gerant saisi.</p>
            ) : (
              <ul className="space-y-1 text-sm text-fg">
                {gerants.map((g, i) => (
                  <li key={`g-${i}`} className="flex items-center justify-between gap-2">
                    <span>
                      {(g.prenom ?? '').trim()} {(g.nom ?? '').trim() || '—'}
                    </span>
                    <span className="text-xs text-fg-subtle">{g.cin ?? ''}</span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      </div>

      <div className="rounded-xl border border-border bg-bg-raised p-6 shadow-sm">
        <h3 className="mb-3 text-base font-bold text-fg">Documents importés</h3>
        <ul className="mb-3 space-y-1 text-sm text-fg-muted">
          <li>
            Juridique : {uploadedJuridique} / {docs.length} document(s) déposé(s)
          </li>
          <li>
            Comptable : {uploadedFin} / {fin.length} document(s) déposé(s)
          </li>
          <li>
            Fiscal : {uploadedFiscal} / {fiscalDocs.length} document(s) déposé(s)
          </li>
        </ul>
        {/* PROMPT H — guide complétude par dossier, non bloquant. */}
        <div className="grid grid-cols-1 gap-3 lg:grid-cols-3">
          <CompletenessPanel title="Juridique" items={completionJuridique} />
          <CompletenessPanel title="Comptable" items={completionComptable} />
          <CompletenessPanel title="Fiscal" items={completionFiscal} />
        </div>
        <p className="mt-2 text-[11px] text-fg-subtle">
          Indicatif — un dossier vide ne bloque pas la finalisation.
        </p>
      </div>

      {/* 2026-06-24 — Suivi fiscal : exercice(s) + echeances. Avant finalisation,
          on montre ce qui SERA ouvert ; apres, le resultat reel + le nb d'echeances. */}
      <div className="rounded-xl border border-border bg-bg-raised p-6 shadow-sm">
        <h3 className="mb-3 flex items-center gap-2 text-base font-bold text-fg">
          <CalendarClock className="h-5 w-5 text-accent" />
          Suivi fiscal
        </h3>
        <p className="mb-3 text-xs text-fg-muted">
          Regime TVA :{' '}
          <strong className="text-fg">{regimeTvaMensuel ? 'Mensuel' : 'Trimestriel'}</strong>
        </p>

        {exerciceResults && exerciceResults.length > 0 ? (
          <>
            <ul className="space-y-1 text-sm">
              {exerciceResults.map((r) => (
                <li key={r.annee} className="flex items-center justify-between gap-2">
                  <span className="text-fg">Exercice {r.annee}</span>
                  <span
                    className={`rounded-full px-2 py-0.5 text-[11px] font-semibold ${
                      r.status === 'OPENED'
                        ? 'bg-success/10 text-success'
                        : r.status === 'EXISTS'
                          ? 'bg-accent/10 text-accent'
                          : 'bg-danger/10 text-danger'
                    }`}
                    title={r.message}
                  >
                    {r.status === 'OPENED'
                      ? 'Ouvert'
                      : r.status === 'EXISTS'
                        ? 'Deja ouvert'
                        : 'Echec'}
                  </span>
                </li>
              ))}
            </ul>
            {echeancesTotal != null && (
              <p className="mt-3 rounded-lg bg-success/10 px-3 py-2 text-sm font-semibold text-success">
                {echeancesTotal} echeance(s) au calendrier du dossier.
              </p>
            )}
          </>
        ) : (
          <div className="text-sm text-fg-muted">
            {plannedAnnee ? (
              <p>
                Exercice(s) a ouvrir a la finalisation :{' '}
                <strong className="text-fg">
                  {[plannedAnnee, ...plannedAnterieures.filter((y) => y !== plannedAnnee)]
                    .sort((a, b) => b - a)
                    .join(', ')}
                </strong>{' '}
                — les echeances seront generees automatiquement.
              </p>
            ) : (
              <p className="text-fg-subtle">
                Aucun exercice selectionne a l'etape Suivi.
              </p>
            )}
          </div>
        )}
      </div>

      <div className="rounded-xl border border-border bg-bg-raised p-6 shadow-sm">
        <label className="flex items-start gap-2 text-sm text-fg">
          <input
            type="checkbox"
            checked={validated}
            onChange={(ev) => setValidated(ev.target.checked)}
            className="mt-1 h-4 w-4 rounded"
          />
          <span>
            Je certifie que les informations et documents importes sont exacts
            et conformes a la realite de la societe.
          </span>
        </label>
      </div>

      <div className="flex items-center justify-end pt-2">
        <button
          type="submit"
          disabled={!validated || saving}
          className={`flex items-center gap-2 rounded-lg px-8 h-12 font-medium transition ${
            validated && !saving
              ? 'bg-success text-bg-raised hover:bg-success/85'
              : 'cursor-not-allowed bg-border text-fg-subtle'
          }`}
        >
          {saving ? 'Validation en cours...' : 'Finaliser'}
          <ChevronRight className="h-4 w-4" />
        </button>
      </div>
    </form>
  );
}

function Item({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-start gap-2">
      <CheckCircle2 className="mt-0.5 h-4 w-4 flex-shrink-0 text-success" />
      <div>
        <p className="text-xs font-semibold uppercase text-fg-subtle">{label}</p>
        <p className="text-sm text-fg">{value}</p>
      </div>
    </div>
  );
}

function CompletenessPanel({
  title,
  items,
}: {
  title: string;
  items: Array<{ value: string; label?: string; done: boolean }>;
}) {
  const done = items.filter((i) => i.done).length;
  return (
    <div
      data-testid={`step5-completeness-${title.toLowerCase()}`}
      className="rounded-lg border border-border bg-bg-overlay p-3"
    >
      <p className="mb-1 flex items-center justify-between text-xs font-semibold text-fg">
        {title}
        <span className="rounded-full bg-accent/10 px-2 py-0.5 text-[10px] font-medium text-accent">
          {done} / {items.length}
        </span>
      </p>
      <ul className="space-y-0.5">
        {items.map((i) => (
          <li
            key={i.value}
            data-done={i.done}
            className="flex items-center gap-1 text-[11px] text-fg-muted"
          >
            {i.done ? (
              <Check className="h-3 w-3 text-success" />
            ) : (
              <Square className="h-3 w-3 text-fg-subtle" />
            )}
            {i.label ?? i.value}
          </li>
        ))}
      </ul>
    </div>
  );
}
