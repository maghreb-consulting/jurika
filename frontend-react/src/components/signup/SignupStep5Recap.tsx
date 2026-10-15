import { useState } from 'react';
import { CheckCircle2 } from 'lucide-react';
import type { SignupProfileData } from './SignupStep1Cabinet';
import type { TwoFactorPreference } from './SignupStep4TwoFactor';
import { PROFESSIONAL_TYPE_LABELS, type ProfessionalType } from '../../types/professional';
import { classesBouton } from '../ui/boutonClasses';
import { Lien } from '../ui/Lien';
import { Message } from '../ui/Message';

// Lot L7 (E5) : grille du CDC 4.3, prix hors taxes.
const PLAN_LABELS: Record<string, string> = {
  essentiel: 'Essentiel (375 DH HT par mois ou 3 750 DH HT par an)',
  professionnel: 'Professionnel (750 DH HT par mois ou 7 500 DH HT par an)',
  max: 'Max (1 500 DH HT par mois ou 15 000 DH HT par an)',
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
      setError('Cochez la case d’acceptation des CGU et de la politique de confidentialité pour continuer.');
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      await onSubmit(cguAccepted);
    } catch (e: unknown) {
      const msg = (e as Error)?.message || 'La création du cabinet a échoué. Réessayez.';
      setError(msg);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="mx-auto max-w-2xl py-4" data-testid="signup-step-recap">
      <div className="rounded-xl border border-border border-t-2 border-t-accent bg-bg-raised p-6 shadow-card sm:p-8">
        <h2 className="mb-2 text-2xl font-semibold tracking-tight text-heading">Récapitulatif</h2>
        <p className="mb-6 text-sm text-fg-subtle">
          Vérifiez les informations avant de créer votre cabinet. Pour corriger une donnée, revenez
          en arrière avec « Retour ».
        </p>

        <div className="mb-6 space-y-4">
          <RecapBlock title="Titulaire">
            <Line label="Nom" value={`${profile.firstName} ${profile.lastName}`} />
            <Line label="Adresse électronique professionnelle" cle="email-professionnel" value={profile.email} />
            <Line label="Téléphone" cle="gsm" value={profile.phone} />
          </RecapBlock>
          <RecapBlock title="Structure">
            <Line label="Dénomination" cle="denomination" value={profile.workspaceName} />
            <Line label="Ville" value={profile.city} />
            <Line label="ICE" value={profile.ice.trim() === '' ? 'À compléter plus tard' : profile.ice} />
          </RecapBlock>
          <RecapBlock title="Configuration">
            {professionalType && (
              <Line label="Type de profil" value={PROFESSIONAL_TYPE_LABELS[professionalType]} />
            )}
            <Line label="Forfait choisi" cle="plan-selectionne" value={PLAN_LABELS[selectedPlan] || selectedPlan} />
            <Line
              label="Double authentification"
              cle="2fa-preferee"
              value={twoFactor === 'SMS' ? 'Code par SMS' : 'Application d’authentification'}
            />
          </RecapBlock>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4" noValidate>
          <label className="flex items-start gap-2 cursor-pointer" data-testid="signup-cgu-label">
            <input
              type="checkbox"
              checked={cguAccepted}
              onChange={(e) => setCguAccepted(e.target.checked)}
              className="mt-0.5 h-4 w-4 accent-[var(--color-accent)]"
              data-testid="signup-cgu-checkbox"
            />
            <span className="text-sm text-fg-muted">
              J’accepte les <Lien href="/cgu" target="_blank" rel="noopener">CGU</Lien>{' '}
              et la <Lien href="/privacy" target="_blank" rel="noopener">politique de confidentialité (CNDP)</Lien>.
            </span>
          </label>

          {error && (
            <div data-testid="signup-step5-error">
              <Message ton="erreur">{error}</Message>
            </div>
          )}

          <div className="flex gap-3">
            <button type="button" onClick={onBack} disabled={submitting} className={btnSecondary} data-testid="signup-step5-back">← Retour</button>
            <button type="submit" disabled={submitting} className={btnPrimary} data-testid="signup-step5-submit">
              {submitting ? (
                <>Création en cours…</>
              ) : (
                <><CheckCircle2 className="inline h-4 w-4 mr-1" />Créer mon cabinet</>
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
/** `cle` fige l'identifiant de test quand le libelle affiche change (recap-<cle>). */
function Line({ label, value, cle }: { label: string; value: string; cle?: string }) {
  return (
    <div className="flex justify-between gap-2 text-xs">
      <dt className="text-fg-subtle">{label}</dt>
      <dd className="text-right text-fg font-medium" data-testid={`recap-${cle ?? label.toLowerCase().replace(/\s+/g, '-')}`}>{value}</dd>
    </div>
  );
}

const btnPrimary = classesBouton('primary', 'lg', 'h-auto min-h-11 flex-1 whitespace-normal py-2 text-center');
const btnSecondary = classesBouton('secondary', 'lg', 'h-auto min-h-11 py-2');
