import { useState } from 'react';
import { AlertTriangle, CheckCircle, Copy } from 'lucide-react';
import { useNavigate } from 'react-router-dom';

interface SignupSuccessProps {
  workspaceCode: string;
  /** BUG 7 (2026-06-08) — identifiant de connexion (login_email @jurika.ma). */
  email: string;
  /** BUG 7 (2026-06-08) — email perso destinataire des notifications. Affiche
   *  uniquement s'il differe du login_email. */
  contactEmail?: string;
  selectedPlan?: string; // essentiel | business | entreprise (spec 2026-06-02)
  /** HIGH-4 (audit) : false si l'envoi SMTP a echoue cote backend. */
  emailDelivered?: boolean;
}

const PLAN_LABELS: Record<string, string> = {
  essentiel: 'Essentiel — 499 MAD/mois',
  business: 'Business — 1 199 MAD/mois',
  entreprise: 'Entreprise — sur devis',
};

export function SignupSuccess({ workspaceCode, email, contactEmail, selectedPlan, emailDelivered = true }: SignupSuccessProps) {
  const planLabel = selectedPlan ? PLAN_LABELS[selectedPlan.toLowerCase()] || selectedPlan : null;
  const navigate = useNavigate();
  const [copied, setCopied] = useState(false);

  function handleCopy() {
    navigator.clipboard.writeText(workspaceCode).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  }

  return (
    <div className="flex min-h-[60vh] items-center justify-center bg-bg-overlay p-8">
      <div className="w-full max-w-[560px] rounded-2xl bg-bg-raised p-10 shadow-xl" data-testid="signup-success">
        <div className="text-center">
          <div className="mb-6 flex justify-center">
            <div className="flex h-14 w-14 items-center justify-center rounded-full bg-success">
              <CheckCircle className="h-8 w-8 text-bg" />
            </div>
          </div>

          <h2 className="mb-3 font-heading text-3xl font-semibold text-fg">
            Votre espace est pret !
          </h2>
          <p className="mb-8 text-fg-subtle">Bienvenue dans la plateforme JURIKA.</p>

          <div className="mb-8 rounded-xl bg-accent/10 p-6">
            <p className="mb-2 text-sm text-fg-subtle">Votre code workspace :</p>

            <div className="mb-4 flex items-center justify-center gap-3">
              <span className="font-mono text-3xl font-bold text-accent">
                {workspaceCode}
              </span>
              <button
                type="button"
                onClick={handleCopy}
                className="rounded-lg p-2 transition hover:bg-accent/20"
                title="Copier le code"
              >
                {copied ? (
                  <CheckCircle className="h-5 w-5 text-success" />
                ) : (
                  <Copy className="h-5 w-5 text-accent" />
                )}
              </button>
            </div>

            <p className="text-xs text-fg-subtle">
              Conservez ce code precieusement — il sera demande a chaque connexion.
            </p>
          </div>

          {planLabel && (
            <div
              className="mb-4 rounded-lg border border-blue-200 bg-blue-50 p-3 text-center text-sm text-blue-900"
              data-testid="signup-selected-plan"
            >
              Forfait choisi : <strong>{planLabel}</strong>
            </div>
          )}

          {!emailDelivered && (
            <div
              className="mb-4 rounded-lg border-2 border-red-300 bg-red-50 p-4 text-left"
              data-testid="signup-email-failed"
            >
              <div className="flex items-start gap-3">
                <AlertTriangle className="h-5 w-5 flex-shrink-0 text-red-600 mt-0.5" />
                <div>
                  <p className="font-semibold text-red-900">Email d'activation non envoye</p>
                  <p className="mt-1 text-xs text-red-800">
                    Une erreur technique a empeche l'envoi de votre mot de passe temporaire.
                    Notez bien le code workspace ci-dessus et contactez{' '}
                    <a href="mailto:support@jurika.ma" className="font-semibold underline">
                      support@jurika.ma
                    </a>{' '}
                    en mentionnant ce code pour debloquer votre compte.
                  </p>
                </div>
              </div>
            </div>
          )}

          <div className="mb-8 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-left">
            <p className="text-sm font-semibold text-emerald-800">
              Comment vous connecter ?
            </p>
            <ul className="mt-2 space-y-1 text-xs text-emerald-700">
              <li>
                1. Saisissez le code <span className="font-mono font-bold">{workspaceCode}</span>
              </li>
              <li>
                2. Connectez-vous avec votre identifiant{' '}
                <span className="font-mono font-bold">{email}</span> + mot de passe
              </li>
              <li>3. Configurez votre 2FA au premier login</li>
            </ul>
            {/* BUG 7 (2026-06-08) — distinction identifiant vs notifications */}
            {contactEmail && contactEmail !== email && (
              <p className="mt-3 rounded border border-emerald-200 bg-emerald-100 px-2 py-1 text-[11px] text-emerald-800">
                Vos notifications continuent d'arriver sur{' '}
                <span className="font-mono font-semibold">{contactEmail}</span>.
              </p>
            )}
          </div>

          <button
            type="button"
            onClick={() => navigate('/login')}
            className="h-12 w-full rounded-lg bg-accent font-semibold text-bg transition hover:bg-accent-hover"
          >
            Aller a la connexion →
          </button>
        </div>
      </div>
    </div>
  );
}
