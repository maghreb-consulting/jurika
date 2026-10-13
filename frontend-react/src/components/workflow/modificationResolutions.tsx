/**
 * Catalogue des types de résolutions du moteur PV directeur + éditeur de champs
 * réutilisable (Phase 2, 2026-08-10).
 *
 * Extrait de {@link ModificationOperationForm} pour être partagé entre :
 *  - l'étape 2 (saisie des nouvelles valeurs, seedée depuis la sélection) ;
 *  - l'étape 3 ({@link ModificationOperationForm}, séance + génération).
 *
 * Aligné 1:1 sur {@code ModificationDirecteurMapper.fillTypeVars} (33 types).
 */
import { Plus, Trash2 } from 'lucide-react';
import { TextField } from '../ui/TextField';
import { Select } from '../ui/Select';
import { ValeurProposee } from './ValeurProposee';
import { InfoBulle, TexteAide } from '../ui/Aide';
import {
  PERSON_FIELD_SOURCE,
  type FieldSpec,
  type FormeCible,
  type PersonLists,
  type SubListSpec,
  type TypeSpec,
} from './modificationResolutionsSpecs';

// Types publics, re-exportes pour les appelants existants.
export type {
  FieldKind,
  FieldSpec,
  FormeCible,
  OldValue,
  PersonLists,
  ResolutionState,
  SubListSpec,
  TypeSpec,
} from './modificationResolutionsSpecs';
// Compatibilite (L6) : ModificationWorkflowPage importe encore ces fonctions et constantes
// d'ici. A retirer des que la page les importera depuis './modificationResolutionsSpecs'
// (hors perimetre de l'etape E8).
export {
  RES_SPECS,
  flattenResolution,
  oldValuesFor,
  personNames,
  seedResolutions,
} from './modificationResolutionsSpecs';

// --------------------------------------------------------------------------
// Composants d'édition réutilisables
// --------------------------------------------------------------------------

/**
 * Champ « liste de choix » — Fix M1 (2026-08-16).
 *
 * <b>Ce qui est affiché doit être ce qui est envoyé.</b> Le select affichait la
 * première option comme repli visuel (`value || options[0].value`) sans jamais
 * l'écrire dans l'état. Or {@link flattenResolution} écarte les valeurs vides :
 * la clé n'atteignait donc pas le payload, et le mapper backend appliquait SON
 * propre défaut — parfois l'INVERSE de ce que l'employé lisait à l'écran.
 *
 * Cas constaté en simulation : « Même préfecture / province ? » affichait
 * « Non », et le PV de transfert de siège écrivait « située dans la <b>même</b>
 * préfecture ». Un acte juridiquement faux, sans le moindre signal.
 *
 * On matérialise donc le défaut affiché dans l'état dès le montage : plus aucun
 * écart possible entre l'écran et l'acte.
 */
function SelectField({
  spec,
  value,
  onChange,
}: {
  spec: FieldSpec;
  value: string;
  onChange: (v: string) => void;
}) {
  // L4a (E2) : plus de premiere option appliquee d'office (valeur affichee mais jamais
  // choisie) ; l'employe choisit, et la generation nomme un choix manquant.
  return (
    <Select
      label={spec.label}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      options={[{ value: '', label: 'Choisir…' }, ...(spec.options ?? [])]}
      hint={value ? undefined : 'À choisir.'}
    />
  );
}

/** L4d (E11c) : un champ accompagne de son aide contextuelle (InfoBulle), s'il en a une. */
function AvecAide({ spec, children }: { spec: FieldSpec; children: React.ReactNode }) {
  if (!spec.aide) return <>{children}</>;
  return (
    <div className={spec.kind === 'textarea' ? 'md:col-span-2' : undefined}>
      <div className="flex items-start gap-1">
        <div className="min-w-0 flex-1">{children}</div>
        <span className="mt-6">
          <InfoBulle libelle={`Aide : ${spec.label}`} texte={spec.aide} />
        </span>
      </div>
    </div>
  );
}

export function DynamicField({
  spec,
  value,
  onChange,
}: {
  spec: FieldSpec;
  value: string;
  onChange: (v: string) => void;
}) {
  return (
    <AvecAide spec={spec}>
      <ChampDynamique spec={spec} value={value} onChange={onChange} />
    </AvecAide>
  );
}

function ChampDynamique({
  spec,
  value,
  onChange,
}: {
  spec: FieldSpec;
  value: string;
  onChange: (v: string) => void;
}) {
  if (spec.proposition) {
    return (
      <ValeurProposee
        id={`resolution-${spec.key}`}
        label={spec.label}
        proposition={spec.proposition}
        valeur={value || undefined}
        onChange={onChange}
      />
    );
  }
  if (spec.kind === 'select') {
    return <SelectField spec={spec} value={value} onChange={onChange} />;
  }
  if (spec.kind === 'textarea') {
    return (
      <label className="flex flex-col gap-1 text-sm md:col-span-2">
        <span className="font-medium text-fg">{spec.label}</span>
        <textarea
          className="min-h-[80px] rounded-lg border border-border bg-bg-raised px-3 py-2 text-sm text-fg"
          value={value}
          placeholder={spec.placeholder}
          onChange={(e) => onChange(e.target.value)}
        />
      </label>
    );
  }
  return (
    <TextField
      label={spec.label}
      type={spec.kind === 'number' ? 'number' : spec.kind === 'date' ? 'date' : 'text'}
      value={value}
      placeholder={spec.placeholder}
      onChange={(e) => onChange(e.target.value)}
    />
  );
}

export function SubListEditor({
  spec,
  rows,
  onChange,
}: {
  spec: SubListSpec;
  rows: Array<Record<string, string>>;
  onChange: (rows: Array<Record<string, string>>) => void;
}) {
  const addRow = () => onChange([...rows, {}]);
  const removeRow = (i: number) => onChange(rows.filter((_, idx) => idx !== i));
  const setCell = (i: number, key: string, v: string) =>
    onChange(rows.map((row, idx) => (idx === i ? { ...row, [key]: v } : row)));

  return (
    <div className="rounded-lg border border-dashed border-border p-3 space-y-3">
      <div className="flex items-center justify-between">
        <span className="inline-flex items-center gap-1 text-sm font-medium text-fg">
          {spec.label}
          {spec.aide && <InfoBulle libelle={`Aide : ${spec.label}`} texte={spec.aide} />}
        </span>
        <button
          type="button"
          onClick={addRow}
          className="inline-flex items-center gap-1 text-sm text-violet-600 hover:underline"
        >
          <Plus className="h-4 w-4" /> Ajouter
        </button>
      </div>
      {rows.length === 0 && <p className="text-xs text-fg-subtle">Aucune ligne : cliquez sur « Ajouter » pour en saisir une.</p>}
      {rows.map((row, i) => (
        <div key={i} className="grid gap-2 md:grid-cols-2">
          {spec.columns.map((c) => (
            <TextField
              key={c.key}
              label={c.label}
              type={c.kind === 'number' ? 'number' : c.kind === 'date' ? 'date' : 'text'}
              value={row[c.key] ?? ''}
              onChange={(e) => setCell(i, c.key, e.target.value)}
            />
          ))}
          <div className="md:col-span-2">
            <button
              type="button"
              onClick={() => removeRow(i)}
              className="inline-flex items-center gap-1 text-xs text-danger hover:underline"
            >
              <Trash2 className="h-3.5 w-3.5" /> Retirer la ligne
            </button>
          </div>
        </div>
      ))}
    </div>
  );
}

/** Sélecteur d'une personne EXISTANTE (associé/gérant) par son nom. */
function PersonSelectField({
  label,
  value,
  names,
  onChange,
}: {
  label: string;
  value: string;
  names: string[];
  onChange: (v: string) => void;
}) {
  return (
    <Select
      label={label}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      options={[{ value: '', label: '— Sélectionner —' }, ...names.map((n) => ({ value: n, label: n }))]}
    />
  );
}

/** Éditeur des champs (+ sous-liste) d'une résolution. */
/** L4d (E11c) : champs demandes pour la forme (un champ propre a l'autre forme n'est pas imprime). */
function champsPourForme(spec: TypeSpec, forme?: FormeCible): FieldSpec[] {
  return spec.fields.filter((f) => !f.seulement || !forme || f.seulement === forme);
}

export function ResolutionFieldsEditor({
  spec,
  values,
  rows,
  personLists,
  forme,
  onField,
  onRows,
}: {
  spec: TypeSpec | undefined;
  values: Record<string, string>;
  rows: Record<string, Array<Record<string, string>>>;
  /** Listes BD des associés/gérants existants (P3 — sélection, pas de re-saisie). */
  personLists?: PersonLists;
  /** L4d (E11c) : forme du proces-verbal (SARL : assemblee ; SARL AU : decisions de l'associe unique). */
  forme?: FormeCible;
  onField: (key: string, value: string) => void;
  onRows: (key: string, rows: Array<Record<string, string>>) => void;
}) {
  if (!spec) return null;
  const champs = champsPourForme(spec, forme);
  const sousListe =
    spec.sublist && (!spec.sublist.seulement || !forme || spec.sublist.seulement === forme)
      ? spec.sublist
      : undefined;
  const nonCouvert = forme ? spec.nonCouvert?.[forme] : undefined;
  const note = forme ? spec.note?.[forme] : undefined;
  return (
    <>
      {nonCouvert && (
        <p role="alert" className="mb-3 rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-800">
          {nonCouvert}
        </p>
      )}
      {note && (
        <div className="mb-3">
          <TexteAide cle={note.cle} titre={`${spec.label} : que renseigner ?`}>
            {note.texte}
          </TexteAide>
        </div>
      )}
      {champs.length ? (
        <div className="grid gap-3 md:grid-cols-2">
          {champs.map((f) => {
            const source = PERSON_FIELD_SOURCE[f.key];
            const names = source ? personLists?.[source] ?? [] : [];
            // Personne existante + liste BD disponible → dropdown (jamais de re-saisie).
            if (source && names.length > 0) {
              return (
                <PersonSelectField
                  key={f.key}
                  label={f.label}
                  value={values[f.key] ?? ''}
                  names={names}
                  onChange={(v) => onField(f.key, v)}
                />
              );
            }
            return (
              <DynamicField
                key={f.key}
                spec={f}
                value={values[f.key] ?? ''}
                onChange={(v) => onField(f.key, v)}
              />
            );
          })}
        </div>
      ) : (
        <p className="text-sm text-fg-subtle">Aucun champ à renseigner pour ce type.</p>
      )}
      {sousListe && (
        <SubListEditor
          spec={sousListe}
          rows={rows[sousListe.key] ?? []}
          onChange={(r) => onRows(sousListe.key, r)}
        />
      )}
    </>
  );
}
