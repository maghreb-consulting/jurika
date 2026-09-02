import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { AlertTriangle, ArrowLeft, Banknote, CheckCircle2, ExternalLink, Loader2, Mail } from 'lucide-react';
import { billingService } from '../../services/billing.service';
import { useAuthStore } from '../../store/authStore';
import type {
  BillingPeriod,
  ChangePlanPreviewResponse,
  PlanCode,
  PricingTier,
  SubscriptionDto,
} from '../../types/billing';

/**
 * BUG 8 (2026-06-07) — page "Changer de forfait" pour un workspace deja
 * abonne.
 *
 * <p>Difference avec {@code PlanPicker} :
 *  - marque le plan courant ("Plan actuel")
 *  - autorise upgrade ET downgrade
 *  - prefetch un preview a chaque survol pour afficher proration / blocage
 *  - le bouton "Confirmer" appelle POST /change-plan (et NON /checkout-session)
 *  - Entreprise -> mailto:contact@jurika.ai (RG-BL10)
 */
export function ChangePlanPage() {
  const user = useAuthStore((s) => s.user);
  const navigate = useNavigate();
  const [subscription, setSubscription] = useState<SubscriptionDto | null>(null);
  const [tiers, setTiers] = useState<PricingTier[]>([]);
  const [period, setPeriod] = useState<BillingPeriod>('monthly');
  const [previews, setPreviews] = useState<Record<string, ChangePlanPreviewResponse>>({});
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState<PlanCode | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [sub, pricing] = await Promise.all([
          billingService.getSubscription(),
          billingService.getPricing(),
        ]);
        if (cancelled) return;
        setSubscription(sub);
        setTiers(pricing.tiers);
        if (sub?.planCode === 'business') setPeriod('monthly');
      } catch (err) {
        if (!cancelled) setError((err as Error)?.message ?? 'Erreur de chargement');
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, []);

  const currentPlanCode = subscription?.planCode ?? null;

  // Prefetch previews pour chaque target plan != courant.
  useEffect(() => {
    if (!subscription) return;
    let cancelled = false;
    const fetchPreviews = async () => {
      const targets = tiers
        .filter((t) => !t.quoteOnly)
        .filter((t) => t.code !== subscription.planCode);
      const results = await Promise.all(targets.map(async (t) => {
        try {
          const p = await billingService.previewChangePlan({
            targetPlanCode: t.code as PlanCode,
            billingPeriod: period,
          });
          return [t.code, p] as const;
        } catch {
          return null;
        }
      }));
      if (cancelled) return;
      const map: Record<string, ChangePlanPreviewResponse> = {};
      for (const r of results) {
        if (r) map[r[0]] = r[1];
      }
      setPreviews(map);
    };
    void fetchPreviews();
    return () => { cancelled = true; };
  }, [subscription, tiers, period]);

  const sortedTiers = useMemo(() => {
    const rank: Record<string, number> = { essentiel: 0, business: 1, entreprise: 2 };
    return [...tiers].sort((a, b) => (rank[a.code] ?? 99) - (rank[b.code] ?? 99));
  }, [tiers]);

  async function confirmChange(tier: PricingTier) {
    if (tier.quoteOnly) {
      window.location.href = `mailto:contact@jurika.ai?subject=Plan Entreprise — ${encodeURIComponent(user?.email ?? '')}`;
      return;
    }
    setError(null);
    setSuccess(null);
    setSubmitting(tier.code as PlanCode);
    try {
      const res = await billingService.changePlan({
        targetPlanCode: tier.code as PlanCode,
        billingPeriod: period,
      });
      setSuccess(`Forfait change : ${res.previousPlanCode} → ${res.newPlanCode} (${res.billingPeriod}). Proration appliquee.`);
      // Refresh subscription apres 1s pour reflet visuel.
      setTimeout(async () => {
        try {
          const s = await billingService.getSubscription();
          setSubscription(s);
        } catch { /* silent */ }
      }, 800);
    } catch (err) {
      const e = err as { response?: { data?: { message?: string; code?: string } }; message?: string };
      setError(e?.response?.data?.message ?? e?.message ?? 'Echec du changement de plan');
    } finally {
      setSubmitting(null);
    }
  }

  if (loading) {
    return (
      <div className="flex h-64 items-center justify-center" data-testid="change-plan-loading">
        <Loader2 className="h-6 w-6 animate-spin text-accent" />
      </div>
    );
  }

  // BUG 14 (2026-06-07) — sans abonnement actif, on route directement vers
  // /app/billing qui propose le selecteur de plan + PaymentPage multi-moyens.
  if (!subscription) {
    return (
      <div className="space-y-4" data-testid="change-plan-no-sub">
        <p className="rounded-lg border border-warning/40 bg-warning/10 p-3 text-sm text-warning">
          Vous n'avez pas encore d'abonnement actif. Choisissez un forfait depuis l'ecran principal,
          puis selectionnez votre moyen de paiement (carte, virement, cheque, especes).
        </p>
        <Link
          to="/app/billing"
          className="inline-flex items-center gap-2 rounded-lg bg-accent px-4 py-2 text-sm font-medium text-bg hover:bg-accent-hover"
        >
          Choisir un forfait
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-6" data-testid="change-plan-page">
      <header className="rounded-2xl bg-bg-raised p-6 shadow-sm">
        <Link to="/app/billing" className="mb-3 inline-flex items-center gap-1 text-xs text-fg-subtle hover:text-fg">
          <ArrowLeft className="h-3 w-3" /> Retour
        </Link>
        <h1 className="font-heading text-2xl font-semibold text-fg">Changer de forfait</h1>
        <p className="mt-1 text-sm text-fg-muted">
          Vous etes actuellement sur le forfait <strong>{currentPlanCode}</strong>. Selectionnez
          un nouveau plan ci-dessous. Les changements (upgrade/downgrade) sont appliques
          immediatement avec proration au prorata des jours restants.
        </p>
      </header>

      {error && (
        <div role="alert" className="rounded-lg border border-danger/40 bg-danger/10 p-3 text-sm text-danger" data-testid="change-plan-error">
          {error}
        </div>
      )}
      {success && (
        <div role="status" className="rounded-lg border border-success/40 bg-success/10 p-3 text-sm text-success" data-testid="change-plan-success">
          {success}
        </div>
      )}

      {/* Toggle Mensuel / Annuel */}
      <div className="flex items-center justify-center gap-3">
        {(['monthly', 'yearly'] as const).map((p) => (
          <button
            key={p}
            type="button"
            onClick={() => setPeriod(p)}
            className={`rounded-full px-4 py-1.5 text-sm font-medium transition ${
              period === p
                ? 'bg-fg text-bg shadow-card'
                : 'bg-bg-raised text-fg-muted border border-border hover:text-fg'
            }`}
            data-testid={`change-plan-period-${p}`}
          >
            {p === 'monthly' ? 'Mensuel' : 'Annuel'}
          </button>
        ))}
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        {sortedTiers.map((tier) => {
          const isCurrent = tier.code === currentPlanCode;
          const preview = previews[tier.code];
          const blocked = preview && preview.isDowngrade && !preview.downgradeAllowed;
          const slice = period === 'yearly' ? tier.yearly : tier.monthly;
          return (
            <article
              key={tier.code}
              data-testid={`change-plan-${tier.code}`}
              className={`relative flex flex-col rounded-2xl border bg-bg-raised p-5 shadow-card ${
                isCurrent ? 'border-success ring-2 ring-success/30' :
                tier.featured ? 'border-accent ring-2 ring-accent/30' : 'border-border'
              }`}
            >
              <header className="mb-3 flex items-center justify-between gap-2">
                <h3 className="font-heading text-lg font-semibold text-fg">{tier.label}</h3>
                {isCurrent ? (
                  <span className="rounded-full bg-success/20 px-2.5 py-0.5 text-xs font-bold uppercase tracking-wider text-success">
                    Plan actuel
                  </span>
                ) : tier.featured && tier.ribbon ? (
                  <span className="rounded-full bg-gradient-to-r from-accent to-accent-hover px-2.5 py-0.5 text-xs font-bold uppercase tracking-wider text-bg shadow-card">
                    {tier.ribbon}
                  </span>
                ) : null}
              </header>
              <p className="text-xs uppercase tracking-wider text-fg-muted">{tier.target}</p>

              <div className="my-3">
                {tier.quoteOnly ? (
                  <p className="font-heading text-2xl font-semibold text-fg">Sur devis</p>
                ) : slice ? (
                  <>
                    <p className="font-heading text-2xl font-semibold text-fg">
                      {new Intl.NumberFormat('fr-FR').format(slice.priceMad)} MAD
                    </p>
                    <p className="text-xs text-fg-muted">/ {period === 'yearly' ? 'an' : 'mois'} HT</p>
                  </>
                ) : null}
              </div>

              {/* Preview info */}
              {preview && !isCurrent && (
                <p className="mb-2 rounded-lg bg-bg-overlay px-3 py-2 text-xs font-medium text-fg-muted" data-testid={`change-plan-${tier.code}-preview`}>
                  {preview.isUpgrade && '⬆ Upgrade — proration calculee par Stripe a la confirmation.'}
                  {preview.isDowngrade && preview.downgradeAllowed && '⬇ Downgrade — credit prorata genere.'}
                  {blocked && (
                    <span className="flex items-start gap-1.5 text-warning">
                      <AlertTriangle className="mt-0.5 h-3.5 w-3.5 flex-none" />
                      {preview.blockedReason}
                    </span>
                  )}
                </p>
              )}

              <ul className="mt-3 flex-1 space-y-1 text-sm text-fg-muted">
                {tier.features.map((f) => (
                  <li key={f} className="flex items-start gap-2">
                    <CheckCircle2 className="mt-0.5 h-4 w-4 flex-none text-success" />
                    <span>{f}</span>
                  </li>
                ))}
              </ul>

              {isCurrent ? (
                <button
                  type="button"
                  disabled
                  className="mt-4 inline-flex w-full items-center justify-center gap-2 rounded-lg bg-bg-overlay px-4 py-2 text-sm font-medium text-fg-subtle"
                  data-testid={`change-plan-${tier.code}-current`}
                >
                  Plan actuel
                </button>
              ) : tier.quoteOnly ? (
                <button
                  type="button"
                  onClick={() => confirmChange(tier)}
                  className="mt-4 inline-flex w-full items-center justify-center gap-2 rounded-lg border border-border-hi bg-bg-raised px-4 py-2 text-sm font-medium text-fg-muted hover:bg-bg-overlay"
                  data-testid={`change-plan-${tier.code}-contact`}
                >
                  <Mail className="h-4 w-4" /> Contacter les ventes
                </button>
              ) : (
                <div className="mt-4 space-y-2">
                  <button
                    type="button"
                    disabled={submitting !== null || blocked}
                    onClick={() => confirmChange(tier)}
                    className="inline-flex w-full items-center justify-center gap-2 rounded-lg bg-accent px-4 py-2 text-sm font-medium text-bg hover:bg-accent-hover disabled:opacity-50"
                    data-testid={`change-plan-${tier.code}-cta`}
                  >
                    {submitting === tier.code ? (
                      <Loader2 className="h-4 w-4 animate-spin" />
                    ) : (
                      <ExternalLink className="h-4 w-4" />
                    )}
                    {submitting === tier.code
                      ? 'Application en cours...'
                      : blocked ? 'Downgrade bloque' : `Passer a ${tier.label} (CB existante)`}
                  </button>
                  {/* BUG 14 — alternative : route vers PaymentPage pour
                      payer ce nouveau plan par un autre moyen (virement, cheque,
                      especes) ou par une nouvelle CB. Visible meme quand le
                      downgrade rapide via Stripe est possible. */}
                  <button
                    type="button"
                    disabled={blocked}
                    onClick={() => navigate(`/app/billing/payment?plan=${encodeURIComponent(tier.code)}&period=${encodeURIComponent(period)}`)}
                    className="inline-flex w-full items-center justify-center gap-2 rounded-lg border border-border bg-bg-raised px-4 py-2 text-xs font-medium text-fg-muted hover:bg-bg-overlay disabled:opacity-50"
                    data-testid={`change-plan-${tier.code}-payment-page`}
                  >
                    <Banknote className="h-3.5 w-3.5" /> Payer par autre moyen
                  </button>
                </div>
              )}
            </article>
          );
        })}
      </div>
    </div>
  );
}
