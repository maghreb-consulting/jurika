import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { CreditCard, Download, ExternalLink, Loader2, Receipt, Sparkles } from 'lucide-react';
import { billingService } from '../../services/billing.service';

/** UX-3 : formatter de date pour les renouvellements / factures Stripe. */
function formatDate(iso: string | null | undefined): string {
  if (!iso) return '-';
  try {
    return new Intl.DateTimeFormat('fr-MA', { day: '2-digit', month: 'long', year: 'numeric' }).format(new Date(iso));
  } catch {
    return iso;
  }
}
import { emitBusinessEvent } from '../../services/analytics.service';
import { PlanPicker } from '../../components/billing/PlanPicker';
import { formatMadCents, type InvoiceDto, type SubscriptionDto } from '../../types/billing';

/**
 * Sprint 12 — page /app/billing rewrite complete (RG-BL01..10).
 *
 * <p>Modes :
 *  - Aucune souscription (404) -> affiche PlanPicker (CTA Stripe Checkout).
 *  - Souscription active -> Overview + PaymentMethod + Invoices + bouton
 *    "Gerer mon abonnement" (Customer Portal).
 *  - Souscription cancelled -> meme rendu + bandeau "annulee a fin de
 *    periode" avec date.
 */
export function BillingPage() {
  const navigate = useNavigate();
  const [subscription, setSubscription] = useState<SubscriptionDto | null>(null);
  const [invoices, setInvoices] = useState<InvoiceDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [portalLoading, setPortalLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    emitBusinessEvent('BILLING_PAGE_VIEWED');
    void refresh();
  }, []);

  async function refresh() {
    setLoading(true);
    setError(null);
    try {
      const [sub, invPage] = await Promise.all([
        billingService.getSubscription(),
        billingService.listInvoices(0, 3).catch(() => ({ items: [], total: 0, page: 0, size: 3 })),
      ]);
      setSubscription(sub);
      setInvoices(invPage.items);
    } catch (err) {
      setError((err as Error)?.message ?? 'Erreur de chargement billing');
    } finally {
      setLoading(false);
    }
  }

  async function openCustomerPortal() {
    setPortalLoading(true);
    try {
      const { url } = await billingService.createCustomerPortal();
      window.location.href = url;
    } catch (err) {
      setError((err as Error)?.message ?? 'Customer Portal indisponible');
      setPortalLoading(false);
    }
  }

  if (loading) {
    return (
      <div className="flex h-64 items-center justify-center" data-testid="billing-loading">
        <Loader2 className="h-6 w-6 animate-spin text-accent" />
      </div>
    );
  }

  return (
    <div className="space-y-6" data-testid="billing-page">
      <header className="rounded-2xl bg-bg-raised p-6 shadow-sm">
        <h1 className="font-heading text-2xl font-semibold text-fg">Abonnement &amp; facturation</h1>
        <p className="mt-1 text-sm text-fg-muted">
          Gerez votre abonnement, votre moyen de paiement et vos factures.
        </p>
      </header>

      {error && (
        <div className="rounded-lg border border-danger/50 bg-danger/10 p-3 text-sm text-danger" role="alert">
          {error}
        </div>
      )}

      {!subscription && (
        <section data-testid="billing-no-subscription">
          {/* UX-2 (2026-06-02) : sections "essai expire / essai actif" supprimees.
              Le concept de trial n'existe plus dans le produit — on affiche
              directement le selecteur de plans. */}
          {/* BUG 14 (2026-06-07) — au lieu de creer directement une Stripe
              Checkout Session, on route vers /app/billing/payment qui propose
              les 4 moyens (carte/virement/cheque/cash) via onPickPlan
              override. */}
          <PlanPicker
            workspaceName="Mon cabinet"
            contactEmail="admin@cabinet.ma"
            onCheckoutStart={(plan, period) =>
              emitBusinessEvent('BILLING_CHECKOUT_STARTED', { properties: { planCode: plan, period } })
            }
            onPickPlan={(plan, period) => {
              navigate(`/app/billing/payment?plan=${encodeURIComponent(plan)}&period=${encodeURIComponent(period)}`);
            }}
            onEnterpriseRequested={() => { window.location.href = `mailto:contact@jurika.ai?subject=Plan Entreprise`; }}
          />
        </section>
      )}

      {subscription && (
        <BillingActiveView
          subscription={subscription}
          invoices={invoices}
          portalLoading={portalLoading}
          onPortal={openCustomerPortal}
        />
      )}
    </div>
  );
}

interface BillingActiveViewProps {
  subscription: SubscriptionDto;
  invoices: InvoiceDto[];
  portalLoading: boolean;
  onPortal: () => void;
}

function BillingActiveView({ subscription, invoices, portalLoading, onPortal }: BillingActiveViewProps) {
  const cancelled = subscription.status === 'cancelled' || subscription.cancelAtPeriodEnd;
  return (
    <>
      <section className="rounded-2xl bg-bg-raised p-5 shadow-sm" data-testid="billing-overview">
        <header className="mb-3 flex items-start justify-between gap-3">
          <div>
            <h2 className="text-lg font-semibold text-fg">Votre abonnement</h2>
            <p className="text-sm text-fg-subtle">
              Plan : <strong>{subscription.planCode}</strong> · statut : <strong>{subscription.status}</strong>
            </p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {/* BUG 8 (2026-06-07) — bouton "Changer de forfait" disponible
                immediatement, ne necessite pas de passer par le portail Stripe. */}
            <Link
              to="/app/billing/change-plan"
              className="inline-flex items-center gap-2 rounded-lg border border-accent/40 bg-accent/10 px-4 py-2 text-sm font-medium text-accent hover:bg-accent/15"
              data-testid="billing-change-plan-cta"
            >
              <Sparkles className="h-4 w-4" />
              Changer de forfait
            </Link>
            <button
              type="button"
              onClick={onPortal}
              disabled={portalLoading}
              className="inline-flex items-center gap-2 rounded-lg bg-accent px-4 py-2 text-sm font-medium text-bg hover:bg-accent-hover disabled:opacity-50"
              data-testid="billing-portal-cta"
            >
              {portalLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : <ExternalLink className="h-4 w-4" />}
              Gerer mon abonnement
            </button>
          </div>
        </header>
        <dl className="mt-3 grid gap-3 text-sm sm:grid-cols-2">
          <div>
            <dt className="text-fg-subtle">Prochain renouvellement</dt>
            <dd className="font-medium text-fg">{formatDate(subscription.currentPeriodEnd)}</dd>
          </div>
          <div>
            <dt className="text-fg-subtle">Periode courante</dt>
            <dd className="font-medium text-fg">
              {formatDate(subscription.currentPeriodStart)} → {formatDate(subscription.currentPeriodEnd)}
            </dd>
          </div>
        </dl>
        {cancelled && (
          <p className="mt-3 rounded-lg border border-amber-300 bg-warning/10 p-3 text-sm text-warning" data-testid="billing-cancelled-banner">
            Abonnement annule. Acces conserve jusqu'au <strong>{formatDate(subscription.currentPeriodEnd)}</strong>.
          </p>
        )}
      </section>

      <section className="rounded-2xl bg-bg-raised p-5 shadow-sm" data-testid="billing-payment-method">
        <h2 className="mb-3 flex items-center gap-2 text-lg font-semibold text-fg">
          <CreditCard className="h-5 w-5 text-fg-subtle" /> Moyen de paiement
        </h2>
        {subscription.paymentMethod ? (
          <p className="text-sm text-fg-muted">
            {subscription.paymentMethod.brand?.toUpperCase() ?? 'Carte'} ••••{' '}
            <strong>{subscription.paymentMethod.last4}</strong> — exp.{' '}
            {subscription.paymentMethod.expMonth}/{subscription.paymentMethod.expYear}
          </p>
        ) : (
          <p className="text-sm text-fg-subtle">Aucun moyen de paiement enregistre.</p>
        )}
      </section>

      <section className="rounded-2xl bg-bg-raised p-5 shadow-sm" data-testid="billing-invoices">
        <h2 className="mb-3 flex items-center gap-2 text-lg font-semibold text-fg">
          <Receipt className="h-5 w-5 text-fg-subtle" /> Dernieres factures
        </h2>
        {invoices.length === 0 ? (
          <p className="text-sm text-fg-subtle">Pas encore de facture.</p>
        ) : (
          <table className="w-full text-sm">
            <thead className="text-left text-fg-subtle">
              <tr>
                <th className="py-2">Numero</th>
                <th>Date</th>
                <th>Montant TTC</th>
                <th>Statut</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {invoices.map((inv) => (
                <tr key={inv.id} className="border-t border-border" data-testid={`invoice-row-${inv.id}`}>
                  <td className="py-2 font-medium text-fg">{inv.number}</td>
                  <td className="text-fg-muted">{formatDate(inv.issuedAt)}</td>
                  <td className="font-medium text-fg">{formatMadCents(inv.amountTtcCents)}</td>
                  <td>
                    <StatusBadge status={inv.status} />
                  </td>
                  <td className="text-right">
                    {inv.invoicePdfUrl && (
                      <a
                        href={inv.invoicePdfUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="inline-flex items-center gap-1 text-sm font-medium text-accent hover:underline"
                        data-testid={`invoice-download-${inv.id}`}
                      >
                        <Download className="h-3 w-3" /> PDF
                      </a>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </>
  );
}

function StatusBadge({ status }: { status: InvoiceDto['status'] }) {
  const cls = status === 'paid'
    ? 'bg-green-100 text-green-800'
    : status === 'open'
      ? 'bg-amber-100 text-warning'
      : 'bg-bg-overlay text-fg-muted';
  return <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${cls}`}>{status}</span>;
}
