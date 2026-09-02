import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import {
  ArrowLeft,
  Eye,
  EyeOff,
  KeyRound,
  Lock,
  Mail,
  ShieldCheck,
} from 'lucide-react';
import { Stepper } from '../../components/auth/Stepper';
import { BrandLogo } from '../../components/ui/BrandLogo';
import { Button } from '../../components/ui/Button';
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
    session_revoked: 'Votre session a ete ouverte sur un autre appareil. Veuillez vous reconnecter.',
    inactivity: 'Votre session a expire pour cause d’inactivite. Veuillez vous reconnecter.',
    expired: 'Votre session a expire. Veuillez vous reconnecter.',
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
      () => patternMsg(code, /^JUR-[A-Z0-9]{5}$/, 'Code workspace invalide (format JUR-XXXXX).'),
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

  return (
    <div className="flex min-h-screen bg-bg-raised">
      {/* Sprint 12.6 -- BrandingPanel gauche supprime (testimonials Cabinet Al-Amin
          + Benali & Associes + decorations SVG bleu/vert hardcodees incompatibles
          avec la nouvelle DA editoriale light). Le formulaire occupe 100% largeur. */}

      {/* Form panel */}
      <div className="relative flex w-full flex-col items-center justify-center bg-bg-raised p-8 lg:p-12">
        {/* Sprint 12.6 -- Logo cliquable retour landing (SPA Link, fluide). */}
        <Link
          to="/"
          className="absolute top-6 left-6 flex items-center gap-2 transition-opacity hover:opacity-80"
          aria-label="Retour à l'accueil"
        >
          <BrandLogo size={36} />
          <span className="font-heading text-xl font-semibold text-fg">JURIKA</span>
        </Link>

        <div className="w-full max-w-[480px]">
          <Stepper currentStep={currentStep} />

          {sessionNotice && (
            <div
              role="status"
              className="mb-6 rounded-lg border-2 border-warning bg-warning/10 px-4 py-3 text-sm text-warning"
            >
              {sessionNotice}
            </div>
          )}

          {step === 'workspace' && (
            <form onSubmit={handleWorkspace} noValidate>
              <div className="mb-8">
                <div className="mb-5 flex justify-center">
                  <div className="flex h-14 w-14 items-center justify-center rounded-full bg-accent/10">
                    <KeyRound className="h-7 w-7 text-accent" />
                  </div>
                </div>
                <h2 className="mb-3 text-center font-heading text-3xl font-semibold text-fg">
                  Accedez a votre espace
                </h2>
                <p className="text-center text-sm text-fg-subtle">
                  Entrez le code unique recu par email lors de votre inscription.
                </p>
              </div>

              <div className="mb-4">
                {/* « JUR- » est fixe : affiche comme partie du champ, jamais a
                    retaper. Un collage de « JUR-KDNJ2 » reste accepte — le
                    prefixe en trop est retire. */}
                <div
                  className={`flex h-14 w-full items-stretch overflow-hidden rounded-lg bg-bg-overlay transition-all ${
                    error
                      ? 'border-2 border-danger'
                      : 'border-2 border-border focus-within:border-accent'
                  }`}
                >
                  {/* Traitement du LOGO : le wordmark JURIKA est bicolore —
                      « JURI » a l'encre navy, « KA » en or. Le prefixe reprend
                      cette signature (lettres navy, tiret or) sur un fond or
                      tres dilue, pour se lire comme une partie de la marque et
                      non comme un champ desactive. Tout en tokens : la bascule
                      clair / sombre suit la charte sans valeur en dur. */}
                  <span
                    aria-hidden="true"
                    className="flex select-none items-center border-r border-accent/30 bg-accent/10 pl-5 pr-3 font-mono text-xl font-bold tracking-wider"
                  >
                    <span className="text-fg">JUR</span>
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
                    // Aucun libelle visible : la maquette montre un grand champ
                    // centre, le sens etant porte par le titre au-dessus. Sans nom
                    // accessible, un lecteur d'ecran annoncait « zone de texte » sur
                    // le tout premier champ du produit.
                    aria-label="Code workspace, les 5 caracteres apres JUR-"
                    inputMode="text"
                    maxLength={5}
                    autoComplete="organization"
                    required
                    className="h-full min-w-0 flex-1 bg-transparent pr-5 font-mono text-xl font-bold tracking-wider outline-none"
                  />
                </div>
              </div>

              {error && <ErrorBanner message={error} />}

              <Button type="submit" className="h-12 w-full" loading={loading}>
                Continuer →
              </Button>

              <p className="mt-6 text-center text-xs text-fg-subtle">
                Pas encore d'espace ?{' '}
                <Link to="/signup" className="font-medium text-accent hover:underline">
                  Creer un nouveau workspace
                </Link>
              </p>
            </form>
          )}

          {step === 'credentials' && (
            <form onSubmit={handleCredentials} noValidate>
              <div className="mb-6 inline-flex items-center gap-2 rounded-full border border-success bg-success/10 px-4 py-2 text-sm text-success">
                ✓ Espace {workspaceCode.toUpperCase()} — {workspaceName}
              </div>

              <div className="mb-8">
                <h2 className="mb-3 font-heading text-3xl font-semibold text-fg">
                  Bienvenue dans votre espace
                </h2>
                <p className="text-sm text-fg-subtle">
                  Connectez-vous avec vos identifiants.
                </p>
              </div>

              <div className="mb-4">
                {/* `htmlFor` + `id` : le libelle est visible et le champ unique —
                    on rattache donc pour de bon, ce qui donne aussi le focus au clic. */}
                <label htmlFor="login-email" className="mb-2 block text-sm font-medium text-fg">
                  Identifiant de connexion
                </label>
                <div className="relative flex h-12 w-full items-stretch overflow-hidden rounded-lg border-2 border-border bg-bg-overlay transition focus-within:border-accent">
                  <Mail className="pointer-events-none absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-fg-subtle" />
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
                    required
                    className="h-full min-w-0 flex-1 bg-transparent pl-12 pr-2 outline-none"
                  />
                  {!emailLibre && (
                    /* Meme signature que le prefixe : la racine a l'encre, le
                       « .ma » en or — la queue coloree fait echo au « KA » du
                       wordmark, et souligne le domaine marocain. */
                    <span
                      aria-hidden="true"
                      className="flex select-none items-center border-l border-accent/30 bg-accent/10 px-3 text-sm font-semibold"
                    >
                      <span className="text-fg-muted">@jurika</span>
                      <span className="text-accent">.ma</span>
                    </span>
                  )}
                </div>
                {/* BUG 7 (2026-06-08) — l'identifiant @jurika.ma est genere a
                    l'inscription/invitation. Pour les comptes legacy d'avant
                    V28, login_email = email perso (backfill 1:1). */}
                <p className="mt-1 text-[11px] text-fg-muted">
                  Identifiant <code>@jurika.ma</code> indique dans votre email de bienvenue.
                  Les notifications continuent d'arriver sur votre email perso.
                </p>
              </div>

              <div className="mb-4">
                <label htmlFor="login-password" className="mb-2 block text-sm font-medium text-fg">
                  Mot de passe
                </label>
                <div className="relative">
                  <Lock className="absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-fg-subtle" />
                  <input
                    id="login-password"
                    type={showPassword ? 'text' : 'password'}
                    name="password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="••••••••"
                    autoComplete="current-password"
                    required
                    className="h-12 w-full rounded-lg border-2 border-border bg-bg-overlay pl-12 pr-12 transition focus:border-accent focus:outline-none"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword((v) => !v)}
                    className="absolute right-4 top-1/2 -translate-y-1/2 text-fg-subtle hover:text-fg"
                    aria-label={showPassword ? 'Masquer' : 'Afficher'}
                  >
                    {showPassword ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
                  </button>
                </div>
              </div>

              {error && <ErrorBanner message={error} />}

              <Button type="submit" className="mt-2 h-12 w-full" loading={loading}>
                Se connecter →
              </Button>

              <div className="mt-6 flex items-center justify-between text-sm">
                <button
                  type="button"
                  onClick={() => setStep('workspace')}
                  className="flex items-center gap-1 text-fg-subtle hover:text-fg"
                >
                  <ArrowLeft className="h-3.5 w-3.5" /> Changer de cabinet
                </button>
                <Link to="/reset-password" className="font-medium text-accent hover:underline">
                  Mot de passe oublie ?
                </Link>
              </div>
            </form>
          )}

          {step === 'twofa' && (
            <form onSubmit={handleTwofa} noValidate>
              <div className="mb-6 flex justify-center">
                <div className="flex h-16 w-16 items-center justify-center rounded-full bg-accent/10">
                  <ShieldCheck className="h-8 w-8 text-accent" />
                </div>
              </div>

              <div className="mb-8 text-center">
                <h2 className="mb-3 font-heading text-3xl font-semibold text-fg">
                  Verification en 2 etapes
                </h2>
                <p className="text-sm text-fg-subtle">
                  Entrez le code a 6 chiffres de votre application authenticator.
                </p>
              </div>

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
                  // Meme cas que le code workspace : champ centre sans libelle visible.
                  aria-label="Code de verification a 6 chiffres"
                  autoComplete="one-time-code"
                  required
                  className="h-14 w-full rounded-lg border-2 border-border bg-bg-overlay text-center font-mono text-2xl font-bold tracking-[0.5em] outline-none transition focus:border-accent"
                />
              </div>

              <p className="mb-6 text-center text-xs text-fg-subtle">
                Utilisez Google Authenticator ou Microsoft Authenticator
              </p>

              {error && <ErrorBanner message={error} />}

              <Button
                type="submit"
                className="h-12 w-full"
                loading={loading}
                disabled={twofa.length !== 6}
              >
                Verifier & Acceder
              </Button>

              <Link
                to="/auth/recover-with-code"
                className="mt-4 block text-center text-sm font-medium text-accent hover:underline"
              >
                Utiliser un code de recuperation
              </Link>

              <button
                type="button"
                onClick={() => setStep('credentials')}
                className="mt-6 flex w-full items-center justify-center gap-1 text-sm text-fg-subtle hover:text-fg"
              >
                <ArrowLeft className="h-3.5 w-3.5" /> Retour
              </button>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}

function ErrorBanner({ message }: { message: string }) {
  return (
    <div className="mb-4 rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
      {message}
    </div>
  );
}
