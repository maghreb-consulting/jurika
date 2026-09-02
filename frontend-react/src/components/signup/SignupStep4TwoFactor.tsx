import { useEffect, useState } from 'react';
import { Smartphone, QrCode } from 'lucide-react';
import { SMS_2FA_ENABLED } from '../../lib/featureFlags';

/**
 * Sprint 11 TASK 2 — Etape 4 du wizard cabinet signup.
 *
 * Choix de preference 2FA (SMS Twilio OU Google Authenticator TOTP).
 * Le SETUP effectif (saisie code OTP / scan QR) est fait au 1er login via
 * la page existante `/account/2fa-choose` + `/account/2fa` (Sprint 3).
 *
 * Le choix est stocke en localStorage (`jurika.signup.preferred2fa`) lu par
 * `Choose2faMethodPage` post-login pour preselection.
 */
export type TwoFactorPreference = 'SMS' | 'TOTP';

interface Props {
  initial: TwoFactorPreference;
  onBack: () => void;
  onNext: (pref: TwoFactorPreference) => void;
}

export function SignupStep4TwoFactor({ initial, onBack, onNext }: Props) {
  // SMS « Hors service » tant qu'aucun fournisseur SMS n'est branche
  // (SMS_2FA_ENABLED=false). On force TOTP pour ne pas laisser choisir une
  // methode qui casserait le setup au 1er login.
  const [pref, setPref] = useState<TwoFactorPreference>(
    SMS_2FA_ENABLED ? initial : 'TOTP',
  );

  // Si le flag est faux et qu'un draft repris portait 'SMS', on rebascule TOTP.
  useEffect(() => {
    if (!SMS_2FA_ENABLED && pref === 'SMS') setPref('TOTP');
  }, [pref]);

  function submit(e: React.FormEvent) {
    e.preventDefault();
    try {
      localStorage.setItem('jurika.signup.preferred2fa', pref);
    } catch {
      // localStorage pas dispo (incognito strict) — non bloquant
    }
    onNext(pref);
  }

  return (
    <div className="mx-auto max-w-2xl px-4 py-8" data-testid="signup-step-2fa">
      <div className="rounded-2xl bg-bg-raised p-6 shadow-md sm:p-8">
        <h2 className="mb-2 font-heading text-2xl font-semibold text-fg">Methode 2FA preferee</h2>
        <p className="mb-6 text-sm text-fg-subtle">
          La 2FA est obligatoire (RG-SAAS-12). Vous activerez votre 2FA au 1er login,
          mais choisissez deja votre methode pour preparer le setup.
        </p>
        <form onSubmit={submit} className="space-y-3" noValidate>
          <PrefCard
            value="SMS" current={pref} setPref={setPref}
            icon={<Smartphone className="h-5 w-5" />}
            title="SMS"
            body="Recevez un code 6 chiffres par SMS. Simple, pas d'app a installer."
            disabled={!SMS_2FA_ENABLED}
            disabledLabel="Hors service"
          />
          <PrefCard
            value="TOTP" current={pref} setPref={setPref}
            icon={<QrCode className="h-5 w-5" />}
            title="Google Authenticator (TOTP)"
            body="Scannez un QR code une seule fois avec Google Authenticator / Authy / 1Password. Recommande : pas de dependance reseau, gratuit."
          />
          <div className="mt-2 flex gap-3">
            <button type="button" onClick={onBack} className={btnSecondary} data-testid="signup-step4-back">← Retour</button>
            <button type="submit" className={btnPrimary} data-testid="signup-step4-next">Continuer → Recap</button>
          </div>
        </form>
      </div>
    </div>
  );
}

interface PrefCardProps {
  value: TwoFactorPreference;
  current: TwoFactorPreference;
  setPref: (v: TwoFactorPreference) => void;
  icon: React.ReactNode;
  title: string;
  body: string;
  /** Carte grisee, non selectionnable (ex. SMS non configure). */
  disabled?: boolean;
  /** Badge affiche quand disabled (ex. « Hors service »). */
  disabledLabel?: string;
}
function PrefCard({ value, current, setPref, icon, title, body, disabled, disabledLabel }: PrefCardProps) {
  const selected = current === value && !disabled;
  return (
    <label
      aria-disabled={disabled || undefined}
      className={[
        'flex items-start gap-3 rounded-lg border-2 p-4 transition-colors',
        disabled
          ? 'cursor-not-allowed border-border bg-bg-overlay opacity-60'
          : selected
            ? 'cursor-pointer border-accent bg-accent/10'
            : 'cursor-pointer border-border bg-bg-raised hover:border-border-hi',
      ].join(' ')}
      data-testid={`signup-2fa-option-${value.toLowerCase()}`}
    >
      <input
        type="radio"
        name="twoFactor"
        value={value}
        checked={selected}
        disabled={disabled}
        onChange={() => !disabled && setPref(value)}
        className="mt-1.5 h-4 w-4 accent-[#2563EB]"
      />
      <div className={`mt-0.5 ${disabled ? 'text-fg-subtle' : 'text-accent'}`}>{icon}</div>
      <div>
        <h3 className="mb-1 flex items-center gap-2 text-sm font-semibold text-fg">
          {title}
          {disabled && disabledLabel && (
            <span className="rounded-full bg-fg-subtle/20 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-fg-muted">
              {disabledLabel}
            </span>
          )}
        </h3>
        <p className="text-xs text-fg-subtle leading-relaxed">{body}</p>
      </div>
    </label>
  );
}

const btnPrimary = 'flex-1 rounded-lg bg-accent px-4 py-3 text-sm font-semibold text-bg hover:bg-accent-hover transition-colors';
const btnSecondary = 'rounded-lg border border-border-hi bg-bg-raised px-4 py-3 text-sm font-semibold text-fg hover:bg-bg-overlay transition-colors';
