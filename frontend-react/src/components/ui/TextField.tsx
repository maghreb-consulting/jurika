import { useId, useState, type InputHTMLAttributes } from 'react';
import { Eye, EyeOff } from 'lucide-react';
import { twMerge } from 'tailwind-merge';

interface Props extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  hint?: string;
  /**
   * Sprint 12.5 T6 — Affiche la valeur en JetBrains Mono.
   * À activer pour codes/IDs/tokens (numéros workspace, codes 2FA, etc.)
   * où la lisibilité monospace est utile.
   */
  mono?: boolean;
}

/**
 * Sprint 12.5 T6 — TextField primitive sur palette marketing.
 *
 * AVANT : light theme implicit (white bg + slate-300 border + indigo focus).
 * APRÈS : bg-bg-raised navy + border-border + gold focus ring (signature).
 * Validation states : error rouge danger (token), focus accent or.
 *
 * 2026-07-04 — Champ mot de passe : bouton œil PERSISTANT (afficher/masquer).
 * Avant, on s'appuyait sur l'œil natif du navigateur (Edge ::-ms-reveal) qui
 * disparaît dès que le champ perd le focus. Désormais tout champ type="password"
 * rend un toggle intégré, toujours visible, et l'œil natif est masqué en CSS
 * global (voir index.css) pour éviter un double œil.
 */
export function TextField({ label, error, hint, mono, className, id, type, ...rest }: Props) {
  // 2026-08-14 — L'identifiant retombait sur `id ?? name`, or la quasi-totalite des
  // appels ne fournit ni l'un ni l'autre : le `<label for>` pointait alors dans le
  // vide. Constate en auditant une etape : 21 des 41 champs n'avaient aucun libelle
  // rattache. Un lecteur d'ecran annoncait un champ anonyme, et cliquer le libelle
  // ne donnait pas le focus. `useId` garantit un identifiant unique et stable.
  const autoId = useId();
  const inputId = id ?? rest.name ?? autoId;
  // Filet : un champ sans libelle VISIBLE (listes ou l'en-tete de bloc porte le
  // sens — « Point 1 », « Document 2 ») n'avait aucun nom accessible. Le
  // placeholder n'en tient pas lieu : il disparait des la saisie et les lecteurs
  // d'ecran le traitent de facon inconstante. On le promeut en `aria-label`
  // quand l'appelant n'a fourni ni `label` ni `aria-label` explicite.
  const ariaLabel = rest['aria-label'] ?? (!label && rest.placeholder ? rest.placeholder : undefined);
  const isPassword = type === 'password';
  const [reveal, setReveal] = useState(false);
  const effectiveType = isPassword ? (reveal ? 'text' : 'password') : type;

  return (
    <div className="flex flex-col gap-1">
      {label && (
        <label htmlFor={inputId} className="text-sm font-medium text-fg-muted">
          {label}
        </label>
      )}
      <div className="relative">
        <input
          id={inputId}
          type={effectiveType}
          aria-label={ariaLabel}
          aria-invalid={error ? true : undefined}
          className={twMerge(
            'w-full rounded-lg border border-border bg-bg-raised px-3 py-2 text-sm text-fg placeholder:text-fg-subtle transition-colors',
            'focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30',
            mono && 'font-mono tracking-wider',
            isPassword && 'pr-10',
            error && 'border-danger focus:border-danger focus:ring-danger/30',
            className,
          )}
          {...rest}
        />
        {isPassword && (
          <button
            type="button"
            onClick={() => setReveal((v) => !v)}
            className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-fg-subtle transition-colors hover:text-fg focus:text-fg focus:outline-none"
            aria-label={reveal ? 'Masquer le mot de passe' : 'Afficher le mot de passe'}
            tabIndex={-1}
          >
            {reveal ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
          </button>
        )}
      </div>
      {hint && !error && <p className="text-xs text-fg-subtle">{hint}</p>}
      {error && (
        <p className="text-xs text-danger" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
