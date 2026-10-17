import { useState } from 'react';
import { ArrowRight, CheckCircle, Copy } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { Button } from '../ui/Button';
import { Lien } from '../ui/Lien';
import { Message } from '../ui/Message';
import { copierTexte } from '../../lib/pressePapiers';

interface SignupSuccessProps {
  workspaceCode: string;
  /** BUG 7 (2026-06-08) — identifiant de connexion (login_email @jurika.ma). */
  email: string;
  /** BUG 7 (2026-06-08) — email perso destinataire des notifications. Affiche
   *  uniquement s'il differe du login_email. */
  contactEmail?: string;
  selectedPlan?: string; // essentiel | professionnel | max (lot L7, CDC 4.3)
  /** HIGH-4 (audit) : false si l'envoi SMTP a echoue cote backend. */
  emailDelivered?: boolean;
}

// Lot L7 (E5) : grille du CDC 4.3, prix hors taxes.
const PLAN_LABELS: Record<string, string> = {
  essentiel: 'Essentiel — 375 DH HT par mois ou 3 750 DH HT par an',
  professionnel: 'Professionnel — 750 DH HT par mois ou 7 500 DH HT par an',
  max: 'Max — 1 500 DH HT par mois ou 15 000 DH HT par an',
  sur_devis: 'Sur Devis — selon devis',
};

export function SignupSuccess({ workspaceCode, email, contactEmail, selectedPlan, emailDelivered = true }: SignupSuccessProps) {
  const planLabel = selectedPlan ? PLAN_LABELS[selectedPlan.toLowerCase()] || selectedPlan : null;
  const navigate = useNavigate();
  const [copied, setCopied] = useState(false);

  function handleCopy() {
    // Lot L6b (E13) : copie aussi en HTTP (navigator.clipboard absent hors contexte securise).
    void copierTexte(workspaceCode).then((ok) => {
      if (!ok) return;
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    });
  }

  return (
    <div className="mx-auto max-w-xl py-4">
      <div
        className="rounded-xl border border-border border-t-2 border-t-accent bg-bg-raised p-6 shadow-card sm:p-8"
        data-testid="signup-success"
      >
        <div className="text-center">
          <div
            aria-hidden="true"
            className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-success-soft text-success"
          >
            <CheckCircle className="h-6 w-6" />
          </div>

          <h2 className="mb-2 text-2xl font-semibold tracking-tight text-heading">Votre espace est prêt !</h2>
          <p className="mb-6 text-sm text-fg-muted">Bienvenue dans la plateforme JURIKA.</p>

          <div className="mb-6 rounded-lg border border-border bg-bg-overlay p-5">
            <p className="mb-2 text-sm text-fg-muted">Code de votre cabinet :</p>

            <div className="mb-3 flex items-center justify-center gap-2">
              <span className="font-mono text-3xl font-bold tracking-wider text-heading">{workspaceCode}</span>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                iconOnly
                onClick={handleCopy}
                aria-label={copied ? 'Code copié' : 'Copier le code'}
                title="Copier le code"
                icon={copied ? <CheckCircle className="text-success" /> : <Copy />}
              />
            </div>

            <p className="text-xs text-fg-muted">
              Conservez précieusement ce code : il vous sera demandé à chaque connexion.
            </p>
          </div>
        </div>

        <div className="space-y-4">
          {planLabel && (
            <p className="text-center text-sm text-fg" data-testid="signup-selected-plan">
              Forfait choisi : <strong>{planLabel}</strong>
            </p>
          )}

          {!emailDelivered && (
            <div data-testid="signup-email-failed">
              <Message ton="erreur" titre="Courriel d’activation non envoyé">
                Une erreur technique a empêché l’envoi de votre mot de passe temporaire. Notez bien le code du cabinet
                ci-dessus et contactez <Lien href="mailto:support@jurika.ma">support@jurika.ma</Lien> en mentionnant
                ce code pour débloquer votre compte.
              </Message>
            </div>
          )}

          <Message ton="succes" titre="Comment vous connecter ?">
            <ol className="mt-1 space-y-1 text-sm">
              <li>
                1. Saisissez le code <span className="font-mono font-bold">{workspaceCode}</span>
              </li>
              <li>
                2. Connectez-vous avec votre identifiant <span className="font-mono font-bold">{email}</span> et le
                mot de passe temporaire reçu par courriel
              </li>
              <li>3. Configurez votre double authentification à la première connexion</li>
            </ol>
            {/* BUG 7 (2026-06-08) — distinction identifiant vs notifications */}
            {contactEmail && contactEmail !== email && (
              <p className="mt-2 text-xs text-fg-muted">
                Vos notifications continuent d’arriver sur <span className="font-mono font-semibold">{contactEmail}</span>.
              </p>
            )}
          </Message>

          <Button type="button" size="lg" fullWidth onClick={() => navigate('/login')} iconEnd={<ArrowRight />}>
            Aller à la connexion
          </Button>
        </div>
      </div>
    </div>
  );
}
