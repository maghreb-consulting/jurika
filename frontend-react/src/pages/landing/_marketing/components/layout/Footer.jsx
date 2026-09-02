import { Logo } from './Logo.jsx';

/**
 * v2 Flagship — Footer raffiné (hairlines or, micro-logo hover, légalités CNDP).
 * Le styling hairline/hover est ajouté TASK 10.
 */
export function Footer() {
  return (
    <footer className="footer" id="contact">
      <div className="container footer-inner">
        <div className="footer-brand">
          <Logo light />
          <p>
            Plateforme SaaS B2B multi-tenant de gestion juridique des entreprises, propulsée par l'IA.
            Éditée par Maghreb Consulting, Casablanca.
          </p>
          <div className="socials">
            <a href="#" aria-label="LinkedIn"><svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M4.98 3.5C4.98 4.88 3.87 6 2.5 6S0 4.88 0 3.5 1.12 1 2.5 1s2.48 1.12 2.48 2.5zM.22 8h4.56v14H.22V8zm7.4 0h4.37v1.92h.06c.61-1.15 2.1-2.36 4.32-2.36 4.62 0 5.47 3.04 5.47 6.99V22h-4.56v-6.3c0-1.5-.03-3.43-2.09-3.43-2.1 0-2.42 1.64-2.42 3.32V22H7.62V8z"/></svg></a>
            <a href="#" aria-label="X (Twitter)"><svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24H16.17l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z"/></svg></a>
            <a href="#" aria-label="GitHub"><svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M12 .5C5.7.5.5 5.7.5 12c0 5.1 3.3 9.4 7.8 10.9.6.1.8-.2.8-.6v-2.2c-3.2.7-3.9-1.4-3.9-1.4-.5-1.4-1.3-1.7-1.3-1.7-1.1-.7.1-.7.1-.7 1.2.1 1.8 1.2 1.8 1.2 1 1.8 2.8 1.3 3.5 1 .1-.8.4-1.3.7-1.6-2.6-.3-5.3-1.3-5.3-5.7 0-1.3.4-2.3 1.2-3.1-.1-.3-.5-1.5.1-3.1 0 0 1-.3 3.2 1.2.9-.3 1.9-.4 2.9-.4s2 .1 2.9.4c2.2-1.5 3.2-1.2 3.2-1.2.6 1.6.2 2.8.1 3.1.7.8 1.2 1.8 1.2 3.1 0 4.4-2.7 5.4-5.3 5.7.4.4.8 1.1.8 2.2v3.3c0 .3.2.7.8.6 4.5-1.5 7.8-5.8 7.8-10.9C23.5 5.7 18.3.5 12 .5z"/></svg></a>
          </div>
        </div>
        <div className="footer-col">
          <h4>Produit</h4>
          <a href="#fonctionnalites">Fonctionnalités</a>
          <a href="#ia">Intelligence Artificielle</a>
          <a href="#pilotage-live">Pilotage & traçabilité</a>
          <a href="#tarifs">Tarifs</a>
          <a href="#faq">FAQ</a>
        </div>
        <div className="footer-col">
          <h4>Solutions</h4>
          <a href="#cible">Cabinets de consulting</a>
          <a href="#cible">Experts-comptables</a>
          <a href="#cible">Avocats &amp; notaires</a>
          <a href="#cible">Centres d'affaires</a>
        </div>
        <div className="footer-col">
          <h4>Cabinet éditeur</h4>
          <p className="footer-text">Maghreb Consulting</p>
          <p className="footer-text">Casablanca, Maroc</p>
          <p className="footer-text">contact@jurika.ai</p>
          <p className="footer-text">+212 5 22 00 00 00</p>
        </div>
      </div>
      <div className="footer-bottom">
        <div className="container footer-bottom-inner">
          <p>© {new Date().getFullYear()} JURIKA — Maghreb Consulting. Tous droits réservés.</p>
          <div className="footer-legal">
            <a href="/cgu">Mentions légales</a>
            <a href="/cgu">CGU</a>
            <a href="/confidentialite">Politique de confidentialité (CNDP)</a>
          </div>
        </div>
      </div>
    </footer>
  );
}
