import { useId } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { TextField } from '../ui/TextField';
import { Select } from '../ui/Select';
import { InfoBulle, TexteAide } from '../ui/Aide';
import { ValeurProposee } from './ValeurProposee';
import {
  rapportGestionDateErreur,
  rapportGestionManquants,
  type EvenementPostCloture,
  type FaitMarquant,
  type Indicateur,
  type OuiNon,
  type RapportGestionSaisie,
} from './rapportGestion';

/**
 * Lot L4d (E11e) : champs du rapport de gestion de la gerance (approbation des comptes,
 * etape 2). Le rapport est rendu par le modele du corpus de la forme de la societe ; les
 * donnees du dossier et des comptes n'y sont pas ressaisies. Aide integree (CDC 15.4) :
 * ce qu'il faut faire, ce qui manque, ce qui vient ensuite.
 */

const OUI_NON = [
  { value: '', label: 'Choisir…' },
  { value: 'non', label: 'Non' },
  { value: 'oui', label: 'Oui' },
];

const champTexte =
  'w-full rounded-lg border border-border-hi bg-bg-raised px-3 py-2 text-sm focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-200';

function ZoneTexte({
  label,
  aide,
  value,
  onChange,
  testId,
}: {
  label: string;
  aide: string;
  value: string;
  onChange: (v: string) => void;
  testId: string;
}) {
  const id = useId();
  return (
    <div>
      <label htmlFor={id} className="mb-1 flex items-center gap-1 text-sm font-medium text-fg-muted">
        {label}
        <InfoBulle libelle={`Aide : ${label}`} texte={aide} />
      </label>
      <textarea id={id} rows={3} value={value} data-testid={testId} onChange={(e) => onChange(e.target.value)} className={champTexte} />
    </div>
  );
}

function BoutonAjouter({ libelle, onClick }: { libelle: string; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="inline-flex items-center gap-1 rounded-lg border border-border bg-bg-raised px-2.5 py-1 text-xs font-medium text-fg hover:border-accent"
    >
      <Plus className="h-3.5 w-3.5" /> {libelle}
    </button>
  );
}

function BoutonRetirer({ libelle, onClick, disabled }: { libelle: string; onClick: () => void; disabled: boolean }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={libelle}
      className="inline-flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg border border-border bg-bg-raised text-fg-subtle transition hover:border-danger hover:text-danger disabled:cursor-not-allowed disabled:opacity-40"
    >
      <Trash2 className="h-3.5 w-3.5" />
    </button>
  );
}

export function RapportGestionFields({
  valeur,
  onChange,
  resultatType,
  dateAgo,
  propositionDebut,
  propositionLieu,
}: {
  valeur: RapportGestionSaisie;
  onChange: (v: RapportGestionSaisie) => void;
  /** Sens du resultat saisi plus haut : « bénéfice », « perte » ou vide. */
  resultatType: string;
  dateAgo?: string;
  /** Ouverture de l'exercice proposee (1er janvier de l'exercice clos), a confirmer. */
  propositionDebut: string;
  /** Lieu de signature propose (ville du siege), a confirmer. */
  propositionLieu: string;
}) {
  const set = <K extends keyof RapportGestionSaisie>(k: K, v: RapportGestionSaisie[K]) => onChange({ ...valeur, [k]: v });
  const manquants = rapportGestionManquants(valeur, { resultatType, dateAgo });
  const erreurDate = rapportGestionDateErreur(valeur, dateAgo);

  const majFait = (i: number, patch: Partial<FaitMarquant>) =>
    set('faitsMarquants', valeur.faitsMarquants.map((f, j) => (j === i ? { ...f, ...patch } : f)));
  const majIndicateur = (i: number, patch: Partial<Indicateur>) =>
    set('indicateurs', valeur.indicateurs.map((x, j) => (j === i ? { ...x, ...patch } : x)));
  const majEvenement = (i: number, patch: Partial<EvenementPostCloture>) =>
    set('evenementsPostClotureListe', valeur.evenementsPostClotureListe.map((x, j) => (j === i ? { ...x, ...patch } : x)));

  return (
    <div className="space-y-4" data-testid="pvago-rapport-gestion">
      <TexteAide cle="pvago-rapport-gestion" titre="Rapport de gestion de la gérance">
        <p>
          Renseignez ici ce que seule la gérance peut exposer : l’activité et les faits marquants de
          l’exercice, les principaux indicateurs comparés à l’exercice précédent, les perspectives, et le
          lieu et la date du rapport. La dénomination, le siège, l’objet social, le capital, les parts et
          les gérants sont repris du dossier ; le résultat, l’affectation, les dividendes, le commissaire
          aux comptes et les conventions réglementées viennent des rubriques ci-dessus.
        </p>
        <p className="mt-1">
          Le rapport est facultatif : ces champs ne bloquent pas l’étape. Il se génère à l’étape 3, avec
          le modèle du cabinet adapté à la forme de la société (SARL ou SARL à associé unique).
        </p>
      </TexteAide>

      {manquants.length > 0 ? (
        <div role="status" data-testid="pvago-rapport-manquants" className="rounded-lg border border-warning/40 bg-warning/10 p-2 text-xs text-fg-muted">
          Pour générer le rapport de gestion, il reste à saisir : {manquants.join(' ; ')}.
        </div>
      ) : (
        <div role="status" data-testid="pvago-rapport-manquants" className="rounded-lg border border-success/40 bg-success/10 p-2 text-xs text-fg-muted">
          Le rapport de gestion est complet : vous pourrez le générer à l’étape 3.
        </div>
      )}

      <div className="grid gap-3 md:grid-cols-2">
        <ValeurProposee
          id="pvago-rg-exercice-debut"
          label="Ouverture de l’exercice"
          type="date"
          proposition={propositionDebut}
          valeur={valeur.exerciceDebut || undefined}
          onChange={(v) => set('exerciceDebut', v)}
        />
        <TextField
          label="Capitaux propres à la clôture (MAD)"
          type="number"
          value={valeur.capitauxPropres}
          data-testid="pvago-rg-capitaux-propres"
          onChange={(e) => set('capitauxPropres', e.target.value)}
          hint="Montant du bilan. S’il est inférieur au quart du capital, le rapport annonce la consultation des associés prévue par l’article 86 de la loi 5-96."
        />
      </div>

      <ZoneTexte
        label="Activité de l’exercice"
        aide="Exposez la marche des affaires pendant l’exercice : clients, contrats, effectifs, investissements. Le texte suit, dans le rapport, le rappel de l’objet social."
        value={valeur.activiteExpose}
        onChange={(v) => set('activiteExpose', v)}
        testId="pvago-rg-activite"
      />

      <section className="space-y-2">
        <div className="flex items-center justify-between">
          <h4 className="flex items-center gap-1 text-sm font-semibold text-fg">
            Faits marquants de l’exercice
            <InfoBulle
              libelle="Aide : faits marquants"
              texte="Un fait par ligne, avec sa date (ouverture d’un établissement, contrat important, cession de parts…). Le rapport en dresse la liste ; il en faut au moins un."
            />
          </h4>
          <BoutonAjouter libelle="Ajouter un fait" onClick={() => set('faitsMarquants', [...valeur.faitsMarquants, { date: '', libelle: '' }])} />
        </div>
        {valeur.faitsMarquants.map((f, i) => (
          <div key={i} className="grid items-end gap-2 md:grid-cols-[10rem_1fr_auto]">
            <TextField label={`Date du fait ${i + 1}`} type="date" value={f.date} onChange={(e) => majFait(i, { date: e.target.value })} />
            <TextField label={`Fait marquant ${i + 1}`} value={f.libelle} onChange={(e) => majFait(i, { libelle: e.target.value })} />
            <BoutonRetirer
              libelle={`Retirer le fait ${i + 1}`}
              disabled={valeur.faitsMarquants.length <= 1}
              onClick={() => set('faitsMarquants', valeur.faitsMarquants.filter((_, j) => j !== i))}
            />
          </div>
        ))}
      </section>

      <section className="space-y-2">
        <div className="flex items-center justify-between">
          <h4 className="flex items-center gap-1 text-sm font-semibold text-fg">
            Principaux indicateurs
            <InfoBulle
              libelle="Aide : indicateurs"
              texte="Chiffre d’affaires, résultat d’exploitation, résultat net, effectif… Saisissez les montants de l’exercice et de l’exercice précédent tels qu’ils figurent aux états de synthèse : la plateforme ne calcule aucune variation."
            />
          </h4>
          <BoutonAjouter
            libelle="Ajouter un indicateur"
            onClick={() => set('indicateurs', [...valeur.indicateurs, { libelle: '', montantExercice: '', montantExercicePrecedent: '' }])}
          />
        </div>
        {valeur.indicateurs.map((x, i) => (
          <div key={i} className="grid items-end gap-2 md:grid-cols-[1fr_10rem_10rem_auto]">
            <TextField label={`Indicateur ${i + 1}`} value={x.libelle} onChange={(e) => majIndicateur(i, { libelle: e.target.value })} />
            <TextField label="Exercice clos" type="number" value={x.montantExercice} onChange={(e) => majIndicateur(i, { montantExercice: e.target.value })} />
            <TextField label="Exercice précédent" type="number" value={x.montantExercicePrecedent} onChange={(e) => majIndicateur(i, { montantExercicePrecedent: e.target.value })} />
            <BoutonRetirer
              libelle={`Retirer l’indicateur ${i + 1}`}
              disabled={valeur.indicateurs.length <= 1}
              onClick={() => set('indicateurs', valeur.indicateurs.filter((_, j) => j !== i))}
            />
          </div>
        ))}
      </section>

      <ZoneTexte
        label="Commentaire de la gérance sur l’évolution"
        aide="Expliquez les écarts entre les deux exercices : ce qui a fait progresser ou reculer l’activité et les résultats."
        value={valeur.evolutionCommentaire}
        onChange={(v) => set('evolutionCommentaire', v)}
        testId="pvago-rg-evolution"
      />

      <div className="grid gap-3 md:grid-cols-2">
        <Select
          label="Événements importants depuis la clôture ?"
          value={valeur.evenementsPostCloture}
          data-testid="pvago-rg-evenements"
          onChange={(e) => set('evenementsPostCloture', e.target.value as OuiNon)}
          options={OUI_NON}
          hint="« Non » : le rapport l’indique expressément. « Oui » : décrivez chaque événement."
        />
        <Select
          label="Travaux de recherche et de développement ?"
          value={valeur.rechercheDeveloppement}
          data-testid="pvago-rg-rd"
          onChange={(e) => set('rechercheDeveloppement', e.target.value as OuiNon)}
          options={OUI_NON}
        />
      </div>

      {valeur.evenementsPostCloture === 'oui' && (
        <section className="space-y-2">
          <div className="flex items-center justify-between">
            <h4 className="text-sm font-semibold text-fg">Événements survenus depuis la clôture</h4>
            <BoutonAjouter
              libelle="Ajouter un événement"
              onClick={() => set('evenementsPostClotureListe', [...valeur.evenementsPostClotureListe, { date: '', libelle: '', incidence: '' }])}
            />
          </div>
          {valeur.evenementsPostClotureListe.map((x, i) => (
            <div key={i} className="grid items-end gap-2 md:grid-cols-[10rem_1fr_1fr_auto]">
              <TextField label={`Date de l’événement ${i + 1}`} type="date" value={x.date} onChange={(e) => majEvenement(i, { date: e.target.value })} />
              <TextField label={`Événement ${i + 1}`} value={x.libelle} onChange={(e) => majEvenement(i, { libelle: e.target.value })} />
              <TextField label="Incidence sur la société" value={x.incidence} onChange={(e) => majEvenement(i, { incidence: e.target.value })} />
              <BoutonRetirer
                libelle={`Retirer l’événement ${i + 1}`}
                disabled={valeur.evenementsPostClotureListe.length <= 1}
                onClick={() => set('evenementsPostClotureListe', valeur.evenementsPostClotureListe.filter((_, j) => j !== i))}
              />
            </div>
          ))}
        </section>
      )}

      {valeur.rechercheDeveloppement === 'oui' && (
        <ZoneTexte
          label="Travaux de recherche et de développement"
          aide="Objet, état d’avancement et coût des travaux engagés pendant l’exercice."
          value={valeur.rechercheDeveloppementExpose}
          onChange={(v) => set('rechercheDeveloppementExpose', v)}
          testId="pvago-rg-rd-expose"
        />
      )}

      <ZoneTexte
        label="Évolution prévisible et perspectives"
        aide="Ce que la gérance prévoit pour l’exercice en cours et les suivants."
        value={valeur.perspectives}
        onChange={(v) => set('perspectives', v)}
        testId="pvago-rg-perspectives"
      />

      {resultatType === 'bénéfice' && (
        <Select
          label="La réserve légale a-t-elle atteint le cinquième du capital ?"
          value={valeur.reserveLegaleDoteeIntegralement}
          data-testid="pvago-rg-reserve-legale"
          onChange={(e) => set('reserveLegaleDoteeIntegralement', e.target.value as OuiNon)}
          options={OUI_NON}
          hint="« Oui » : le rapport indique qu’aucune dotation n’est due. « Non » : il rappelle le prélèvement de 5 % du bénéfice."
        />
      )}
      {resultatType === 'perte' && (
        <TextField
          label="Pertes cumulées après affectation (MAD)"
          type="number"
          value={valeur.pertesCumulees}
          data-testid="pvago-rg-pertes-cumulees"
          onChange={(e) => set('pertesCumulees', e.target.value)}
          hint="Montant total des pertes reportées, une fois la perte de l’exercice affectée."
        />
      )}

      <div className="grid gap-3 md:grid-cols-2">
        <ValeurProposee
          id="pvago-rg-lieu"
          label="Lieu de signature du rapport"
          proposition={propositionLieu}
          valeur={valeur.lieuSignature || undefined}
          onChange={(v) => set('lieuSignature', v)}
        />
        <TextField
          label="Date du rapport"
          type="date"
          value={valeur.date}
          data-testid="pvago-rg-date"
          onChange={(e) => set('date', e.target.value)}
          error={erreurDate}
          hint="Le rapport est établi par la gérance et communiqué aux associés avant l’assemblée."
        />
      </div>
    </div>
  );
}
