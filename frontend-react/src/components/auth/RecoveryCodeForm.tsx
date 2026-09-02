import { useMemo, useState } from 'react';
import { KeyRound } from 'lucide-react';
import { Button } from '../ui/Button';

interface Props {
  workspaceCode: string;
  email: string;
  loading: boolean;
  error: string | null;
  onSubmit: (codeNormalized: string) => void;
}

/**
 * Sprint 14 bis / B4 — saisie d'un code de recuperation 2FA single-use.
 *
 * <p>Format attendu : 4 groupes de 4 caracteres alphanumeriques (ex.
 * {@code A7K2-9XQM-3FBL-7TPN}). L'utilisateur peut taper avec ou sans tirets ;
 * on normalise (uppercase, strip espaces, ajout des tirets pour la lisibilite)
 * a la volee et on envoie la version normalisee a l'API.
 *
 * <p>Bouton "Verifier" desactive tant que le code n'a pas exactement 16 chars
 * alphanum (apres strip des tirets/espaces).
 */
export function RecoveryCodeForm({ workspaceCode, email, loading, error, onSubmit }: Props) {
  const [raw, setRaw] = useState('');

  const { display, normalized, isComplete } = useMemo(() => {
    const upper = raw.toUpperCase().replace(/[^A-Z0-9]/g, '');
    const trimmed = upper.slice(0, 16);
    const groups: string[] = [];
    for (let i = 0; i < trimmed.length; i += 4) {
      groups.push(trimmed.slice(i, i + 4));
    }
    return {
      display: groups.join('-'),
      normalized: trimmed,
      isComplete: trimmed.length === 16,
    };
  }, [raw]);

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!isComplete || loading) return;
    onSubmit(normalized);
  }

  return (
    <form onSubmit={handleSubmit} data-testid="recovery-code-form" noValidate>
      <div className="mb-6 flex justify-center">
        <div className="flex h-16 w-16 items-center justify-center rounded-full bg-accent/10">
          <KeyRound className="h-8 w-8 text-accent" />
        </div>
      </div>

      <div className="mb-6 text-center">
        <h2 className="mb-2 text-2xl font-bold text-fg">Code de recuperation</h2>
        <p className="text-sm text-fg-subtle">
          Saisissez l'un de vos 10 codes 2FA generes lors de l'activation. Chaque code
          n'est valide qu'<strong>une seule fois</strong>.
        </p>
      </div>

      <div className="mb-4 inline-flex w-full items-center justify-center gap-2 rounded-full border border-success bg-success/10 px-4 py-2 text-sm text-success">
        ✓ {workspaceCode} · {email}
      </div>

      <div className="mb-4">
        <label htmlFor="recovery-code-input" className="mb-2 block text-sm font-medium text-fg">
          Code de recuperation
        </label>
        <input
          id="recovery-code-input"
          type="text"
          inputMode="text"
          autoComplete="one-time-code"
          spellCheck={false}
          value={display}
          onChange={(e) => setRaw(e.target.value)}
          placeholder="XXXX-XXXX-XXXX-XXXX"
          required
          aria-invalid={!!error}
          maxLength={19}
          className={`h-14 w-full rounded-lg bg-bg-overlay px-6 text-center font-mono text-xl font-bold tracking-wider outline-none transition-all ${
            error
              ? 'border-2 border-danger'
              : 'border-2 border-border focus:border-accent'
          }`}
        />
        <p className="mt-2 text-center text-xs text-fg-subtle">
          16 caracteres en 4 groupes de 4 ({normalized.length}/16)
        </p>
      </div>

      {error && (
        <div
          role="alert"
          className="mb-4 rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger"
        >
          {error}
        </div>
      )}

      <Button type="submit" className="h-12 w-full" loading={loading} disabled={!isComplete}>
        Verifier & Acceder
      </Button>

      <p className="mt-6 text-center text-xs text-fg-subtle">
        Vous n'avez plus aucun code de recuperation ? Contactez le support a{' '}
        <a className="font-medium text-accent hover:underline" href="mailto:support@jurika.ma">
          support@jurika.ma
        </a>
        .
      </p>
    </form>
  );
}
