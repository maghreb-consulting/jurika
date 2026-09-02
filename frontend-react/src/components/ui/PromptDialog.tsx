import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Modal } from './Modal';
import { Button } from './Button';
import { TextField } from './TextField';

interface PromptDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: ReactNode;
  description?: ReactNode;
  label?: string;
  placeholder?: string;
  /** Valeur initiale du champ. */
  defaultValue?: string;
  /** Saisie multi-ligne (motif, note…). */
  multiline?: boolean;
  confirmLabel?: string;
  cancelLabel?: string;
  variant?: 'danger' | 'primary';
  loading?: boolean;
  /**
   * Validation synchrone avant confirmation. Renvoie un message d'erreur
   * (affiché sous le champ, style charte) ou `null` si la valeur est valide.
   */
  validate?: (value: string) => string | null;
  /** Appelé avec la valeur saisie quand l'utilisateur confirme (valeur valide). */
  onConfirm: (value: string) => void;
}

/**
 * Remplaçant stylé de `window.prompt` : saisit une valeur texte unique dans un
 * Modal à la charte, avec validation JS in-app (aucune bulle native).
 * Bâti sur {@link Modal} (Radix : focus trap, Esc, overlay).
 */
export function PromptDialog({
  open,
  onOpenChange,
  title,
  description,
  label,
  placeholder,
  defaultValue = '',
  multiline = false,
  confirmLabel = 'Confirmer',
  cancelLabel = 'Annuler',
  variant = 'primary',
  loading = false,
  validate,
  onConfirm,
}: PromptDialogProps) {
  const [value, setValue] = useState(defaultValue);
  const [error, setError] = useState<string | null>(null);
  const textareaRef = useRef<HTMLTextAreaElement | null>(null);

  // Réinitialise le champ à chaque ouverture.
  useEffect(() => {
    if (open) {
      setValue(defaultValue);
      setError(null);
    }
  }, [open, defaultValue]);

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const msg = validate?.(value) ?? null;
    if (msg) {
      setError(msg);
      return;
    }
    onConfirm(value);
  }

  return (
    <Modal open={open} onOpenChange={onOpenChange} title={title} description={description} size="sm">
      <form onSubmit={handleSubmit} noValidate>
        {multiline ? (
          <div className="flex flex-col gap-1">
            {label && (
              <label htmlFor="prompt-dialog-input" className="text-sm font-medium text-fg-muted">
                {label}
              </label>
            )}
            <textarea
              id="prompt-dialog-input"
              ref={textareaRef}
              value={value}
              onChange={(e) => {
                setValue(e.target.value);
                if (error) setError(null);
              }}
              placeholder={placeholder}
              rows={4}
              aria-invalid={error ? true : undefined}
              autoFocus
              className={`w-full resize-y rounded-lg border bg-bg-raised px-3 py-2 text-sm text-fg placeholder:text-fg-subtle transition-colors focus:outline-none focus:ring-2 ${
                error
                  ? 'border-danger focus:border-danger focus:ring-danger/30'
                  : 'border-border focus:border-accent focus:ring-accent/30'
              }`}
            />
            {error && (
              <p className="text-xs text-danger" role="alert">
                {error}
              </p>
            )}
          </div>
        ) : (
          <TextField
            id="prompt-dialog-input"
            label={label}
            placeholder={placeholder}
            value={value}
            autoFocus
            error={error ?? undefined}
            onChange={(e) => {
              setValue(e.target.value);
              if (error) setError(null);
            }}
          />
        )}
        <div className="mt-6 flex justify-end gap-2">
          <Button
            type="button"
            variant="secondary"
            onClick={() => onOpenChange(false)}
            disabled={loading}
          >
            {cancelLabel}
          </Button>
          <Button type="submit" variant={variant} loading={loading}>
            {confirmLabel}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
