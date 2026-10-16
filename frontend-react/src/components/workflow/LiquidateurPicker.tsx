/**
 * Désignation du liquidateur — lot « Dissolution 4 étapes » (2026-08-12).
 *
 * RÈGLE : sélection **depuis la BD d'abord**. La liste déroulante propose les
 * gérants puis les associés (personnes physiques) du dossier, lus via
 * `GET /workflows/dossiers/{id}/parties` — aucune re-saisie d'identité. Le
 * liquidateur peut aussi être **externe** : saisie manuelle assistée par l'OCR CIN
 * ({@link IdentityExtractor} en mode « cin »).
 *
 * Les valeurs alimentent `$LIQUIDATEUR_CIVILITE / _PRENOM / _NOM / _ADRESSE` de
 * l'annonce légale et le bloc liquidateur du PV de dissolution.
 */
import { UserCheck } from 'lucide-react';
import { Select } from '../ui/Select';
import { TextField } from '../ui/TextField';
import { IdentityExtractor } from '../identity/IdentityExtractor';
import { TypePieceProposee } from './ValeurProposee';
import { TexteAide } from '../ui/Aide';
import { estPersonneMorale } from '../../lib/liquidateur';
import { EXTERNE_KEY, type LiquidateurOption, type LiquidateurState } from './liquidateurOptions';

export type { LiquidateurOption, LiquidateurSource, LiquidateurState } from './liquidateurOptions';
export { EXTERNE_KEY } from './liquidateurOptions';

export interface LiquidateurPickerProps {
  value: LiquidateurState;
  onChange: (next: LiquidateurState) => void;
  options: LiquidateurOption[];
  /** Chargement des parties prenantes en cours (BD). */
  loading?: boolean;
  /** Dossier pour l'archivage de la CIN scannée (mode externe). */
  dossierId?: string | null;
  /** Message d'erreur à afficher sous le bloc (validation de la page hôte). */
  error?: string | null;
}

/**
 * Lot L4d (E11d) : remuneration du liquidateur, telle que le PV du corpus la redige
 * (a titre gratuit, ou montant et modalites). Remplace le champ libre propose
 * "exercees a titre gratuit".
 */
function RemunerationLiquidateur({
  value,
  set,
}: {
  value: LiquidateurState;
  set: (patch: Partial<LiquidateurState>) => void;
}) {
  return (
    <div className="grid gap-3 md:grid-cols-3" data-testid="liquidateur-remuneration">
      <Select
        label="Rémunération du liquidateur *"
        value={value.remunerationApplicable ?? ''}
        data-testid="liquidateur-remuneration-applicable"
        onChange={(e) => set({ remunerationApplicable: e.target.value as '' | 'oui' | 'non' })}
        hint={value.remunerationApplicable ? undefined : 'À indiquer : le procès-verbal fixe la rémunération ou la gratuité des fonctions.'}
        options={[
          { value: '', label: 'Choisir…' },
          { value: 'non', label: 'Fonctions exercées à titre gratuit' },
          { value: 'oui', label: 'Fonctions rémunérées' },
        ]}
      />
      {value.remunerationApplicable === 'oui' && (
        <>
          <TextField
            label="Montant (dirhams) *"
            type="number"
            value={value.remunerationMontant ?? ''}
            data-testid="liquidateur-remuneration-montant"
            onChange={(e) => set({ remunerationMontant: e.target.value })}
          />
          <TextField
            label="Modalités de versement *"
            value={value.remunerationModalites ?? ''}
            data-testid="liquidateur-remuneration-modalites"
            onChange={(e) => set({ remunerationModalites: e.target.value })}
            placeholder="Ex. : payable en fin de mission"
          />
        </>
      )}
    </div>
  );
}

export function LiquidateurPicker({
  value,
  onChange,
  options,
  loading,
  dossierId,
  error,
}: LiquidateurPickerProps) {
  const set = (patch: Partial<LiquidateurState>) => onChange({ ...value, ...patch });

  const selectValue = value.source === 'EXTERNE' ? EXTERNE_KEY : value.partieKey;

  const handleSelect = (key: string) => {
    if (key === EXTERNE_KEY) {
      // Bascule externe : on repart d'une identité vierge (l'OCR CIN la remplira).
      set({ source: 'EXTERNE', partieKey: '', forme: 'physique', civilite: 'M.', prenom: '', nom: '', cin: '', denomination: '' });
      return;
    }
    const opt = options.find((o) => o.key === key);
    if (!opt) {
      set({ source: 'BD', partieKey: '' });
      return;
    }
    if (opt.morale) {
      set({
        source: 'BD', partieKey: opt.key, forme: 'morale', civilite: '', prenom: '', nom: '', cin: '',
        denomination: opt.denomination, formeJuridique: opt.formeJuridique, siegeSocial: opt.siegeSocial,
        rcNumero: opt.rcNumero, rcVille: opt.rcVille, ice: opt.ice, representantNom: opt.representantNom,
        adresse: opt.siegeSocial || value.adresse,
      });
      return;
    }
    set({
      source: 'BD',
      partieKey: opt.key,
      forme: 'physique',
      civilite: opt.civilite,
      prenom: opt.prenom,
      nom: opt.nom,
      cin: opt.cin,
      // L'adresse connue est pré-remplie ; sinon l'employé la saisit (publiée au JAL).
      adresse: opt.adresse || value.adresse,
      // L6b (E15) : nationalité et date de naissance connues du dossier, sans re-saisie.
      nationalite: opt.nationalite ?? value.nationalite,
      dateNaissance: opt.dateNaissance ?? value.dateNaissance,
    });
  };

  const selectOptions = [
    { value: '', label: options.length > 0 ? '— Choisir dans la société —' : '— Aucune partie connue —' },
    ...options.map((o) => ({ value: o.key, label: o.label })),
    { value: EXTERNE_KEY, label: 'Autre — liquidateur externe (personne physique ou morale)' },
  ];

  return (
    <section className="space-y-4 rounded-xl border border-border bg-bg-raised p-4" data-testid="liquidateur-picker">
      <div className="flex items-center gap-2">
        <UserCheck className="h-4 w-4 text-fg-muted" />
        <h4 className="text-sm font-semibold text-fg">Liquidateur *</h4>
      </div>
      <p className="text-xs text-fg-subtle">
        Choisissez d’abord un <strong>gérant ou associé existant</strong> de la société : son
        identité est reprise de la Data Room, sans ressaisie. S’il s’agit d’un tiers,
        sélectionnez « Autre », puis indiquez s’il s’agit d'une personne physique (CIN, avec
        lecture automatique) ou d'une société (personne morale).
      </p>

      <Select
        label="Désignation"
        value={selectValue}
        data-testid="liquidateur-select"
        onChange={(e) => handleSelect(e.target.value)}
        options={selectOptions}
      />
      {loading && <p className="text-[11px] text-fg-subtle">Chargement des parties prenantes…</p>}

      {value.source === 'EXTERNE' && (
        <Select
          label="Nature du liquidateur"
          value={value.forme ?? 'physique'}
          data-testid="liquidateur-forme"
          onChange={(e) => set({ forme: e.target.value as 'physique' | 'morale' })}
          options={[
            { value: 'physique', label: 'Personne physique' },
            { value: 'morale', label: 'Personne morale (société)' },
          ]}
        />
      )}

      {estPersonneMorale(value) && (
        <>
          <TexteAide cle="liquidateur-personne-morale" titre="Liquidateur personne morale">
            <p>
              Une société peut être nommée liquidateur. Elle est désignée par sa dénomination et
              agit par un représentant permanent. Son siège, son registre du commerce et son ICE
              figurent dans le procès-verbal et l’annonce légale : ce qui manque vous sera indiqué
              à la génération.
            </p>
          </TexteAide>
          <div className="grid gap-3 md:grid-cols-2" data-testid="liquidateur-morale">
            <TextField label="Dénomination *" value={value.denomination ?? ''} data-testid="liquidateur-denomination"
              onChange={(e) => set({ denomination: e.target.value })} />
            <TextField label="Forme juridique" value={value.formeJuridique ?? ''} data-testid="liquidateur-forme-juridique"
              onChange={(e) => set({ formeJuridique: e.target.value })} hint="Par exemple : SARL, SA." />
            <TextField label="Capital (dirhams)" value={value.capital ?? ''} data-testid="liquidateur-capital"
              onChange={(e) => set({ capital: e.target.value })} />
            <TextField label="Siège social *" value={value.siegeSocial ?? ''} data-testid="liquidateur-siege-social"
              onChange={(e) => set({ siegeSocial: e.target.value, adresse: e.target.value })}
              hint="Publié dans l’annonce légale." />
            <TextField label="Ville du registre du commerce" value={value.rcVille ?? ''} data-testid="liquidateur-rc-ville"
              onChange={(e) => set({ rcVille: e.target.value })} />
            <TextField label="Numéro du registre du commerce" value={value.rcNumero ?? ''} data-testid="liquidateur-rc-numero"
              onChange={(e) => set({ rcNumero: e.target.value })} />
            <TextField label="ICE" value={value.ice ?? ''} data-testid="liquidateur-ice"
              onChange={(e) => set({ ice: e.target.value })} />
            <TextField label="Représentant permanent *" value={value.representantNom ?? ''} data-testid="liquidateur-representant-nom"
              onChange={(e) => set({ representantNom: e.target.value })} hint="Civilité, prénom et nom." />
            <TextField label="Qualité du représentant" value={value.representantQualite ?? ''} data-testid="liquidateur-representant-qualite"
              onChange={(e) => set({ representantQualite: e.target.value })} hint="Par exemple : gérant." />
          </div>
        </>
      )}

      {value.source === 'EXTERNE' && !estPersonneMorale(value) && (
        <IdentityExtractor
          mode="cin"
          dossierId={dossierId ?? undefined}
          onApply={(vals) =>
            set({
              nom: (vals.nom as string) ?? value.nom,
              prenom: (vals.prenom as string) ?? value.prenom,
              cin: (vals.cin as string) ?? value.cin,
              adresse: (vals.adresse as string) ?? value.adresse,
            })
          }
        />
      )}

      {/*
        Champs d'identité TOUJOURS saisissables (2026-08-14).

        Ils étaient verrouillés dès qu'une partie prenante était choisie dans la
        liste. Or la Data Room est souvent incomplète — un gérant y figure
        fréquemment SANS son numéro de CIN — et le champ verrouillé et vide ne
        laissait alors aucun moyen de le renseigner, alors qu'il est publié.
        La sélection PRÉ-REMPLIT ; elle n'interdit plus de corriger.
      */}
      {!estPersonneMorale(value) && (
      <div className="grid gap-3 md:grid-cols-2">
        <Select
          label="Civilité"
          value={value.civilite}
          data-testid="liquidateur-civilite"
          onChange={(e) => set({ civilite: e.target.value })}
          options={[
            { value: 'M.', label: 'M.' },
            { value: 'Mme', label: 'Mme' },
            { value: 'Mlle', label: 'Mlle' },
          ]}
        />
        <TextField
          label="Prénom"
          value={value.prenom}
          data-testid="liquidateur-prenom"
          onChange={(e) => set({ prenom: e.target.value })}
        />
        <TextField
          label="Nom *"
          value={value.nom}
          data-testid="liquidateur-nom"
          onChange={(e) => set({ nom: e.target.value })}
        />
        <TextField
          label="N° CIN"
          value={value.cin}
          data-testid="liquidateur-cin"
          onChange={(e) => set({ cin: e.target.value })}
          hint={
            value.source === 'BD' && value.partieKey && !value.cin
              ? 'Absent de la Data Room pour cette personne : saisissez-le.'
              : undefined
          }
        />
        <TypePieceProposee
          id="liquidateur-piece-type"
          valeur={value.pieceType}
          onChange={(v) => set({ pieceType: v })}
        />
        <TextField
          label="Adresse du liquidateur *"
          value={value.adresse}
          data-testid="liquidateur-adresse"
          onChange={(e) => set({ adresse: e.target.value })}
          hint="Publiée dans l’annonce légale (« demeurant à … »)."
        />
        <TextField
          label="Nationalité"
          value={value.nationalite ?? ''}
          data-testid="liquidateur-nationalite"
          onChange={(e) => set({ nationalite: e.target.value })}
          hint="Mentionnée au procès-verbal de dissolution."
        />
        <TextField
          label="Date de naissance"
          type="date"
          value={value.dateNaissance ?? ''}
          data-testid="liquidateur-date-naissance"
          onChange={(e) => set({ dateNaissance: e.target.value })}
        />
        {!(value.source === 'BD' && value.partieKey.startsWith('gerant:')) && (
          <Select
            label="A-t-il été gérant de la société ?"
            value={value.ancienGerant ?? ''}
            data-testid="liquidateur-ancien-gerant"
            onChange={(e) => set({ ancienGerant: e.target.value as '' | 'oui' | 'non' })}
            hint="Le procès-verbal lui rappelle alors qu’aucune cession d’actif ne peut lui être consentie (article 366 de la loi 17-95)."
            options={[
              { value: '', label: 'Choisir…' },
              { value: 'oui', label: 'Oui' },
              { value: 'non', label: 'Non' },
            ]}
          />
        )}
      </div>
      )}

      <RemunerationLiquidateur value={value} set={set} />

      {error && (
        <p className="text-xs text-danger" role="alert" data-testid="liquidateur-error">
          {error}
        </p>
      )}
    </section>
  );
}
