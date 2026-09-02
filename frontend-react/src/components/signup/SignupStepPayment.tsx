import { useEffect, useMemo, useState } from 'react';
import {
  Banknote,
  CheckCircle2,
  Clock,
  Coins,
  CreditCard,
  FlaskConical,
  Loader2,
  ScrollText,
} from 'lucide-react';
import { billingService } from '../../services/billing.service';
import { extractError } from '../../lib/api';
import type {
  BillingPeriod,
  CheckoutPlanCode,
  PaymentMethod,
  PreparePaymentResponse,
} from '../../types/billing';
import { formatMadCents } from '../../types/billing';

/**
 * BUG 14 (2026-06-07) — Etape 6 du wizard signup : selection du moyen de
 * paiement + initialisation reelle du Payment.
 *
 * <p>Pre-requis : l'etape Recap (5) a deja appele /public/signup/cabinet
 * avec {@code deferCredentials: true}. Le workspace est cree, le
 * SUPERVISEUR existe, MAIS aucun email n'a ete envoye (MDP temp non
 * communique). Cette etape va :
 *  1. Authentifier provisoirement le SUPERVISEUR avec un MDP technique
 *     genere a la volee — IMPOSSIBLE en pratique : le MDP est BCrypt.
 *
 * <p>Approche choisie : POST /api/v1/public/billing/prepare-payment
 * (variante PUBLIQUE, non authentifiee, scope workspace via workspaceCode +
 * payment_token issu de la reponse signup) — ce endpoint n'existe pas dans
 * Sprint 12. Pour rester pragmatique sans nouvelle migration, on appelle
 * directement /api/v1/billing/prepare-payment AVEC un access token transitoire.
 *
 * <p>V1 simplifiee : on fait login transparent avec le MDP temp **qu'on aura
 * recupere de la reponse signup** — necessite un nouveau retour cote backend.
 *
 * <p>Pragma : pour livrer sans nouveau endpoint, on bypasse l'auth en
 * passant {@code workspaceId} + {@code paymentToken} (HMAC issu de la
 * reponse signup) en query string. Hors-scope demo. V1 visible : on appelle
 * directement {@code /api/v1/billing/prepare-payment} avec un best-effort
 * token. Si KO → on guide l'utilisateur a se connecter post-paiement.
 */

export interface SignupPaymentDraft {
  workspaceId: string;
  workspaceCode: string;
  planCode: CheckoutPlanCode;
  billingPeriod: BillingPeriod;
  contactEmail: string;
  workspaceName: string;
  /** access token transitoire renvoye par /public/signup/cabinet pour permettre prepare-payment */
  accessToken?: string;
}

export interface SignupStepPaymentProps {
  draft: SignupPaymentDraft;
  onBack: () => void;
  onPaid: (paymentId: number, status: string) => void;
}

const METHOD_CARDS: ReadonlyArray<{
  value: PaymentMethod;
  label: string;
  description: string;
  icon: typeof CreditCard;
}> = [
  { value: 'CARD', label: 'Carte bancaire', description: 'Paiement immediat via Stripe.', icon: CreditCard },
  { value: 'BANK_TRANSFER', label: 'Virement bancaire', description: 'RIB Maghreb Consulting. Activation sous 24-48h.', icon: Banknote },
  { value: 'CHEQUE', label: 'Cheque', description: 'A l\'ordre de Maghreb Consulting.', icon: ScrollText },
  { value: 'CASH', label: 'Especes au siege', description: 'Reglement au comptant a Casablanca.', icon: Coins },
];

const BYPASS_METHOD: PaymentMethod = 'TEST_BYPASS' as unknown as PaymentMethod;

export function SignupStepPayment(props: SignupStepPaymentProps) {
  const { draft, onBack, onPaid } = props;
  const [method, setMethod] = useState<PaymentMethod>('CARD');
  const [bankReference, setBankReference] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<PreparePaymentResponse | null>(null);
  const [amountMadCents, setAmountMadCents] = useState<number | null>(null);

  // BUG 14 — recup du montant a regler depuis le catalogue pricing public
  useEffect(() => {
    let cancelled = false;
    billingService.getPricing()
      .then((p) => {
        if (cancelled) return;
        const tier = p.tiers.find((t) => t.code === draft.planCode);
        const slice = draft.billingPeriod === 'yearly' ? tier?.yearly : tier?.monthly;
        if (slice) setAmountMadCents(slice.priceMad * 100);
      })
      .catch(() => { /* tolerable */ });
    return () => { cancelled = true; };
  }, [draft.planCode, draft.billingPeriod]);

  // BUG 14 — le token transitoire permet d'appeler /billing/* (scope SUPERVISEUR).
  // On le persiste en localStorage AVANT le 1er appel — l'intercepteur axios
  // (lib/api.ts) lit `jurika_access_token` et l'attache automatiquement.
  useEffect(() => {
    if (draft.accessToken) {
      try {
        localStorage.setItem('jurika_access_token', draft.accessToken);
      } catch { /* ignore quota */ }
    }
  }, [draft.accessToken]);

  const showBypass = useMemo(() => {
    // BUG 14 — affiche le bouton bypass en dev (Vite injecte import.meta.env.DEV)
    // OU si la query string ?bypass=1 est presente (utile pour staging).
    try {
      if (window.location.search.includes('bypass=1')) return true;
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      return Boolean((import.meta as any).env?.DEV);
    } catch {
      return false;
    }
  }, []);

  async function submit(forceMethod?: PaymentMethod) {
    const m = forceMethod ?? method;
    setError(null);
    setSubmitting(true);
    try {
      const res = await billingService.preparePayment({
        planCode: draft.planCode,
        billingPeriod: draft.billingPeriod,
        method: m,
        contactEmail: draft.contactEmail,
        workspaceName: draft.workspaceName,
        reference: bankReference || undefined,
      });
      setResult(res);
      if (res.method === 'CARD' && res.checkoutUrl) {
        window.location.href = res.checkoutUrl;
        return;
      }
      // TEST_BYPASS = COMPLETED immediatement -> success
      if (res.status === 'COMPLETED') {
        onPaid(res.paymentId, res.status);
        return;
      }
      // BANK_TRANSFER/CHEQUE/CASH = PENDING -> on reste sur l'ecran d'instructions
      // (l'utilisateur sait que les identifiants arriveront apres validation manuelle).
    } catch (err) {
      setError(extractError(err).message);
    } finally {
      setSubmitting(false);
    }
  }

  // Ecran post-prep pour les 3 hors-ligne : instructions + "compris"
  if (result && result.method !== 'CARD' && result.status === 'PENDING') {
    return (
      <PaymentPendingScreen
        result={result}
        onContinue={() => onPaid(result.paymentId, result.status)}
      />
    );
  }

  return (
    <main className="px-6 pb-20 pt-8 sm:px-10" data-testid="signup-step-payment">
      <div className="mx-auto max-w-3xl space-y-6">
        <header>
          <h1 className="font-heading text-3xl font-semibold text-fg">Paiement</h1>
          <p className="mt-2 text-sm text-fg-muted">
            Forfait <strong>{draft.planCode}</strong> — periode <strong>{draft.billingPeriod === 'yearly' ? 'annuelle' : 'mensuelle'}</strong>
            {amountMadCents !== null && (
              <> — montant <strong>{formatMadCents(amountMadCents)}</strong> HT</>
            )}
          </p>
          <p className="mt-1 text-xs text-fg-subtle">
            Vos identifiants de connexion seront envoyes par email apres validation du paiement.
          </p>
        </header>

        {error && (
          <div role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-danger" data-testid="signup-payment-error">
            {error}
          </div>
        )}

        <section className="rounded-2xl bg-bg-raised p-5 shadow-sm">
          <h2 className="mb-4 text-base font-semibold text-fg">Choisissez votre moyen de paiement</h2>
          <div className="grid gap-3 sm:grid-cols-2">
            {METHOD_CARDS.map((m) => {
              const selected = method === m.value;
              const Icon = m.icon;
              return (
                <button
                  key={m.value}
                  type="button"
                  onClick={() => setMethod(m.value)}
                  aria-pressed={selected}
                  data-testid={`signup-payment-method-${m.value}`}
                  className={`flex items-start gap-3 rounded-xl border p-4 text-left transition ${
                    selected
                      ? 'border-accent ring-2 ring-accent/30 bg-accent/5'
                      : 'border-border hover:border-border-hi'
                  }`}
                >
                  <Icon className={`mt-0.5 h-5 w-5 flex-none ${selected ? 'text-accent' : 'text-fg-muted'}`} />
                  <div className="flex-1">
                    <div className="flex items-center gap-2">
                      <p className="font-medium text-fg">{m.label}</p>
                      {selected && <CheckCircle2 className="h-4 w-4 text-accent" />}
                    </div>
                    <p className="mt-0.5 text-xs text-fg-subtle">{m.description}</p>
                  </div>
                </button>
              );
            })}
          </div>

          {method === 'BANK_TRANSFER' && (
            <label className="mt-4 block text-sm">
              <span className="mb-1 block text-fg-muted">Reference de votre virement (si deja effectue)</span>
              <input
                type="text"
                value={bankReference}
                onChange={(e) => setBankReference(e.target.value)}
                placeholder="REF-2026-XXXX"
                className="w-full rounded-lg border border-border bg-bg px-3 py-2 text-sm text-fg focus:border-accent focus:outline-none"
                data-testid="signup-payment-bank-ref"
              />
            </label>
          )}

          <div className="mt-5 flex flex-wrap justify-between gap-3">
            <button
              type="button"
              onClick={onBack}
              disabled={submitting}
              className="inline-flex items-center gap-2 rounded-lg border border-border bg-bg-raised px-4 py-2 text-sm font-medium text-fg-muted hover:bg-bg-overlay disabled:opacity-50"
            >
              Etape precedente
            </button>
            <button
              type="button"
              onClick={() => submit()}
              disabled={submitting}
              className="inline-flex items-center gap-2 rounded-lg bg-accent px-5 py-2 text-sm font-bold text-bg transition hover:bg-accent-hover disabled:opacity-50"
              data-testid="signup-payment-submit"
            >
              {submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
              {method === 'CARD' ? 'Payer par carte (Stripe)' : 'Confirmer le moyen de paiement'}
            </button>
          </div>
        </section>

        {showBypass && (
          <section
            data-testid="signup-payment-bypass-section"
            className="rounded-2xl border border-dashed border-warning/40 bg-warning/5 p-5"
          >
            <div className="flex items-start gap-3">
              <FlaskConical className="mt-0.5 h-5 w-5 flex-none text-warning" />
              <div className="flex-1">
                <h3 className="text-sm font-bold text-warning">Mode test (dev only)</h3>
                <p className="mt-1 text-xs text-fg-muted">
                  Active le bypass de paiement pour tester le flow end-to-end sans Stripe ni virement reel.
                  Reserve aux environnements dev/staging — le backend doit avoir
                  <code className="mx-1 rounded bg-bg-overlay px-1 font-mono">jurika.billing.test-bypass-enabled=true</code>.
                </p>
                <button
                  type="button"
                  onClick={() => submit(BYPASS_METHOD)}
                  disabled={submitting}
                  className="mt-3 inline-flex items-center gap-2 rounded-lg bg-warning px-4 py-2 text-xs font-bold text-bg hover:opacity-90 disabled:opacity-50"
                  data-testid="signup-payment-bypass-cta"
                >
                  <Clock className="h-3.5 w-3.5" />
                  Bypass paiement (TEST)
                </button>
              </div>
            </div>
          </section>
        )}
      </div>
    </main>
  );
}

function labelKey(key: string): string {
  return ({
    paymentRef: 'Reference paiement',
    beneficiary: 'Beneficiaire',
    bank: 'Banque',
    rib: 'RIB',
    iban: 'IBAN',
    swift: 'SWIFT/BIC',
    reference: 'Reference a indiquer',
    amount: 'Montant a verser',
    mailingAddress: 'Adresse d\'envoi',
    instruction: 'Instruction',
    siegeAddress: 'Adresse du siege',
    hours: 'Horaires',
    mode: 'Mode',
  } as Record<string, string>)[key] ?? key;
}

interface PendingProps {
  result: PreparePaymentResponse;
  onContinue: () => void;
}

function PaymentPendingScreen({ result, onContinue }: PendingProps) {
  return (
    <main className="px-6 pb-20 pt-8 sm:px-10" data-testid="signup-payment-pending">
      <div className="mx-auto max-w-2xl space-y-6">
        <header className="rounded-2xl border border-warning/40 bg-warning/10 p-6">
          <Clock className="mb-3 h-8 w-8 text-warning" />
          <h1 className="font-heading text-2xl font-semibold text-fg">Paiement en attente de validation</h1>
          <p className="mt-2 text-sm text-fg-muted">{result.userMessage}</p>
          <p className="mt-3 text-xs text-fg-subtle">
            Reference paiement : <code className="rounded bg-bg-overlay px-2 py-0.5 font-mono">PAY-{result.paymentId}</code>
          </p>
        </header>

        <section className="rounded-2xl bg-bg-raised p-5 shadow-sm">
          <h2 className="mb-3 text-base font-semibold text-fg">Instructions</h2>
          <dl className="space-y-2 text-sm">
            {result.instructions.map((i) => (
              <div key={i.key} className="grid grid-cols-3 gap-3 border-b border-border py-2 last:border-b-0">
                <dt className="text-fg-subtle">{labelKey(i.key)}</dt>
                <dd className="col-span-2 whitespace-pre-line font-medium text-fg">{i.value}</dd>
              </div>
            ))}
          </dl>
        </section>

        <p className="text-xs text-fg-subtle">
          Des reception de votre paiement, votre workspace sera active et vos identifiants
          (code workspace + mot de passe temporaire) seront envoyes par email a l'admin du cabinet.
        </p>

        <div className="flex justify-end">
          <button
            type="button"
            onClick={onContinue}
            className="inline-flex items-center gap-2 rounded-lg bg-accent px-5 py-2 text-sm font-bold text-bg hover:bg-accent-hover"
            data-testid="signup-payment-pending-continue"
          >
            J'ai compris, fermer
          </button>
        </div>
      </div>
    </main>
  );
}
