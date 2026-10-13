import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { CheckCircle2, KeyRound } from 'lucide-react';
import { BrandLogo } from '../../components/ui/BrandLogo';
import { InfoBulle } from '../../components/ui/Aide';
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
        // Meme minimum que la politique du serveur (PasswordPolicy : 12 caracteres).
        () => minLengthMsg(newPassword, 12),
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
        <Link to="/" className="mb-8 flex items-center justify-center" aria-label="JURIKA, retour à l’accueil">
          <BrandLogo variant="full" size={48} />
        </Link>
        <Card className="space-y-5 p-8">
          {confirmed ? (
            <div className="space-y-4 text-center">
              <CheckCircle2 className="mx-auto h-12 w-12 text-success" />
              <h1 className="font-heading text-xl font-semibold text-fg">Mot de passe modifié</h1>
              <p className="text-sm text-fg-muted">
                Connectez-vous avec votre nouveau mot de passe.
              </p>
              <Link to="/login" className="block">
                <Button className="w-full">Aller à la connexion</Button>
              </Link>
            </div>
          ) : tokenFromUrl || token ? (
            <>
              <div className="text-center">
                <KeyRound className="mx-auto h-10 w-10 text-accent" />
                <h1 className="mt-3 font-heading text-xl font-semibold text-fg">Nouveau mot de passe</h1>
                <p className="mt-1 text-sm text-fg-muted">
                  Choisissez votre nouveau mot de passe. Le lien reçu par courriel ne sert qu’une fois.
                </p>
              </div>
              <form onSubmit={confirm} className="space-y-4" noValidate>
                {!tokenFromUrl && (
                  <TextField
                    label="Code reçu par courriel"
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
                  hint="Au moins 12 caractères, dont une majuscule, une minuscule, un chiffre et un caractère spécial."
                  required
                  minLength={12}
                  error={fieldErrors.newPassword}
                />
                {error && (
                  <div className="rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
                    {error}
                  </div>
                )}
                <Button type="submit" className="w-full" loading={loading}>
                  Enregistrer le nouveau mot de passe
                </Button>
              </form>
            </>
          ) : requested ? (
            <div className="space-y-4 text-center">
              <CheckCircle2 className="mx-auto h-12 w-12 text-success" />
              <h1 className="font-heading text-xl font-semibold text-fg">Courriel envoyé</h1>
              <p className="text-sm text-fg-muted">
                Si cette adresse correspond à un compte du workspace, un lien de réinitialisation
                vient de lui être envoyé. Ouvrez-le pour choisir votre nouveau mot de passe ; pensez
                à vérifier les courriers indésirables.
              </p>
              <Link to="/login" className="block">
                <Button variant="secondary" className="w-full">Retour à la connexion</Button>
              </Link>
            </div>
          ) : (
            <>
              <div className="text-center">
                <KeyRound className="mx-auto h-10 w-10 text-accent" />
                <h1 className="mt-3 flex items-center justify-center gap-1 font-heading text-xl font-semibold text-fg">
                  Mot de passe oublié
                  <InfoBulle
                    libelle="Aide sur la réinitialisation du mot de passe"
                    texte="Indiquez le code de votre workspace (JUR- suivi de 5 lettres ou chiffres) et l’adresse électronique de votre compte. Vous recevrez un lien, valable pour une durée limitée indiquée dans le courriel, pour choisir un nouveau mot de passe."
                  />
                </h1>
                <p className="mt-1 text-sm text-fg-muted">
                  Saisissez le code de votre workspace et votre adresse électronique : nous vous
                  enverrons un lien de réinitialisation.
                </p>
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
                {error && (
                  <div className="rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
                    {error}
                  </div>
                )}
                <Button type="submit" className="w-full" loading={loading}>
                  Envoyer le lien
                </Button>
                <Link to="/login" className="block text-center text-sm text-fg-subtle hover:text-fg">
                  Retour à la connexion
                </Link>
              </form>
            </>
          )}
        </Card>
      </div>
    </div>
  );
}
