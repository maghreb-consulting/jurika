import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { CheckCircle2, Mail, RefreshCw, ShieldAlert } from 'lucide-react';
import { BrandLogo } from '../../components/ui/BrandLogo';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { TextField } from '../../components/ui/TextField';
import { authService } from '../../services/auth.service';
import { extractError } from '../../lib/api';
import {
  emailMsg,
  patternMsg,
  requiredMsg,
  firstError,
  focusFirstError,
  type FieldErrors,
} from '../../lib/formValidation';

type State = 'verifying' | 'success' | 'error' | 'resend';

export function VerifyEmailPage() {
  const [params] = useSearchParams();
  const token = params.get('token');

  const [state, setState] = useState<State>(token ? 'verifying' : 'resend');
  const [message, setMessage] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const [workspaceCode, setWorkspaceCode] = useState('');
  const [email, setEmail] = useState('');
  const [resending, setResending] = useState(false);
  const [resent, setResent] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});

  useEffect(() => {
    if (!token) return;
    setState('verifying');
    authService
      .verifyEmail(token)
      .then((r) => {
        setMessage(r.message);
        setState('success');
      })
      .catch((err) => {
        setErrorMsg(extractError(err).message);
        setState('error');
      });
  }, [token]);

  async function handleResend(e: React.FormEvent) {
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
    setErrorMsg(null);
    setResending(true);
    try {
      await authService.resendVerification(workspaceCode.toUpperCase(), email);
      setResent(true);
    } catch (err) {
      setErrorMsg(extractError(err).message);
    } finally {
      setResending(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-bg via-bg-raised to-accent/10 px-4">
      <div className="w-full max-w-md">
        <div className="mb-8 flex items-center justify-center gap-2">
          <BrandLogo size={40} />
          <span className="font-heading font-heading text-2xl font-semibold text-fg">JURIKA</span>
        </div>

        <Card className="p-8">
          {state === 'verifying' && (
            <div className="space-y-4 text-center">
              <RefreshCw className="mx-auto h-10 w-10 animate-spin text-accent" />
              <h2 className="font-heading text-xl font-semibold text-fg">Verification en cours...</h2>
              <p className="text-sm text-fg-muted">Nous validons votre email aupres du serveur.</p>
            </div>
          )}

          {state === 'success' && (
            <div className="space-y-5 text-center">
              <CheckCircle2 className="mx-auto h-12 w-12 text-success" />
              <h2 className="font-heading text-xl font-semibold text-fg">Email verifie</h2>
              <p className="text-sm text-fg-muted">
                {message ?? 'Email verifie avec succes. Vous pouvez vous connecter.'}
              </p>
              <Link to="/login">
                <Button className="w-full">Aller a la connexion</Button>
              </Link>
            </div>
          )}

          {state === 'error' && (
            <div className="space-y-5 text-center">
              <ShieldAlert className="mx-auto h-12 w-12 text-danger" />
              <h2 className="font-heading text-xl font-semibold text-fg">Lien invalide ou expire</h2>
              <p className="text-sm text-fg-muted">{errorMsg}</p>
              <Button variant="secondary" onClick={() => setState('resend')} className="w-full">
                Renvoyer un lien
              </Button>
            </div>
          )}

          {state === 'resend' && (
            <form onSubmit={handleResend} className="space-y-5" noValidate>
              <div className="text-center">
                <Mail className="mx-auto h-10 w-10 text-accent" />
                <h2 className="mt-3 font-heading text-xl font-semibold text-fg">Renvoyer l'email</h2>
                <p className="mt-1 text-sm text-fg-muted">
                  Indiquez votre code workspace et email pour recevoir un nouveau lien.
                </p>
              </div>
              <TextField
                label="Code workspace"
                name="workspaceCode"
                placeholder="JUR-AB123"
                value={workspaceCode}
                onChange={(e) => {
                  setWorkspaceCode(e.target.value.toUpperCase());
                  setFieldErrors((f) => ({ ...f, workspaceCode: undefined }));
                }}
                pattern="^JUR-[A-Z0-9]{5}$"
                maxLength={9}
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
              {errorMsg && (
                <div className="rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
                  {errorMsg}
                </div>
              )}
              {resent && (
                <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700">
                  Si le compte existe, un email a ete envoye. Consultez votre boite mail.
                </div>
              )}
              <Button type="submit" className="w-full" loading={resending}>
                Envoyer le lien
              </Button>
              <Link
                to="/login"
                className="block text-center text-sm font-medium text-accent hover:text-accent"
              >
                Retour a la connexion
              </Link>
            </form>
          )}
        </Card>
      </div>
    </div>
  );
}
