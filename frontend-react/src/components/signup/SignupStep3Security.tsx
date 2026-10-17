import { Mail, ShieldCheck, KeyRound } from 'lucide-react';
import { classesBouton } from '../ui/boutonClasses';

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
    <div className="mx-auto max-w-2xl py-4" data-testid="signup-step-security">
      <div className="rounded-xl border border-border border-t-2 border-t-accent bg-bg-raised p-6 shadow-card sm:p-8">
        <h2 className="mb-2 text-2xl font-semibold tracking-tight text-heading">Sécurité du compte</h2>
        <p className="mb-6 text-sm text-fg-subtle">
          Comment votre mot de passe est créé et comment votre compte est activé. Rien à saisir ici :
          lisez, puis continuez.
        </p>
        <div className="space-y-4">
          <Card icon={<Mail className="h-5 w-5 text-accent" />}
                title="Mot de passe temporaire par courriel"
                body="Pour votre sécurité, nous générons un mot de passe aléatoire de 12 caractères et vous l’envoyons par courriel avec un lien d’activation valable 24 heures." />
          <Card icon={<KeyRound className="h-5 w-5 text-accent" />}
                title="Changement obligatoire à la première connexion"
                body="À votre première connexion, vous choisirez votre propre mot de passe : au moins 12 caractères, avec une majuscule, une minuscule, un chiffre et un caractère spécial." />
          <Card icon={<ShieldCheck className="h-5 w-5 text-accent" />}
                title="Double authentification obligatoire (étape suivante)"
                body="La double authentification est exigée pour tous les comptes du cabinet. Vous choisissez votre méthode à l’étape suivante, puis vous l’activez à votre première connexion." />
        </div>
        <div className="mt-6 flex gap-3">
          <button type="button" onClick={onBack} className={btnSecondary} data-testid="signup-step3-back">← Retour</button>
          <button type="button" onClick={onNext} className={btnPrimary} data-testid="signup-step3-next">Continuer vers la double authentification</button>
        </div>
      </div>
    </div>
  );
}

const btnPrimary = classesBouton('primary', 'lg', 'h-auto min-h-11 flex-1 whitespace-normal py-2 text-center');
const btnSecondary = classesBouton('secondary', 'lg', 'h-auto min-h-11 py-2');

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
