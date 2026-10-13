import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { CheckCircle2, Mail, RefreshCw, ShieldAlert } from 'lucide-react';
import { BrandLogo } from '../../components/ui/BrandLogo';
import { InfoBulle } from '../../components/ui/Aide';
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

  // L'etat initial vaut deja 'verifying' quand un lien est present : l'effet ne
  // fait que lancer la verification et ne change l'etat qu'a la reponse.
  useEffect(() => {
    if (!token) return;
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
            'Code workspace non valide : format attendu JUR- suivi de 5 lettres ou chiffres.',
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
        <Link to="/" className="mb-8 flex items-center justify-center" aria-label="JURIKA, retour à l’accueil">
          <BrandLogo variant="full" size={48} />
        </Link>

        <Card className="p-8">
          {state === 'verifying' && (
            <div className="space-y-4 text-center">
              <RefreshCw className="mx-auto h-10 w-10 animate-spin text-accent" />
              <h1 className="font-heading text-xl font-semibold text-fg">Vérification en cours…</h1>
              <p className="text-sm text-fg-muted">Nous vérifions votre adresse électronique.</p>
            </div>
          )}

          {state === 'success' && (
            <div className="space-y-5 text-center">
              <CheckCircle2 className="mx-auto h-12 w-12 text-success" />
              <h1 className="font-heading text-xl font-semibold text-fg">Adresse vérifiée</h1>
              <p className="text-sm text-fg-muted">
                {message ?? 'Adresse électronique vérifiée. Vous pouvez maintenant vous connecter.'}
              </p>
              <p className="text-xs text-fg-subtle">
                À la première connexion, vous choisirez un nouveau mot de passe puis la méthode de
                double authentification.
              </p>
              <Link to="/login">
                <Button className="w-full">Aller à la connexion</Button>
              </Link>
            </div>
          )}

          {state === 'error' && (
            <div className="space-y-5 text-center">
              <ShieldAlert className="mx-auto h-12 w-12 text-danger" />
              <h1 className="font-heading text-xl font-semibold text-fg">Lien non valide ou expiré</h1>
              <p className="text-sm text-fg-muted">{errorMsg}</p>
              <p className="text-xs text-fg-subtle">
                Demandez un nouveau lien : il vous sera envoyé par courriel, avec sa durée de validité.
              </p>
              <Button variant="secondary" onClick={() => setState('resend')} className="w-full">
                Renvoyer un lien
              </Button>
            </div>
          )}

          {state === 'resend' && (
            <form onSubmit={handleResend} className="space-y-5" noValidate>
              <div className="text-center">
                <Mail className="mx-auto h-10 w-10 text-accent" />
                <h1 className="mt-3 flex items-center justify-center gap-1 font-heading text-xl font-semibold text-fg">
                  Recevoir un nouveau lien
                  <InfoBulle
                    libelle="Aide sur la vérification de l’adresse"
                    texte="Le lien de vérification active votre compte. Indiquez le code de votre workspace (dans le courriel de bienvenue) et l’adresse électronique saisie à l’inscription : un nouveau lien vous sera envoyé."
                  />
                </h1>
                <p className="mt-1 text-sm text-fg-muted">
                  Indiquez le code de votre workspace et votre adresse électronique pour recevoir un
                  nouveau lien d’activation.
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
                label="Adresse électronique"
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
                <div role="status" className="rounded-lg border border-success/40 bg-success/10 px-3 py-2 text-sm text-success">
                  Si ce compte existe, un courriel vient de lui être envoyé. Consultez votre messagerie
                  (pensez aux courriers indésirables).
                </div>
              )}
              <Button type="submit" className="w-full" loading={resending}>
                Envoyer le lien
              </Button>
              <Link
                to="/login"
                className="block text-center text-sm font-medium text-accent hover:text-accent"
              >
                Retour à la connexion
              </Link>
            </form>
          )}
        </Card>
      </div>
    </div>
  );
}
