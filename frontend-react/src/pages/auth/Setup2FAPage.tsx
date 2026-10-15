import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ShieldCheck } from 'lucide-react';
import { PageAccueil, TitreAccueil } from '../../components/auth/PageAccueil';
import { Button } from '../../components/ui/Button';
import { Skeleton, SqueletteZone } from '../../components/ui/LoadingSkeleton';
import { Message } from '../../components/ui/Message';
import { TextField } from '../../components/ui/TextField';
import { RecoveryCodesDisplay } from '../../components/auth/RecoveryCodesDisplay';
import { authService } from '../../services/auth.service';
import { extractError, tokenStorage } from '../../lib/api';
import { firstError, requiredMsg, patternMsg } from '../../lib/formValidation';

type Step = 'SCAN_QR' | 'CONFIRM_CODE' | 'SHOW_RECOVERY';

export function Setup2FAPage() {
  const navigate = useNavigate();
  const [step, setStep] = useState<Step>('SCAN_QR');
  const [qrPng, setQrPng] = useState<string | null>(null);
  const [secret, setSecret] = useState<string>('');
  const [code, setCode] = useState('');
  const [recoveryCodes, setRecoveryCodes] = useState<string[] | null>(null);
  // Vrai des le premier rendu : la preparation (QR code) part au montage.
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    authService
      .setup2fa()
      .then((r) => {
        if (!active) return;
        setQrPng(r.qrCodePngBase64);
        setSecret(r.secret);
        setStep('CONFIRM_CODE');
      })
      .catch((err) => active && setError(extractError(err).message))
      .finally(() => active && setLoading(false));
    return () => {
      active = false;
    };
  }, []);

  async function handleConfirm(e: React.FormEvent) {
    e.preventDefault();
    // Validation JS avant l'appel API (remplace la validation native).
    const codeErr = firstError(
      () => requiredMsg(code),
      () => patternMsg(code, /^\d{6}$/, 'Le code doit comporter 6 chiffres.'),
    );
    if (codeErr) {
      setError(codeErr);
      document.querySelector<HTMLElement>('[name="code"]')?.focus();
      return;
    }
    setError(null);
    setLoading(true);
    try {
      // Hotfix 2026-06-04 : le backend renvoie un NOUVEAU couple de tokens
      // (r2s=false). On DOIT le persister AVANT d'appeler /2fa/recovery-codes
      // ou de naviguer, sinon les requetes repartent avec l'ancien token
      // (r2s=true) et Setup2faRequiredEnforcer renvoie 403 — l'utilisateur
      // se retrouve bloque alors qu'il vient juste de finir la configuration.
      const tokens = await authService.confirm2fa(parseInt(code, 10));
      tokenStorage.setTokens(tokens);
      // Sprint 3 / TASK 3 — generation immediate des 10 codes de recuperation
      // (RG-AU33). Affichage ONE-SHOT avant de naviguer.
      const { codes } = await authService.generateRecoveryCodes();
      setRecoveryCodes(codes);
      setStep('SHOW_RECOVERY');
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <PageAccueil titre="Double authentification" retourAccueil={false} largeur={step === 'SHOW_RECOVERY' ? 'normale' : 'etroite'}>
      {step !== 'SHOW_RECOVERY' && (
        <div className="space-y-5">
          <TitreAccueil
            icone={<ShieldCheck />}
            ton="succes"
            titre="Activez la double authentification"
            libelleInfoBulle="Aide sur la double authentification"
            infoBulle="1. Ouvrez une application d’authentification (Google Authenticator, Microsoft Authenticator…) sur votre téléphone. 2. Scannez le QR code, ou saisissez le code affiché dessous. 3. Saisissez le code à 6 chiffres que l’application affiche. Vous recevrez ensuite 10 codes de récupération à conserver."
          >
            Scannez le QR code avec votre application d’authentification, puis saisissez le code à 6 chiffres
            qu’elle affiche.
          </TitreAccueil>

          {/* Emplacement du QR code a ses dimensions : aucun decalage quand il arrive. */}
          <div className="flex justify-center">
            {qrPng ? (
              <img
                src={`data:image/png;base64,${qrPng}`}
                alt="QR code à scanner avec votre application d’authentification"
                width={224}
                height={224}
                className="h-56 w-56 rounded-lg border border-border bg-bg-raised p-2"
              />
            ) : loading ? (
              <SqueletteZone libelle="Préparation du QR code">
                <Skeleton className="h-56 w-56 rounded-lg" />
              </SqueletteZone>
            ) : null}
          </div>

          {secret && (
            <div className="rounded-lg border border-border bg-bg-overlay p-3 text-center text-xs">
              <p className="text-fg-muted">Impossible de scanner ? Saisissez ce code dans l’application :</p>
              <code className="mt-1 inline-block break-all font-mono text-sm text-fg">{secret}</code>
            </div>
          )}

          <form onSubmit={handleConfirm} className="space-y-4" noValidate>
            <TextField
              label="Code à 6 chiffres"
              name="code"
              inputMode="numeric"
              pattern="[0-9]{6}"
              maxLength={6}
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
              autoComplete="one-time-code"
              className="h-11 text-center text-lg tracking-[0.5em]"
              mono
              required
            />
            {error && <Message ton="erreur">{error}</Message>}
            <Button type="submit" size="lg" fullWidth loading={loading} disabled={code.length !== 6}>
              Confirmer et activer
            </Button>
          </form>
        </div>
      )}

      {step === 'SHOW_RECOVERY' && recoveryCodes && (
        <div className="space-y-5">
          <TitreAccueil
            icone={<ShieldCheck />}
            ton="succes"
            titre="Double authentification activée : vos codes de récupération"
          >
            Ces 10 codes vous permettent de vous reconnecter si vous perdez votre téléphone. Conservez-les en lieu
            sûr : ils ne seront plus affichés.
          </TitreAccueil>

          <RecoveryCodesDisplay
            codes={recoveryCodes}
            context="INITIAL_SETUP"
            continueLabel="J’ai mis mes codes en lieu sûr, continuer"
            onContinue={() => navigate('/dashboard', { replace: true })}
          />
        </div>
      )}
    </PageAccueil>
  );
}
