import { Logo } from './Logo.jsx';
import { adresseContact } from '../../../../legal/contact';

/**
 * Pied de page de la vitrine (lot L7 E3).
 *
 * L'adresse de contact vient de VITE_CONTACT_EMAIL (fixee au build) ; sans valeur, aucune
 * adresse n'est affichee. Aucun telephone ni reseau social : aucun n'est etabli.
 */
export function Footer() {
  const contact = adresseContact();
  return (
    <footer className="footer" id="contact">
      <div className="container footer-inner">
        <div className="footer-brand">
          <Logo light />
          <p>
            Plateforme de gestion juridique des entreprises au Maroc, pour les cabinets.
            Éditée par Maghreb Consulting, Casablanca.
          </p>
        </div>
        <div className="footer-col">
          <h4>Produit</h4>
          <a href="#fonctionnalites">Fonctionnalités</a>
          <a href="#ia">Intelligence artificielle</a>
          <a href="#pilotage-live">Suivi et traçabilité</a>
          <a href="#tarifs">Tarifs</a>
          <a href="#faq">Questions fréquentes</a>
        </div>
        <div className="footer-col">
          <h4>Éditeur</h4>
          <p className="footer-text">Maghreb Consulting</p>
          <p className="footer-text">Casablanca, Maroc</p>
          {contact && (
            <a className="footer-text" href={`mailto:${contact}`} data-testid="footer-contact">
              {contact}
            </a>
          )}
        </div>
      </div>
      <div className="footer-bottom">
        <div className="container footer-bottom-inner">
          <p>© {new Date().getFullYear()} JURIKA — Maghreb Consulting. Tous droits réservés.</p>
          <div className="footer-legal">
            <a href="/cgu">Conditions générales d’abonnement</a>
            <a href="/confidentialite">Politique de confidentialité</a>
          </div>
        </div>
      </div>
    </footer>
  );
}
