import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Check, KeyRound, ShieldCheck, X } from 'lucide-react';
import { PageAccueil, TitreAccueil } from '../../components/auth/PageAccueil';
import { Button } from '../../components/ui/Button';
import { Message } from '../../components/ui/Message';
import { TextField } from '../../components/ui/TextField';
import { authService } from '../../services/auth.service';
import { extractError, tokenStorage } from '../../lib/api';

interface Rule {
  label: string;
  test: (v: string) => boolean;
}

const RULES: Rule[] = [
  { label: 'Au moins 12 caractères', test: (v) => v.length >= 12 },
  { label: 'Une majuscule', test: (v) => /[A-Z]/.test(v) },
  { label: 'Une minuscule', test: (v) => /[a-z]/.test(v) },
  { label: 'Un chiffre', test: (v) => /[0-9]/.test(v) },
  { label: 'Un caractère spécial (!@#$%…)', test: (v) => /[^A-Za-z0-9]/.test(v) },
];

export function ForceChangePasswordPage() {
  const navigate = useNavigate();
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const ruleStatus = RULES.map((r) => ({ ...r, ok: r.test(newPassword) }));
  const allRulesOk = ruleStatus.every((r) => r.ok);
  const passwordsMatch = newPassword.length > 0 && newPassword === confirmPassword;
  const canSubmit = oldPassword.length > 0 && allRulesOk && passwordsMatch;

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (!canSubmit) return;
    setLoading(true);
    try {
      // Hotfix 2026-06-04 : le backend renvoie un NOUVEAU couple de tokens
      // (mcp=false). On DOIT le persister AVANT de naviguer, sinon les
      // requetes suivantes (ex: /2fa/setup-options) repartent avec l'ancien
      // token (mcp=true) et ChangePasswordEnforcer renvoie 403 — l'utilisateur
      // se retrouve coince a l'ecran "configurez 2FA -> changez votre MDP".
      const tokens = await authService.changePassword(oldPassword, newPassword, confirmPassword);
      tokenStorage.setTokens(tokens);
      navigate('/account/2fa-choose', { replace: true });
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <PageAccueil titre="Choisissez votre mot de passe" largeur="etroite" retourAccueil={false}>
      <TitreAccueil icone={<KeyRound />} ton="attention" titre="Choisissez votre mot de passe">
        Par sécurité, remplacez le mot de passe temporaire reçu par courriel avant de continuer. Étape suivante :
        la double authentification.
      </TitreAccueil>

      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        <TextField
          label="Mot de passe temporaire (reçu par courriel)"
          type="password"
          value={oldPassword}
          onChange={(e) => setOldPassword(e.target.value)}
          autoComplete="current-password"
          required
        />
        <TextField
          label="Nouveau mot de passe"
          type="password"
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
          autoComplete="new-password"
          required
        />
        <TextField
          label="Confirmez le nouveau mot de passe"
          type="password"
          value={confirmPassword}
          onChange={(e) => setConfirmPassword(e.target.value)}
          autoComplete="new-password"
          required
        />

        <ul aria-label="Règles du mot de passe" className="space-y-1 rounded-lg border border-border bg-bg-overlay px-3 py-2.5 text-xs">
          {ruleStatus.map((r) => (
            <li key={r.label} className={`flex items-center gap-2 ${r.ok ? 'text-success' : 'text-fg-muted'}`}>
              {r.ok ? <Check className="h-3.5 w-3.5" aria-label="respectée" /> : <X className="h-3.5 w-3.5" aria-label="à respecter" />}
              {r.label}
            </li>
          ))}
          <li className={`flex items-center gap-2 ${passwordsMatch ? 'text-success' : 'text-fg-muted'}`}>
            {passwordsMatch ? <Check className="h-3.5 w-3.5" aria-label="respectée" /> : <X className="h-3.5 w-3.5" aria-label="à respecter" />}
            Les deux saisies correspondent
          </li>
        </ul>

        {error && <Message ton="erreur">{error}</Message>}

        <Button type="submit" size="lg" fullWidth loading={loading} disabled={!canSubmit} icon={<ShieldCheck />}>
          Enregistrer et continuer
        </Button>
      </form>
    </PageAccueil>
  );
}
