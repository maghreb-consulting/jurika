import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ShieldCheck } from 'lucide-react';
import { BrandLogo } from '../../components/ui/BrandLogo';
import { Button } from '../../components/ui/Button';
import { TextField } from '../../components/ui/TextField';
import { Card } from '../../components/ui/Card';
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
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setLoading(true);
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
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-bg to-accent/10 px-4 py-12">
      <div className="w-full max-w-md">
        <div className="mb-8 flex items-center justify-center gap-2">
          <BrandLogo size={40} />
          <span className="font-heading font-heading text-2xl font-semibold text-fg">JURIKA</span>
        </div>

        {step !== 'SHOW_RECOVERY' && (
          <Card className="space-y-5 p-8">
            <div className="text-center">
              <ShieldCheck className="mx-auto h-10 w-10 text-success" />
              <h1 className="mt-3 font-heading text-xl font-semibold text-fg">Activez la 2FA</h1>
              <p className="mt-1 text-sm text-fg-muted">
                Scannez le QR code avec Google Authenticator
              </p>
            </div>

            {qrPng && (
              <div className="flex justify-center">
                <img
                  src={`data:image/png;base64,${qrPng}`}
                  alt="QR code 2FA"
                  className="h-56 w-56 rounded-lg border border-border bg-bg-raised p-2"
                />
              </div>
            )}

            {secret && (
              <div className="rounded-lg bg-bg-overlay p-3 text-center text-xs">
                <p className="text-fg-subtle">Ou saisissez ce code manuellement :</p>
                <code className="mt-1 inline-block break-all font-mono text-sm text-fg">{secret}</code>
              </div>
            )}

            <form onSubmit={handleConfirm} className="space-y-3" noValidate>
              <TextField
                label="Code a 6 chiffres"
                name="code"
                inputMode="numeric"
                pattern="[0-9]{6}"
                maxLength={6}
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
                autoComplete="one-time-code"
                className="text-center text-lg tracking-[0.5em]"
                required
              />
              {error && (
                <div className="rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
                  {error}
                </div>
              )}
              <Button type="submit" className="w-full" loading={loading} disabled={code.length !== 6}>
                Confirmer et activer
              </Button>
            </form>
          </Card>
        )}

        {step === 'SHOW_RECOVERY' && recoveryCodes && (
          <Card className="space-y-5 p-8">
            <div className="text-center">
              <ShieldCheck className="mx-auto h-10 w-10 text-success" />
              <h1 className="mt-3 font-heading text-xl font-semibold text-fg">
                2FA activee — vos codes de recuperation
              </h1>
              <p className="mt-1 text-sm text-fg-muted">
                Ces 10 codes vous permettent de vous reconnecter si vous perdez votre telephone.
              </p>
            </div>

            <RecoveryCodesDisplay
              codes={recoveryCodes}
              context="INITIAL_SETUP"
              continueLabel="J'ai sauvegarde mes codes, continuer"
              onContinue={() => navigate('/dashboard', { replace: true })}
            />
          </Card>
        )}
      </div>
    </div>
  );
}
