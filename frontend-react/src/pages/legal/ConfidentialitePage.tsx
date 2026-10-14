import { LegalPageLayout } from './LegalPageLayout';
import { adresseContact } from './contact';
import { InfoBulle } from '../../components/ui/Aide';

/**
 * Lot L7 (E3) : politique de confidentialite de JURIKA.
 *
 * Chaque affirmation vient du cahier des charges (P10, 4.2, 4.4, 4.8, 12.1, 13, 15.6, 18)
 * ou du contrat d'abonnement valide (pieces B et C). Ce qui depend encore de la direction
 * est affiche comme tel, jamais comme une valeur :
 *  - declaration des traitements et autorisation de transfert aupres de la CNDP : en cours ;
 *  - duree de conservation des archives : celle de la declaration CNDP, A_DECIDER (en cours) ;
 * Sous-traitants, localisations et delai de notification : ceux de la version 1 du contrat
 * (pieces B et C, validee le 14 octobre 2026).
 * Aucun jargon technique ni engagement non etabli (delegue, cookies, tests d'intrusion...).
 */
function AFixer({ children }: { children: string }) {
  return (
    <mark data-a-completer="" title="Mention encore à compléter" className="rounded bg-warning/15 px-0.5 text-fg">
      {children}
    </mark>
  );
}

export function ConfidentialitePage() {
  const contact = adresseContact();
  return (
    <LegalPageLayout
      title="Politique de confidentialité"
      subtitle="Traitement des données à caractère personnel sur la plateforme JURIKA (loi n° 09-08)"
      lastUpdated="14 octobre 2026"
    >
      <h2>1. Qui traite vos données</h2>
      <p>
        JURIKA est éditée par <strong>Maghreb Consulting</strong>, Casablanca (Maroc).
      </p>
      <ul>
        <li>
          Pour les données de ses propres utilisateurs (comptes, connexions, facturation),
          Maghreb Consulting est <strong>responsable du traitement</strong>.
        </li>
        <li>
          Pour les données personnelles qu’un cabinet enregistre sur la plateforme (ses clients,
          associés, gérants, signataires et autres personnes figurant dans ses dossiers), le
          <strong> cabinet</strong> est responsable du traitement et Maghreb Consulting agit comme
          <strong> sous-traitant</strong>, selon l’accord de traitement des données du contrat
          d’abonnement.
        </li>
      </ul>
      <p data-testid="cndp-statut">
        <strong>CNDP</strong> : la déclaration des traitements auprès de la Commission nationale de
        contrôle de la protection des données à caractère personnel (CNDP) et l’autorisation de
        transfert liée à l’hébergement sont <strong>en cours</strong>.
      </p>

      <h2>2. Données traitées</h2>
      <ul>
        <li>
          <strong>Inscription à l’essai gratuit</strong> : nom, prénom, organisation (nom du
          cabinet), téléphone et adresse électronique, et seulement celles-ci.
        </li>
        <li>
          <strong>Comptes des utilisateurs</strong> : nom, prénom, adresse électronique,
          identifiant de connexion ; dates et heures des actions effectuées.
        </li>
        <li>
          <strong>Acceptation du contrat</strong> : version acceptée, compte et qualité du
          signataire, date, heure et adresse IP de l’acceptation.
        </li>
        <li>
          <strong>Facturation</strong> : forfait, paiements et factures. Le paiement par carte se
          fait sur la page de paiement sécurisée du Centre monétique interbancaire : la plateforme
          ne voit ni ne stocke jamais les numéros de carte.
        </li>
        <li>
          <strong>Dossiers des cabinets</strong> : les données que le cabinet saisit ou dépose
          pour les sociétés qu’il suit.
        </li>
      </ul>
      <p>
        Le chatbot juridique ne reçoit <strong>aucune donnée personnelle</strong> : une question qui
        en contient une (numéro de CIN, ICE, RC, identifiant fiscal, téléphone, adresse
        électronique, RIB, date de naissance) est bloquée avant l’envoi.
      </p>

      <h2>3. Pourquoi ces données sont traitées</h2>
      <ul>
        <li>Fournir le service : comptes, dossiers, génération des actes, Data Room.</li>
        <li>Gérer l’abonnement, les paiements et les factures.</li>
        <li>Assurer la sécurité de la plateforme et la traçabilité des actions.</li>
      </ul>
      <p>
        Les données confiées par un cabinet ne sont utilisées qu’à ces fins, et jamais pour
        entraîner un modèle d’intelligence artificielle.
      </p>

      <h2>4. Qui y a accès</h2>
      <ul>
        <li>Les utilisateurs du cabinet, selon leur rôle ; chaque cabinet est strictement cloisonné des autres.</li>
        <li>Le client d’un dossier, uniquement pour les documents rendus visibles et dans la limite des permissions accordées par le cabinet.</li>
        <li>
          Les équipes de Maghreb Consulting n’accèdent pas aux dossiers du cabinet, sauf demande
          expresse du cabinet pour une assistance, et dans cette seule limite.
        </li>
        <li>Les sous-traitants ultérieurs ci-dessous, pour leur seul rôle.</li>
      </ul>

      <h2>5. Sous-traitants et hébergement</h2>
      <div className="my-4 overflow-x-auto">
        <table className="w-full border-collapse text-left text-sm" data-testid="sous-traitants">
          <thead>
            <tr>
              <th className="border-b border-border px-2 py-1 font-semibold text-fg">Prestataire</th>
              <th className="border-b border-border px-2 py-1 font-semibold text-fg">Rôle</th>
              <th className="border-b border-border px-2 py-1 font-semibold text-fg">Localisation</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td className="border-b border-border px-2 py-1">Amazon Web Services</td>
              <td className="border-b border-border px-2 py-1">Hébergement de la plateforme et des documents</td>
              <td className="border-b border-border px-2 py-1">Union européenne — Paris</td>
            </tr>
            <tr>
              <td className="border-b border-border px-2 py-1">Brevo, puis Amazon Web Services (SES)</td>
              <td className="border-b border-border px-2 py-1">Envoi des courriels de la plateforme</td>
              <td className="border-b border-border px-2 py-1">Union européenne</td>
            </tr>
            <tr>
              <td className="border-b border-border px-2 py-1">Fournisseur du modèle de langage désigné sur la plateforme</td>
              <td className="border-b border-border px-2 py-1">Chatbot juridique, sans données personnelles, sans conservation ni réutilisation</td>
              <td className="border-b border-border px-2 py-1">Indiquée sur la plateforme</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p>
        L’hébergement hors du Maroc est réalisé sous réserve de l’autorisation de la CNDP, obtenue
        par Maghreb Consulting avant la mise en production ; cette démarche est en cours.
      </p>

      <h2>6. Durée de conservation</h2>
      <ul>
        <li>
          Les documents juridiques d’une société sont conservés <strong>au moins dix ans</strong>,
          sauf demande contraire du cabinet dans le respect de ses propres obligations.
        </li>
        <li>
          Le compte d’un utilisateur qui quitte un cabinet est désactivé, jamais supprimé : son
          historique est conservé.
        </li>
        <li>
          À l’arrêt d’un forfait, les données du cabinet sont archivées, jamais supprimées sans sa
          demande expresse ; les archives sont conservées pendant{' '}
          <AFixer>[durée déclarée à la CNDP — déclaration en cours]</AFixer>.{' '}
          <InfoBulle
            libelle="Pourquoi cette durée n’est-elle pas indiquée ?"
            texte="La durée de conservation des archives est fixée par la déclaration des traitements auprès de la CNDP, qui est en cours. La mention surlignée sera complétée dès que cette déclaration aura abouti."
          />
        </li>
        <li>
          Essai gratuit sans souscription : l’espace passe en lecture seule pendant 30 jours, puis
          ses données sont supprimées définitivement, après avertissement.
        </li>
        <li>L’historique des contrats d’un cabinet est conservé au moins dix ans.</li>
      </ul>

      <h2>7. Vos droits</h2>
      <p>
        Les personnes dont les données sont traitées disposent des droits d’accès, de rectification
        et d’opposition prévus par la loi n° 09-08.
      </p>
      <ul>
        <li>
          Pour les données figurant dans les dossiers d’un cabinet, la demande s’adresse au
          cabinet, qui dispose des outils pour y répondre ; Maghreb Consulting lui transmet sans
          délai toute demande qu’elle recevrait directement.
        </li>
        <li>
          Pour les données de votre compte, adressez votre demande à Maghreb Consulting
          {contact ? (
            <>
              {' '}: <a href={`mailto:${contact}`} data-testid="confidentialite-contact">{contact}</a>.
            </>
          ) : (
            '.'
          )}
        </li>
      </ul>
      <p>
        Vous pouvez également saisir la CNDP (
        <a href="https://www.cndp.ma" rel="noreferrer" target="_blank">www.cndp.ma</a>).
      </p>

      <h2>8. Sécurité</h2>
      <p>Tous les forfaits bénéficient du même niveau de sécurité :</p>
      <ul>
        <li>chiffrement des échanges (HTTPS) ;</li>
        <li>double authentification ;</li>
        <li>cloisonnement strict des cabinets ;</li>
        <li>droits par rôle vérifiés côté serveur ;</li>
        <li>traçabilité des actions ;</li>
        <li>sauvegardes quotidiennes, testées par restauration ;</li>
        <li>export complet des données du cabinet.</li>
      </ul>

      <h2>9. Violation de données</h2>
      <p>
        Maghreb Consulting informe le cabinet de toute violation de données personnelles dans les
        meilleurs délais, et au plus tard 72 heures après en avoir eu
        connaissance, en précisant sa nature, ses conséquences probables et les mesures prises.
      </p>
    </LegalPageLayout>
  );
}
