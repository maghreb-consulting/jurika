import { forwardRef, useId, type SelectHTMLAttributes } from 'react';
import { ChevronDown } from 'lucide-react';
import { cn } from '../../lib/utils';
import { CadreChamp, CLASSES_CHAMP } from './TextField';

/**
 * Liste deroulante (lot L6b, E1) : <select> natif (accessible, clavier, mobile), fleche
 * dessinee par la bibliotheque, memes libelle / aide / erreur que TextField.
 */
interface OptionItem {
  value: string;
  label: string;
  disabled?: boolean;
}

interface OptionGroup {
  label: string;
  options: OptionItem[];
}

export interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  error?: string;
  /** Aide sous le champ (masquee quand une erreur est affichee). */
  hint?: string;
  /**
   * Options "a plat" (rendues avant les groupes, ex. un choix vide). Ajout L6b E5 (W2) :
   * facultatif ; des <option> passees en enfants sont rendues apres `options` et `groups`
   * (listes construites par le JSX de l'appelant : conditions, regroupements).
   */
  options?: OptionItem[];
  /** Groupes rendus en <optgroup> apres `options`. */
  groups?: OptionGroup[];
  containerClassName?: string;
}

export const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select(
  { label, error, hint, options = [], groups, className, containerClassName, id, 'aria-describedby': decritPar, children, ...rest },
  ref,
) {
  const autoId = useId();
  const selectId = id ?? rest.name ?? autoId;
  const idAide = `${selectId}-aide`;
  const idErreur = `${selectId}-erreur`;
  const decrit = [decritPar, hint && !error ? idAide : undefined, error ? idErreur : undefined].filter(Boolean).join(' ') || undefined;
  return (
    <CadreChamp id={selectId} label={label} hint={hint} error={error} idAide={idAide} idErreur={idErreur} className={containerClassName}>
      <div className="relative">
        <select
          ref={ref}
          id={selectId}
          aria-invalid={error ? true : undefined}
          aria-describedby={decrit}
          className={cn(CLASSES_CHAMP, 'h-9 cursor-pointer appearance-none pr-9', className)}
          {...rest}
        >
          {options.map((opt) => (
            <option key={opt.value} value={opt.value} disabled={opt.disabled}>
              {opt.label}
            </option>
          ))}
          {groups?.map((group) => (
            <optgroup key={group.label} label={group.label}>
              {group.options.map((opt) => (
                <option key={opt.value} value={opt.value} disabled={opt.disabled}>
                  {opt.label}
                </option>
              ))}
            </optgroup>
          ))}
          {children}
        </select>
        <ChevronDown
          className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-fg-subtle"
          aria-hidden="true"
        />
      </div>
    </CadreChamp>
  );
});

/** Nom francais du meme composant. */
export const ListeDeroulante = Select;
