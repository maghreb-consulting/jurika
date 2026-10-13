import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { CheckCircle2, Loader2 } from 'lucide-react';
import { billingService } from '../../services/billing.service';
import { emitBusinessEvent } from '../../services/analytics.service';
import type { SubscriptionDto } from '../../types/billing';
import { libelleForfait } from '../../lib/adminLabels';

/**
 * Sprint 12 — page /billing/success?session_id=xxx (RG-BL04).
 *
 * <p>Affichee apres redirection depuis Stripe Checkout. Polls
 * GET /api/v1/billing/subscription toutes les 2s jusqu'a status=active
 * (le webhook checkout.session.completed peut prendre quelques secondes
 * a etre delivre + traite). Max 20 essais (~40s) puis affiche un fallback
 * "verification en cours".
 */
const POLL_INTERVAL_MS = 2000;
const MAX_ATTEMPTS = 20;

export function BillingSuccessPage() {
  const [params] = useSearchParams();
  const sessionId = params.get('session_id');
  const navigate = useNavigate();
  const [subscription, setSubscription] = useState<SubscriptionDto | null>(null);
  const [attempts, setAttempts] = useState(0);
  const [done, setDone] = useState(false);

  useEffect(() => {
    emitBusinessEvent('BILLING_CHECKOUT_RETURN', { properties: { sessionId: sessionId ?? null } });
    let cancelled = false;
    let attempt = 0;

    const poll = async () => {
      if (cancelled) return;
      attempt += 1;
      setAttempts(attempt);
      try {
        const sub = await billingService.getSubscription();
        if (sub && (sub.status === 'active' || sub.status === 'trialing')) {
          setSubscription(sub);
          setDone(true);
          emitBusinessEvent('BILLING_CONVERSION_COMPLETED', { properties: { planCode: sub.planCode } });
          return;
        }
      } catch {
        // Ignore — on continue de poller jusqu'au max.
      }
      if (attempt < MAX_ATTEMPTS) {
        window.setTimeout(poll, POLL_INTERVAL_MS);
      } else {
        setDone(true); // fallback affichage "verification en cours"
      }
    };
    void poll();
    return () => {
      cancelled = true;
    };
  }, [sessionId]);

  return (
    <div className="mx-auto max-w-xl p-6" data-testid="billing-success-page">
      <div className="rounded-2xl bg-bg-raised p-8 text-center shadow-sm">
        {subscription ? (
          <>
            <CheckCircle2 className="mx-auto h-12 w-12 text-success" />
            <h1 className="mt-4 font-heading text-2xl font-semibold text-fg">Bienvenue au forfait {libelleForfait(subscription.planCode)} !</h1>
            <p className="mt-2 text-sm text-fg-muted">
              Votre abonnement est actif. Vous avez maintenant accès à toutes les fonctionnalités de JURIKA.
            </p>
            <div className="mt-6 flex justify-center gap-3">
              <Link to="/app/dashboard" className="rounded-lg bg-accent px-4 py-2 text-sm font-medium text-bg hover:bg-accent-hover">
                Accéder au tableau de bord
              </Link>
              <Link to="/app/billing" className="rounded-lg border border-border-hi bg-bg-raised px-4 py-2 text-sm font-medium text-fg-muted hover:bg-bg-overlay">
                Voir mon abonnement
              </Link>
            </div>
          </>
        ) : (
          <>
            <Loader2 className="mx-auto h-12 w-12 animate-spin text-accent" />
            <h1 className="mt-4 font-heading text-xl font-semibold text-fg">Activation en cours…</h1>
            <p className="mt-2 text-sm text-fg-muted">
              Nous vérifions votre paiement auprès de Stripe. Cela prend quelques secondes (vérification {attempts} sur {MAX_ATTEMPTS}).
              Ne fermez pas cette page.
            </p>
            {done && (
              <p className="mt-4 text-sm text-amber-700">
                L’activation prend plus de temps que prévu. Ouvrez{' '}
                <button type="button" onClick={() => navigate('/app/billing')} className="underline">
                  la page de facturation
                </button>{' '}
                pour vérifier l’état de votre abonnement.
              </p>
            )}
          </>
        )}
      </div>
    </div>
  );
}
