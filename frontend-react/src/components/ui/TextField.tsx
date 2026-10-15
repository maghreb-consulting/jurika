import { forwardRef, useId, useState, type InputHTMLAttributes, type ReactNode, type TextareaHTMLAttributes } from 'react';
import { Eye, EyeOff } from 'lucide-react';
import { cn } from '../../lib/utils';

/**
 * Champs de saisie de la bibliotheque (lot L6b, E1) : `TextField` (texte, courriel,
 * nombre, date, mot de passe... : tout `type` d'<input>) et `ZoneTexte` (<textarea>).
 *
 * Chacun porte son libelle (`label`, relie par for/id), une aide (`hint`, reliee par
 * aria-describedby) et une erreur (`error` : aria-invalid, role="alert", reliee aussi).
 * L'aide est masquee quand une erreur s'affiche (deux messages se concurrencent).
 *
 * Historique conserve :
 * - 2026-08-14 : identifiant par useId (un <label for> ne pointe plus dans le vide) ;
 * - placeholder promu en aria-label quand il n'y a ni libelle ni aria-label ;
 * - 2026-07-04 : bouton oeil persistant pour les mots de passe (oeil natif masque en CSS).
 */

export const CLASSES_CHAMP =
  'w-full rounded-lg border border-border-input bg-bg-raised px-3 text-sm text-fg placeholder:text-fg-subtle ' +
  'transition-colors duration-150 hover:border-fg-subtle ' +
  'focus:border-focus focus:outline-2 focus:outline-offset-0 focus:outline-focus ' +
  'disabled:cursor-not-allowed disabled:bg-bg-overlay disabled:text-fg-subtle ' +
  'aria-[invalid=true]:border-danger aria-[invalid=true]:focus:outline-danger';

interface CadreProps {
  id: string;
  label?: ReactNode;
  hint?: ReactNode;
  error?: string;
  idAide: string;
  idErreur: string;
  children: ReactNode;
  className?: string;
}

/** Libelle + champ + aide + erreur : mise en forme commune des champs. */
export function CadreChamp({ id, label, hint, error, idAide, idErreur, children, className }: CadreProps) {
  return (
    <div className={cn('flex min-w-0 flex-col gap-1.5', className)}>
      {label && (
        <label htmlFor={id} className="text-sm font-medium text-fg">
          {label}
        </label>
      )}
      {children}
      {hint && !error && (
        <p id={idAide} className="text-xs text-fg-subtle">
          {hint}
        </p>
      )}
      {error && (
        <p id={idErreur} className="text-xs font-medium text-danger" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}

function decrit(base: string | undefined, hint: unknown, error: unknown, idAide: string, idErreur: string) {
  const ids = [base, hint && !error ? idAide : undefined, error ? idErreur : undefined].filter(Boolean);
  return ids.length ? ids.join(' ') : undefined;
}

export interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  hint?: string;
  /** Valeur en chasse fixe (codes, identifiants, jetons). */
  mono?: boolean;
  /** Classes du conteneur (libelle + champ + aide). */
  containerClassName?: string;
}

export const TextField = forwardRef<HTMLInputElement, TextFieldProps>(function TextField(
  { label, error, hint, mono, className, containerClassName, id, type, 'aria-describedby': decritPar, ...rest },
  ref,
) {
  const autoId = useId();
  const inputId = id ?? rest.name ?? autoId;
  const idAide = `${inputId}-aide`;
  const idErreur = `${inputId}-erreur`;
  const ariaLabel = rest['aria-label'] ?? (!label && rest.placeholder ? rest.placeholder : undefined);
  const isPassword = type === 'password';
  const [reveal, setReveal] = useState(false);
  const effectiveType = isPassword ? (reveal ? 'text' : 'password') : type;

  return (
    <CadreChamp id={inputId} label={label} hint={hint} error={error} idAide={idAide} idErreur={idErreur} className={containerClassName}>
      <div className="relative">
        <input
          ref={ref}
          id={inputId}
          type={effectiveType}
          aria-label={ariaLabel}
          aria-invalid={error ? true : undefined}
          aria-describedby={decrit(decritPar, hint, error, idAide, idErreur)}
          className={cn(CLASSES_CHAMP, 'h-9', mono && 'font-mono tracking-wide', isPassword && 'pr-10', className)}
          {...rest}
        />
        {isPassword && (
          <button
            type="button"
            onClick={() => setReveal((v) => !v)}
            className="absolute right-1.5 top-1/2 inline-flex h-7 w-7 -translate-y-1/2 items-center justify-center rounded-md text-fg-subtle hover:bg-bg-overlay hover:text-fg"
            aria-label={reveal ? 'Masquer le mot de passe' : 'Afficher le mot de passe'}
            tabIndex={-1}
          >
            {reveal ? <EyeOff className="h-4 w-4" aria-hidden="true" /> : <Eye className="h-4 w-4" aria-hidden="true" />}
          </button>
        )}
      </div>
    </CadreChamp>
  );
});

/** Nom francais du meme composant. */
export const Champ = TextField;

export interface ZoneTexteProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string;
  error?: string;
  hint?: string;
  containerClassName?: string;
}

/** Zone de texte sur plusieurs lignes (memes libelle, aide et erreur que TextField). */
export const ZoneTexte = forwardRef<HTMLTextAreaElement, ZoneTexteProps>(function ZoneTexte(
  { label, error, hint, className, containerClassName, id, rows = 4, 'aria-describedby': decritPar, ...rest },
  ref,
) {
  const autoId = useId();
  const zoneId = id ?? rest.name ?? autoId;
  const idAide = `${zoneId}-aide`;
  const idErreur = `${zoneId}-erreur`;
  const ariaLabel = rest['aria-label'] ?? (!label && rest.placeholder ? rest.placeholder : undefined);
  return (
    <CadreChamp id={zoneId} label={label} hint={hint} error={error} idAide={idAide} idErreur={idErreur} className={containerClassName}>
      <textarea
        ref={ref}
        id={zoneId}
        rows={rows}
        aria-label={ariaLabel}
        aria-invalid={error ? true : undefined}
        aria-describedby={decrit(decritPar, hint, error, idAide, idErreur)}
        className={cn(CLASSES_CHAMP, 'min-h-[5rem] py-2 leading-relaxed', className)}
        {...rest}
      />
    </CadreChamp>
  );
});
