import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowLeft, Mail } from 'lucide-react';
import { BrandLogo } from '../../components/ui/BrandLogo';
import { BrandingPanel } from '../../components/auth/BrandingPanel';
import { RecoveryCodeForm } from '../../components/auth/RecoveryCodeForm';
import { Button } from '../../components/ui/Button';
import { authService } from '../../services/auth.service';
import { useAuthStore } from '../../store/authStore';
import { extractError } from '../../lib/api';
import { firstError, requiredMsg, patternMsg, emailMsg } from '../../lib/formValidation';

type Step = 'identify' | 'code';

/**
 * Sprint 14 bis / B4 — page de recuperation via code 2FA single-use.
 *
 * <p>Parcours en 2 etapes pour matcher le pattern du LoginPage :
 * <ol>
 *   <li>{@code identify} : workspaceCode + email</li>
 *   <li>{@code code} : saisie du code de recuperation (RecoveryCodeForm)</li>
 * </ol>
 *
 * <p>Apres succes : tokens stockes via {@code authStore.setSession} et redirect
 * vers {@code /dashboard}. En cas de 429 (RG-AU42) le serveur retourne un
 * message explicite affiche dans le banner d'erreur.
 */
export function RecoverWithCodePage() {
  const navigate = useNavigate();
  const setSession = useAuthStore((s) => s.setSession);

  const [step, setStep] = useState<Step>('identify');
  const [workspaceCode, setWorkspaceCode] = useState('');
  const [email, setEmail] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function handleIdentify(e: React.FormEvent) {
    e.preventDefault();
    // Validation JS avant de passer a l'etape code (remplace la validation native).
    const wsErr = firstError(
      () => requiredMsg(workspaceCode),
      () =>
        patternMsg(
          workspaceCode.toUpperCase(),
          /^JUR-[A-Z0-9]{5}$/,
          'Format attendu : JUR-XXXXX',
        ),
    );
    if (wsErr) {
      setError(wsErr);
      document.querySelector<HTMLElement>('[name="workspaceCode"]')?.focus();
      return;
    }
    const emailErr = emailMsg(email);
    if (emailErr) {
      setError(emailErr);
      document.querySelector<HTMLElement>('[name="email"]')?.focus();
      return;
    }
    setError(null);
    setStep('code');
  }

  async function handleVerify(code: string) {
    setError(null);
    setLoading(true);
    try {
      const tokens = await authService.verifyRecoveryCode(
        workspaceCode.toUpperCase(),
        email,
        code,
      );
      setSession(tokens, 'EMPLOYE', email);
      navigate('/dashboard', { replace: true });
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="flex min-h-screen bg-bg-raised">
      <BrandingPanel />

      <div className="relative flex w-full flex-col items-center justify-center bg-bg-raised p-8 lg:w-[58%] lg:p-12">
        <div className="absolute top-6 left-6 flex items-center gap-2 lg:hidden">
          <BrandLogo size={36} />
          <span className="font-heading text-xl font-semibold text-fg">JURIKA</span>
        </div>

        <div className="w-full max-w-[480px]">
          {step === 'identify' && (
            <form onSubmit={handleIdentify} data-testid="recovery-identify-form" noValidate>
              <div className="mb-8">
                <h1 className="mb-2 font-heading text-3xl font-semibold text-fg">
                  Connexion avec code de recuperation
                </h1>
                <p className="text-sm text-fg-subtle">
                  Vous n'avez plus acces a votre authenticator ou a votre numero
                  enregistre&nbsp;? Utilisez l'un de vos 10 codes de recuperation
                  generes lors de l'activation 2FA.
                </p>
              </div>

              <div className="mb-4">
                <label htmlFor="ws-code" className="mb-2 block text-sm font-medium text-fg">
                  Code workspace
                </label>
                <input
                  id="ws-code"
                  name="workspaceCode"
                  type="text"
                  value={workspaceCode}
                  onChange={(e) => setWorkspaceCode(e.target.value.toUpperCase())}
                  placeholder="JUR-XXXXX"
                  pattern="^JUR-[A-Z0-9]{5}$"
                  maxLength={9}
                  autoComplete="organization"
                  required
                  className="h-12 w-full rounded-lg border-2 border-border bg-bg-overlay px-4 text-center font-mono text-lg font-bold tracking-wider outline-none focus:border-accent"
                />
              </div>

              <div className="mb-4">
                <label htmlFor="email" className="mb-2 block text-sm font-medium text-fg">
                  Email du compte
                </label>
                <div className="relative">
                  <Mail className="absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-fg-subtle" />
                  <input
                    id="email"
                    name="email"
                    type="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="nom@cabinet.ma"
                    autoComplete="email"
                    required
                    className="h-12 w-full rounded-lg border-2 border-border bg-bg-overlay pl-12 pr-4 outline-none focus:border-accent"
                  />
                </div>
              </div>

              {error && (
                <div
                  role="alert"
                  className="mb-4 rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger"
                >
                  {error}
                </div>
              )}

              <Button type="submit" className="h-12 w-full">
                Continuer →
              </Button>

              <Link
                to="/login"
                className="mt-6 flex w-full items-center justify-center gap-1 text-sm text-fg-subtle hover:text-fg"
              >
                <ArrowLeft className="h-3.5 w-3.5" /> Retour a la connexion classique
              </Link>
            </form>
          )}

          {step === 'code' && (
            <>
              <RecoveryCodeForm
                workspaceCode={workspaceCode}
                email={email}
                loading={loading}
                error={error}
                onSubmit={handleVerify}
              />
              <button
                type="button"
                onClick={() => {
                  setStep('identify');
                  setError(null);
                }}
                className="mt-6 flex w-full items-center justify-center gap-1 text-sm text-fg-subtle hover:text-fg"
              >
                <ArrowLeft className="h-3.5 w-3.5" /> Changer d'identifiants
              </button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
