import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Check, KeyRound, ShieldCheck, X } from 'lucide-react';
import { BrandLogo } from '../../components/ui/BrandLogo';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
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
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-warning/10 via-bg-raised to-accent/10 px-4">
      <div className="w-full max-w-md">
        <div className="mb-8 flex items-center justify-center">
          <BrandLogo variant="full" size={48} />
        </div>

        <Card className="p-8">
          <div className="space-y-2 text-center">
            <KeyRound className="mx-auto h-10 w-10 text-warning" />
            <h1 className="font-heading text-xl font-semibold text-fg">Choisissez votre mot de passe</h1>
            <p className="text-sm text-fg-muted">
              Par sécurité, remplacez le mot de passe temporaire reçu par courriel avant de continuer.
              Étape suivante : la double authentification.
            </p>
          </div>

          <form onSubmit={handleSubmit} className="mt-6 space-y-4" noValidate>
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

            <ul aria-label="Règles du mot de passe" className="space-y-1 rounded-lg border border-border bg-bg-overlay px-3 py-2 text-xs">
              {ruleStatus.map((r) => (
                <li
                  key={r.label}
                  className={`flex items-center gap-2 ${r.ok ? 'text-success' : 'text-fg-subtle'}`}
                >
                  {r.ok ? <Check className="h-3.5 w-3.5" aria-label="respectée" /> : <X className="h-3.5 w-3.5" aria-label="à respecter" />}
                  {r.label}
                </li>
              ))}
              <li
                className={`flex items-center gap-2 ${passwordsMatch ? 'text-success' : 'text-fg-subtle'}`}
              >
                {passwordsMatch ? <Check className="h-3.5 w-3.5" aria-label="respectée" /> : <X className="h-3.5 w-3.5" aria-label="à respecter" />}
                Les deux saisies correspondent
              </li>
            </ul>

            {error && (
              <div className="rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
                {error}
              </div>
            )}

            <Button type="submit" className="w-full" loading={loading} disabled={!canSubmit}>
              <ShieldCheck className="mr-2 h-4 w-4" />
              Enregistrer et continuer
            </Button>
          </form>
        </Card>
      </div>
    </div>
  );
}
