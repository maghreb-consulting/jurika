import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  CheckCircle2,
  ChevronRight,
  Copy,
  Loader2,
  Lock,
  MessageSquare,
  ShieldCheck,
  Smartphone,
} from 'lucide-react';
import { BrandLogo } from '../../components/ui/BrandLogo';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { TextField } from '../../components/ui/TextField';
import { authService } from '../../services/auth.service';
import { extractError, tokenStorage } from '../../lib/api';
import { firstError, requiredMsg, patternMsg } from '../../lib/formValidation';
import { SMS_2FA_ENABLED } from '../../lib/featureFlags';

type Step = 'choose' | 'totp-setup' | 'sms-setup' | 'recovery-codes';

export function Choose2faMethodPage() {
  const navigate = useNavigate();
  const [step, setStep] = useState<Step>('choose');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  // TOTP state
  const [, setOtpAuthUri] = useState<string>('');
  const [qrCode, setQrCode] = useState<string>('');
  const [secret, setSecret] = useState<string>('');
  const [totpCode, setTotpCode] = useState('');

  // SMS state
  const [maskedPhone, setMaskedPhone] = useState<string>('');
  const [smsCode, setSmsCode] = useState('');

  const [recoveryCodes, setRecoveryCodes] = useState<string[]>([]);
  const [acknowledged, setAcknowledged] = useState(false);

  // Methodes 2FA disponibles (TOTP toujours, SMS conditionnel selon presence
  // d'un telephone enregistre a l'inscription — RG-AU30).
  const [smsAvailable, setSmsAvailable] = useState<boolean>(false);
  const [phoneHint, setPhoneHint] = useState<string | null>(null);
  const [optionsLoaded, setOptionsLoaded] = useState<boolean>(false);

  useEffect(() => {
    let cancelled = false;
    authService
      .getSetup2faOptions()
      .then((opts) => {
        if (cancelled) return;
        setSmsAvailable(opts.smsAvailable);
        setPhoneHint(opts.maskedPhone);
        setOptionsLoaded(true);
      })
      .catch(() => {
        // En cas d'erreur (404 endpoint absent / network), fallback ouvert :
        // on laisse les deux options pour ne pas bloquer le user. Le backend
        // refusera l'envoi SMS proprement avec PHONE_NOT_SET si pas de phone.
        if (cancelled) return;
        setSmsAvailable(true);
        setPhoneHint(null);
        setOptionsLoaded(true);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  async function pickTotp() {
    setError(null);
    setLoading(true);
    try {
      await authService.choose2faMethod('TOTP');
      const setup = await authService.setup2fa();
      setOtpAuthUri(setup.otpAuthUri);
      setQrCode(setup.qrCodePngBase64);
      setSecret(setup.secret);
      setStep('totp-setup');
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }

  async function pickSms() {
    setError(null);
    setLoading(true);
    try {
      await authService.choose2faMethod('SMS');
      const sent = await authService.sendSmsOtp('2FA_SETUP');
      setMaskedPhone(sent.maskedPhone);
      setStep('sms-setup');
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }

  async function confirmTotp(e: React.FormEvent) {
    e.preventDefault();
    // Validation JS avant l'appel API (remplace la validation native).
    const codeErr = firstError(
      () => requiredMsg(totpCode),
      () => patternMsg(totpCode, /^\d{6}$/, 'Le code doit comporter 6 chiffres.'),
    );
    if (codeErr) {
      setError(codeErr);
      document.querySelector<HTMLElement>('[name="totpCode"]')?.focus();
      return;
    }
    setError(null);
    setLoading(true);
    try {
      // Hotfix 2026-06-04 : persister le nouveau token (r2s=false) AVANT
      // d'appeler /2fa/recovery-codes — sinon 403 SETUP_2FA_REQUIRED.
      const tokens = await authService.confirm2fa(parseInt(totpCode, 10));
      tokenStorage.setTokens(tokens);
      const r = await authService.generateRecoveryCodes();
      setRecoveryCodes(r.codes);
      setStep('recovery-codes');
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }

  async function confirmSms(e: React.FormEvent) {
    e.preventDefault();
    // Validation JS avant l'appel API (remplace la validation native).
    const codeErr = firstError(
      () => requiredMsg(smsCode),
      () => patternMsg(smsCode, /^\d{6}$/, 'Le code doit comporter 6 chiffres.'),
    );
    if (codeErr) {
      setError(codeErr);
      document.querySelector<HTMLElement>('[name="smsCode"]')?.focus();
      return;
    }
    setError(null);
    setLoading(true);
    try {
      // Hotfix 2026-06-04 : meme bug que TOTP/change-password — persister le
      // nouveau token AVANT /2fa/recovery-codes, sinon 403 SETUP_2FA_REQUIRED.
      const tokens = await authService.verifySmsOtp('2FA_SETUP', smsCode);
      if (tokens) tokenStorage.setTokens(tokens);
      const r = await authService.generateRecoveryCodes();
      setRecoveryCodes(r.codes);
      setStep('recovery-codes');
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }

  function copyRecoveryCodes() {
    navigator.clipboard.writeText(recoveryCodes.join('\n')).catch(() => {});
  }

  function finalize() {
    navigate('/dashboard', { replace: true });
  }

  function downloadRecoveryCodes() {
    const blob = new Blob([recoveryCodes.join('\n')], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'jurika-recovery-codes.txt';
    a.click();
    URL.revokeObjectURL(url);
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-bg via-bg-raised to-accent/10 px-4 py-12">
      <div className="w-full max-w-lg">
        <div className="mb-8 flex items-center justify-center gap-2">
          <BrandLogo size={40} />
          <span className="font-heading font-heading text-2xl font-semibold text-fg">JURIKA</span>
        </div>

        <Card className="p-8">
          {step === 'choose' && (
            <div className="space-y-5">
              <div className="text-center">
                <ShieldCheck className="mx-auto h-12 w-12 text-accent" />
                <h2 className="mt-3 font-heading text-xl font-semibold text-fg">
                  Activation 2FA obligatoire
                </h2>
                <p className="mt-1 text-sm text-fg-muted">
                  Pour proteger votre cabinet, vous devez activer la double authentification
                  avant d'acceder a la plateforme.
                </p>
              </div>

              <button
                type="button"
                onClick={pickTotp}
                disabled={loading}
                className="flex w-full items-center gap-4 rounded-xl border-2 border-accent/40 bg-accent/10/30 p-4 text-left transition-colors hover:border-indigo-400 hover:bg-accent/10"
              >
                <div className="rounded-lg bg-accent/20 p-2">
                  <Smartphone className="h-6 w-6 text-accent" />
                </div>
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <p className="font-semibold text-fg">Google Authenticator (QR Code)</p>
                    <span className="rounded-full bg-accent px-2 py-0.5 text-[10px] font-semibold text-bg-raised">
                      Recommande
                    </span>
                  </div>
                  <p className="mt-0.5 text-xs text-fg-subtle">
                    Aucun numero de telephone requis. Scannez un QR Code avec
                    n'importe quel telephone, tablette ou desktop avec une app
                    d'authentification (Google Authenticator, Authy, 1Password, etc.)
                  </p>
                </div>
                <ChevronRight className="h-4 w-4 text-fg-subtle" />
              </button>

              {!SMS_2FA_ENABLED ? (
                // SMS « Hors service » : aucun fournisseur SMS reel n'est branche
                // (flag SMS_2FA_ENABLED=false). Carte grisee, non selectionnable.
                <div
                  aria-disabled="true"
                  data-testid="2fa-sms-out-of-service"
                  className="flex w-full items-start gap-4 rounded-xl border border-dashed border-border bg-bg-overlay p-4 text-left opacity-70"
                >
                  <div className="rounded-lg bg-bg-raised p-2">
                    <MessageSquare className="h-6 w-6 text-fg-subtle" />
                  </div>
                  <div className="flex-1">
                    <p className="flex items-center gap-2 font-semibold text-fg-muted">
                      SMS
                      <span className="rounded-full bg-fg-subtle/20 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-fg-muted">
                        Hors service
                      </span>
                    </p>
                    <p className="text-xs text-fg-subtle">
                      La verification par SMS n'est pas encore disponible. Utilisez
                      Google Authenticator ci-dessus.
                    </p>
                  </div>
                </div>
              ) : smsAvailable ? (
                <button
                  type="button"
                  onClick={pickSms}
                  disabled={loading}
                  className="flex w-full items-center gap-4 rounded-xl border border-border bg-bg-raised p-4 text-left transition-colors hover:border-emerald-300 hover:bg-emerald-50/40"
                >
                  <div className="rounded-lg bg-emerald-100 p-2">
                    <MessageSquare className="h-6 w-6 text-success" />
                  </div>
                  <div className="flex-1">
                    <p className="font-semibold text-fg">SMS</p>
                    <p className="text-xs text-fg-subtle">
                      Code 6 chiffres envoye au{' '}
                      {phoneHint ? (
                        <span className="font-mono">{phoneHint}</span>
                      ) : (
                        'numero declare a l\'inscription'
                      )}
                      . Necessite un telephone avec une carte SIM active.
                    </p>
                  </div>
                  <ChevronRight className="h-4 w-4 text-fg-subtle" />
                </button>
              ) : optionsLoaded ? (
                <div className="flex w-full items-start gap-3 rounded-xl border border-dashed border-border bg-bg-overlay p-4 text-left">
                  <div className="rounded-lg bg-bg-raised p-2">
                    <MessageSquare className="h-6 w-6 text-fg-subtle" />
                  </div>
                  <div className="flex-1">
                    <p className="font-semibold text-fg-muted">SMS indisponible</p>
                    <p className="text-xs text-fg-subtle">
                      Aucun numero de telephone n'a ete renseigne lors de l'inscription.
                      Utilisez Google Authenticator ci-dessus, ou ajoutez un telephone
                      depuis votre profil pour activer cette methode.
                    </p>
                  </div>
                </div>
              ) : null}

              <p className="text-center text-xs text-fg-subtle">
                Pas de telephone ? Choisissez <strong className="text-fg-muted">Google Authenticator</strong>.
                Vous pouvez utiliser une app desktop sans SIM.
              </p>

              {loading && (
                <p className="flex items-center justify-center gap-2 text-sm text-fg-subtle">
                  <Loader2 className="h-3 w-3 animate-spin" /> Patientez...
                </p>
              )}
              {error && (
                <div className="rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
                  {error}
                </div>
              )}
            </div>
          )}

          {step === 'totp-setup' && (
            <form onSubmit={confirmTotp} className="space-y-5" noValidate>
              <div className="text-center">
                <Smartphone className="mx-auto h-10 w-10 text-accent" />
                <h2 className="mt-3 font-heading text-xl font-semibold text-fg">Scanner le QR Code</h2>
                <p className="mt-1 text-sm text-fg-muted">
                  Avec Google Authenticator, Authy ou Microsoft Authenticator.
                </p>
              </div>

              {qrCode && (
                <div className="flex justify-center">
                  <img
                    src={`data:image/png;base64,${qrCode}`}
                    alt="QR Code TOTP"
                    className="h-48 w-48 rounded-lg border-2 border-border bg-bg-raised p-2"
                  />
                </div>
              )}

              <div className="space-y-1 rounded-lg border border-border bg-bg-overlay p-3">
                <p className="text-xs font-medium text-fg-muted">
                  Cle secrete (si vous ne pouvez pas scanner) :
                </p>
                <code className="break-all text-xs font-mono text-fg">{secret}</code>
              </div>

              <TextField
                label="Code a 6 chiffres genere par l'app"
                name="totpCode"
                inputMode="numeric"
                pattern="[0-9]{6}"
                maxLength={6}
                value={totpCode}
                onChange={(e) => setTotpCode(e.target.value.replace(/\D/g, ''))}
                placeholder="123456"
                className="text-center text-lg tracking-[0.5em]"
                required
              />

              {error && (
                <div className="rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
                  {error}
                </div>
              )}

              <Button type="submit" className="w-full" loading={loading} disabled={totpCode.length !== 6}>
                Activer le 2FA
              </Button>
            </form>
          )}

          {step === 'sms-setup' && (
            <form onSubmit={confirmSms} className="space-y-5" noValidate>
              <div className="text-center">
                <MessageSquare className="mx-auto h-10 w-10 text-success" />
                <h2 className="mt-3 font-heading text-xl font-semibold text-fg">Verification SMS</h2>
                <p className="mt-1 text-sm text-fg-muted">
                  Un code a 6 chiffres a ete envoye au <span className="font-mono">{maskedPhone}</span>.
                  Il expire dans 5 minutes.
                </p>
              </div>

              <TextField
                label="Code SMS"
                name="smsCode"
                inputMode="numeric"
                pattern="[0-9]{6}"
                maxLength={6}
                value={smsCode}
                onChange={(e) => setSmsCode(e.target.value.replace(/\D/g, ''))}
                placeholder="123456"
                className="text-center text-lg tracking-[0.5em]"
                required
              />

              {error && (
                <div className="rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
                  {error}
                </div>
              )}

              <Button type="submit" className="w-full" loading={loading} disabled={smsCode.length !== 6}>
                Activer le 2FA
              </Button>
              <button
                type="button"
                className="block w-full text-center text-xs font-medium text-accent hover:text-accent"
                onClick={() => pickSms()}
              >
                Renvoyer un code
              </button>
            </form>
          )}

          {step === 'recovery-codes' && (
            <div className="space-y-5">
              <div className="text-center">
                <Lock className="mx-auto h-10 w-10 text-warning" />
                <h2 className="mt-3 font-heading text-xl font-semibold text-fg">Codes de recuperation</h2>
                <p className="mt-1 text-sm text-fg-muted">
                  Conservez ces 10 codes en lieu sur. Ils ne seront plus jamais affiches.
                  Chaque code n'est utilisable qu'une seule fois.
                </p>
              </div>

              <div className="grid grid-cols-2 gap-2 rounded-lg border border-amber-200 bg-warning/10 p-4">
                {recoveryCodes.map((c) => (
                  <code
                    key={c}
                    className="rounded bg-bg-raised px-2 py-1 text-center text-sm font-mono text-fg"
                  >
                    {c}
                  </code>
                ))}
              </div>

              <div className="grid grid-cols-2 gap-2">
                <Button variant="secondary" onClick={copyRecoveryCodes}>
                  <Copy className="mr-2 h-4 w-4" /> Copier
                </Button>
                <Button variant="secondary" onClick={downloadRecoveryCodes}>
                  Telecharger .txt
                </Button>
              </div>

              <label className="flex items-start gap-2 text-sm text-fg-muted">
                <input
                  type="checkbox"
                  checked={acknowledged}
                  onChange={(e) => setAcknowledged(e.target.checked)}
                  className="mt-0.5 h-4 w-4 rounded border-border-hi text-accent"
                />
                J'ai sauvegarde mes codes de recuperation en lieu sur.
              </label>

              <Button onClick={finalize} disabled={!acknowledged} className="w-full">
                <CheckCircle2 className="mr-2 h-4 w-4" /> Terminer
              </Button>
            </div>
          )}
        </Card>
      </div>
    </div>
  );
}
