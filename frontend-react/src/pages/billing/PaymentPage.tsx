import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import {
  ArrowLeft,
  Banknote,
  CheckCircle2,
  Clock,
  Coins,
  CreditCard,
  Loader2,
  ScrollText,
} from 'lucide-react';
import { billingService } from '../../services/billing.service';
import { useAuthStore } from '../../store/authStore';
import type {
  BillingPeriod,
  CheckoutPlanCode,
  PaymentMethod,
  PreparePaymentResponse,
  PricingTier,
} from '../../types/billing';
import { formatMadCents } from '../../types/billing';

/**
 * BUG 14 (2026-06-07) — page de paiement multi-moyens entre le choix du
 * forfait et l'activation des identifiants.
 *
 * <p>Route : {@code /app/billing/payment?plan=...&period=...}
 *
 * <p>Flux :
 *  - CARD : POST /prepare-payment -> redirect vers checkoutUrl Stripe
 *  - BANK_TRANSFER : affiche RIB + reference unique + bouton "J'ai vire"
 *  - CHEQUE : affiche ordre + adresse postale + bouton "J'ai envoye"
 *  - CASH : affiche adresse siege + heures + bouton "Je passerai au siege"
 *
 * <p>Pour les 3 hors-ligne : la page se referme sur un ecran "En attente de
 * validation" — les identifiants ne seront emis que apres PATCH /validate
 * par le back-office.
 */
export function PaymentPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);

  const planCode = (searchParams.get('plan') ?? 'essentiel') as CheckoutPlanCode;
  const period = (searchParams.get('period') ?? 'monthly') as BillingPeriod;

  const [tier, setTier] = useState<PricingTier | null>(null);
  const [method, setMethod] = useState<PaymentMethod>('CARD');
  const [bankReference, setBankReference] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<PreparePaymentResponse | null>(null);

  useEffect(() => {
    let cancelled = false;
    billingService.getPricing()
      .then((p) => {
        if (cancelled) return;
        setTier(p.tiers.find((t) => t.code === planCode) ?? null);
      })
      .catch(() => { /* fallback handled in render */ });
    return () => { cancelled = true; };
  }, [planCode]);

  const amountMadCents = useMemo(() => {
    if (!tier) return null;
    const slice = period === 'yearly' ? tier.yearly : tier.monthly;
    return slice ? slice.priceMad * 100 : null;
  }, [tier, period]);

  async function submit() {
    if (!user) {
      setError('Session expiree — reconnectez-vous.');
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      const res = await billingService.preparePayment({
        planCode,
        billingPeriod: period,
        method,
        contactEmail: user.email,
        workspaceName: `Cabinet ${user.email}`,
        reference: bankReference || undefined,
      });
      setResult(res);
      if (res.method === 'CARD' && res.checkoutUrl) {
        // Redirection vers le checkout Stripe.
        window.location.href = res.checkoutUrl;
      }
    } catch (err) {
      const e = err as { response?: { data?: { message?: string } }; message?: string };
      setError(e?.response?.data?.message ?? e?.message ?? 'Erreur lors de la preparation du paiement');
    } finally {
      setSubmitting(false);
    }
  }

  // Confirmation post-preparation pour les 3 hors-ligne.
  if (result && result.method !== 'CARD') {
    return (
      <div className="space-y-6 max-w-2xl" data-testid="payment-pending-screen">
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
          Vos identifiants de connexion (code workspace + mot de passe temporaire) vous seront envoyes par email
          des que ce paiement aura ete valide par notre equipe.
        </p>

        <div className="flex gap-3">
          <Link to="/app/billing" className="inline-flex items-center gap-2 rounded-lg border border-border bg-bg-raised px-4 py-2 text-sm font-medium text-fg-muted hover:bg-bg-overlay">
            Retour facturation
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6 max-w-3xl" data-testid="payment-page">
      <header className="rounded-2xl bg-bg-raised p-6 shadow-sm">
        <button
          type="button"
          onClick={() => navigate(-1)}
          className="mb-3 inline-flex items-center gap-1 text-xs text-fg-subtle hover:text-fg"
        >
          <ArrowLeft className="h-3 w-3" /> Etape precedente
        </button>
        <h1 className="font-heading text-2xl font-semibold text-fg">Paiement</h1>
        <p className="mt-1 text-sm text-fg-muted">
          Forfait <strong>{tier?.label ?? planCode}</strong> — periode <strong>{period === 'yearly' ? 'annuelle' : 'mensuelle'}</strong>
          {amountMadCents !== null && (
            <> — montant <strong>{formatMadCents(amountMadCents)}</strong> HT</>
          )}
        </p>
      </header>

      {error && (
        <div role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-danger" data-testid="payment-error">
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
                className={`flex items-start gap-3 rounded-xl border p-4 text-left transition ${
                  selected
                    ? 'border-accent ring-2 ring-accent/30 bg-accent/5'
                    : 'border-border hover:border-border-hi'
                }`}
                data-testid={`payment-method-${m.value}`}
                aria-pressed={selected}
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

        {/* Champ optionnel reference pour BANK_TRANSFER */}
        {method === 'BANK_TRANSFER' && (
          <label className="mt-4 block text-sm">
            <span className="mb-1 block text-fg-muted">Reference de votre virement (si deja effectue)</span>
            <input
              type="text"
              value={bankReference}
              onChange={(e) => setBankReference(e.target.value)}
              placeholder="REF-2026-XXXX"
              className="w-full rounded-lg border border-border bg-bg px-3 py-2 text-sm text-fg focus:border-accent focus:outline-none"
              data-testid="payment-bank-reference"
            />
          </label>
        )}

        <div className="mt-5 flex justify-end gap-3">
          <Link to="/app/billing/change-plan" className="inline-flex items-center gap-2 rounded-lg border border-border bg-bg-raised px-4 py-2 text-sm font-medium text-fg-muted hover:bg-bg-overlay">
            Changer de forfait
          </Link>
          <button
            type="button"
            onClick={submit}
            disabled={submitting}
            className="inline-flex items-center gap-2 rounded-lg bg-accent px-5 py-2 text-sm font-bold text-bg transition hover:bg-accent-hover disabled:opacity-50"
            data-testid="payment-submit"
          >
            {submitting ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
            {method === 'CARD' ? 'Payer par carte (Stripe)' : 'Confirmer le moyen de paiement'}
          </button>
        </div>
      </section>

      <p className="text-xs text-fg-subtle">
        Paiement securise. Les paiements hors-ligne (virement / cheque / especes) sont valides manuellement par
        notre equipe sous 24-48h.
      </p>
    </div>
  );
}

const METHOD_CARDS: ReadonlyArray<{
  value: PaymentMethod;
  label: string;
  description: string;
  icon: typeof CreditCard;
}> = [
  { value: 'CARD', label: 'Carte bancaire', description: 'Paiement immediat via Stripe (Visa, Mastercard, CIH).', icon: CreditCard },
  { value: 'BANK_TRANSFER', label: 'Virement bancaire', description: 'RIB Maghreb Consulting. Activation sous 24-48h.', icon: Banknote },
  { value: 'CHEQUE', label: 'Cheque', description: 'A l\'ordre de Maghreb Consulting, envoye au siege.', icon: ScrollText },
  { value: 'CASH', label: 'Especes au siege', description: 'Reglement au comptant a Casablanca.', icon: Coins },
];

function labelKey(key: string): string {
  return ({
    checkoutUrl: 'URL de paiement',
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
  } as Record<string, string>)[key] ?? key;
}
