import { forwardRef, useId, type InputHTMLAttributes, type ReactNode } from 'react';
import { cn } from '../../lib/utils';

/**
 * Cases a cocher, boutons radio et interrupteur (lot L6b, E1).
 *
 * - `Case` : <input type="checkbox"> natif, libelle cliquable, description optionnelle.
 * - `Radio` : <input type="radio"> natif ; `GroupeRadio` les regroupe dans un <fieldset>
 *   avec sa legende (fleches du clavier natives).
 * - `Interrupteur` : bouton role="switch" (aria-checked) pour un reglage qui s'applique
 *   tout de suite (sinon : une case).
 */

const CLASSES_COCHE =
  'mt-0.5 h-4 w-4 shrink-0 cursor-pointer border-border-input accent-[var(--color-accent)] ' +
  'disabled:cursor-not-allowed focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus';

interface CocheProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label: ReactNode;
  description?: ReactNode;
  containerClassName?: string;
}

const Coche = forwardRef<HTMLInputElement, CocheProps & { type: 'checkbox' | 'radio' }>(function Coche(
  { type, label, description, containerClassName, className, id, disabled, ...rest },
  ref,
) {
  const autoId = useId();
  const inputId = id ?? autoId;
  const idDesc = description ? `${inputId}-desc` : undefined;
  return (
    <div className={cn('flex items-start gap-2.5', disabled && 'opacity-60', containerClassName)}>
      <input
        ref={ref}
        id={inputId}
        type={type}
        disabled={disabled}
        aria-describedby={idDesc}
        className={cn(CLASSES_COCHE, type === 'checkbox' ? 'rounded-sm' : 'rounded-full', className)}
        {...rest}
      />
      <div className="min-w-0">
        <label htmlFor={inputId} className={cn('text-sm text-fg', disabled ? 'cursor-not-allowed' : 'cursor-pointer')}>
          {label}
        </label>
        {description && (
          <p id={idDesc} className="text-xs text-fg-subtle">
            {description}
          </p>
        )}
      </div>
    </div>
  );
});

export const Case = forwardRef<HTMLInputElement, CocheProps>(function Case(props, ref) {
  return <Coche {...props} type="checkbox" ref={ref} />;
});

export const Radio = forwardRef<HTMLInputElement, CocheProps>(function Radio(props, ref) {
  return <Coche {...props} type="radio" ref={ref} />;
});

export interface OptionRadio {
  value: string;
  label: ReactNode;
  description?: ReactNode;
  disabled?: boolean;
}

export function GroupeRadio({
  legende,
  name,
  options,
  value,
  onChange,
  error,
  horizontal = false,
  className,
}: {
  legende: ReactNode;
  name: string;
  options: OptionRadio[];
  value: string | null;
  onChange: (value: string) => void;
  error?: string;
  horizontal?: boolean;
  className?: string;
}) {
  const idErreur = useId();
  return (
    <fieldset className={cn('min-w-0', className)} aria-describedby={error ? idErreur : undefined}>
      <legend className="mb-2 text-sm font-medium text-fg">{legende}</legend>
      <div className={cn('flex gap-3', horizontal ? 'flex-row flex-wrap gap-x-6' : 'flex-col')}>
        {options.map((o) => (
          <Radio
            key={o.value}
            name={name}
            value={o.value}
            label={o.label}
            description={o.description}
            disabled={o.disabled}
            checked={value === o.value}
            onChange={() => onChange(o.value)}
          />
        ))}
      </div>
      {error && (
        <p id={idErreur} role="alert" className="mt-1.5 text-xs font-medium text-danger">
          {error}
        </p>
      )}
    </fieldset>
  );
}

export function Interrupteur({
  checked,
  onChange,
  label,
  description,
  disabled,
  className,
  id,
}: {
  checked: boolean;
  onChange: (checked: boolean) => void;
  label: ReactNode;
  description?: ReactNode;
  disabled?: boolean;
  className?: string;
  id?: string;
}) {
  const autoId = useId();
  const btnId = id ?? autoId;
  const idLib = `${btnId}-lib`;
  const idDesc = description ? `${btnId}-desc` : undefined;
  return (
    <div className={cn('flex items-start justify-between gap-4', disabled && 'opacity-60', className)}>
      <div className="min-w-0">
        <span id={idLib} className="text-sm text-fg">
          {label}
        </span>
        {description && (
          <p id={idDesc} className="text-xs text-fg-subtle">
            {description}
          </p>
        )}
      </div>
      <button
        id={btnId}
        type="button"
        role="switch"
        aria-checked={checked}
        aria-labelledby={idLib}
        aria-describedby={idDesc}
        disabled={disabled}
        onClick={() => onChange(!checked)}
        className={cn(
          'relative inline-flex h-6 w-11 shrink-0 cursor-pointer items-center rounded-full border transition-colors duration-150',
          'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus disabled:cursor-not-allowed',
          checked ? 'border-accent bg-accent' : 'border-border-input bg-bg-overlay',
        )}
      >
        <span
          aria-hidden="true"
          className={cn(
            'inline-block h-4.5 w-4.5 rounded-full shadow-card transition-transform duration-150',
            checked ? 'translate-x-5.5 bg-accent-fg' : 'translate-x-0.5 bg-fg-subtle',
          )}
        />
      </button>
    </div>
  );
}
