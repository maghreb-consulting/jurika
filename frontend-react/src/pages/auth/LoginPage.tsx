import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  ArrowLeft,
  ArrowRight,
  Eye,
  EyeOff,
  KeyRound,
  Lock,
  Mail,
  ShieldCheck,
} from 'lucide-react';
import { Stepper } from '../../components/auth/Stepper';
import { PageAccueil, TitreAccueil } from '../../components/auth/PageAccueil';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Lien } from '../../components/ui/Lien';
import { Message } from '../../components/ui/Message';
import { CLASSES_CHAMP } from '../../components/ui/TextField';
import { authService } from '../../services/auth.service';
import { useAuthStore } from '../../store/authStore';
import { extractError } from '../../lib/api';
import { firstError, requiredMsg, emailMsg, patternMsg } from '../../lib/formValidation';

type Step = 'workspace' | 'credentials' | 'twofa';

export function LoginPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const setSession = useAuthStore((s) => s.setSession);

  // Message contextualise apres une deconnexion cote serveur (interceptor api.ts).
  const SESSION_NOTICES: Record<string, string> = {
    session_revoked: 'Votre session a été ouverte sur un autre appareil. Reconnectez-vous pour continuer.',
    inactivity: 'Votre session a expiré après une période d’inactivité. Reconnectez-vous pour continuer.',
    expired: 'Votre session a expiré. Reconnectez-vous pour continuer.',
  };
  const sessionNotice = SESSION_NOTICES[searchParams.get('reason') ?? ''] ?? null;

  const [step, setStep] = useState<Step>('workspace');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const [workspaceCode, setWorkspaceCode] = useState('');
  const [workspaceName, setWorkspaceName] = useState('');
  const [workspaceId, setWorkspaceId] = useState('');

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [userId, setUserId] = useState('');

  const [twofa, setTwofa] = useState('');
  // Methode de la double authentification du compte (texte de l'etape 3).
  const [methode2fa, setMethode2fa] = useState<'TOTP' | 'SMS' | null>(null);

  const currentStep: 1 | 2 | 3 = step === 'workspace' ? 1 : step === 'credentials' ? 2 : 3;

  // ------------------------------------------------------------------
  //  Affixes fixes (2026-08-15) — « JUR- » et « @jurika.ma »
  // ------------------------------------------------------------------
  // Ces deux fragments ne changent jamais d'une connexion a l'autre : les
  // retaper a chaque fois est du travail inutile. Ils sont donc affiches comme
  // parties FIXES du champ, et la saisie ne porte plus que ce qui varie.
  //
  // Les etats `workspaceCode` et `email` continuent de porter la valeur
  // COMPLETE : tout ce qui les consomme (checkWorkspace, login, setSession)
  // reste inchange. Seul l'affichage est decoupe.
  const WS_PREFIXE = 'JUR-';
  const MAIL_SUFFIXE = '@jurika.ma';

  /** Partie saisissable du code workspace (les 5 caracteres apres « JUR- »). */
  const wsSaisie = workspaceCode.replace(/^JUR-?/i, '');

  /**
   * L'identifiant n'est PAS toujours en @jurika.ma : le commentaire BUG 7
   * ci-dessous le rappelle — les comptes anterieurs a V28 se connectent avec
   * leur email personnel (backfill 1:1), et les espaces de demonstration
   * utilisent d'autres domaines. Figer le suffixe sans echappatoire les
   * exclurait purement et simplement.
   *
   * Des que la saisie contient un « @ », on repasse donc en adresse LIBRE : le
   * suffixe disparait et le champ accepte n'importe quel domaine.
   */
  const emailLibre = email.includes('@') && !email.endsWith(MAIL_SUFFIXE);
  const mailSaisie = emailLibre ? email : email.replace(new RegExp(`${MAIL_SUFFIXE}$`), '');

  function focus(name: string) {
    // Focus in-app du champ concerné (a11y), sans bulle native.
    document.querySelector<HTMLElement>(`[name="${name}"]`)?.focus();
  }

  async function handleWorkspace(e: React.FormEvent) {
    e.preventDefault();
    const code = workspaceCode.toUpperCase();
    const invalid = firstError(
      () => requiredMsg(code),
      () => patternMsg(code, /^JUR-[A-Z0-9]{5}$/, 'Code du cabinet incomplet : saisissez les 5 lettres ou chiffres qui suivent « JUR- ».'),
    );
    if (invalid) {
      setError(invalid);
      focus('workspaceCode');
      return;
    }
    setError(null);
    setLoading(true);
    try {
      const result = await authService.checkWorkspace(workspaceCode.toUpperCase());
      setWorkspaceId(result.workspaceId);
      setWorkspaceName(result.name);
      setStep('credentials');
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }

  async function handleCredentials(e: React.FormEvent) {
    e.preventDefault();
    const emailErr = emailMsg(email);
    if (emailErr) {
      setError(emailErr);
      focus('email');
      return;
    }
    const pwdErr = requiredMsg(password);
    if (pwdErr) {
      setError(pwdErr);
      focus('password');
      return;
    }
    setError(null);
    setLoading(true);
    try {
      const result = await authService.login(workspaceCode.toUpperCase(), email, password);
      setUserId(result.userId);
      setMethode2fa(result.twofaMethod);
      if (result.requires2fa) {
        if (result.twofaMethod === 'SMS') {
          try {
            await authService.sendSmsOtp('2FA_LOGIN');
          } catch {
            /* backend will report on verify */
          }
        }
        setStep('twofa');
        return;
      }
      setSession(
        {
          accessToken: result.accessToken!,
          refreshToken: result.refreshToken!,
          accessExpiresAt: result.accessExpiresAt!,
          refreshExpiresAt: result.refreshExpiresAt!,
          userId: result.userId,
          workspaceId: result.workspaceId,
        },
        'EMPLOYE',
        email,
        workspaceCode.toUpperCase(),
      );
      if (result.mustChangePassword) {
        navigate('/account/change-password', { replace: true });
        return;
      }
      if (result.requires2faSetup) {
        navigate('/account/2fa-choose', { replace: true });
        return;
      }
      navigate('/dashboard', { replace: true });
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }

  async function handleTwofa(e: React.FormEvent) {
    e.preventDefault();
    const codeErr = firstError(
      () => requiredMsg(twofa),
      () => patternMsg(twofa, /^\d{6}$/, 'Le code doit comporter 6 chiffres.'),
    );
    if (codeErr) {
      setError(codeErr);
      focus('twofa');
      return;
    }
    setError(null);
    setLoading(true);
    try {
      // `twofa` reste une CHAINE : `parseInt` mangeait le zero initial d'un code
      // sur dix, que le serveur rejetait ensuite pour longueur invalide.
      const tokens = await authService.verify2fa(userId, workspaceId, twofa);
      setSession(tokens, 'EMPLOYE', email, workspaceCode.toUpperCase());
      navigate('/dashboard', { replace: true });
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }

  // Cadre commun des champs a affixe fixe (code du cabinet, identifiant) : memes jetons que
  // les champs de la bibliotheque (bordure de saisie, anneau de focus).
  const cadreChamp = (enErreur: boolean) =>
    `flex w-full items-stretch overflow-hidden rounded-lg border bg-bg-raised transition-colors duration-150 focus-within:outline-2 focus-within:outline-offset-0 ${
      enErreur
        ? 'border-danger focus-within:outline-danger'
        : 'border-border-input hover:border-fg-subtle focus-within:border-focus focus-within:outline-focus'
    }`;

  return (
    <PageAccueil
      titre="Connexion"
      enTete={
        <>
          Pas encore d’espace ? <Lien to="/essai">Essai gratuit</Lien>
        </>
      }
    >
      <Stepper currentStep={currentStep} />

      {sessionNotice && (
        <Message ton="attention" className="mb-6">
          {sessionNotice}
        </Message>
      )}

      {step === 'workspace' && (
        <form onSubmit={handleWorkspace} noValidate>
          <TitreAccueil
            icone={<KeyRound />}
            titre="Accédez à votre espace"
            infoBulle="Le code du cabinet commence par « JUR- » suivi de 5 lettres ou chiffres. Il figure dans le courriel de bienvenue ou d’invitation. Vous ne l’avez plus ? Demandez-le au superviseur de votre cabinet. Étape suivante : votre identifiant et votre mot de passe."
            libelleInfoBulle="Où trouver le code du cabinet ?"
          >
            Saisissez le code de votre cabinet, reçu par courriel à l’ouverture de votre compte.
          </TitreAccueil>

          <div className="mb-4">
            {/* « JUR- » est fixe : affiche comme partie du champ, jamais a
                retaper. Un collage de « JUR-KDNJ2 » reste accepte — le
                prefixe en trop est retire. Prefixe a la signature du logo
                (lettres a l'encre, tiret or), tout en jetons. */}
            <div className={`${cadreChamp(!!error)} h-14`}>
              <span
                aria-hidden="true"
                className="flex select-none items-center border-r border-border bg-bg-overlay pl-5 pr-3 font-mono text-xl font-bold tracking-wider"
              >
                <span className="text-heading">JUR</span>
                <span className="text-accent">-</span>
              </span>
              <input
                type="text"
                name="workspaceCode"
                value={wsSaisie}
                onChange={(e) => {
                  // On tolere le collage du code complet et les minuscules.
                  const brut = e.target.value.toUpperCase().replace(/^JUR-?/, '');
                  setWorkspaceCode(WS_PREFIXE + brut.replace(/[^A-Z0-9]/g, '').slice(0, 5));
                  setError(null);
                }}
                placeholder="XXXXX"
                // Le sens est porte par le titre au-dessus : nom accessible explicite.
                aria-label="Code du cabinet, les 5 caractères après JUR-"
                aria-invalid={error ? true : undefined}
                inputMode="text"
                maxLength={5}
                autoComplete="organization"
                required
                className="h-full min-w-0 flex-1 bg-transparent px-4 font-mono text-xl font-bold tracking-wider text-fg outline-none placeholder:text-fg-subtle"
              />
            </div>
          </div>

          {error && <ErrorBanner message={error} />}

          <Button type="submit" size="lg" fullWidth loading={loading} iconEnd={<ArrowRight />}>
            Continuer
          </Button>

          <p className="mt-6 text-center text-sm text-fg-muted">
            Votre cabinet n’a pas encore d’espace ? <Lien to="/signup">Créer l’espace du cabinet</Lien>
          </p>
        </form>
      )}

      {step === 'credentials' && (
        <form onSubmit={handleCredentials} noValidate>
          <div className="mb-6 flex justify-center">
            <Badge variant="success" dot className="whitespace-normal">
              Espace {workspaceCode.toUpperCase()} — {workspaceName}
            </Badge>
          </div>

          <TitreAccueil titre="Bienvenue dans votre espace">
            Connectez-vous avec votre identifiant et votre mot de passe. Si la double authentification est active
            sur votre compte, un code vous sera ensuite demandé.
          </TitreAccueil>

          <div className="mb-4 flex flex-col gap-1.5">
            <label htmlFor="login-email" className="text-sm font-medium text-fg">
              Identifiant de connexion
            </label>
            <div className={`${cadreChamp(false)} relative h-11`}>
              <Mail
                className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-fg-subtle"
                aria-hidden="true"
              />
              <input
                id="login-email"
                // `type="email"` exigerait une adresse complete ; en mode
                // suffixe fixe, le champ ne porte que la partie locale.
                type={emailLibre ? 'email' : 'text'}
                name="email"
                value={mailSaisie}
                onChange={(e) => {
                  const v = e.target.value.trim();
                  // Un « @ » saisi ou colle bascule en adresse libre : les
                  // comptes anterieurs a V28 ne sont pas en @jurika.ma.
                  setEmail(v.includes('@') ? v : v ? v + MAIL_SUFFIXE : '');
                }}
                placeholder={emailLibre ? 'prenom.nom@jurika.ma' : 'prenom.nom'}
                autoComplete="email"
                aria-describedby="login-email-aide"
                required
                className="h-full min-w-0 flex-1 bg-transparent pl-10 pr-2 text-sm text-fg outline-none placeholder:text-fg-subtle"
              />
              {!emailLibre && (
                /* Meme signature que le prefixe : la racine a l'encre, le
                   « .ma » en or. */
                <span
                  aria-hidden="true"
                  className="flex select-none items-center border-l border-border bg-bg-overlay px-3 text-sm font-semibold"
                >
                  <span className="text-fg-muted">@jurika</span>
                  <span className="text-accent">.ma</span>
                </span>
              )}
            </div>
            {/* BUG 7 (2026-06-08) — l'identifiant @jurika.ma est genere a
                l'inscription/invitation. Pour les comptes legacy d'avant
                V28, login_email = email perso (backfill 1:1). */}
            <p id="login-email-aide" className="text-xs text-fg-subtle">
              Votre identifiant en <code className="font-mono">@jurika.ma</code> figure dans votre courriel de
              bienvenue. Les notifications continuent d’arriver à votre adresse personnelle.
            </p>
          </div>

          <div className="mb-4 flex flex-col gap-1.5">
            <label htmlFor="login-password" className="text-sm font-medium text-fg">
              Mot de passe
            </label>
            <div className={`${cadreChamp(false)} relative h-11`}>
              <Lock
                className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-fg-subtle"
                aria-hidden="true"
              />
              <input
                id="login-password"
                type={showPassword ? 'text' : 'password'}
                name="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                autoComplete="current-password"
                required
                className="h-full min-w-0 flex-1 bg-transparent pl-10 pr-2 text-sm text-fg outline-none placeholder:text-fg-subtle"
              />
              <button
                type="button"
                onClick={() => setShowPassword((v) => !v)}
                className="mr-1.5 inline-flex h-8 w-8 shrink-0 items-center justify-center self-center rounded-md text-fg-subtle hover:bg-bg-overlay hover:text-fg"
                aria-label={showPassword ? 'Masquer le mot de passe' : 'Afficher le mot de passe'}
              >
                {showPassword ? <EyeOff className="h-4 w-4" aria-hidden="true" /> : <Eye className="h-4 w-4" aria-hidden="true" />}
              </button>
            </div>
          </div>

          {error && <ErrorBanner message={error} />}

          <Button type="submit" size="lg" fullWidth className="mt-2" loading={loading} iconEnd={<ArrowRight />}>
            Se connecter
          </Button>

          <div className="mt-6 flex flex-wrap items-center justify-between gap-3 text-sm">
            <Button type="button" variant="ghost" size="sm" icon={<ArrowLeft />} onClick={() => setStep('workspace')}>
              Changer de cabinet
            </Button>
            <Lien to="/reset-password">Mot de passe oublié ?</Lien>
          </div>
        </form>
      )}

      {step === 'twofa' && (
        <form onSubmit={handleTwofa} noValidate>
          <TitreAccueil icone={<ShieldCheck />} titre="Double authentification">
            {methode2fa === 'SMS'
              ? 'Saisissez le code à 6 chiffres que nous venons de vous envoyer par SMS.'
              : 'Saisissez le code à 6 chiffres affiché par votre application d’authentification.'}
          </TitreAccueil>

          <div className="mb-4">
            <input
              type="text"
              name="twofa"
              inputMode="numeric"
              pattern="[0-9]{6}"
              maxLength={6}
              value={twofa}
              onChange={(e) => setTwofa(e.target.value.replace(/\D/g, ''))}
              placeholder="123456"
              // Champ centre sans libelle visible : nom accessible explicite.
              aria-label="Code de vérification à 6 chiffres"
              aria-describedby="login-twofa-aide"
              autoComplete="one-time-code"
              required
              className={`${CLASSES_CHAMP} h-14 text-center font-mono text-2xl font-bold tracking-[0.5em]`}
            />
          </div>

          <p id="login-twofa-aide" className="mb-6 text-center text-xs text-fg-subtle">
            {methode2fa === 'SMS'
              ? 'Le code est valable quelques minutes. Vous n’avez rien reçu ? Revenez en arrière et reconnectez-vous pour en recevoir un nouveau.'
              : 'Google Authenticator, Microsoft Authenticator ou toute application compatible. Le code change toutes les 30 secondes.'}
          </p>

          {error && <ErrorBanner message={error} />}

          <Button type="submit" size="lg" fullWidth loading={loading} disabled={twofa.length !== 6}>
            Vérifier et accéder
          </Button>

          <p className="mt-4 text-center text-sm">
            <Lien to="/auth/recover-with-code">
              Vous n’avez plus accès à votre téléphone ? Utiliser un code de récupération
            </Lien>
          </p>

          <div className="mt-6 flex justify-center">
            <Button type="button" variant="ghost" size="sm" icon={<ArrowLeft />} onClick={() => setStep('credentials')}>
              Retour
            </Button>
          </div>
        </form>
      )}
    </PageAccueil>
  );
}

function ErrorBanner({ message }: { message: string }) {
  return (
    <Message ton="erreur" className="mb-4">
      {message}
    </Message>
  );
}
