import { Mail, ShieldCheck, KeyRound } from 'lucide-react';

/**
 * Sprint 11 TASK 2 — Etape 3 du wizard cabinet signup.
 *
 * Info-only : pas de saisie de mot de passe ici car JURIKA suit le pattern
 * V2 strict (RG-AU05) — le serveur genere un mot de passe temporaire 12ch
 * envoye par email avec verification + must_change_password = TRUE au 1er
 * login. C'est plus securise qu'une saisie cote landing (anti phishing).
 *
 * L'utilisateur passe cette etape pour comprendre le flow + politique.
 */
export function SignupStep3Security({ onBack, onNext }: { onBack: () => void; onNext: () => void }) {
  return (
    <div className="mx-auto max-w-2xl px-4 py-8" data-testid="signup-step-security">
      <div className="rounded-2xl bg-bg-raised p-6 shadow-md sm:p-8">
        <h2 className="mb-2 font-heading text-2xl font-semibold text-fg">Securite du compte</h2>
        <p className="mb-6 text-sm text-fg-subtle">
          Politique de mot de passe et processus d'activation.
        </p>
        <div className="space-y-4">
          <Card icon={<Mail className="h-5 w-5 text-accent" />}
                title="Mot de passe temporaire par email"
                body="Pour garantir votre securite, nous generons un mot de passe aleatoire 12 caracteres et vous l'envoyons par email avec un lien d'activation (TTL 24h)." />
          <Card icon={<KeyRound className="h-5 w-5 text-accent" />}
                title="Changement obligatoire au 1er login"
                body="Vous serez invite a definir votre propre mot de passe (min 12 caracteres, majuscules + chiffres + caracteres speciaux) lors de votre premiere connexion." />
          <Card icon={<ShieldCheck className="h-5 w-5 text-accent" />}
                title="2FA obligatoire (etape suivante)"
                body="La 2FA est requise pour tous les comptes. Vous choisissez SMS ou Google Authenticator a l'etape suivante, puis vous activez la 2FA au 1er login." />
        </div>
        <div className="mt-6 flex gap-3">
          <button type="button" onClick={onBack} className={btnSecondary} data-testid="signup-step3-back">← Retour</button>
          <button type="button" onClick={onNext} className={btnPrimary} data-testid="signup-step3-next">Continuer → 2FA</button>
        </div>
      </div>
    </div>
  );
}

const btnPrimary = 'flex-1 rounded-lg bg-accent px-4 py-3 text-sm font-semibold text-bg hover:bg-accent-hover transition-colors';
const btnSecondary = 'rounded-lg border border-border-hi bg-bg-raised px-4 py-3 text-sm font-semibold text-fg hover:bg-bg-overlay transition-colors';

function Card({ icon, title, body }: { icon: React.ReactNode; title: string; body: string }) {
  return (
    <div className="flex items-start gap-3 rounded-lg border border-border bg-bg-overlay p-4">
      <div className="mt-0.5">{icon}</div>
      <div>
        <h3 className="mb-1 text-sm font-semibold text-fg">{title}</h3>
        <p className="text-xs text-fg-subtle leading-relaxed">{body}</p>
      </div>
    </div>
  );
}
