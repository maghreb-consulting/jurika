import { useId, type SelectHTMLAttributes } from 'react';
import { twMerge } from 'tailwind-merge';

interface OptionItem {
  value: string;
  label: string;
}

interface OptionGroup {
  label: string;
  options: OptionItem[];
}

interface Props extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  error?: string;
  /**
   * Aide contextuelle sous le champ (pendant de `TextField.hint`). Masquée quand une
   * erreur est affichée : deux messages sous le même champ se concurrencent.
   */
  hint?: string;
  /** Options "a plat" (rendues avant les groupes — ex. un placeholder). */
  options: OptionItem[];
  /**
   * Groupes optionnels rendus en <optgroup> apres {@link options}. Utile pour
   * separer p.ex. "Employes" et "Clients" dans un meme <select>.
   */
  groups?: OptionGroup[];
}

/**
 * Sprint 12.5 T6 — Select primitive sur palette marketing.
 *
 * Pendant de TextField : navy bg, gold focus, danger error.
 * Note : <select> natif est volontairement conservé (a11y, mobile UX) ;
 * la flèche est laissée au browser. Si on veut un select custom avec
 * Radix Select en T12, on le crée à part (SelectCustom).
 */
export function Select({ label, error, hint, options, groups, className, id, ...rest }: Props) {
  // Meme correctif que TextField : sans `id` ni `name`, le `<label for>` pointait
  // dans le vide. Voir la note detaillee dans TextField.tsx.
  const autoId = useId();
  const selectId = id ?? rest.name ?? autoId;
  // Un <select> sans libelle visible n'a pas de placeholder pour se rabattre :
  // on n'invente rien, mais on laisse passer un `aria-label` explicite.
  const ariaLabel = rest['aria-label'];
  return (
    <div className="flex flex-col gap-1">
      {label && (
        <label htmlFor={selectId} className="text-sm font-medium text-fg-muted">
          {label}
        </label>
      )}
      <select
        id={selectId}
        aria-label={ariaLabel}
        aria-invalid={error ? true : undefined}
        className={twMerge(
          'w-full rounded-lg border border-border bg-bg-raised px-3 py-2 text-sm text-fg transition-colors',
          'focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30',
          error && 'border-danger focus:border-danger focus:ring-danger/30',
          className,
        )}
        {...rest}
      >
        {options.map((opt) => (
          <option key={opt.value} value={opt.value} className="bg-bg-raised text-fg">
            {opt.label}
          </option>
        ))}
        {groups?.map((group) => (
          <optgroup key={group.label} label={group.label} className="bg-bg-raised text-fg">
            {group.options.map((opt) => (
              <option key={opt.value} value={opt.value} className="bg-bg-raised text-fg">
                {opt.label}
              </option>
            ))}
          </optgroup>
        ))}
      </select>
      {error ? (
        <p className="text-xs text-danger" role="alert">
          {error}
        </p>
      ) : (
        hint && <p className="text-xs text-fg-subtle">{hint}</p>
      )}
    </div>
  );
}
