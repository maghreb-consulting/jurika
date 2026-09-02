import { MagneticButton } from '../interactive/MagneticButton.jsx';
import { GoldParticles } from '../interactive/GoldParticles.jsx';

/**
 * v2 Flagship — CTA final full-bleed gradient navy → navy-deep,
 * particules or canvas, form magnetic submit.
 *
 * Sprint 12.6 -- Toutes les references "demo" supprimees. La section
 * pousse directement a l'inscription /signup.
 */
export function CTAFinal({ onDemoClick }) {
  return (
    <section className="section section-cta" id="inscription">
      <div className="container">
        <div className="cta-box">
          <div className="cta-glow" aria-hidden="true" />
          <div className="cta-particles-wrapper" aria-hidden="true">
            <GoldParticles density={36} opacity={0.6} />
          </div>
          <span className="eyebrow eyebrow-light">Prêt à digitaliser votre cabinet ?</span>
          <h2 className="cta-title">
            Démarrez avec <em>JURIKA</em> en quelques minutes
          </h2>
          <p className="cta-sub">
            Découvrez comment l'IA juridique accélère la production de vos actes
            et offre à vos clients un suivi temps réel de leurs dossiers.
          </p>
          <ul className="cta-recap" aria-label="Ce que couvre JURIKA">
            {[
              { t: 'Workflows automatisés', d: 'Création, modifications, dissolution, liquidation — saisie CIN assistée par OCR/KIE' },
              { t: 'Data Room multi-tenant', d: 'Dépôts client, permissions, archivage cloisonné' },
              { t: 'Assistant IA sourcé', d: 'Chatbot RAG + copilote qui propose' },
              { t: 'Pilotage & traçabilité', d: 'Tickets, dashboards, journal d’audit' },
            ].map((it) => (
              <li key={it.t}>
                <span className="cta-recap__ico" aria-hidden="true">
                  <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"><polyline points="20 6 9 17 4 12" /></svg>
                </span>
                <div>
                  <strong>{it.t}</strong>
                  <span>{it.d}</span>
                </div>
              </li>
            ))}
          </ul>
          <MagneticButton
            type="button"
            className="btn btn-primary btn-lg"
            onClick={onDemoClick}
            data-testid="cta-signup-button"
            strength={0.3}
          >
            Créer mon compte
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" style={{ marginLeft: 8 }}><path d="M5 12h14M13 5l7 7-7 7"/></svg>
          </MagneticButton>
          <p className="cta-fineprint">Sans carte bancaire · hébergement Maroc (plan Entreprise) ou UE · conçu pour la conformité CNDP / Loi 09-08</p>
        </div>
      </div>
    </section>
  );
}
