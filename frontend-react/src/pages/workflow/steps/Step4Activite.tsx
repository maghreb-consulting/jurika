import { useState } from 'react';
import { AlertTriangle, Briefcase, ChevronRight } from 'lucide-react';
import { useStepAutosave } from '../useStepAutosave';
import { normalizeActivites } from '../objetSocial';
import { BlocageValidation } from '../../../components/workflow/BlocageValidation';

interface Props {
  existing?: Record<string, unknown>;
  saving: boolean;
  onSubmit: (payload: Record<string, unknown>) => Promise<void>;
  onSave?: (payload: Record<string, unknown>) => Promise<void>;
  /**
   * 2026-08-15 (fix IMPORT) — En CREATION la date de commencement de l'exercice
   * est saisie à l'étape Capital (Step3). En IMPORT, Step3 masque ce champ, or
   * le backend `ImportWorkflow.handleActivite` exige `dateDebutExercice` sur le
   * payload de CETTE étape. Le flag réexpose donc le champ ICI, uniquement en
   * import, sans impacter la CREATION (dont le backend ne l'exige pas ici).
   */
  importMode?: boolean;
  /** P1 2026-06-21 — Cowork : enregistre un getter "dirty" pour flush au navigation. */
  registerDirty?: (
    step: number,
    getter: () => Record<string, unknown> | null,
  ) => () => void;
}

const CATEGORIES_ONA = [
  'Agriculture, foret et peche',
  'Industries extractives',
  'Industrie manufacturiere',
  'BTP & Construction',
  'Commerce',
  'Transport et entreposage',
  'Hebergement et restauration',
  'Information et communication',
  'Services financiers et assurance',
  'Activites immobilieres',
  'Services aux entreprises',
  'Education et formation',
  'Sante humaine et action sociale',
  'Arts, spectacles et loisirs',
  'Autres services',
];

const SECTEURS = [
  'Primaire',
  'Secondaire',
  'Tertiaire',
  'Numerique / Tech',
  'Industriel',
  'Commercial',
];

export function Step4Activite({ existing, saving, onSubmit, importMode = false, registerDirty }: Props) {
  // Persistance : donnee stockee sous `activite` (executeStep) ou a plat (draft).
  const e =
    (existing?.activite as Record<string, unknown>) ??
    (existing as Record<string, unknown>) ??
    {};
  // Ré-hydratation : la description brute (multi-lignes) fait foi ; à défaut on
  // reconstruit le texte depuis la liste `activites[]` (une activité par ligne).
  const [description, setDescription] = useState<string>(
    (e.description as string) ??
      (Array.isArray(e.activites) ? (e.activites as string[]).join('\n') : '') ??
      '',
  );
  const [reglementee, setReglementee] = useState<boolean>(
    (e.activiteReglementee as boolean) ?? false,
  );
  const [categorieOna, setCategorieOna] = useState<string>(
    (e.categorieOna as string) ?? '',
  );
  const [secteur, setSecteur] = useState<string>((e.secteur as string) ?? '');
  // 2026-08-15 (fix IMPORT) — date de début d'exercice, requise par le backend
  // IMPORT à cette étape (voir Props.importMode). Ignorée en CREATION.
  const [dateDebutExercice, setDateDebutExercice] = useState<string>(
    (e.dateDebutExercice as string) ?? '',
  );

  const canSubmit =
    description.trim().length > 10 && (!importMode || !!dateDebutExercice);

  // Objet social multi-activités (2026-08) : une activité par ligne non vide.
  // On persiste la liste dérivée `activites[]` en plus de la `description` brute
  // (rétro-compatible) ; le builder l'utilise pour rendre l'objet en liste.
  const activites = normalizeActivites(undefined, description);

  // P1 2026-06-21 — autosave manquant sur Step4 -> valeurs perdues a la
  // navigation retour. Le getter miroite le payload onSubmit.
  useStepAutosave(
    4,
    () => ({
      description,
      activites,
      activiteReglementee: reglementee,
      categorieOna,
      secteur,
      ...(importMode ? { dateDebutExercice } : {}),
    }),
    registerDirty,
  );

  return (
    <form
      noValidate
      onSubmit={(ev) => {
        ev.preventDefault();
        onSubmit({
          description,
          activites,
          activiteReglementee: reglementee,
          categorieOna,
          secteur,
          ...(importMode ? { dateDebutExercice } : {}),
        });
      }}
      className="mx-auto max-w-[820px] space-y-6"
    >
      <div className="rounded-xl border border-border bg-bg-raised p-6 shadow-sm">
        <div className="mb-1 flex items-center gap-2">
          <Briefcase className="h-5 w-5 text-accent" />
          <h3 className="text-lg font-bold text-fg">
            Activite de la societe
          </h3>
        </div>
        <p className="mb-6 text-xs text-fg-subtle">
          Decrivez l'ensemble des activites de la societe. Ce texte sera repris
          dans l'objet social des statuts. <strong>Une activite par ligne</strong> :
          les statuts les presenteront alors en liste (a tirets).
        </p>

        {/* Description — une activite par ligne (objet social multi-activites). */}
        <div className="mb-6">
          <label htmlFor="creation-activites" className="mb-2 block text-sm font-medium text-fg">
            Activites de la societe{' '}
            <span className="font-normal text-fg-subtle">(une par ligne)</span>
          </label>
          {/* Champ unique (hors liste) : `htmlFor` + `id` sont possibles ici, et
              preferables — le clic sur le libelle donne aussi le focus. */}
          <textarea
            id="creation-activites"
            rows={5}
            value={description}
            onChange={(ev) => setDescription(ev.target.value)}
            placeholder={
              'Une activite par ligne, ex :\n' +
              'Le conseil et l’ingenierie informatique\n' +
              'La formation professionnelle\n' +
              'L’import-export de materiel'
            }
            className="w-full resize-none rounded-lg border-2 border-border bg-bg-overlay px-4 py-3 text-sm leading-relaxed focus:border-accent focus:outline-none"
          />
          <p className="mt-1 text-xs text-fg-subtle">
            {description.length} caracteres — minimum 10 caracteres requis.
            {activites.length > 1 && (
              <span className="ml-1 font-medium text-accent">
                {activites.length} activites detectees (rendues en liste).
              </span>
            )}
          </p>
        </div>

        {/* Reglementee */}
        <div className="mb-6">
          <label className="mb-3 block text-sm font-medium text-fg">
            Activite reglementee ?
          </label>
          <div className="flex gap-3">
            <button
              type="button"
              onClick={() => setReglementee(false)}
              className={`flex-1 rounded-lg border-2 py-3 text-sm font-medium transition ${
                !reglementee
                  ? 'border-success bg-success text-bg-raised'
                  : 'border-border bg-bg-raised text-fg-subtle hover:border-success'
              }`}
            >
              Non
            </button>
            <button
              type="button"
              onClick={() => setReglementee(true)}
              className={`flex-1 rounded-lg border-2 py-3 text-sm font-medium transition ${
                reglementee
                  ? 'border-warning bg-warning text-bg-raised'
                  : 'border-border bg-bg-raised text-fg-subtle hover:border-warning'
              }`}
            >
              Oui
            </button>
          </div>
        </div>

        {reglementee && (
          <div className="mb-6 flex items-start gap-3 rounded-lg border-l-4 border-warning bg-warning/10 p-4">
            <AlertTriangle className="mt-0.5 h-5 w-5 flex-shrink-0 text-warning" />
            <div>
              <p className="text-sm font-semibold text-warning">
                Activite reglementee
              </p>
              <p className="text-xs text-warning">
                Des autorisations prefectorales ou sectorielles seront exigees
                a l'etape Pieces jointes (autorisation, agrement, licence…).
              </p>
            </div>
          </div>
        )}

        {/* Categories ONA + Secteur (optionnels) */}
        <div className="mb-6 grid grid-cols-1 gap-4 md:grid-cols-2">
          <div>
            <label className="mb-1 block text-xs font-medium text-fg">
              Categorie ONA{' '}
              <span className="font-normal text-fg-subtle">(optionnel)</span>
            </label>
            <select
              value={categorieOna}
              onChange={(ev) => setCategorieOna(ev.target.value)}
              className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
            >
              <option value="">— Selectionnez —</option>
              {CATEGORIES_ONA.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="mb-1 block text-xs font-medium text-fg">
              Secteur economique{' '}
              <span className="font-normal text-fg-subtle">(optionnel)</span>
            </label>
            <select
              value={secteur}
              onChange={(ev) => setSecteur(ev.target.value)}
              className="h-10 w-full rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
            >
              <option value="">— Selectionnez —</option>
              {SECTEURS.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </div>
        </div>

        {/* Dé-dup 2026-08 — « Date de commencement de l'exercice » retirée d'ici :
            saisie UNIQUE à l'étape Capital (Step3), qui calcule aussi la date de fin.
            2026-08-15 (fix IMPORT) — réexposée UNIQUEMENT en import, car Step3 la
            masque en importMode alors que le backend IMPORT l'exige ici. */}
        {importMode && (
          <div className="mt-6 border-t border-border pt-6">
            <label
              htmlFor="import-date-debut-exercice"
              className="mb-2 block text-sm font-medium text-fg"
            >
              Date de début du premier exercice repris{' '}
              <span className="text-danger">*</span>
            </label>
            <input
              id="import-date-debut-exercice"
              type="date"
              value={dateDebutExercice}
              onChange={(ev) => setDateDebutExercice(ev.target.value)}
              className="h-10 w-full max-w-xs rounded-lg border-2 border-border bg-bg-overlay px-3 text-sm focus:border-accent focus:outline-none"
            />
            <p className="mt-1 text-xs text-fg-subtle">
              Alimente le suivi des exercices fiscaux (étape Suivi).
            </p>
          </div>
        )}
      </div>

      {/* Fix A4 (principe transverse) — motif affiché plutôt qu'un bouton
          silencieusement grisé. En import, la date d'exercice est exigée par le
          backend : sans message, l'étape paraissait bloquée sans raison. */}
      <BlocageValidation
        testId="step4-blocage"
        raisons={
          canSubmit
            ? []
            : [
                description.trim().length <= 10 &&
                  'Décrivez les activités de la société (au moins 10 caractères).',
                importMode && !dateDebutExercice &&
                  'Renseignez la date de début du premier exercice repris.',
              ]
        }
      />

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
          {saving ? 'Enregistrement…' : 'Valider et continuer'}
          <ChevronRight className="h-4 w-4" />
        </button>
      </div>
    </form>
  );
}
