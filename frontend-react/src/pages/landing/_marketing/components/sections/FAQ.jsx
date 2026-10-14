import { useState } from 'react';

/**
 * Questions frequentes (lot L7 E3) : chaque reponse reprend le cahier des charges
 * (4.2 a 4.7, 15.3, P1, 18) ou le contrat d'abonnement ; rien n'y est ajoute.
 * Une seule reponse ouverte a la fois.
 */
const FAQS = [
  {
    q: 'Où sont hébergées les données ?',
    a: "La plateforme est hébergée chez Amazon Web Services, à Paris (Union européenne). L'hébergement hors du Maroc est soumis à l'autorisation de transfert de la CNDP, que Maghreb Consulting obtient avant la mise en production ; la démarche est en cours.",
  },
  {
    q: 'Comment les données de mon cabinet sont-elles protégées ?',
    a: "Au même niveau pour tous les forfaits : chiffrement des échanges (HTTPS), double authentification, cloisonnement strict des cabinets, droits par rôle vérifiés côté serveur, traçabilité des actions, sauvegardes quotidiennes testées par restauration et export complet de vos données. Vous restez seul propriétaire de vos données.",
  },
  {
    q: 'Combien coûte JURIKA ?',
    a: "Quatre forfaits, souscrits pour un an, prix hors taxes : Essentiel 3 750 DH par an ou 375 DH par mois (jusqu'à 25 dossiers par mois) ; Professionnel 7 500 DH par an ou 750 DH par mois (jusqu'à 50 dossiers) ; Max 15 000 DH par an ou 1 500 DH par mois (jusqu'à 100 dossiers) ; Sur Devis au-delà de 100 dossiers. En paiement mensuel, douze mensualités égales au dixième du prix annuel : le paiement annuel revient à deux mois offerts.",
  },
  {
    q: "Comment fonctionne l'essai gratuit ?",
    a: "L'essai dure 7 jours, sans moyen de paiement, un essai par cabinet. Il donne accès aux fonctions du forfait Professionnel, dans la limite de 3 dossiers, 1 superviseur et 1 employé, 500 Mo de stockage et 20 questions au chatbot. Aucun téléchargement n'est permis : les documents se consultent en aperçu et les actes portent la mention « VERSION D'ÉVALUATION — SANS VALEUR JURIDIQUE ». Si vous souscrivez, vous conservez tout votre travail.",
  },
  {
    q: 'Puis-je changer de forfait ?',
    a: "Oui, à tout moment. Un forfait supérieur s'applique dès la confirmation du paiement ; vous réglez la différence au prorata de la période restante. Un forfait inférieur s'applique à la prochaine échéance, sans remboursement de la période en cours, si votre usage tient dans le forfait choisi. Le rythme de paiement peut être changé à la prochaine échéance.",
  },
  {
    q: "Que deviennent mes données si j'arrête mon forfait ?",
    a: "Tout votre travail est archivé, jamais supprimé : dossiers, tickets, actes, pièces, Data Rooms, comptes et historique. Les accès sont fermés, mais le superviseur peut toujours se connecter pour exporter l'intégralité des données ou en demander la suppression définitive. Si vous souscrivez de nouveau, votre espace est réactivé dans l'état exact où vous l'avez laissé.",
  },
  {
    q: 'Les actes sont-ils rédigés par une intelligence artificielle ?',
    a: "Non. Les actes sont générés à partir de modèles validés : à données identiques, acte identique. Aucune intelligence artificielle ne rédige un acte. Vous relisez chaque acte avant sa signature ou son dépôt.",
  },
  {
    q: 'Quelle disponibilité est prévue ?',
    a: "L'objectif est de 99,5 % de disponibilité en heures ouvrées. Il s'agit d'un objectif ; un engagement de service contractuel n'est prévu que pour le forfait Sur Devis, dans les conditions de son offre.",
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
