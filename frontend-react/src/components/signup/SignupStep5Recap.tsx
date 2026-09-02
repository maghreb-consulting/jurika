import { useState } from 'react';
import { CheckCircle2, AlertCircle } from 'lucide-react';
import type { SignupProfileData } from './SignupStep1Cabinet';
import type { TwoFactorPreference } from './SignupStep4TwoFactor';
import { PROFESSIONAL_TYPE_LABELS, type ProfessionalType } from '../../types/professional';

// Spec directeur 2026-06-02 : essentiel / business / entreprise.
const PLAN_LABELS: Record<string, string> = {
  essentiel: 'Essentiel (499 MAD/mois)',
  business: 'Business (1 199 MAD/mois)',
  entreprise: 'Entreprise (sur devis)',
};

interface Props {
  profile: SignupProfileData;
  twoFactor: TwoFactorPreference;
  selectedPlan: string;
  professionalType?: ProfessionalType | null;
  onBack: () => void;
  onSubmit: (cguAccepted: boolean) => Promise<void>;
}

export function SignupStep5Recap({ profile, twoFactor, selectedPlan, professionalType, onBack, onSubmit }: Props) {
  const [cguAccepted, setCguAccepted] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!cguAccepted) {
      setError('Vous devez accepter les CGU et la politique de confidentialite.');
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      await onSubmit(cguAccepted);
    } catch (e: unknown) {
      const msg = (e as Error)?.message || 'Erreur lors de la creation du cabinet.';
      setError(msg);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="mx-auto max-w-2xl px-4 py-8" data-testid="signup-step-recap">
      <div className="rounded-2xl bg-bg-raised p-6 shadow-md sm:p-8">
        <h2 className="mb-2 font-heading text-2xl font-semibold text-fg">Recapitulatif</h2>
        <p className="mb-6 text-sm text-fg-subtle">
          Verifiez les informations avant de creer votre cabinet.
        </p>

        <div className="mb-6 space-y-4">
          <RecapBlock title="Titulaire">
            <Line label="Nom" value={`${profile.firstName} ${profile.lastName}`} />
            <Line label="Email professionnel" value={profile.email} />
            <Line label="GSM" value={profile.phone} />
          </RecapBlock>
          <RecapBlock title="Structure">
            <Line label="Denomination" value={profile.workspaceName} />
            <Line label="Ville" value={profile.city} />
            <Line label="ICE" value={profile.ice.trim() === '' ? 'A completer plus tard' : profile.ice} />
          </RecapBlock>
          <RecapBlock title="Configuration">
            {professionalType && (
              <Line label="Type de profil" value={PROFESSIONAL_TYPE_LABELS[professionalType]} />
            )}
            <Line label="Plan selectionne" value={PLAN_LABELS[selectedPlan] || selectedPlan} />
            <Line label="2FA preferee" value={twoFactor === 'SMS' ? 'SMS Twilio' : 'Google Authenticator (TOTP)'} />
          </RecapBlock>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4" noValidate>
          <label className="flex items-start gap-2 cursor-pointer" data-testid="signup-cgu-label">
            <input
              type="checkbox"
              checked={cguAccepted}
              onChange={(e) => setCguAccepted(e.target.checked)}
              className="mt-0.5 h-4 w-4 accent-[#2563EB]"
              data-testid="signup-cgu-checkbox"
            />
            <span className="text-sm text-fg-muted">
              J'accepte les <a href="/cgu" target="_blank" className="text-accent underline">CGU</a>{' '}
              et la <a href="/privacy" target="_blank" className="text-accent underline">politique de confidentialite (CNDP)</a>.
            </span>
          </label>

          {error && (
            <div className="flex items-start gap-2 rounded-lg bg-danger/10 p-3 text-sm text-danger" data-testid="signup-step5-error">
              <AlertCircle className="mt-0.5 h-4 w-4 flex-shrink-0" />
              <span>{error}</span>
            </div>
          )}

          <div className="flex gap-3">
            <button type="button" onClick={onBack} disabled={submitting} className={btnSecondary} data-testid="signup-step5-back">← Retour</button>
            <button type="submit" disabled={submitting} className={btnPrimary} data-testid="signup-step5-submit">
              {submitting ? (
                <>Creation en cours...</>
              ) : (
                <><CheckCircle2 className="inline h-4 w-4 mr-1" />Creer mon cabinet</>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

function RecapBlock({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="rounded-lg border border-border bg-bg-overlay p-4">
      <h3 className="mb-2 text-sm font-semibold text-fg">{title}</h3>
      <dl className="space-y-1">{children}</dl>
    </div>
  );
}
function Line({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-2 text-xs">
      <dt className="text-fg-subtle">{label}</dt>
      <dd className="text-right text-fg font-medium" data-testid={`recap-${label.toLowerCase().replace(/\s+/g, '-')}`}>{value}</dd>
    </div>
  );
}

const btnPrimary = 'flex-1 rounded-lg bg-accent px-4 py-3 text-sm font-semibold text-bg hover:bg-accent-hover transition-colors disabled:opacity-60 disabled:cursor-not-allowed';
const btnSecondary = 'rounded-lg border border-border-hi bg-bg-raised px-4 py-3 text-sm font-semibold text-fg hover:bg-bg-overlay transition-colors disabled:opacity-60 disabled:cursor-not-allowed';
