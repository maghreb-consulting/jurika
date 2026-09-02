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
  adresse: string;
  remuneration: string;
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
    remuneration: 'exercées à titre gratuit',
  };
}

function str(v: unknown): string {
  return v == null ? '' : String(v).trim();
}

/** Personne morale → jamais liquidateur ici (le modèle vise une personne physique). */
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
    if (!isPhysique(p)) return;
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
      set({ source: 'EXTERNE', partieKey: '', civilite: 'M.', prenom: '', nom: '', cin: '' });
      return;
    }
    const opt = options.find((o) => o.key === key);
    if (!opt) {
      set({ source: 'BD', partieKey: '' });
      return;
    }
    set({
      source: 'BD',
      partieKey: opt.key,
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
    { value: EXTERNE_KEY, label: 'Autre — liquidateur externe (saisie + OCR CIN)' },
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
        sélectionnez « Autre » et importez sa CIN (OCR).
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
        <TextField
          label="Adresse du liquidateur *"
          value={value.adresse}
          data-testid="liquidateur-adresse"
          onChange={(e) => set({ adresse: e.target.value })}
          hint="Publiée dans l'annonce légale (« demeurant à … »)."
        />
        <TextField
          label="Rémunération du liquidateur"
          value={value.remuneration}
          onChange={(e) => set({ remuneration: e.target.value })}
        />
      </div>

      {error && (
        <p className="text-xs text-danger" role="alert" data-testid="liquidateur-error">
          {error}
        </p>
      )}
    </section>
  );
}
