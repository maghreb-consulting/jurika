import { useNavigate } from 'react-router-dom';
import { PRICING_TIERS, formatPrice } from '../../config/pricing.js';
import { MagneticButton } from '../interactive/MagneticButton.jsx';

/**
 * v2 Flagship — Pricing 3 cards : featured glow border animé,
 * magnetic CTA, ticks qui se cochent au reveal.
 *
 * Sprint 12.6 + spec 2026-06-02 -- Tous les tiers (Essentiel / Business /
 * Entreprise) menent a /signup?plan=X via React Router navigate
 * (SPA fluide, plus de demo).
 */
export function Pricing({ onDemoClick: _ignored }) {
  const navigate = useNavigate();
  void _ignored;
  const handleCtaClick = (tier) => {
    navigate(`/signup?plan=${tier.id}`);
  };

  return (
    <section className="section section-pricing" id="tarifs">
      <div className="container">
        <div className="section-head">
          <span className="eyebrow">Tarification</span>
          <h2 className="section-title">Des formules pensées pour chaque taille de cabinet</h2>
          <p className="section-sub">
            Sans carte bancaire · 2 mois offerts en facturation annuelle
          </p>
        </div>
        <div className="pricing-grid" data-testid="pricing-grid">
          {PRICING_TIERS.map((tier) => (
            <article
              key={tier.id}
              className={`price-card ${tier.featured ? 'price-featured' : ''}`}
              data-testid={`pricing-card-${tier.id}`}
            >
              {tier.ribbon && <span className="ribbon">{tier.ribbon}</span>}
              <h3>{tier.label}</h3>
              <div className="price">
                {tier.price === null ? (
                  // Sprint Beta — Entreprise sur devis : pas de montant affiche.
                  <span className="amount">{tier.priceLabel || 'Sur devis'}</span>
                ) : (
                  <>
                    {tier.priceLabel && <span className="period">{tier.priceLabel} </span>}
                    <span className="amount">{formatPrice(tier.price)}</span>
                    <span className="period">{tier.currency} / {tier.period}</span>
                  </>
                )}
              </div>
              <p className="price-tag">{tier.target}</p>
              <ul>
                {tier.features.map((f, i) => (
                  <li key={i}>
                    <span className="tick" aria-hidden="true">
                      <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
                        <polyline points="20 6 9 17 4 12" />
                      </svg>
                    </span>
                    <span>{f}</span>
                  </li>
                ))}
              </ul>
              <MagneticButton
                type="button"
                className={`btn ${tier.cta.style}`}
                onClick={() => handleCtaClick(tier)}
                data-testid={`pricing-cta-${tier.id}`}
                strength={tier.featured ? 0.3 : 0.18}
              >
                {tier.cta.label}
              </MagneticButton>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}
