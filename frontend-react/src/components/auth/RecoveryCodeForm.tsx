import { useMemo, useState } from 'react';
import { KeyRound } from 'lucide-react';
import { Badge } from '../ui/Badge';
import { Button } from '../ui/Button';
import { Lien } from '../ui/Lien';
import { Message } from '../ui/Message';
import { CLASSES_CHAMP } from '../ui/TextField';
import { TitreAccueil } from './PageAccueil';

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
      {/* L6b (E8) : titre de l'etape = titre de l'ecran (h1, la page n'en a pas d'autre a cette etape). */}
      <TitreAccueil icone={<KeyRound />} titre="Code de récupération">
        Saisissez l’un des 10 codes de récupération reçus à l’activation de la double authentification. Chaque
        code ne sert qu’<strong className="text-fg">une seule fois</strong>.
      </TitreAccueil>

      <div className="mb-4 flex justify-center">
        <Badge variant="success" dot className="whitespace-normal">
          {workspaceCode} · {email}
        </Badge>
      </div>

      <div className="mb-4 flex flex-col gap-1.5">
        <label htmlFor="recovery-code-input" className="text-sm font-medium text-fg">
          Code de récupération
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
          aria-describedby="recovery-code-aide"
          maxLength={19}
          className={`${CLASSES_CHAMP} h-14 text-center font-mono text-xl font-bold tracking-wider`}
        />
        <p id="recovery-code-aide" className="text-center text-xs text-fg-subtle">
          16 caractères en 4 groupes de 4 ({normalized.length}/16)
        </p>
      </div>

      {error && (
        <Message ton="erreur" className="mb-4">
          {error}
        </Message>
      )}

      <Button type="submit" size="lg" fullWidth loading={loading} disabled={!isComplete}>
        Vérifier et accéder
      </Button>

      <p className="mt-6 text-center text-xs text-fg-muted">
        Vous n’avez plus aucun code de récupération ? Écrivez au support :{' '}
        <Lien href="mailto:support@jurika.ma">support@jurika.ma</Lien>.
      </p>
    </form>
  );
}
