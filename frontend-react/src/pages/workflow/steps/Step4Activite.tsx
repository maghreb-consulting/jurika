import { useState } from 'react';
import { AlertTriangle, Briefcase, ChevronRight } from 'lucide-react';
import { useStepAutosave } from '../useStepAutosave';
import { normalizeActivites } from '../objetSocial';
import { BlocageValidation } from '../../../components/workflow/BlocageValidation';
import { InfoBulle, TexteAide } from '../../../components/ui/Aide';
import { GroupeRadio } from '../../../components/ui/Choix';
import { TextField, ZoneTexte } from '../../../components/ui/TextField';
import { Select } from '../../../components/ui/Select';
import { Button } from '../../../components/ui/Button';

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

/**
 * Libelles affiches des categories et secteurs (L6, langue) : la valeur enregistree reste
 * celle de la liste (donnees deja saisies inchangees), seul l'affichage est accentue.
 */
const LIBELLES_LISTES: Record<string, string> = {
  'Agriculture, foret et peche': 'Agriculture, forêt et pêche',
  'Industrie manufacturiere': 'Industrie manufacturière',
  'BTP & Construction': 'BTP et construction',
  'Hebergement et restauration': 'Hébergement et restauration',
  'Activites immobilieres': 'Activités immobilières',
  'Education et formation': 'Éducation et formation',
  'Sante humaine et action sociale': 'Santé humaine et action sociale',
  'Numerique / Tech': 'Numérique et technologies',
};

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
          <Briefcase className="h-5 w-5 text-fg-muted" />
          <h3 className="text-lg font-bold text-fg">
            Activité de la société
          </h3>
        </div>
        <p className="mb-4 text-xs text-fg-subtle">
          Décrivez l’ensemble des activités de la société. Ce texte sera repris
          dans l’objet social des statuts. <strong>Une activité par ligne</strong> :
          les statuts les présenteront alors en liste (à tirets).
        </p>
        <div className="mb-6">
          <TexteAide cle={importMode ? 'import-etape-activite' : 'creation-etape-4'} titre="Ce qu’il faut faire">
            {importMode
              ? 'Reprenez l’objet social tel qu’il figure aux statuts, une activité par ligne, indiquez la date de début du premier exercice repris, puis cliquez sur « Valider et continuer ».'
              : 'Saisissez les activités, une par ligne, en reprenant l’activité portée sur le certificat négatif. Indiquez si l’activité est réglementée : les autorisations seront alors demandées avec les pièces jointes. Cliquez ensuite sur « Valider et continuer » ; l’étape suivante porte sur la gérance.'}
          </TexteAide>
        </div>

        {/* Description — une activite par ligne (objet social multi-activites). */}
        <div className="mb-6">
          <label htmlFor="creation-activites" className="mb-2 block text-sm font-medium text-fg">
            Activités de la société{' '}
            <span className="font-normal text-fg-subtle">(une par ligne)</span>
          </label>
          {/* Champ unique (hors liste) : `htmlFor` + `id` sont possibles ici, et
              preferables — le clic sur le libelle donne aussi le focus. */}
          <ZoneTexte
            id="creation-activites"
            rows={5}
            value={description}
            onChange={(ev) => setDescription(ev.target.value)}
            placeholder={
              'Une activité par ligne, par exemple :\n' +
              'Le conseil et l’ingénierie informatique\n' +
              'La formation professionnelle\n' +
              'L’import-export de matériel'
            }
            className="resize-none leading-relaxed"
          />
          <p className="mt-1 text-xs text-fg-subtle">
            {description.length} caractères (plus de 10 caractères attendus).
            {activites.length > 1 && (
              <span className="ml-1 font-medium text-heading">
                {activites.length} activités détectées (présentées en liste).
              </span>
            )}
          </p>
        </div>

        {/* Reglementee */}
        <div className="mb-6">
          <GroupeRadio
            legende={
              <span className="flex items-center gap-1">
                Activité réglementée ?
                <InfoBulle
                  libelle="Qu’est-ce qu’une activité réglementée ?"
                  texte="Une activité soumise à une autorisation, un agrément ou une licence préalable (transport, santé, enseignement, établissement financier, débit de boissons…). Répondez « Oui » en cas de doute : la pièce sera demandée à l’étape des pièces jointes."
                />
              </span>
            }
            name="creation-activite-reglementee"
            horizontal
            value={reglementee ? 'oui' : 'non'}
            onChange={(v) => setReglementee(v === 'oui')}
            options={[
              { value: 'non', label: 'Non' },
              { value: 'oui', label: 'Oui' },
            ]}
          />
        </div>

        {reglementee && (
          <div className="mb-6 flex items-start gap-3 rounded-lg border-l-4 border-warning bg-warning-soft p-4">
            <AlertTriangle className="mt-0.5 h-5 w-5 flex-shrink-0 text-warning" />
            <div>
              <p className="text-sm font-semibold text-warning">
                Activité réglementée
              </p>
              <p className="text-xs text-warning">
                Des autorisations préfectorales ou sectorielles seront exigées
                à l’étape des pièces jointes (autorisation, agrément, licence…).
              </p>
            </div>
          </div>
        )}

        {/* Categories ONA + Secteur (optionnels) */}
        <div className="mb-6 grid grid-cols-1 gap-4 md:grid-cols-2">
          <div>
            <Select
              label="Catégorie ONA (facultatif)"
              aria-label="Catégorie ONA"
              value={categorieOna}
              onChange={(ev) => setCategorieOna(ev.target.value)}
            >
              <option value="">— Sélectionnez —</option>
              {CATEGORIES_ONA.map((c) => (
                <option key={c} value={c}>
                  {LIBELLES_LISTES[c] ?? c}
                </option>
              ))}
            </Select>
          </div>
          <div>
            <Select
              label="Secteur économique (facultatif)"
              aria-label="Secteur économique"
              value={secteur}
              onChange={(ev) => setSecteur(ev.target.value)}
            >
              <option value="">— Sélectionnez —</option>
              {SECTEURS.map((s) => (
                <option key={s} value={s}>
                  {LIBELLES_LISTES[s] ?? s}
                </option>
              ))}
            </Select>
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
            <TextField
              id="import-date-debut-exercice"
              type="date"
              value={dateDebutExercice}
              onChange={(ev) => setDateDebutExercice(ev.target.value)}
              hint="Sert au suivi des exercices comptables de la société (étape Suivi)."
              containerClassName="max-w-xs"
            />
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
                  'Décrivez les activités de la société (plus de 10 caractères).',
                importMode && !dateDebutExercice &&
                  'Renseignez la date de début du premier exercice repris.',
              ]
        }
      />

      <div className="flex items-center justify-end pt-2">
        <Button
          type="submit"
          disabled={!canSubmit || saving}
          size="lg"
          iconEnd={<ChevronRight />}
        >
          {saving ? 'Enregistrement…' : 'Valider et continuer'}
        </Button>
      </div>
    </form>
  );
}
