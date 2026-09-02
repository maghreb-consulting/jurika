import { useState } from 'react';

/**
 * v2 Flagship — FAQ accordion : 6 questions, height auto smooth, chevron rotate.
 * Une seule réponse ouverte à la fois (mais cliquable indépendamment).
 */
const FAQS = [
  {
    q: "Mes données sont-elles hébergées au Maroc ?",
    a: "Oui pour le plan Entreprise, sur OVH Casablanca. Pour les plans Essentiel et Business, hébergement européen (Hetzner Allemagne) conforme CNDP via clause de transfert international. Vous pouvez migrer vers l'hébergement Maroc à tout moment.",
  },
  {
    q: "JURIKA est-il conforme CNDP / Loi 09-08 ?",
    a: "Oui. Déclaration CNDP en cours, chiffrement AES-256 au repos, TLS 1.3 en transit, audit log complet, durée de rétention paramétrable. Notre DPO est joignable à dpo@maghreb-consulting.ma.",
  },
  {
    q: "Combien coûte JURIKA ?",
    a: "Trois plans : Essentiel 499 MAD/mois (2 utilisateurs), Business 1 199 MAD/mois (jusqu'à 6 utilisateurs, chatbot RAG inclus), Entreprise sur devis (utilisateurs illimités, hébergement Maroc, SSO). 2 mois offerts en facturation annuelle.",
  },
  {
    q: "Comment migrer depuis mon outil actuel (Excel, Word, autre SaaS) ?",
    a: "Notre équipe d'onboarding migre gratuitement vos dossiers actifs depuis Excel/Word, Sage, EBP ou un autre SaaS lors de votre activation. Compris dans tous les plans payants. Aucune perte de données : audit log complet de la migration.",
  },
  {
    q: "Quel support proposez-vous ?",
    a: "Plan Essentiel : email J+1 ouvré. Plan Business : prioritaire 24h ouvrées + chat in-app. Plan Entreprise : account manager dédié, SLA 4h, 2 sessions de formation sur site, hotline directe.",
  },
];

export function FAQ() {
  const [open, setOpen] = useState(0);
  const toggle = (i) => setOpen((cur) => (cur === i ? -1 : i));

  return (
    <section className="section section-faq" id="faq">
      <div className="container faq-container">
        <div className="section-head">
          <span className="eyebrow">Questions fréquentes</span>
          <h2 className="section-title">Tout ce que vous voulez vérifier avant de signer</h2>
        </div>
        <ul className="faq-list">
          {FAQS.map((f, i) => (
            <li key={i} className={`faq-item ${open === i ? 'is-open' : ''}`}>
              <button
                type="button"
                className="faq-trigger"
                onClick={() => toggle(i)}
                aria-expanded={open === i}
                aria-controls={`faq-panel-${i}`}
              >
                <span>{f.q}</span>
                <svg className="faq-chevron" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                  <polyline points="6 9 12 15 18 9" />
                </svg>
              </button>
              <div className="faq-panel" id={`faq-panel-${i}`} role="region">
                <div className="faq-panel__inner">
                  <p>{f.a}</p>
                </div>
              </div>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
