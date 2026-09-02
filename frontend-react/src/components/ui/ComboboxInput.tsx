import { useId } from 'react';
import { twMerge } from 'tailwind-merge';

interface Props {
  label?: string;
  error?: string;
  hint?: string;
  value: string;
  onChange: (value: string) => void;
  /** Suggestions proposees dans le <datalist>. La saisie libre reste permise. */
  options: string[];
  placeholder?: string;
  onBlur?: () => void;
  id?: string;
  name?: string;
  className?: string;
  disabled?: boolean;
}

/**
 * ComboboxInput — 2026-07-05.
 *
 * Petit combobox « liste de suggestions + saisie libre » : un <input> couple a
 * un <datalist> natif. L'utilisateur peut choisir une des {@link Props.options}
 * OU taper une valeur absente de la liste (free-text), qui est remontee telle
 * quelle via {@link Props.onChange}.
 *
 * Extrait du pattern initialement inline dans Step2Siege.tsx (province /
 * commune) pour etre reutilise partout ou une liste de reference ne doit pas
 * bloquer une saisie hors-liste (villes du Maroc, etc.).
 *
 * L'habillage (label / error / hint) miroite {@link TextField} pour une
 * integration homogene dans les formulaires existants.
 */
export function ComboboxInput({
  label,
  error,
  hint,
  value,
  onChange,
  options,
  placeholder,
  onBlur,
  id,
  name,
  className,
  disabled,
}: Props) {
  const inputId = id ?? name;
  const listId = useId();
  return (
    <div className="flex flex-col gap-1">
      {label && (
        <label htmlFor={inputId} className="text-sm font-medium text-fg-muted">
          {label}
        </label>
      )}
      <input
        id={inputId}
        name={name}
        type="text"
        list={listId}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onBlur={onBlur}
        placeholder={placeholder}
        autoComplete="off"
        disabled={disabled}
        aria-invalid={error ? true : undefined}
        className={twMerge(
          'w-full rounded-lg border border-border bg-bg-raised px-3 py-2 text-sm text-fg placeholder:text-fg-subtle transition-colors',
          'focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30',
          error && 'border-danger focus:border-danger focus:ring-danger/30',
          className,
        )}
      />
      <datalist id={listId}>
        {options.map((opt) => (
          <option key={opt} value={opt} />
        ))}
      </datalist>
      {hint && !error && <p className="text-xs text-fg-subtle">{hint}</p>}
      {error && (
        <p className="text-xs text-danger" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}

export default ComboboxInput;
