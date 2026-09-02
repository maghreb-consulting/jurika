import { useEffect, useState } from 'react';
import { Check, Sparkles } from 'lucide-react';
import { billingService } from '../../services/billing.service';
import type { BillingPeriod, PlanCode, PricingTier } from '../../types/billing';

/** Cle sessionStorage partagee avec PlanPicker (page /app/billing) pour
 *  pre-selectionner la periode choisie au signup quand le user passera
 *  au checkout reel (post-trial). */
export const SIGNUP_PERIOD_STORAGE_KEY = 'jurika.signup.billingPeriod';

// Re-export pour les call sites historiques (SignupPage importe PlanCode d'ici).
export type { PlanCode };

interface Props {
  initial?: PlanCode;
  onNext: (plan: PlanCode) => void;
}

/**
 * Grille de secours (spec directeur 2026-06-02). Utilisee si l'API
 * /api/v1/public/pricing tombe ou pendant le 1er paint. Les valeurs
 * doivent rester strictement alignees sur PlanCatalog (jurika-common).
 */
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

/**
 * UX-1 (2026-06-02) — Premiere etape du wizard signup : choix du forfait.
 *
 * <p>Affichee UNIQUEMENT si l'utilisateur arrive sur /signup sans pre-selection
 * via querystring ?plan=. Quand il clique un CTA depuis la landing
 * jurika.ai/#tarifs, le plan est deja dans l'URL et cette etape est sautee.
 *
 * <p>Spec tarifaire canonique 2026-06-02 : la grille (codes, labels, prix,
 * quotas, features) est lue depuis l'API publique /api/v1/public/pricing,
 * qui sert le catalogue PlanCatalog cote backend (source unique de verite).
 * Le FALLBACK_TIERS local n'est utilise que pendant le 1er paint ou si
 * l'API tombe — il doit rester aligne.
 *
 * <p>Avant ce fix, l'utilisateur qui cliquait "S'inscrire" depuis le header
 * (sans plan en URL) atterrissait directement sur l'etape Cabinet et le plan
 * "essentiel" etait silencieusement assigne par defaut — il decouvrait son
 * forfait seulement au step 5 Recap. C'est un piege UX : un client qui pensait
 * choisir un plan superieur se voyait inscrit sur Essentiel.
 */
export function SignupStep0Plan({ initial, onNext }: Props) {
  const [tiers, setTiers] = useState<PricingTier[]>(FALLBACK_TIERS);
  // Par defaut Annuel (le user voit l'economie tout de suite, et le directeur
  // pousse l'annuel — 2 mois offerts cf. spec 2026-06-02).
  const [period, setPeriod] = useState<BillingPeriod>(() => {
    try {
      const saved = sessionStorage.getItem(SIGNUP_PERIOD_STORAGE_KEY);
      if (saved === 'monthly' || saved === 'yearly') return saved;
    } catch { /* sessionStorage indisponible — fallback yearly */ }
    return 'yearly';
  });

  // Charge la grille canonique depuis l'API au mount. Fallback statique sinon.
  useEffect(() => {
    let cancelled = false;
    billingService
      .getPricing()
      .then((res) => {
        if (!cancelled && res?.tiers?.length) setTiers(res.tiers);
      })
      .catch(() => { /* fallback statique tolere */ });
    return () => { cancelled = true; };
  }, []);

  // Persiste la periode des qu'elle change, pour pre-remplir le PlanPicker
  // au checkout reel (post-trial 14j).
  useEffect(() => {
    try { sessionStorage.setItem(SIGNUP_PERIOD_STORAGE_KEY, period); }
    catch { /* indispo — degradation gracieuse */ }
  }, [period]);

  const isYearly = period === 'yearly';

  return (
    <div className="mx-auto max-w-5xl px-4 py-8" data-testid="signup-step-plan">
      <div className="mb-6 text-center">
        <h2 className="font-heading text-3xl font-semibold text-fg">
          Choisissez votre forfait
        </h2>
        <p className="mt-2 text-sm text-fg-subtle">
          Selectionnez le plan qui correspond a votre cabinet.
        </p>
      </div>

      {/* Toggle Mensuel / Annuel — defaut Annuel (2 mois offerts). */}
      <div
        className="mx-auto mb-8 inline-flex w-full max-w-xs items-center justify-center rounded-full border border-border bg-bg-raised p-1"
        data-testid="signup-period-toggle"
        role="group"
        aria-label="Periode de facturation"
      >
        <button
          type="button"
          onClick={() => setPeriod('monthly')}
          aria-pressed={!isYearly}
          data-testid="signup-period-monthly"
          className={[
            'flex-1 rounded-full px-4 py-2 text-sm font-medium transition-colors focus:outline-none focus:ring-2 focus:ring-accent',
            !isYearly ? 'bg-accent text-bg shadow-sm' : 'text-fg-subtle hover:text-fg',
          ].join(' ')}
        >
          Mensuel
        </button>
        <button
          type="button"
          onClick={() => setPeriod('yearly')}
          aria-pressed={isYearly}
          data-testid="signup-period-yearly"
          className={[
            'flex-1 rounded-full px-4 py-2 text-sm font-medium transition-colors focus:outline-none focus:ring-2 focus:ring-accent',
            isYearly ? 'bg-accent text-bg shadow-sm' : 'text-fg-subtle hover:text-fg',
          ].join(' ')}
        >
          Annuel
          <span className={['ml-1 text-xs', isYearly ? 'text-bg/80' : 'text-success'].join(' ')}>
            · -17%
          </span>
        </button>
      </div>

      <div className="grid gap-6 md:grid-cols-3">
        {tiers.map((tier) => {
          const isSelected = initial === (tier.code as PlanCode);
          const highlighted = tier.featured;
          return (
            <button
              type="button"
              key={tier.code}
              data-testid={`signup-plan-${tier.code}`}
              onClick={() => onNext(tier.code as PlanCode)}
              className={[
                'flex flex-col rounded-2xl border-2 bg-bg-raised p-6 text-left transition-all',
                'hover:scale-[1.02] hover:shadow-xl focus:outline-none focus:ring-2 focus:ring-accent',
                highlighted
                  ? 'border-accent shadow-lg'
                  : isSelected
                    ? 'border-accent'
                    : 'border-border',
              ].join(' ')}
            >
              {highlighted && tier.ribbon && (
                <div className="-mt-9 mb-3 flex justify-center">
                  <span className="inline-flex items-center gap-1 rounded-full bg-accent px-3 py-1 text-xs font-semibold text-bg">
                    <Sparkles className="h-3 w-3" />
                    {tier.ribbon}
                  </span>
                </div>
              )}

              <h3 className="mb-1 font-heading text-2xl font-semibold text-fg">
                {tier.label}
              </h3>
              <p className="mb-4 text-xs text-fg-subtle">{tier.target}</p>

              <div className="mb-5">
                {tier.quoteOnly || tier.price === null ? (
                  <div className="text-lg font-semibold text-fg">Sur devis</div>
                ) : (
                  <div className="flex flex-col gap-1">
                    {/* Prix principal : reflete la periode active du toggle. */}
                    <div className="flex items-baseline gap-1">
                      <span className="text-3xl font-bold text-fg">
                        {(isYearly && tier.annualPrice != null
                          ? tier.annualPrice
                          : tier.price
                        ).toLocaleString('fr-FR')}
                      </span>
                      <span className="text-sm text-fg-subtle">
                        MAD / {isYearly ? 'an' : 'mois'}
                      </span>
                      {isYearly && tier.annualPrice != null && (
                        <span className="ml-1 inline-flex items-center rounded-full bg-success/10 px-2 py-0.5 text-xs font-medium text-success">
                          2 mois offerts
                        </span>
                      )}
                    </div>
                    {/* Prix alternatif (l'autre periode) en petit, pour rester transparent. */}
                    {tier.annualPrice != null && (
                      <div className="text-xs text-fg-subtle">
                        {isYearly ? (
                          <>
                            soit{' '}
                            <span className="font-semibold text-fg">
                              {Math.round(tier.annualPrice / 12).toLocaleString('fr-FR')} MAD
                            </span>{' '}
                            / mois facture annuellement
                          </>
                        ) : (
                          <>
                            ou{' '}
                            <span className="font-semibold text-fg">
                              {tier.annualPrice.toLocaleString('fr-FR')} MAD
                            </span>{' '}
                            / an
                            <span className="text-success"> · 2 mois offerts</span>
                          </>
                        )}
                      </div>
                    )}
                  </div>
                )}
              </div>

              <ul className="mb-6 flex-1 space-y-2">
                {tier.features.map((feature) => (
                  <li key={feature} className="flex items-start gap-2 text-sm text-fg">
                    <Check className="mt-0.5 h-4 w-4 flex-shrink-0 text-success" />
                    <span>{feature}</span>
                  </li>
                ))}
              </ul>

              <div
                className={[
                  'mt-auto rounded-lg px-4 py-3 text-center text-sm font-semibold transition-colors',
                  highlighted
                    ? 'bg-accent text-bg'
                    : 'border border-accent text-accent',
                ].join(' ')}
              >
                {tier.quoteOnly ? 'Contacter les ventes' : `Choisir ${tier.label}`} →
              </div>
            </button>
          );
        })}
      </div>

      <p className="mt-8 text-center text-xs text-fg-subtle">
        Vous pourrez changer de forfait a tout moment depuis votre espace de facturation.
      </p>
    </div>
  );
}
