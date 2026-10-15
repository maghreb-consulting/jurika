import { InfoBulle } from '../ui/Aide';
import { Button } from '../ui/Button';
import { Select } from '../ui/Select';
import { TextField } from '../ui/TextField';

/**
 * Lot L3 (D6, 2026-10-10) : aucune valeur par defaut silencieuse.
 *
 * Une valeur d'usage (nombre d'originaux, heure de l'acte, duree de 99 ans, mandat du
 * commissaire aux comptes, type de piece "CIN"...) est PROPOSEE : affichee, modifiable,
 * et transmise seulement une fois confirmee par l'employe -- en cliquant sur
 * "Confirmer", ou en la modifiant. Non confirmee, elle reste absente : la generation
 * la nomme comme une donnee manquante.
 *
 * `valeur` : la valeur confirmee, ou `undefined` tant que l'employe n'a pas confirme.
 */
export function ValeurProposee({
  id,
  label,
  proposition,
  valeur,
  onChange,
  options,
  type = 'text',
}: {
  id: string;
  label: string;
  proposition: string;
  valeur: string | undefined;
  onChange: (valeur: string) => void;
  /** Liste de choix (select) ; sinon un champ libre. */
  options?: { value: string; label: string }[];
  type?: 'text' | 'number' | 'date';
}) {
  const confirmee = valeur !== undefined && valeur !== '';
  const affichee = confirmee ? valeur : proposition;
  return (
    <div>
      <label htmlFor={id} className="mb-1 flex items-center gap-1 text-xs font-medium text-fg">
        {label}
        <InfoBulle
          libelle={`Pourquoi « ${label} » est-elle proposée ?`}
          texte="La plateforme propose l’usage courant, mais ne l’imprime qu’après votre confirmation. Modifiez la valeur si le dossier l’exige ; sans confirmation, elle n’est pas reprise dans les actes."
        />
      </label>
      <div className="flex items-center gap-2">
        {options ? (
          <Select
            id={id}
            containerClassName="flex-1"
            value={affichee}
            onChange={(e) => onChange(e.target.value)}
            options={options}
          />
        ) : (
          <TextField id={id} type={type} containerClassName="flex-1" value={affichee} onChange={(e) => onChange(e.target.value)} />
        )}
        {!confirmee && (
          <Button type="button" variant="secondary" size="sm" onClick={() => onChange(proposition)}>
            Confirmer
          </Button>
        )}
      </div>
      <p className={`mt-1 text-[11px] ${confirmee ? 'text-success' : 'text-warning'}`} role="status">
        {confirmee ? 'Valeur confirmée.' : 'Valeur proposée, à confirmer.'}
      </p>
    </div>
  );
}

/** Types de piece d'identite proposes a l'ecran ; "CIN" est l'usage courant. */
const TYPES_PIECE = [
  { value: 'CIN', label: 'CIN' },
  { value: 'passeport', label: 'Passeport' },
  { value: 'carte de séjour', label: 'Carte de séjour' },
];

/** Type de piece d'identite : "CIN" propose, confirme ou change par l'employe (L3, D6). */
export function TypePieceProposee({
  id,
  valeur,
  onChange,
  label = 'Type de pièce d’identité',
}: {
  id: string;
  valeur: string | undefined;
  onChange: (valeur: string) => void;
  label?: string;
}) {
  return (
    <ValeurProposee id={id} label={label} proposition="CIN" valeur={valeur} onChange={onChange} options={TYPES_PIECE} />
  );
}
