import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { CheckCircle2, KeyRound } from 'lucide-react';
import { PageAccueil, TitreAccueil } from '../../components/auth/PageAccueil';
import { Button } from '../../components/ui/Button';
import { Lien } from '../../components/ui/Lien';
import { Message } from '../../components/ui/Message';
import { TextField } from '../../components/ui/TextField';
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
            'Code du cabinet non valide : format attendu JUR- suivi de 5 lettres ou chiffres.',
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
    <PageAccueil
      titre="Mot de passe oublié"
      largeur="etroite"
      enTete={<Lien to="/login">Se connecter</Lien>}
    >
      {confirmed ? (
        <div className="space-y-5">
          <TitreAccueil icone={<CheckCircle2 />} ton="succes" titre="Mot de passe modifié">
            Connectez-vous avec votre nouveau mot de passe.
          </TitreAccueil>
          <Lien to="/login" commeBouton={{ variant: 'primary', size: 'lg' }} className="w-full">
            Aller à la connexion
          </Lien>
        </div>
      ) : tokenFromUrl || token ? (
        <>
          <TitreAccueil icone={<KeyRound />} titre="Nouveau mot de passe">
            Choisissez votre nouveau mot de passe. Le lien reçu par courriel ne sert qu’une fois.
          </TitreAccueil>
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
                mono
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
              autoComplete="new-password"
              required
              minLength={12}
              error={fieldErrors.newPassword}
            />
            {error && <Message ton="erreur">{error}</Message>}
            <Button type="submit" size="lg" fullWidth loading={loading}>
              Enregistrer le nouveau mot de passe
            </Button>
          </form>
        </>
      ) : requested ? (
        <div className="space-y-5">
          <TitreAccueil icone={<CheckCircle2 />} ton="succes" titre="Courriel envoyé">
            Si cette adresse correspond à un compte du cabinet, un lien de réinitialisation vient de lui être
            envoyé. Ouvrez-le pour choisir votre nouveau mot de passe ; pensez à vérifier les courriers
            indésirables.
          </TitreAccueil>
          <Lien to="/login" commeBouton={{ variant: 'secondary', size: 'lg' }} className="w-full">
            Retour à la connexion
          </Lien>
        </div>
      ) : (
        <>
          <TitreAccueil
            icone={<KeyRound />}
            titre="Mot de passe oublié"
            libelleInfoBulle="Aide sur la réinitialisation du mot de passe"
            infoBulle="Indiquez le code de votre cabinet (JUR- suivi de 5 lettres ou chiffres) et l’adresse électronique de votre compte. Vous recevrez un lien, valable pour une durée limitée indiquée dans le courriel, pour choisir un nouveau mot de passe."
          >
            Saisissez le code de votre cabinet et votre adresse électronique : nous vous enverrons un lien de
            réinitialisation.
          </TitreAccueil>
          <form onSubmit={request} className="space-y-4" noValidate>
            <TextField
              label="Code du cabinet"
              name="workspaceCode"
              value={workspaceCode}
              onChange={(e) => {
                setWorkspaceCode(e.target.value.toUpperCase());
                setFieldErrors((f) => ({ ...f, workspaceCode: undefined }));
              }}
              pattern="^JUR-[A-Z0-9]{5}$"
              maxLength={9}
              placeholder="JUR-AB123"
              autoComplete="organization"
              mono
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
              autoComplete="email"
              required
              error={fieldErrors.email}
            />
            {error && <Message ton="erreur">{error}</Message>}
            <Button type="submit" size="lg" fullWidth loading={loading}>
              Envoyer le lien
            </Button>
            <p className="text-center text-sm">
              <Lien to="/login" variante="discret">
                Retour à la connexion
              </Lien>
            </p>
          </form>
        </>
      )}
    </PageAccueil>
  );
}
