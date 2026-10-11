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
import { TypePieceProposee, ValeurProposee } from './ValeurProposee';
import { TexteAide } from '../ui/Aide';
import type { DossierPartie, DossierParties } from '../../services/workflow.service';

/** Origine du liquidateur : partie prenante du dossier (BD) ou tiers (EXTERNE). */
export type LiquidateurSource = 'BD' | 'EXTERNE';

export interface LiquidateurState {
  source: LiquidateurSource;
  /** Clé de l'option BD retenue ('' si externe). */
  partieKey: string;
  civilite: string;
  prenom: string;
  nom: string;
  cin: string;
  /** L3 (D6) : type de piece, "CIN" propose a l'ecran puis confirme. */
  pieceType?: string;
  adresse: string;
  remuneration: string;
  /**
   * Lot L4d (E5, décision du directeur du 11 octobre 2026) : le liquidateur est une
   * personne physique ou une personne morale. Absent = personne physique.
   */
  forme?: 'physique' | 'morale';
  denomination?: string;
  formeJuridique?: string;
  capital?: string;
  siegeSocial?: string;
  rcVille?: string;
  rcNumero?: string;
  ice?: string;
  representantNom?: string;
  representantQualite?: string;
}

/** Lot L4d (E5) : le liquidateur désigné est-il une personne morale ? */
export function estPersonneMorale(l: LiquidateurState): boolean {
  return l.forme === 'morale';
}

/** Identité lisible du liquidateur : dénomination, ou civilité, prénom et nom. */
export function identiteLiquidateur(l: LiquidateurState): string {
  if (estPersonneMorale(l)) return (l.denomination ?? '').trim();
  return [l.civilite, l.prenom, l.nom].filter((x) => (x ?? '').trim()).join(' ').trim();
}

/**
 * Ce qui manque pour désigner le liquidateur (message nommé), ou null. Les autres données
 * (RC, ICE, capital…) sont nommées à la génération si le modèle les imprime.
 */
export function manqueLiquidateur(l: LiquidateurState): string | null {
  if (estPersonneMorale(l)) {
    if (!(l.denomination ?? '').trim()) return 'Indiquez la dénomination du liquidateur personne morale.';
    if (!(l.siegeSocial ?? '').trim()) return 'Indiquez le siège social du liquidateur personne morale : il figure dans l’annonce légale.';
    if (!(l.representantNom ?? '').trim()) return 'Indiquez le représentant permanent du liquidateur personne morale.';
    return null;
  }
  if (!l.nom.trim()) return 'Désignez le liquidateur (gérant ou associé existant, ou tiers).';
  if (!l.adresse.trim()) return "L'adresse du liquidateur est obligatoire : elle figure dans l'annonce légale.";
  return null;
}

/** Champs propres à la personne morale, à joindre aux charges du liquidateur (vide sinon). */
export function champsPersonneMorale(l: LiquidateurState): Record<string, string> {
  if (!estPersonneMorale(l)) return {};
  return {
    forme: 'morale',
    denomination: (l.denomination ?? '').trim(),
    formeJuridique: (l.formeJuridique ?? '').trim(),
    capital: (l.capital ?? '').trim(),
    siegeSocial: (l.siegeSocial ?? '').trim(),
    rcVille: (l.rcVille ?? '').trim(),
    rcNumero: (l.rcNumero ?? '').trim(),
    ice: (l.ice ?? '').trim(),
    representantNom: (l.representantNom ?? '').trim(),
    representantQualite: (l.representantQualite ?? '').trim(),
  };
}

export interface LiquidateurOption {
  key: string;
  label: string;
  role: 'gerant' | 'associe';
  civilite: string;
  prenom: string;
  nom: string;
  cin: string;
  adresse: string;
  /** Lot L4d (E5) : associé personne morale (désigné par sa dénomination). */
  morale?: boolean;
  denomination?: string;
  formeJuridique?: string;
  siegeSocial?: string;
  rcNumero?: string;
  rcVille?: string;
  ice?: string;
  representantNom?: string;
}

export const EXTERNE_KEY = '__externe__';

export function emptyLiquidateur(): LiquidateurState {
  return {
    source: 'BD',
    partieKey: '',
    civilite: 'M.',
    prenom: '',
    nom: '',
    cin: '',
    adresse: '',
    remuneration: '', // L4a (E2) : proposee a l'ecran, a confirmer
  };
}

function str(v: unknown): string {
  return v == null ? '' : String(v).trim();
}

/** Lot L4d (E5) : une partie personne morale peut être liquidateur (désignée par sa dénomination). */
function isPhysique(p: DossierPartie): boolean {
  return str(p.typePersonne).toUpperCase() !== 'MORALE';
}

/**
 * Construit les options « liquidateur » depuis les parties prenantes BD : gérants
 * d'abord (ils sont les candidats naturels), puis associés. Dédoublonnage sur le nom
 * complet (un gérant associé n'apparaît qu'une fois, avec le rôle gérant).
 */
export function buildLiquidateurOptions(
  parties: DossierParties | null | undefined,
): LiquidateurOption[] {
  const out: LiquidateurOption[] = [];
  const seen = new Set<string>();
  const push = (p: DossierPartie, role: 'gerant' | 'associe') => {
    if (!isPhysique(p)) {
      const denomination = str(p.denomination);
      if (!denomination) return;
      const cle = denomination.toLowerCase();
      if (seen.has(cle)) return;
      seen.add(cle);
      out.push({
        key: `${role}:${cle}`,
        label: `${denomination} — ${role === 'gerant' ? 'gérant' : 'associé'} (personne morale)`,
        role, civilite: '', prenom: '', nom: '', cin: '',
        adresse: str(p.siegeSocial) || str(p.adresse),
        morale: true,
        denomination,
        formeJuridique: str(p.formeJuridique),
        siegeSocial: str(p.siegeSocial) || str(p.adresse),
        rcNumero: str(p.rcNumero),
        rcVille: str(p.rcVille),
        ice: str(p.ice),
        representantNom: str(p.representantNom),
      });
      return;
    }
    const prenom = str(p.prenom);
    const nom = str(p.nom);
    const full = [prenom, nom].filter(Boolean).join(' ').trim();
    if (!full) return;
    const dedupe = full.toLowerCase();
    if (seen.has(dedupe)) return;
    seen.add(dedupe);
    out.push({
      key: `${role}:${dedupe}`,
      label: `${full} — ${role === 'gerant' ? 'gérant' : 'associé'}`,
      role,
      civilite: str(p.civilite) || 'M.',
      prenom,
      nom,
      // Fix D4 (2026-08-16) — la CIN du liquidateur n'était pas reprise de la base
      // et devait être re-saisie, alors que le liquidateur est presque toujours un
      // gérant ou un associé déjà connu. En cause : on ne lisait QUE la clé `cin`,
      // or la fiche structurée la range selon son origine sous `cinNumero`
      // (dirigeants du wizard de création) ou `pieceNumero` (voie directeur).
      cin: str(p.cin) || str(p.cinNumero) || str(p.pieceNumero),
      adresse: str(p.adresse) || str(p.domicile),
    });
  };
  (parties?.gerants ?? []).forEach((g) => push(g, 'gerant'));
  (parties?.associes ?? []).forEach((a) => push(a, 'associe'));
  return out;
}

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
        <UserCheck className="h-4 w-4 text-accent" />
        <h4 className="text-sm font-semibold text-fg">Liquidateur *</h4>
      </div>
      <p className="text-xs text-fg-subtle">
        Choisissez d'abord un <strong>gérant ou associé existant</strong> de la société : son
        identité est reprise de la Data Room, sans re-saisie. S'il s'agit d'un tiers,
        sélectionnez « Autre », puis indiquez s'il s'agit d'une personne physique (CIN, avec
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
            <ValeurProposee
              id="liquidateur-remuneration-pm"
              label="Rémunération du liquidateur"
              proposition="exercées à titre gratuit"
              valeur={value.remuneration || undefined}
              onChange={(v) => set({ remuneration: v })}
            />
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
              ? "Absent de la Data Room pour cette personne : saisissez-le."
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
          hint="Publiée dans l'annonce légale (« demeurant à … »)."
        />
        <ValeurProposee
          id="liquidateur-remuneration"
          label="Rémunération du liquidateur"
          proposition="exercées à titre gratuit"
          valeur={value.remuneration || undefined}
          onChange={(v) => set({ remuneration: v })}
        />
      </div>
      )}

      {error && (
        <p className="text-xs text-danger" role="alert" data-testid="liquidateur-error">
          {error}
        </p>
      )}
    </section>
  );
}
