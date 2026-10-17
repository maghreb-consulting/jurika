import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, ArrowRight } from 'lucide-react';
import { PageAccueil, TitreAccueil } from '../../components/auth/PageAccueil';
import { RecoveryCodeForm } from '../../components/auth/RecoveryCodeForm';
import { Button } from '../../components/ui/Button';
import { Lien } from '../../components/ui/Lien';
import { Message } from '../../components/ui/Message';
import { TextField } from '../../components/ui/TextField';
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
          'Code du cabinet non valide : format attendu JUR- suivi de 5 lettres ou chiffres.',
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
      setSession(tokens, 'EMPLOYE', email, workspaceCode.toUpperCase());
      navigate('/dashboard', { replace: true });
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <PageAccueil titre="Code de récupération" enTete={<Lien to="/login">Se connecter</Lien>}>
      {step === 'identify' && (
        <form onSubmit={handleIdentify} data-testid="recovery-identify-form" noValidate className="space-y-4">
          <TitreAccueil
            titre="Connexion avec un code de récupération"
            libelleInfoBulle="Aide sur les codes de récupération"
            infoBulle="À l’activation de la double authentification, JURIKA vous a remis 10 codes de récupération (fichier téléchargé ou copié). Chaque code ne sert qu’une fois. Après la connexion, pensez à régénérer vos codes depuis la page Sécurité de votre compte."
          >
            Vous n’avez plus accès à votre application d’authentification ni au numéro enregistré&nbsp;? Indiquez
            d’abord le code de votre cabinet et votre adresse, puis l’un de vos 10 codes de récupération.
          </TitreAccueil>

          <TextField
            id="ws-code"
            label="Code du cabinet"
            name="workspaceCode"
            type="text"
            value={workspaceCode}
            onChange={(e) => setWorkspaceCode(e.target.value.toUpperCase())}
            placeholder="JUR-XXXXX"
            pattern="^JUR-[A-Z0-9]{5}$"
            maxLength={9}
            autoComplete="organization"
            mono
            required
          />

          <TextField
            id="email"
            label="Adresse électronique du compte"
            name="email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="nom@cabinet.ma"
            autoComplete="email"
            required
          />

          {error && <Message ton="erreur">{error}</Message>}

          <Button type="submit" size="lg" fullWidth iconEnd={<ArrowRight />}>
            Continuer
          </Button>

          <p className="pt-2 text-center text-sm">
            <Lien to="/login" variante="discret">
              Retour à la connexion
            </Lien>
          </p>
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
          <div className="mt-6 flex justify-center">
            <Button
              type="button"
              variant="ghost"
              size="sm"
              icon={<ArrowLeft />}
              onClick={() => {
                setStep('identify');
                setError(null);
              }}
            >
              Modifier le cabinet ou l’adresse
            </Button>
          </div>
        </>
      )}
    </PageAccueil>
  );
}
