import { useNavigate } from 'react-router-dom';
import { FORFAITS, SOCLE_COMMUN, ESSAI, fonctionsDuForfait, formatPrice } from '../../config/pricing.js';
import { MagneticButton } from '../interactive/MagneticButton.jsx';
import { InfoBulle } from '../../../../../components/ui/Aide';
import { adresseContact } from '../../../../legal/contact';

/**
 * Grille tarifaire (cahier des charges 4.2 a 4.5, lot L7 E3).
 *
 * Quatre forfaits : Essentiel, Professionnel, Max, Sur Devis. Chaque carte donne le prix HT
 * annuel (un seul reglement) et mensuel (douze mensualites), les quotas et les fonctions du
 * forfait. Le forfait Sur Devis se demande a JURIKA (RG-SOU-01) : par courriel quand une
 * adresse de contact est configuree au build, sinon sans adresse affichee.
 */
function Coche() {
  return (
    <span className="tick" aria-hidden="true">
      <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
        <polyline points="20 6 9 17 4 12" />
      </svg>
    </span>
  );
}

export function Pricing() {
  const navigate = useNavigate();
  const contact = adresseContact();

  return (
    <section className="section section-pricing" id="tarifs">
      <div className="container">
        <div className="section-head">
          <span className="eyebrow">Tarifs</span>
          <h2 className="section-title">Quatre forfaits, souscrits pour un an</h2>
          <p className="section-sub">
            Prix hors taxes. Réglez en une fois (paiement annuel) ou chaque mois (paiement mensuel) :
            le paiement annuel revient à deux mois offerts.{' '}
            <InfoBulle
              libelle="Comment se règle un forfait ?"
              texte="Le forfait est souscrit pour un an. Paiement annuel : un seul règlement pour l’année. Paiement mensuel : douze mensualités égales au dixième du prix annuel. Paiement par carte bancaire en ligne, virement, chèque ou espèces ; chaque paiement donne lieu à une facture."
            />
          </p>
        </div>
        <div className="pricing-grid pricing-grid--4" data-testid="pricing-grid">
          {FORFAITS.map((forfait) => {
            const surDevis = forfait.prixAnnuelHt === null;
            const professionnel = forfait.id === 'professionnel';
            return (
              <article
                key={forfait.id}
                className={`price-card ${professionnel ? 'price-featured' : ''}`}
                data-testid={`pricing-card-${forfait.id}`}
              >
                {professionnel && <span className="ribbon">Forfait de l’essai gratuit</span>}
                <h3>{forfait.label}</h3>
                {surDevis ? (
                  <div className="price">
                    <span className="amount">Sur devis</span>
                  </div>
                ) : (
                  <>
                    <div className="price">
                      <span className="amount">{formatPrice(forfait.prixAnnuelHt)}</span>{' '}
                      <span className="period">DH HT / an</span>
                    </div>
                    <p className="price-tag" data-testid={`pricing-mensuel-${forfait.id}`}>
                      ou {formatPrice(forfait.prixMensuelHt)} DH HT / mois, soit{' '}
                      {formatPrice(forfait.prixMensuelHt * 12)} DH HT sur un an
                    </p>
                  </>
                )}
                <ul>
                  <li>
                    <Coche />
                    <span>
                      {forfait.dossiers}{' '}
                      <InfoBulle
                        libelle="Comment les dossiers sont-ils comptés ?"
                        texte="Un dossier juridique est une société suivie par le cabinet. Il est compté une seule fois dans le mois où un ticket y est ouvert, quel que soit le nombre de tickets ; le compteur repart à zéro chaque mois. Les dossiers existants restent toujours accessibles."
                      />
                    </span>
                  </li>
                  <li><Coche /><span>{forfait.utilisateurs}</span></li>
                  <li><Coche /><span>{forfait.stockage}</span></li>
                  {fonctionsDuForfait(forfait.id).map((f) => (
                    <li key={f}><Coche /><span>{f}</span></li>
                  ))}
                </ul>
                {surDevis ? (
                  contact ? (
                    <a
                      className="btn btn-outline"
                      href={`mailto:${contact}?subject=${encodeURIComponent('Demande de devis JURIKA')}`}
                      data-testid="pricing-cta-sur-devis"
                    >
                      Demander un devis
                    </a>
                  ) : (
                    <p className="price-tag" data-testid="pricing-sur-devis-sans-contact">
                      Pour plus de 100 dossiers par mois, le forfait est établi avec JURIKA,
                      qui ouvre alors l’espace de votre cabinet.
                    </p>
                  )
                ) : (
                  <MagneticButton
                    type="button"
                    className={`btn ${professionnel ? 'btn-primary' : 'btn-outline'}`}
                    onClick={() => navigate('/signup')}
                    data-testid={`pricing-cta-${forfait.id}`}
                    strength={professionnel ? 0.3 : 0.18}
                  >
                    Choisir {forfait.label}
                  </MagneticButton>
                )}
              </article>
            );
          })}
        </div>

        <div className="pricing-annexes">
          <div className="pricing-annexe" data-testid="pricing-essai">
            <h3>Essai gratuit de {ESSAI.dureeJours} jours</h3>
            <p className="pricing-annexe__lead">
              Pour découvrir la plateforme avant de souscrire.{' '}
              <InfoBulle
                libelle="Et à la fin de l’essai ?"
                texte="Des rappels s’affichent trois jours puis un jour avant la fin. Sans souscription au terme des 7 jours, l’espace passe en lecture seule pendant 30 jours, puis ses données sont supprimées définitivement, après avertissement."
              />
            </p>
            <ul>
              {ESSAI.conditions.map((c) => (
                <li key={c}><Coche /><span>{c}</span></li>
              ))}
            </ul>
          </div>
          <div className="pricing-annexe" data-testid="pricing-socle">
            <h3>Inclus dans tous les forfaits</h3>
            <p className="pricing-annexe__lead">
              La sécurité et vos droits sur vos données ne dépendent pas du forfait.
            </p>
            <ul>
              {SOCLE_COMMUN.map((g) => (
                <li key={g}><Coche /><span>{g}</span></li>
              ))}
            </ul>
          </div>
        </div>
      </div>
    </section>
  );
}
