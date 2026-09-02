import { useEffect, useRef } from 'react';
import { useReducedMotion } from '../../hooks/useReducedMotion.js';
import { useScrollReveal } from '../../hooks/useScrollReveal.js';

/**
 * v2 Flagship — 12 modules en perspective CSS rotateX au scroll.
 *
 * TASK 7 : rotateX scroll-driven (grille se redresse au passage) + reveal
 * cascade des cards via IntersectionObserver.
 *
 * Si reduced-motion : grille plate, pas de rotateX, reveal instantané.
 */
const MODULES = [
  { n: '01', title: 'Authentification & sécurité', desc: '3 étapes, 2FA TOTP, sessions, verrouillage, chiffrement AES-256.' },
  { n: '02', title: 'Tickets & Kanban', desc: 'Déclencheur de workflow, 4 états, suivi visuel, états débours IA.' },
  { n: '03', title: 'Workflows Création', desc: 'SARL & SARL AU en 9 étapes guidées avec validation IA.' },
  { n: '04', title: 'Workflows Modification', desc: 'Interface dual-list, 28 types, documents impactés automatiques.' },
  { n: '05', title: 'Dissolution & Liquidation', desc: 'PV AGE, rapport de liquidation, génération et validation.' },
  { n: '06', title: 'Data Room', desc: 'Dossier juridique + financier + archive, permissions granulaires.' },
  { n: '07', title: 'Chat temps réel', desc: 'WebSocket intra-workspace et client-employé, historique persistant.' },
  { n: '08', title: 'Chatbot RAG', desc: 'Assistant juridique contextuel, sources documentaires, historique.' },
  { n: '09', title: 'Dashboard analytique', desc: 'KPIs, performances, diagrammes IA à la demande.' },
  { n: '10', title: 'Supervision', desc: 'Accès lecture seule, remarques flottantes, gestion abonnement.' },
  { n: '11', title: 'Notifications', desc: 'In-app, email, push mobile — alertes délais et expirations.' },
  { n: '12', title: 'Demandes Client', desc: "Tracking des requêtes depuis l'espace client de la Data Room." },
];

export function ModulesConstellation() {
  const reduced = useReducedMotion();
  const sectionRef = useRef(null);
  const gridRef = useRef(null);
  useScrollReveal({ scope: sectionRef });

  // Effet "constellation" : la grille rotateX selon la position dans le viewport
  useEffect(() => {
    if (reduced) return undefined;
    const grid = gridRef.current;
    if (!grid) return undefined;

    let scheduled = false;
    const update = () => {
      const r = grid.getBoundingClientRect();
      const vh = window.innerHeight;
      // Centre du grid relativement au viewport (-1..1, 0 = milieu écran)
      const center = (r.top + r.height / 2) / vh - 0.5;
      // Centre vers haut → rotateX positif (tilte vers nous), centre vers bas → négatif
      const rx = Math.max(-8, Math.min(8, center * -16));
      grid.style.transform = `rotateX(${rx}deg)`;
      scheduled = false;
    };

    const onScroll = () => {
      if (scheduled) return;
      scheduled = true;
      requestAnimationFrame(update);
    };

    update();
    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('resize', update);
    return () => {
      window.removeEventListener('scroll', onScroll);
      window.removeEventListener('resize', update);
    };
  }, [reduced]);

  return (
    <section className="section section-modules" id="modules" ref={sectionRef}>
      <div className="container">
        <div className="section-head">
          <span className="eyebrow" data-reveal>Modules métier</span>
          <h2 className="section-title" data-reveal>12 modules + 4 workflows spécialisés</h2>
          <p className="section-sub" data-reveal>
            Couverture complète du cycle de vie juridique des entreprises au Maroc.
          </p>
        </div>
        <div className="modules-grid constellation" ref={gridRef}>
          {MODULES.map((m, i) => (
            <article
              className="module-card"
              key={m.n}
              data-module={m.n}
              data-reveal
              style={{ transitionDelay: `${i * 40}ms` }}
            >
              <div className="module-n">{m.n}</div>
              <h3>{m.title}</h3>
              <p>{m.desc}</p>
              <a href="#demo" className="module-link">En savoir plus →</a>
            </article>
          ))}
        </div>
      </div>
    </section>
  );
}
