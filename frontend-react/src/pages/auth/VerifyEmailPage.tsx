import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { CheckCircle2, Mail, RefreshCw, ShieldAlert } from 'lucide-react';
import { PageAccueil, TitreAccueil } from '../../components/auth/PageAccueil';
import { Button } from '../../components/ui/Button';
import { Lien } from '../../components/ui/Lien';
import { SqueletteZone } from '../../components/ui/LoadingSkeleton';
import { Message } from '../../components/ui/Message';
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
    <PageAccueil
      titre="Vérification de l’adresse"
      largeur="etroite"
      enTete={<Lien to="/login">Se connecter</Lien>}
    >
      {state === 'verifying' && (
        <SqueletteZone libelle="Vérification de votre adresse en cours">
          <TitreAccueil icone={<RefreshCw className="motion-safe:animate-spin" />} titre="Vérification en cours…">
            Nous vérifions votre adresse électronique.
          </TitreAccueil>
        </SqueletteZone>
      )}

      {state === 'success' && (
        <div className="space-y-5">
          <TitreAccueil icone={<CheckCircle2 />} ton="succes" titre="Adresse vérifiée">
            <p>{message ?? 'Adresse électronique vérifiée. Vous pouvez maintenant vous connecter.'}</p>
            <p className="mt-2 text-xs text-fg-subtle">
              À la première connexion, vous choisirez un nouveau mot de passe puis la méthode de double
              authentification.
            </p>
          </TitreAccueil>
          <Lien to="/login" commeBouton={{ variant: 'primary', size: 'lg' }} className="w-full">
            Aller à la connexion
          </Lien>
        </div>
      )}

      {state === 'error' && (
        <div className="space-y-5">
          <TitreAccueil icone={<ShieldAlert />} ton="erreur" titre="Lien non valide ou expiré">
            <p>{errorMsg}</p>
            <p className="mt-2 text-xs text-fg-subtle">
              Demandez un nouveau lien : il vous sera envoyé par courriel, avec sa durée de validité.
            </p>
          </TitreAccueil>
          <Button type="button" variant="secondary" size="lg" fullWidth onClick={() => setState('resend')}>
            Renvoyer un lien
          </Button>
        </div>
      )}

      {state === 'resend' && (
        <form onSubmit={handleResend} className="space-y-4" noValidate>
          <TitreAccueil
            icone={<Mail />}
            titre="Recevoir un nouveau lien"
            libelleInfoBulle="Aide sur la vérification de l’adresse"
            infoBulle="Le lien de vérification active votre compte. Indiquez le code de votre cabinet (dans le courriel de bienvenue) et l’adresse électronique saisie à l’inscription : un nouveau lien vous sera envoyé."
          >
            Indiquez le code de votre cabinet et votre adresse électronique pour recevoir un nouveau lien
            d’activation.
          </TitreAccueil>
          <TextField
            label="Code du cabinet"
            name="workspaceCode"
            placeholder="JUR-AB123"
            value={workspaceCode}
            onChange={(e) => {
              setWorkspaceCode(e.target.value.toUpperCase());
              setFieldErrors((f) => ({ ...f, workspaceCode: undefined }));
            }}
            pattern="^JUR-[A-Z0-9]{5}$"
            maxLength={9}
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
          {errorMsg && <Message ton="erreur">{errorMsg}</Message>}
          {resent && (
            <Message ton="succes">
              Si ce compte existe, un courriel vient de lui être envoyé. Consultez votre messagerie (pensez aux
              courriers indésirables).
            </Message>
          )}
          <Button type="submit" size="lg" fullWidth loading={resending}>
            Envoyer le lien
          </Button>
          <p className="text-center text-sm">
            <Lien to="/login" variante="discret">
              Retour à la connexion
            </Lien>
          </p>
        </form>
      )}
    </PageAccueil>
  );
}
