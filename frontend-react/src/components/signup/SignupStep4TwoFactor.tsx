import { useState } from 'react';
import { Smartphone, QrCode } from 'lucide-react';
import { SMS_2FA_ENABLED } from '../../lib/featureFlags';
import { Badge } from '../ui/Badge';
import { classesBouton } from '../ui/boutonClasses';

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
  const [prefChoisie, setPref] = useState<TwoFactorPreference>(
    SMS_2FA_ENABLED ? initial : 'TOTP',
  );
  // Si le flag est faux et qu'un draft repris portait 'SMS', on rebascule TOTP
  // (valeur deduite pendant le rendu, sans effet).
  const pref: TwoFactorPreference = !SMS_2FA_ENABLED && prefChoisie === 'SMS' ? 'TOTP' : prefChoisie;

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
    <div className="mx-auto max-w-2xl py-4" data-testid="signup-step-2fa">
      <div className="rounded-xl border border-border border-t-2 border-t-accent bg-bg-raised p-6 shadow-card sm:p-8">
        <h2 className="mb-2 text-2xl font-semibold tracking-tight text-heading">Méthode de double authentification</h2>
        <p className="mb-6 text-sm text-fg-subtle">
          La double authentification est obligatoire pour protéger les données du cabinet.
          Choisissez dès maintenant votre méthode : vous l’activerez à votre première connexion.
        </p>
        <form onSubmit={submit} className="space-y-3" noValidate>
          <PrefCard
            value="SMS" current={pref} setPref={setPref}
            icon={<Smartphone className="h-5 w-5" />}
            title="SMS"
            body="Recevez un code à 6 chiffres par SMS. Simple : aucune application à installer."
            disabled={!SMS_2FA_ENABLED}
            disabledLabel="Hors service"
          />
          <PrefCard
            value="TOTP" current={pref} setPref={setPref}
            icon={<QrCode className="h-5 w-5" />}
            title="Application d’authentification"
            body="Scannez une seule fois un QR code avec Google Authenticator, Authy ou 1Password. Recommandé : gratuit et indépendant du réseau téléphonique."
          />
          <div className="mt-2 flex gap-3">
            <button type="button" onClick={onBack} className={btnSecondary} data-testid="signup-step4-back">← Retour</button>
            <button type="submit" className={btnPrimary} data-testid="signup-step4-next">Continuer vers le récapitulatif</button>
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
        'flex items-start gap-3 rounded-lg border p-4 transition-colors duration-150 has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-focus',
        disabled
          ? 'cursor-not-allowed border-border bg-bg-overlay opacity-60'
          : selected
            ? 'cursor-pointer border-accent bg-accent-soft'
            : 'cursor-pointer border-border-input bg-bg-raised hover:border-fg-subtle',
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
        className="mt-1.5 h-4 w-4 accent-[var(--color-accent)]"
      />
      <div className={`mt-0.5 ${disabled ? 'text-fg-subtle' : 'text-accent'}`}>{icon}</div>
      <div>
        <h3 className="mb-1 flex items-center gap-2 text-sm font-semibold text-fg">
          {title}
          {disabled && disabledLabel && (
            <Badge variant="neutral">{disabledLabel}</Badge>
          )}
        </h3>
        <p className="text-xs text-fg-subtle leading-relaxed">{body}</p>
      </div>
    </label>
  );
}

const btnPrimary = classesBouton('primary', 'lg', 'h-auto min-h-11 flex-1 whitespace-normal py-2 text-center');
const btnSecondary = classesBouton('secondary', 'lg', 'h-auto min-h-11 py-2');
