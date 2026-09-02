import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { CheckCircle2, KeyRound } from 'lucide-react';
import { BrandLogo } from '../../components/ui/BrandLogo';
import { Button } from '../../components/ui/Button';
import { TextField } from '../../components/ui/TextField';
import { Card } from '../../components/ui/Card';
import { authService } from '../../services/auth.service';
import { extractError } from '../../lib/api';
import {
  emailMsg,
  patternMsg,
  requiredMsg,
  minLengthMsg,
  firstError,
  focusFirstError,
  type FieldErrors,
} from '../../lib/formValidation';

export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const tokenFromUrl = params.get('token');

  const [workspaceCode, setWorkspaceCode] = useState('');
  const [email, setEmail] = useState('');
  const [token, setToken] = useState(tokenFromUrl ?? '');
  const [newPassword, setNewPassword] = useState('');
  const [requested, setRequested] = useState(false);
  const [confirmed, setConfirmed] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});

  async function request(e: React.FormEvent) {
    e.preventDefault();
    // Validation JS avant l'appel API (remplace la validation native).
    const errors: FieldErrors = {
      workspaceCode: firstError(
        () => requiredMsg(workspaceCode),
        () =>
          patternMsg(
            workspaceCode.toUpperCase(),
            /^JUR-[A-Z0-9]{5}$/,
            'Code workspace invalide (format JUR-XXXXX).',
          ),
      ) ?? undefined,
      email: emailMsg(email) ?? undefined,
    };
    setFieldErrors(errors);
    if (errors.workspaceCode || errors.email) {
      focusFirstError(errors, ['workspaceCode', 'email']);
      return;
    }
    setError(null);
    setLoading(true);
    try {
      await authService.requestPasswordReset(workspaceCode.toUpperCase(), email);
      setRequested(true);
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }

  async function confirm(e: React.FormEvent) {
    e.preventDefault();
    // Validation JS avant l'appel API (remplace la validation native).
    const errors: FieldErrors = {
      token: tokenFromUrl ? undefined : requiredMsg(token) ?? undefined,
      newPassword: firstError(
        () => requiredMsg(newPassword),
        () => minLengthMsg(newPassword, 10),
      ) ?? undefined,
    };
    setFieldErrors(errors);
    if (errors.token || errors.newPassword) {
      focusFirstError(errors, ['token', 'newPassword']);
      return;
    }
    setError(null);
    setLoading(true);
    try {
      await authService.confirmPasswordReset(token, newPassword);
      setConfirmed(true);
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-bg to-accent/10 px-4 py-12">
      <div className="w-full max-w-md">
        <div className="mb-8 flex items-center justify-center gap-2">
          <BrandLogo size={40} />
          <span className="font-heading font-heading text-2xl font-semibold text-fg">JURIKA</span>
        </div>
        <Card className="space-y-5 p-8">
          {confirmed ? (
            <div className="space-y-4 text-center">
              <CheckCircle2 className="mx-auto h-12 w-12 text-success" />
              <h1 className="font-heading text-xl font-semibold text-fg">Mot de passe modifie</h1>
              <Link to="/login" className="block">
                <Button className="w-full">Retour a la connexion</Button>
              </Link>
            </div>
          ) : tokenFromUrl || token ? (
            <>
              <div className="text-center">
                <KeyRound className="mx-auto h-10 w-10 text-accent" />
                <h1 className="mt-3 font-heading text-xl font-semibold text-fg">Nouveau mot de passe</h1>
              </div>
              <form onSubmit={confirm} className="space-y-4" noValidate>
                {!tokenFromUrl && (
                  <TextField
                    label="Token recu par email"
                    name="token"
                    value={token}
                    onChange={(e) => {
                      setToken(e.target.value);
                      setFieldErrors((f) => ({ ...f, token: undefined }));
                    }}
                    required
                    error={fieldErrors.token}
                  />
                )}
                <TextField
                  label="Nouveau mot de passe"
                  name="newPassword"
                  type="password"
                  value={newPassword}
                  onChange={(e) => {
                    setNewPassword(e.target.value);
                    setFieldErrors((f) => ({ ...f, newPassword: undefined }));
                  }}
                  hint="Min. 10 caracteres"
                  required
                  minLength={10}
                  error={fieldErrors.newPassword}
                />
                {error && (
                  <div className="rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
                    {error}
                  </div>
                )}
                <Button type="submit" className="w-full" loading={loading}>
                  Reinitialiser
                </Button>
              </form>
            </>
          ) : requested ? (
            <div className="space-y-4 text-center">
              <CheckCircle2 className="mx-auto h-12 w-12 text-success" />
              <h1 className="font-heading text-xl font-semibold text-fg">Email envoye</h1>
              <p className="text-sm text-fg-muted">
                Si cet email existe dans le workspace, un lien de reinitialisation vient d'etre envoye.
              </p>
              <Link to="/login" className="block">
                <Button variant="secondary" className="w-full">Retour a la connexion</Button>
              </Link>
            </div>
          ) : (
            <>
              <div className="text-center">
                <KeyRound className="mx-auto h-10 w-10 text-accent" />
                <h1 className="mt-3 font-heading text-xl font-semibold text-fg">Mot de passe oublie</h1>
                <p className="mt-1 text-sm text-fg-muted">Saisissez votre cabinet et email</p>
              </div>
              <form onSubmit={request} className="space-y-4" noValidate>
                <TextField
                  label="Code workspace"
                  name="workspaceCode"
                  value={workspaceCode}
                  onChange={(e) => {
                    setWorkspaceCode(e.target.value.toUpperCase());
                    setFieldErrors((f) => ({ ...f, workspaceCode: undefined }));
                  }}
                  pattern="^JUR-[A-Z0-9]{5}$"
                  maxLength={9}
                  placeholder="JUR-AB123"
                  required
                  error={fieldErrors.workspaceCode}
                />
                <TextField
                  label="Email"
                  name="email"
                  type="email"
                  value={email}
                  onChange={(e) => {
                    setEmail(e.target.value);
                    setFieldErrors((f) => ({ ...f, email: undefined }));
                  }}
                  required
                  error={fieldErrors.email}
                />
                {error && (
                  <div className="rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
                    {error}
                  </div>
                )}
                <Button type="submit" className="w-full" loading={loading}>
                  Envoyer le lien
                </Button>
                <Link to="/login" className="block text-center text-sm text-fg-subtle hover:text-fg">
                  Retour a la connexion
                </Link>
              </form>
            </>
          )}
        </Card>
      </div>
    </div>
  );
}
