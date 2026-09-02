import { useEffect, useMemo, useState } from 'react';
import { CheckCircle2, ExternalLink, Loader2, Mail } from 'lucide-react';
import { billingService } from '../../services/billing.service';
import type {
  BillingPeriod,
  CheckoutPlanCode,
  CheckoutSessionRequest,
  ContactSalesRequest,
  PlanCode,
  PricingTier,
} from '../../types/billing';

/**
 * Sprint 12 + Sprint Beta (pricing-deploy) — composant de selection de plan.
 *
 * <p>3 cards Essentiel / Business / Entreprise + toggle Mensuel / Annuel
 * (spec directeur 2026-06-02). Source : GET /api/v1/public/pricing
 * (auth-service lit PlanCatalog). Affiche les quotas (maxUsers,
 * maxDossiers, maxStorageGb) en plus des features marketing.
 *
 *  - Essentiel / Business -> POST /billing/checkout-session (Stripe RG-BL03)
 *  - Entreprise -> formulaire contact-sales (RG-BL10)
 *
 * <p>Ribbon "Recommande" et "featured" pris depuis le catalogue. Si l'API
 * tombe, fallback statique aligne sur la grille canonique.
 */
export interface PlanPickerProps {
  workspaceName: string;
  contactEmail: string;
  onCheckoutStart?: (planCode: PlanCode, period: BillingPeriod) => void;
  onEnterpriseRequested?: () => void;
  /** Force la periode au mount (sinon defaut mensuel + toggle libre). */
  defaultPeriod?: BillingPeriod;
  /**
   * BUG 14 (2026-06-07) — si fourni, REMPLACE le flux Stripe checkout par
   * un callback custom (ex. navigate vers /app/billing/payment qui propose
   * les 4 moyens). Le callback peut etre async ; le PlanPicker l'attend
   * avant de relacher le loading state.
   */
  onPickPlan?: (planCode: CheckoutPlanCode, period: BillingPeriod) => void | Promise<void>;
}

const FALLBACK_TIERS: PricingTier[] = [
  {
    id: 'essentiel', code: 'essentiel', label: 'Essentiel',
    target: 'Petites structures juridiques',
    currency: 'MAD', price: 499, annualPrice: 4999, period: 'mois',
    monthly: { priceMad: 499, stripeLookupKey: 'essentiel_monthly' },
    yearly: { priceMad: 4999, stripeLookupKey: 'essentiel_yearly' },
    quotas: { maxUsers: 2, maxDossiers: 30, maxStorageGb: 5,
      usersUnlimited: false, dossiersUnlimited: false, storageUnlimited: false },
    features: [
      '2 utilisateurs',
      '30 dossiers actifs',
      'Tous les workflows',
      'Data Room 5 Go',
      'Chat client-employe',
      'Generation de documents par IA',
    ],
    featured: false, quoteOnly: false,
  },
  {
    id: 'business', code: 'business', label: 'Business',
    target: 'Cabinets en croissance',
    currency: 'MAD', price: 1199, annualPrice: 11999, period: 'mois',
    monthly: { priceMad: 1199, stripeLookupKey: 'business_monthly' },
    yearly: { priceMad: 11999, stripeLookupKey: 'business_yearly' },
    quotas: { maxUsers: 6, maxDossiers: 100, maxStorageGb: 20,
      usersUnlimited: false, dossiersUnlimited: false, storageUnlimited: false },
    features: [
      '6 utilisateurs',
      '100 dossiers actifs',
      'Tous les workflows',
      'Data Room 20 Go',
      'Chat client-employe',
      'Generation de documents par IA',
      'Chatbot RAG (assistant juridique)',
    ],
    featured: true, ribbon: 'Recommande', quoteOnly: false,
  },
  {
    id: 'entreprise', code: 'entreprise', label: 'Entreprise',
    target: 'Reseaux & cabinets multi-sites',
    currency: 'MAD', price: null, annualPrice: null, period: 'mois',
    quotas: { maxUsers: -1, maxDossiers: -1, maxStorageGb: -1,
      usersUnlimited: true, dossiersUnlimited: true, storageUnlimited: true },
    features: [
      'Utilisateurs illimites',
      'Plus de 100 dossiers',
      'Tous les workflows',
      'Data Room sur mesure',
      'Chat client-employe',
      'Generation de documents par IA',
      'Chatbot RAG',
      'Accompagnement dedie',
    ],
    featured: false, quoteOnly: true,
  },
];

function formatMad(value: number): string {
  return new Intl.NumberFormat('fr-FR').format(value);
}

/** Lit la periode pre-selectionnee au signup (sessionStorage), sinon defaut. */
function readSignupPeriod(): BillingPeriod | null {
  try {
    const v = sessionStorage.getItem('jurika.signup.billingPeriod');
    return v === 'monthly' || v === 'yearly' ? v : null;
  } catch {
    return null;
  }
}

export function PlanPicker(props: PlanPickerProps) {
  const [tiers, setTiers] = useState<PricingTier[]>(FALLBACK_TIERS);
  // Priorite : prop explicite > choix memorise au wizard signup > defaut mensuel.
  const [period, setPeriod] = useState<BillingPeriod>(
    () => props.defaultPeriod ?? readSignupPeriod() ?? 'monthly'
  );
  const [loading, setLoading] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  // Charge le catalogue depuis l'API au mount.
  useEffect(() => {
    let cancelled = false;
    billingService
      .getPricing()
      .then((res) => {
        if (!cancelled && res?.tiers?.length) setTiers(res.tiers);
      })
      .catch(() => {
        // Pas de toast : on garde le fallback statique. L'erreur reseau
        // est tolerable pour cet ecran (catalogue purement marketing).
      });
    return () => { cancelled = true; };
  }, []);

  const savings = useMemo(() => {
    const biz = tiers.find((t) => t.code === 'business');
    if (!biz?.monthly || !biz?.yearly) return null;
    const yearlyFullPrice = biz.monthly.priceMad * 12;
    const pct = Math.round((1 - biz.yearly.priceMad / yearlyFullPrice) * 100);
    return pct > 0 ? pct : null;
  }, [tiers]);

  async function onPick(tier: PricingTier) {
    if (tier.quoteOnly) {
      props.onEnterpriseRequested?.();
      return;
    }
    const planCode = tier.code as CheckoutPlanCode;
    setError(null);
    setLoading(planCode);
    props.onCheckoutStart?.(planCode, period);

    // BUG 14 (2026-06-07) — si l'host fournit onPickPlan (cas BillingPage qui
    // route vers /app/billing/payment), on lui delegue COMPLETEMENT le
    // pick et on n'appelle PAS createCheckoutSession (le checkout Stripe
    // sera initie depuis la PaymentPage si l'utilisateur choisit CARD).
    if (props.onPickPlan) {
      try {
        await props.onPickPlan(planCode, period);
      } catch (err) {
        const msg = (err as Error)?.message ?? 'Erreur lors de la selection du plan';
        setError(msg);
      } finally {
        setLoading(null);
      }
      return;
    }

    try {
      const req: CheckoutSessionRequest = {
        planCode,
        billingPeriod: period,
        contactEmail: props.contactEmail,
        workspaceName: props.workspaceName,
      };
      const res = await billingService.createCheckoutSession(req);
      window.location.href = res.checkoutUrl;
    } catch (err) {
      const msg = (err as Error)?.message ?? 'Erreur lors de la creation du checkout';
      setError(msg);
      setLoading(null);
    }
  }

  function priceLabelFor(tier: PricingTier): { main: string; sub: string } {
    if (tier.quoteOnly) return { main: 'Sur devis', sub: 'Tarif personnalise' };
    const slice = period === 'yearly' ? tier.yearly : tier.monthly;
    if (!slice) return { main: '—', sub: '' };
    return period === 'yearly'
      ? { main: `${formatMad(slice.priceMad)} MAD`, sub: '/ an HT' }
      : { main: `${formatMad(slice.priceMad)} MAD`, sub: '/ mois HT' };
  }

  function quotasLine(tier: PricingTier): string {
    const q = tier.quotas;
    const users = q.usersUnlimited ? 'Utilisateurs illimites' : `${q.maxUsers} utilisateurs`;
    const dossiers = q.dossiersUnlimited
      ? 'Dossiers illimites'
      : tier.code === 'entreprise'
        ? '> 100 dossiers'
        : `${q.maxDossiers} dossiers`;
    const storage = q.storageUnlimited ? 'Stockage sur mesure' : `${q.maxStorageGb} Go`;
    return `${users} · ${dossiers} · ${storage}`;
  }

  return (
    <div className="space-y-4" data-testid="plan-picker">
      {error && (
        <div className="rounded-lg border border-danger/50 bg-danger/10 p-3 text-sm text-danger" role="alert">
          {error}
        </div>
      )}

      {/* Toggle Mensuel / Annuel */}
      <div className="flex items-center justify-center gap-3" data-testid="period-toggle">
        <button
          type="button"
          onClick={() => setPeriod('monthly')}
          aria-pressed={period === 'monthly'}
          className={`rounded-full px-4 py-1.5 text-sm font-medium transition ${
            period === 'monthly'
              ? 'bg-fg text-bg shadow-card'
              : 'bg-bg-raised text-fg-muted border border-border hover:text-fg'
          }`}
          data-testid="period-monthly"
        >
          Mensuel
        </button>
        <button
          type="button"
          onClick={() => setPeriod('yearly')}
          aria-pressed={period === 'yearly'}
          className={`relative rounded-full px-4 py-1.5 text-sm font-medium transition ${
            period === 'yearly'
              ? 'bg-fg text-bg shadow-card'
              : 'bg-bg-raised text-fg-muted border border-border hover:text-fg'
          }`}
          data-testid="period-yearly"
        >
          Annuel
          {savings !== null && (
            <span className="ml-2 inline-flex items-center rounded-full bg-success/20 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider text-success">
              -{savings}%
            </span>
          )}
        </button>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        {tiers.map((tier) => {
          const labels = priceLabelFor(tier);
          return (
            <article
              key={tier.code}
              data-testid={`plan-${tier.code}`}
              className={`relative flex flex-col rounded-2xl border bg-bg-raised p-5 shadow-card ${
                tier.featured ? 'border-accent ring-2 ring-accent/30' : 'border-border'
              }`}
            >
              <header className="mb-3 flex items-center justify-between">
                <h3 className="font-heading text-lg font-semibold text-fg">{tier.label}</h3>
                {tier.featured && tier.ribbon && (
                  <span className="rounded-full bg-gradient-to-r from-accent to-accent-hover px-2.5 py-0.5 text-xs font-bold uppercase tracking-wider text-bg shadow-card">
                    {tier.ribbon}
                  </span>
                )}
              </header>
              <p className="text-xs uppercase tracking-wider text-fg-muted">{tier.target}</p>

              <div className="my-3">
                <p className="font-heading text-2xl font-semibold text-fg">{labels.main}</p>
                {labels.sub && <p className="text-xs text-fg-muted">{labels.sub}</p>}
              </div>

              <p
                className="rounded-lg bg-bg-overlay px-3 py-2 text-xs font-medium text-fg-muted"
                data-testid={`plan-${tier.code}-quotas`}
              >
                {quotasLine(tier)}
              </p>

              <ul className="mt-3 flex-1 space-y-1 text-sm text-fg-muted">
                {tier.features.map((f) => (
                  <li key={f} className="flex items-start gap-2">
                    <CheckCircle2 className="mt-0.5 h-4 w-4 flex-none text-success" />
                    <span>{f}</span>
                  </li>
                ))}
              </ul>

              {tier.quoteOnly ? (
                <button
                  type="button"
                  onClick={() => props.onEnterpriseRequested?.()}
                  className="mt-4 inline-flex w-full items-center justify-center gap-2 rounded-lg border border-border-hi bg-bg-raised px-4 py-2 text-sm font-medium text-fg-muted hover:bg-bg-overlay"
                  data-testid={`plan-${tier.code}-contact`}
                >
                  <Mail className="h-4 w-4" /> Contacter les ventes
                </button>
              ) : (
                <button
                  type="button"
                  disabled={loading !== null}
                  onClick={() => onPick(tier)}
                  className="mt-4 inline-flex w-full items-center justify-center gap-2 rounded-lg bg-accent px-4 py-2 text-sm font-medium text-bg hover:bg-accent-hover disabled:opacity-50"
                  data-testid={`plan-${tier.code}-cta`}
                >
                  {loading === tier.code ? (
                    <Loader2 className="h-4 w-4 animate-spin" />
                  ) : (
                    <ExternalLink className="h-4 w-4" />
                  )}
                  {loading === tier.code
                    ? 'Redirection Stripe...'
                    : `Choisir ${tier.label}`}
                </button>
              )}
            </article>
          );
        })}
      </div>
    </div>
  );
}

export async function submitContactSales(req: ContactSalesRequest) {
  return billingService.contactSales(req);
}
