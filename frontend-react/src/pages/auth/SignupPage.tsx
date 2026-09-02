import { useEffect, useState, type ReactNode } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { SignupStep0Plan, type PlanCode } from '../../components/signup/SignupStep0Plan';
import { SignupStep1Cabinet, type SignupProfileData } from '../../components/signup/SignupStep1Cabinet';
import { SignupStep3Security } from '../../components/signup/SignupStep3Security';
import { SignupStep4TwoFactor, type TwoFactorPreference } from '../../components/signup/SignupStep4TwoFactor';
import { SignupStep5Recap } from '../../components/signup/SignupStep5Recap';
import { SignupStepPayment, type SignupPaymentDraft } from '../../components/signup/SignupStepPayment';
import { SignupSuccess } from '../../components/signup/SignupSuccess';
import { SignupStepper } from '../../components/signup/SignupStepper';
import { BrandLogo } from '../../components/ui/BrandLogo';
import { authService, type SignupCabinetPayload, type SignupCabinetResponse } from '../../services/auth.service';
import { extractError, tokenStorage } from '../../lib/api';
import { emitBusinessEvent } from '../../services/analytics.service';
import type { BillingPeriod, CheckoutPlanCode } from '../../types/billing';
import type { ProfessionalType } from '../../types/professional';

const EMPTY_PROFILE: SignupProfileData = {
  firstName: '', lastName: '', workspaceName: '', email: '', phone: '', city: '', ice: '',
};
// Spec directeur 2026-06-02 : codes canoniques essentiel / business / entreprise.
const ALLOWED_PLANS = new Set<PlanCode>(['essentiel', 'business', 'entreprise']);

// Simplification inscription (2026-07-13) : Cabinet + Admin fusionnes en une
// seule etape "Profil". Le wizard passe de 8 a 7 etapes :
//   0 Forfait / 1 Profil / 2 Securite / 3 2FA / 4 Recap / 5 Paiement / 6 Succes.
type Step = 0 | 1 | 2 | 3 | 4 | 5 | 6;

// Cle sessionStorage pour persister le draft du wizard (survit F5 / clic
// accidentel / fermeture d'onglet). Purge au succes (step 6).
const DRAFT_KEY = 'jurika.signup.draft';
interface SignupDraft {
  step: Step;
  profile: SignupProfileData;
  twoFactor: TwoFactorPreference;
  selectedPlan: string;
  professionalType: ProfessionalType | null;
}

function loadDraft(): Partial<SignupDraft> {
  try {
    const raw = sessionStorage.getItem(DRAFT_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw);
    if (typeof parsed !== 'object' || parsed === null) return {};
    return parsed as Partial<SignupDraft>;
  } catch {
    return {};
  }
}

function saveDraft(draft: SignupDraft): void {
  try {
    sessionStorage.setItem(DRAFT_KEY, JSON.stringify(draft));
  } catch {
    // sessionStorage indisponible (private mode / quota) — degradation gracieuse
  }
}

function clearDraft(): void {
  try {
    sessionStorage.removeItem(DRAFT_KEY);
  } catch {
    // noop
  }
}

/**
 * Wizard signup self-service "cabinet".
 *
 * Simplification inscription (2026-07-13) : formulaire UNIQUE (etape Profil)
 * regroupant type de profil + titulaire (nom/prenom/email/GSM) + structure
 * (denomination pre-remplie + ville + ICE optionnel), sans distinction
 * personne physique / morale et sans double email. Etapes restantes :
 * Forfait / Profil / Securite / 2FA / Recap / Paiement / Succes.
 *
 * Querystring `?plan=essentiel|business|entreprise` preselectionne le plan
 * depuis la landing jurika.ai — le choix reste confirme par l'utilisateur.
 */
export function SignupPage() {
  const [searchParams] = useSearchParams();
  const planParam = (searchParams.get('plan') || '').toLowerCase() as PlanCode;
  // Plan d'URL = pre-selection au Step 0, JAMAIS un skip. Le choix du tarif
  // doit etre confirme par chaque utilisateur peu importe le canal d'entree.
  const planFromUrl: PlanCode | null = ALLOWED_PLANS.has(planParam) ? planParam : null;

  // Hydrate l'etat depuis sessionStorage si un draft existe.
  //
  // Point d'entree unique (2026-07-27) : le wizard demarre TOUJOURS au choix du
  // forfait (step 0), quel que soit le canal (landing ?plan=, reprise de draft,
  // retour arriere). Le draft ne sert qu'a PRE-REMPLIR les valeurs (profil,
  // plan, 2FA) — on n'y lit JAMAIS `draft.step` pour reprendre au milieu. Cela
  // garantit que le forfait est reconfirme comme premiere action.
  const draft = loadDraft();
  const [step, setStep] = useState<Step>(0);
  const [profile, setProfile] = useState<SignupProfileData>(draft.profile ?? EMPTY_PROFILE);
  const [twoFactor, setTwoFactor] = useState<TwoFactorPreference>(draft.twoFactor ?? 'TOTP');
  const [selectedPlan, setSelectedPlan] = useState<PlanCode | null>(
    (draft.selectedPlan as PlanCode | undefined) ?? planFromUrl,
  );
  const [professionalType, setProfessionalType] = useState<ProfessionalType | null>(
    draft.professionalType ?? null,
  );
  const [success, setSuccess] = useState<SignupCabinetResponse | null>(null);

  // Sync plan avec ?plan= changements (back/forward navigation depuis landing)
  useEffect(() => {
    if (planFromUrl) setSelectedPlan(planFromUrl);
  }, [planFromUrl]);

  // On garde le mode "plan toujours confirme" (pas de skip).
  useEffect(() => {
    try {
      sessionStorage.setItem('jurika.signup.skipPlan', 'false');
    } catch { /* sessionStorage indispo (private mode) */ }
  }, []);

  // Persiste l'etat a chaque changement pour resister a F5 / fermeture d'onglet.
  useEffect(() => {
    if (step >= 0 && step <= 5 && selectedPlan) {
      saveDraft({ step, profile, twoFactor, selectedPlan, professionalType });
    }
  }, [step, profile, twoFactor, selectedPlan, professionalType]);

  // Avertir avant fermeture si un draft est en cours.
  useEffect(() => {
    if (step === 0 || step === 6) return; // step 0 = pas encore engage, step 6 = succes
    const handler = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = '';
    };
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, [step]);

  // emit SIGNUP_STARTED au 1er mount + purge la session residuelle (un vieux JWT
  // attache aux requetes /public/** ferait echouer le onboarding anonyme).
  useEffect(() => {
    try { tokenStorage.clear(); } catch { /* no-op si indispo */ }
    emitBusinessEvent('SIGNUP_STARTED', { properties: { plan: planFromUrl ?? 'unset' } });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function handleSubmit(cguAccepted: boolean): Promise<void> {
    if (!selectedPlan) {
      throw new Error('Veuillez choisir un forfait avant de finaliser l\'inscription.');
    }
    // Paiement-first : on cree le workspace en deferCredentials=true ; le backend
    // renvoie un access token transitoire utilise au Step Paiement. Le welcome
    // email part apres validation du paiement.
    const payload: SignupCabinetPayload = {
      workspaceName: profile.workspaceName,
      ice: profile.ice.trim() === '' ? undefined : profile.ice,
      city: profile.city,
      firstName: profile.firstName,
      lastName: profile.lastName,
      email: profile.email,
      phone: profile.phone,
      selectedPlan,
      professionalType: professionalType ?? undefined,
      cguAccepted,
      deferCredentials: true,
    };
    try {
      const result = await authService.signupCabinet(payload);
      setSuccess(result);
      setStep(5); // Step 5 = Paiement (PAS encore le succes)
      // Le draft est purge UNIQUEMENT au step 6 (succes paiement)
    } catch (err) {
      // Re-throw avec message human-friendly pour SignupStep5Recap qui catch
      throw new Error(extractError(err).message);
    }
  }

  // Callback declenchee par SignupStepPayment quand le paiement est COMPLETED.
  function onPaymentDone(_paymentId: number, _status: string): void {
    if (!success || !selectedPlan) return;
    setStep(6);
    clearDraft();
    emitBusinessEvent('SIGNUP_COMPLETED', {
      workspaceId: success.workspaceId,
      properties: { plan: selectedPlan, twoFactor, paymentId: _paymentId, paymentStatus: _status },
    });
  }

  // Wrapper layout commun : header overlay sur les etapes, masque sur succes.
  const content: ReactNode = (() => {
    if (step === 6 && success) {
      return (
        <SignupSuccess
          workspaceCode={success.workspaceCode}
          // Affiche l'identifiant @jurika.ma genere si present, sinon fallback
          // sur l'email pro fourni (compat back V1).
          email={success.loginEmail ?? success.adminEmail}
          contactEmail={success.contactEmail ?? success.adminEmail}
          selectedPlan={selectedPlan ?? undefined}
          emailDelivered={true} /* email envoye par IssueCredentialsUseCase post-paiement */
        />
      );
    }
    if (step === 5 && success && selectedPlan) {
      const paymentDraft: SignupPaymentDraft = {
        workspaceId: success.workspaceId,
        workspaceCode: success.workspaceCode,
        planCode: selectedPlan as CheckoutPlanCode,
        billingPeriod: 'monthly' as BillingPeriod, // TODO Sprint 13 : lire periode du Step0Plan
        contactEmail: profile.email,
        workspaceName: profile.workspaceName,
        accessToken: success.accessToken,
      };
      return (
        <SignupStepPayment
          draft={paymentDraft}
          onBack={() => setStep(4)}
          onPaid={onPaymentDone}
        />
      );
    }
    if (step === 0) {
      return (
        <SignupStep0Plan
          initial={selectedPlan ?? undefined}
          onNext={(p) => { setSelectedPlan(p); setStep(1); }}
        />
      );
    }
    if (step === 1) {
      return (
        <SignupStep1Cabinet
          initial={profile}
          professionalType={professionalType}
          onProfessionalTypeChange={setProfessionalType}
          onNext={(d) => { setProfile(d); setStep(2); }}
          onBack={() => setStep(0)}
        />
      );
    }
    if (step === 2) {
      return (
        <SignupStep3Security
          onBack={() => setStep(1)}
          onNext={() => setStep(3)}
        />
      );
    }
    if (step === 3) {
      return (
        <SignupStep4TwoFactor
          initial={twoFactor}
          onBack={() => setStep(2)}
          onNext={(pref) => { setTwoFactor(pref); setStep(4); }}
        />
      );
    }
    return (
      <SignupStep5Recap
        profile={profile}
        twoFactor={twoFactor}
        selectedPlan={selectedPlan ?? 'essentiel'}
        professionalType={professionalType}
        onBack={() => setStep(3)}
        onSubmit={handleSubmit}
      />
    );
  })();

  return (
    <div className="relative min-h-screen">
      <header className="absolute inset-x-0 top-0 z-10 flex items-center justify-between px-6 py-4 sm:px-10">
        <Link
          to="/"
          className="flex items-center gap-2 transition-opacity hover:opacity-80"
          aria-label="Retour à l'accueil"
        >
          <BrandLogo size={36} />
          <span className="font-heading text-xl font-semibold text-fg">JURIKA</span>
        </Link>
        {step !== 6 && (
          <p className="text-sm text-fg-muted">
            Deja un compte ?{' '}
            <Link to="/login" className="font-medium text-accent hover:underline">
              Se connecter
            </Link>
          </p>
        )}
      </header>
      {/*
        Roadmap hissee dans l'orchestrateur (2026-07-27) : rendue UNE SEULE fois
        pour toutes les etapes (0 -> 6), au-dessus du contenu, avec l'etape
        courante = `step`. Les rendus dupliques a l'interieur des composants
        d'etape ont ete retires. Garantit une roadmap toujours visible et
        coherente (7 etapes en permanence).
      */}
      <div className="px-4 pt-20 sm:pt-24">
        <SignupStepper currentStep={step} />
      </div>
      {content}
    </div>
  );
}
