import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  CheckCircle2,
  ChevronRight,
  Copy,
  Download,
  Lock,
  MessageSquare,
  ShieldCheck,
  Smartphone,
} from 'lucide-react';
import { PageAccueil, TitreAccueil } from '../../components/auth/PageAccueil';
import { Badge } from '../../components/ui/Badge';
import { copierTexte } from '../../lib/pressePapiers';
import { Button, Spinner } from '../../components/ui/Button';
import { Case } from '../../components/ui/Choix';
import { Skeleton } from '../../components/ui/LoadingSkeleton';
import { Message } from '../../components/ui/Message';
import { TextField } from '../../components/ui/TextField';
import { authService, type Setup2faOptions } from '../../services/auth.service';
import { extractError, tokenStorage } from '../../lib/api';
import { firstError, requiredMsg, patternMsg } from '../../lib/formValidation';
import { TexteAide } from '../../components/ui/Aide';

type Step = 'choose' | 'totp-setup' | 'sms-phone' | 'sms-setup' | 'recovery-codes' | 'sms-done';

/** Meme forme que le serveur (E.164) : + puis 7 a 15 chiffres. */
const TELEPHONE_E164 = /^\+[1-9]\d{6,14}$/;

/**
 * Recette R2 (2026-10-17) : aide affichee quand le SMS est SIMULE (environnement de test).
 */
function AideSmsSimule() {
  return (
    <TexteAide cle="2fa-sms-simule" titre="SMS simulé (environnement de test)">
      <p>
        Environnement de test : aucun SMS n’est réellement envoyé. Le code à 6 chiffres est écrit dans le
        journal du service d’authentification (marque SMS-DEV-STUB) ; demandez-le à l’équipe technique. En
        production, le SMS n’est proposé que si un fournisseur de SMS est configuré.
      </p>
    </TexteAide>
  );
}

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
  // Recette R2 : numero saisi, code de la methode actuelle (changement de methode).
  const [telephone, setTelephone] = useState('');
  const [codeActuel, setCodeActuel] = useState('');
  const [codeActuelEnvoye, setCodeActuelEnvoye] = useState(false);

  const [recoveryCodes, setRecoveryCodes] = useState<string[]>([]);
  const [acknowledged, setAcknowledged] = useState(false);

  // Methodes 2FA disponibles. Recette R2 (2026-10-17) : le SMS suit le SERVEUR (fournisseur
  // configure, ou simulation en environnement de test) ; plus d'interrupteur de construction.
  // Si les options ne se chargent pas, le SMS n'est PAS propose (avant : repli ouvert).
  const [options, setOptions] = useState<Setup2faOptions | null>(null);
  const [optionsEchec, setOptionsEchec] = useState<boolean>(false);
  const optionsLoaded = options !== null || optionsEchec;
  const smsAvailable = options?.smsAvailable === true;
  const smsSimule = options?.smsSimule === true;
  const phoneHint = options?.maskedPhone ?? null;
  const methodeActive = options?.methodeActive ?? null;

  useEffect(() => {
    let cancelled = false;
    authService
      .getSetup2faOptions()
      .then((opts) => {
        if (!cancelled) setOptions(opts);
      })
      .catch(() => {
        if (!cancelled) setOptionsEchec(true);
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
      setStep('sms-phone');
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }

  /** Changement depuis le SMS : code envoye au numero ACTUEL (preuve de la methode actuelle). */
  async function recevoirCodeActuel() {
    setError(null);
    setLoading(true);
    try {
      await authService.sendSmsOtp('2FA_LOGIN');
      setCodeActuelEnvoye(true);
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }

  async function envoyerCodeSms(e?: React.FormEvent) {
    e?.preventDefault();
    const numero = telephone.replace(/[\s.-]/g, '');
    if (!TELEPHONE_E164.test(numero)) {
      setError('Saisissez le numéro au format international, par exemple +212612345678.');
      document.querySelector<HTMLElement>('[name="telephone"]')?.focus();
      return;
    }
    if (methodeActive && !/^\d{6}$/.test(codeActuel)) {
      setError(
        methodeActive === 'SMS'
          ? 'Saisissez d’abord le code à 6 chiffres reçu sur votre numéro actuel.'
          : 'Saisissez d’abord le code à 6 chiffres affiché par votre application d’authentification.',
      );
      document.querySelector<HTMLElement>('[name="codeActuel"]')?.focus();
      return;
    }
    setError(null);
    setLoading(true);
    try {
      const sent = await authService.sendSmsOtp('2FA_SETUP', {
        telephone: numero,
        codeActuel: methodeActive ? codeActuel : undefined,
      });
      setMaskedPhone(sent.maskedPhone);
      setSmsCode('');
      setStep('sms-setup');
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setLoading(false);
    }
  }

  /** Nouveau code : directement a la premiere configuration ; sinon le code actuel est redemande. */
  function renvoyerCodeSms() {
    if (methodeActive) {
      setCodeActuel('');
      setCodeActuelEnvoye(false);
      setError(null);
      setStep('sms-phone');
      return;
    }
    void envoyerCodeSms();
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
      if (methodeActive) {
        // Changement de methode : les codes de recuperation existants sont conserves.
        setStep('sms-done');
        return;
      }
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
    // Lot L6b (E13) : copie aussi en HTTP (navigator.clipboard absent hors contexte securise).
    void copierTexte(recoveryCodes.join('\n'));
  }

  function finalize() {
    navigate('/dashboard', { replace: true });
  }

  function downloadRecoveryCodes() {
    const blob = new Blob([recoveryCodes.join('\n')], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'jurika-codes-de-recuperation.txt';
    a.click();
    URL.revokeObjectURL(url);
  }

  return (
    <PageAccueil titre="Double authentification" retourAccueil={false}>
      {step === 'choose' && (
        <div className="space-y-4">
          <TitreAccueil
            icone={<ShieldCheck />}
            titre="Activez la double authentification"
            libelleInfoBulle="Aide sur la double authentification"
            infoBulle="À chaque connexion, en plus du mot de passe, JURIKA vous demandera un code à 6 chiffres : celui de votre application d’authentification, ou celui reçu par SMS. Après l’activation, vous recevrez 10 codes de récupération à conserver, utiles si vous perdez votre téléphone."
          >
            Pour protéger les dossiers de votre cabinet, la double authentification est obligatoire avant
            d’accéder à la plateforme. Choisissez une méthode.
          </TitreAccueil>

          {/* Choix de methode : de vrais boutons (bordure, survol, focus), une carte par methode. */}
          <button
            type="button"
            onClick={pickTotp}
            disabled={loading}
            className="flex w-full items-center gap-4 rounded-xl border border-border-input bg-bg-raised p-4 text-left shadow-card transition-[border-color,box-shadow] duration-150 hover:border-accent hover:shadow-card-lifted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus disabled:cursor-not-allowed disabled:opacity-60"
          >
            <span aria-hidden="true" className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-accent-soft text-accent">
              <Smartphone className="h-5 w-5" />
            </span>
            <span className="min-w-0 flex-1">
              <span className="flex flex-wrap items-center gap-2">
                <span className="font-semibold text-fg">Application d’authentification (QR code)</span>
                <Badge variant="default">Recommandée</Badge>
              </span>
              <span className="mt-0.5 block text-xs text-fg-muted">
                Aucun numéro de téléphone requis. Scannez un QR code avec une application d’authentification
                (Google Authenticator, Microsoft Authenticator, Authy, 1Password…), sur téléphone, tablette ou
                ordinateur.
              </span>
            </span>
            <ChevronRight className="h-4 w-4 shrink-0 text-fg-subtle" aria-hidden="true" />
          </button>

          {!optionsLoaded ? (
            <Skeleton className="h-[5.5rem] w-full rounded-xl" />
          ) : smsAvailable ? (
            <>
              <button
                type="button"
                onClick={pickSms}
                disabled={loading}
                className="flex w-full items-center gap-4 rounded-xl border border-border-input bg-bg-raised p-4 text-left shadow-card transition-[border-color,box-shadow] duration-150 hover:border-accent hover:shadow-card-lifted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus disabled:cursor-not-allowed disabled:opacity-60"
              >
                <span aria-hidden="true" className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-success-soft text-success">
                  <MessageSquare className="h-5 w-5" />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="flex flex-wrap items-center gap-2">
                    <span className="font-semibold text-fg">SMS</span>
                    {smsSimule && <Badge variant="neutral">Simulé (test)</Badge>}
                  </span>
                  <span className="block text-xs text-fg-muted">
                    Code à 6 chiffres envoyé sur votre téléphone mobile : vous saisirez votre numéro à l’étape
                    suivante{phoneHint ? <> (numéro actuel : <span className="font-mono">{phoneHint}</span>)</> : null}.
                    Nécessite un téléphone avec une carte SIM active.
                  </span>
                </span>
                <ChevronRight className="h-4 w-4 shrink-0 text-fg-subtle" aria-hidden="true" />
              </button>
              {smsSimule && <AideSmsSimule />}
            </>
          ) : (
            // Recette R2 : aucun fournisseur de SMS (production) ou options illisibles :
            // l'option n'est pas proposee (le serveur la refuserait de toute facon).
            <div
              aria-disabled="true"
              data-testid="2fa-sms-indisponible"
              className="flex w-full items-start gap-4 rounded-xl border border-dashed border-border bg-bg-overlay p-4 text-left"
            >
              <span aria-hidden="true" className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-bg-raised text-fg-subtle">
                <MessageSquare className="h-5 w-5" />
              </span>
              <div className="min-w-0 flex-1">
                <p className="flex flex-wrap items-center gap-2 font-semibold text-fg-muted">
                  SMS
                  <Badge variant="neutral">Indisponible</Badge>
                </p>
                <p className="text-xs text-fg-muted">
                  {optionsEchec
                    ? 'La disponibilité du SMS n’a pas pu être vérifiée. Utilisez l’application d’authentification ci-dessus, ou réessayez plus tard.'
                    : 'La vérification par SMS n’est pas disponible : aucun fournisseur de SMS n’est configuré sur la plateforme. Utilisez l’application d’authentification ci-dessus.'}
                </p>
              </div>
            </div>
          )}

          <p className="text-center text-xs text-fg-muted">
            Pas de téléphone ? Choisissez l’<strong className="text-fg">application d’authentification</strong> :
            elle fonctionne aussi sur ordinateur, sans carte SIM.
          </p>

          {loading && (
            <p role="status" className="flex items-center justify-center gap-2 text-sm text-fg-muted">
              <Spinner className="h-3.5 w-3.5" /> Patientez…
            </p>
          )}
          {error && <Message ton="erreur">{error}</Message>}
        </div>
      )}

      {step === 'totp-setup' && (
        <form onSubmit={confirmTotp} className="space-y-5" noValidate>
          <TitreAccueil icone={<Smartphone />} titre="Scannez le QR code">
            Avec votre application d’authentification, puis saisissez le code à 6 chiffres qu’elle affiche.
          </TitreAccueil>

          {qrCode && (
            <div className="flex justify-center">
              <img
                src={`data:image/png;base64,${qrCode}`}
                alt="QR code à scanner avec votre application d’authentification"
                width={192}
                height={192}
                className="h-48 w-48 rounded-lg border border-border bg-bg-raised p-2"
              />
            </div>
          )}

          <div className="space-y-1 rounded-lg border border-border bg-bg-overlay p-3">
            <p className="text-xs font-medium text-fg-muted">Clé secrète (si vous ne pouvez pas scanner) :</p>
            <code className="break-all font-mono text-xs text-fg">{secret}</code>
          </div>

          <TextField
            label="Code à 6 chiffres affiché par l’application"
            name="totpCode"
            inputMode="numeric"
            pattern="[0-9]{6}"
            maxLength={6}
            value={totpCode}
            onChange={(e) => setTotpCode(e.target.value.replace(/\D/g, ''))}
            placeholder="123456"
            autoComplete="one-time-code"
            className="h-11 text-center text-lg tracking-[0.5em]"
            mono
            required
          />

          {error && <Message ton="erreur">{error}</Message>}

          <Button type="submit" size="lg" fullWidth loading={loading} disabled={totpCode.length !== 6}>
            Activer la double authentification
          </Button>
        </form>
      )}

      {step === 'sms-phone' && (
        <form onSubmit={envoyerCodeSms} className="space-y-5" noValidate>
          <TitreAccueil
            icone={<MessageSquare />}
            titre="Votre numéro de téléphone mobile"
            libelleInfoBulle="Aide sur le numéro de téléphone"
            infoBulle="Le numéro est enregistré sur votre compte seulement après la vérification du code reçu. À chaque connexion, un nouveau code à 6 chiffres y sera envoyé ; il n’est valable que quelques minutes."
          >
            Saisissez le numéro qui recevra vos codes de connexion, au format international. Vous recevrez un code
            à 6 chiffres pour le vérifier.
          </TitreAccueil>

          <TextField
            label="Numéro de téléphone mobile"
            name="telephone"
            type="tel"
            inputMode="tel"
            value={telephone}
            onChange={(e) => setTelephone(e.target.value)}
            placeholder="+212612345678"
            autoComplete="tel"
            hint="Au format international : + puis l’indicatif du pays, par exemple +212612345678."
            required
          />

          {methodeActive === 'TOTP' && (
            <TextField
              label="Code affiché par votre application d’authentification"
              name="codeActuel"
              inputMode="numeric"
              maxLength={6}
              value={codeActuel}
              onChange={(e) => setCodeActuel(e.target.value.replace(/\D/g, ''))}
              placeholder="123456"
              autoComplete="one-time-code"
              hint="Pour changer de méthode, confirmez d’abord avec votre méthode actuelle."
              mono
              required
            />
          )}

          {methodeActive === 'SMS' && (
            <div className="space-y-3 rounded-lg border border-border bg-bg-overlay p-3">
              <p className="text-xs text-fg-muted">
                Pour changer de numéro, confirmez d’abord avec un code envoyé à votre numéro actuel
                {phoneHint ? <> (<span className="font-mono">{phoneHint}</span>)</> : null}.
              </p>
              <Button type="button" variant="secondary" size="sm" onClick={recevoirCodeActuel} loading={loading}>
                Recevoir un code sur mon numéro actuel
              </Button>
              {codeActuelEnvoye && (
                <p role="status" className="text-xs text-fg-muted">
                  Code envoyé sur votre numéro actuel.
                </p>
              )}
              <TextField
                label="Code reçu sur votre numéro actuel"
                name="codeActuel"
                inputMode="numeric"
                maxLength={6}
                value={codeActuel}
                onChange={(e) => setCodeActuel(e.target.value.replace(/\D/g, ''))}
                placeholder="123456"
                autoComplete="one-time-code"
                mono
                required
              />
            </div>
          )}

          {smsSimule && <AideSmsSimule />}
          {error && <Message ton="erreur">{error}</Message>}

          <Button type="submit" size="lg" fullWidth loading={loading}>
            Recevoir le code
          </Button>
          <div className="flex justify-center">
            <Button type="button" variant="ghost" size="sm" onClick={() => { setError(null); setStep('choose'); }}>
              Choisir une autre méthode
            </Button>
          </div>
        </form>
      )}

      {step === 'sms-done' && (
        <div className="space-y-5">
          <TitreAccueil icone={<CheckCircle2 />} ton="succes" titre="SMS activé">
            La double authentification par SMS est activée : à chaque connexion, un code sera envoyé au{' '}
            <span className="font-mono">{maskedPhone}</span>. Vos codes de récupération restent valables.
          </TitreAccueil>
          <Button type="button" size="lg" fullWidth onClick={() => navigate('/settings/security', { replace: true })}>
            Revenir à la sécurité du compte
          </Button>
        </div>
      )}

      {step === 'sms-setup' && (
        <form onSubmit={confirmSms} className="space-y-5" noValidate>
          <TitreAccueil icone={<MessageSquare />} ton="succes" titre="Vérification par SMS">
            Un code à 6 chiffres a été envoyé au <span className="font-mono">{maskedPhone}</span>. Il n’est valable
            que quelques minutes ; après 5 codes faux, il faut en demander un nouveau.
          </TitreAccueil>
          {smsSimule && <AideSmsSimule />}

          <TextField
            label="Code reçu par SMS"
            name="smsCode"
            inputMode="numeric"
            pattern="[0-9]{6}"
            maxLength={6}
            value={smsCode}
            onChange={(e) => setSmsCode(e.target.value.replace(/\D/g, ''))}
            placeholder="123456"
            autoComplete="one-time-code"
            className="h-11 text-center text-lg tracking-[0.5em]"
            mono
            required
          />

          {error && <Message ton="erreur">{error}</Message>}

          <Button type="submit" size="lg" fullWidth loading={loading} disabled={smsCode.length !== 6}>
            Activer la double authentification
          </Button>
          <div className="flex justify-center">
            <Button type="button" variant="ghost" size="sm" onClick={renvoyerCodeSms}>
              Renvoyer un code
            </Button>
          </div>
        </form>
      )}

      {step === 'recovery-codes' && (
        <div className="space-y-5">
          <TitreAccueil icone={<Lock />} ton="attention" titre="Codes de récupération">
            Conservez ces 10 codes en lieu sûr : ils ne seront plus jamais affichés. Chaque code ne sert qu’une
            fois, si vous perdez l’accès à votre méthode de double authentification.
          </TitreAccueil>

          <div className="grid gap-2 rounded-lg border border-border bg-bg-overlay p-3 sm:grid-cols-2">
            {recoveryCodes.map((c) => (
              <code
                key={c}
                className="rounded-md bg-bg-raised px-2 py-1.5 text-center font-mono text-sm tracking-wider text-fg ring-1 ring-border"
              >
                {c}
              </code>
            ))}
          </div>

          <div className="flex flex-wrap gap-2">
            <Button type="button" variant="secondary" size="sm" icon={<Copy />} onClick={copyRecoveryCodes}>
              Copier
            </Button>
            <Button type="button" variant="secondary" size="sm" icon={<Download />} onClick={downloadRecoveryCodes}>
              Télécharger (.txt)
            </Button>
          </div>

          <div className="rounded-lg border border-border bg-bg-raised p-3">
            <Case
              label="J’ai mis mes codes de récupération en lieu sûr."
              checked={acknowledged}
              onChange={(e) => setAcknowledged(e.target.checked)}
            />
          </div>

          <Button type="button" size="lg" fullWidth onClick={finalize} disabled={!acknowledged} icon={<CheckCircle2 />}>
            Terminer
          </Button>
        </div>
      )}
    </PageAccueil>
  );
}
