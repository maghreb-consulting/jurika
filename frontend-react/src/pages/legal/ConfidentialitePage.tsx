import { LegalPageLayout } from './LegalPageLayout';

/**
 * Sprint Beta (pricing-deploy) — TASK 6.
 *
 * <p>Politique de confidentialite JURIKA. Conforme :
 *  - Loi marocaine 09-08 du 18 fevrier 2009 (protection des donnees
 *    personnelles, CNDP).
 *  - RGPD UE 2016/679 pour les Souscripteurs etablis dans l'Union.
 *  - Loi 53-05 (echanges electroniques) pour la signature et l'archivage.
 *  - CGI art. 211 (retention 10 ans documents comptables).
 *
 * <p>Statut CNDP : declaration en cours (mentionne explicitement).
 */
export function ConfidentialitePage() {
  return (
    <LegalPageLayout
      title="Politique de Confidentialité"
      subtitle="Traitement des données à caractère personnel sur la plateforme JURIKA"
      lastUpdated="1er juin 2026"
    >
      <h2>1. Responsable du traitement</h2>
      <p>
        Le responsable du traitement est <strong>Maghreb Consulting</strong>, cabinet
        juridique établi à Casablanca, Maroc, éditeur de la plateforme JURIKA.
        Contact dédié protection des données :{' '}
        <a href="mailto:dpo@jurika.ma">dpo@jurika.ma</a>.
      </p>
      <p>
        <strong>Déclaration CNDP</strong> : conformément à la loi 09-08 relative à la
        protection des personnes physiques à l'égard du traitement des données à caractère
        personnel, une déclaration auprès de la Commission Nationale de contrôle de la
        protection des Données à caractère Personnel (CNDP) est en cours d'instruction.
        Le numéro CNDP sera publié sur cette page dès attribution.
      </p>

      <h2>2. Base légale du traitement</h2>
      <ul>
        <li>
          <strong>Exécution du contrat</strong> : les données nécessaires à la fourniture
          du service JURIKA (compte, profil, dossiers, documents).
        </li>
        <li>
          <strong>Obligation légale</strong> : conservation des documents comptables et
          fiscaux conformément au CGI art. 211 (10 ans), facturation Stripe.
        </li>
        <li>
          <strong>Intérêt légitime</strong> : sécurisation de la plateforme, prévention
          de la fraude, amélioration du service (analytics anonymes agrégés).
        </li>
        <li>
          <strong>Consentement</strong> : envois d'emails marketing, cookies non-essentiels
          (retirable à tout moment).
        </li>
      </ul>

      <h2>3. Catégories de données traitées</h2>
      <h3>3.1 Données d'identification</h3>
      <p>
        Nom, prénom, email pro, numéro de téléphone, fonction, ICE/IF/RC du
        cabinet, ville, photo de profil (optionnelle).
      </p>
      <h3>3.2 Données de connexion</h3>
      <p>
        Adresse IP, agent utilisateur, horodatage des connexions, identifiants de session
        JWT, codes 2FA TOTP/SMS, codes de récupération (stockés chiffrés BCrypt).
      </p>
      <h3>3.3 Données métier</h3>
      <p>
        Dossiers clients (raison sociale, gérants, adresses, statuts), tickets, documents
        juridiques, comptables et fiscaux téléversés, échanges du chat client-employé.
        Certaines données sensibles (numéro CIN des gérants) sont chiffrées AES-256-GCM en
        base.
      </p>
      <h3>3.4 Données de facturation</h3>
      <p>
        Identifiant client Stripe, plan souscrit, factures émises. Les numéros de cartes
        bancaires <strong>ne transitent jamais</strong> par les serveurs JURIKA :
        Stripe (certifié PCI DSS niveau 1) gère l'encaissement de bout en bout.
      </p>
      <h3>3.5 Données techniques</h3>
      <p>
        Logs applicatifs, traces OpenTelemetry, métriques Prometheus, événements d'audit.
        Ces données sont anonymisées au-delà de 30 jours sauf événements de sécurité.
      </p>

      <h2>4. Finalités</h2>
      <ul>
        <li>Fourniture et exécution du service JURIKA.</li>
        <li>Gestion des comptes et de la facturation.</li>
        <li>Support client et communication relative au service.</li>
        <li>Sécurité, prévention de la fraude, audit (RG-SAAS-02).</li>
        <li>Statistiques d'usage anonymisées pour amélioration du produit.</li>
        <li>Respect des obligations légales (comptables, fiscales, judiciaires).</li>
      </ul>

      <h2>5. Destinataires</h2>
      <p>Les données sont accessibles à :</p>
      <ul>
        <li>Les Utilisateurs du Workspace concerné (selon leur rôle et permissions RBAC).</li>
        <li>Les équipes Maghreb Consulting habilitées (support, ingénierie) sous accord de confidentialité.</li>
        <li>Les sous-traitants techniques listés au §7, dans la limite stricte de leurs missions.</li>
        <li>Les autorités administratives ou judiciaires sur réquisition légale dûment motivée.</li>
      </ul>
      <p>
        L'<strong>isolation multi-tenant</strong> est garantie par Row Level Security
        PostgreSQL : aucune donnée d'un Workspace n'est techniquement accessible depuis
        un autre Workspace, même par les opérateurs JURIKA hors interventions de support
        explicitement journalisées.
      </p>

      <h2>6. Transferts hors Maroc</h2>
      <p>
        L'hébergement principal est situé sur le territoire marocain (souveraineté des
        données). Certains sous-traitants peuvent traiter des données dans l'Union
        européenne (Stripe Ireland, OVH France) sous garanties contractuelles type
        (clauses contractuelles standard, certifications ISO 27001).
      </p>

      <h2>7. Sous-traitants</h2>
      <ul>
        <li><strong>Stripe (Irlande)</strong> — paiements et facturation (PCI DSS L1).</li>
        <li><strong>Brevo / Mailjet</strong> — envoi des emails transactionnels.</li>
        <li><strong>OpenAI</strong> — assistant juridique RAG, sur demande de l'Utilisateur
            (les contenus sont anonymisés avant transmission).</li>
        <li><strong>Twilio</strong> — envoi des SMS 2FA (numéros chiffrés au repos).</li>
        <li><strong>Sentry</strong> — supervision des erreurs (PII filtrée côté client).</li>
        <li><strong>Hébergeur</strong> — datacenter Maroc (préférentiel) ou OVH France.</li>
      </ul>

      <h2>8. Durée de conservation</h2>
      <ul>
        <li><strong>Comptes actifs</strong> : pendant toute la durée de l'abonnement.</li>
        <li><strong>Documents comptables et fiscaux</strong> : 10 ans après clôture de l'exercice (CGI art. 211).</li>
        <li><strong>Factures Stripe</strong> : 10 ans (obligation comptable + fiscale).</li>
        <li><strong>Logs de sécurité et audit</strong> : 12 mois.</li>
        <li><strong>Données après résiliation</strong> : 30 jours en lecture seule pour export, puis suppression sécurisée (sauf rétention légale).</li>
        <li><strong>Cookies analytiques</strong> : 13 mois maximum.</li>
      </ul>

      <h2>9. Droits des personnes</h2>
      <p>Conformément à la loi 09-08 et au RGPD, vous disposez des droits suivants :</p>
      <ul>
        <li><strong>Accès</strong> : obtenir une copie de vos données.</li>
        <li><strong>Rectification</strong> : corriger des données inexactes.</li>
        <li><strong>Effacement</strong> : suppression, sauf obligations légales de rétention.</li>
        <li><strong>Limitation</strong> : restreindre le traitement.</li>
        <li><strong>Portabilité</strong> : recevoir vos données dans un format structuré (JSON / CSV).</li>
        <li><strong>Opposition</strong> : pour les traitements basés sur l'intérêt légitime.</li>
        <li><strong>Retrait du consentement</strong> à tout moment pour les traitements consentis.</li>
      </ul>
      <p>
        Pour exercer ces droits : <a href="mailto:dpo@jurika.ma">dpo@jurika.ma</a> avec
        copie d'un justificatif d'identité. Réponse sous 30 jours maximum. Recours possible
        auprès de la CNDP (<a href="https://www.cndp.ma" rel="noreferrer" target="_blank">cndp.ma</a>)
        en l'absence de réponse satisfaisante.
      </p>

      <h2>10. Sécurité</h2>
      <ul>
        <li>Chiffrement TLS 1.3 obligatoire pour toutes les communications.</li>
        <li>Mots de passe stockés en BCrypt (coût 12) ; jamais en clair.</li>
        <li>JWT RS256 (clés asymétriques 2048 bits) + rotation des refresh tokens.</li>
        <li>Authentification 2FA TOTP ou SMS obligatoire.</li>
        <li>Chiffrement AES-256-GCM sur les champs sensibles (CIN, numéros de pièces).</li>
        <li>Sauvegardes quotidiennes chiffrées avec rétention 30 j / 12 sem / 12 mois.</li>
        <li>Pentests annuels sur les modules critiques.</li>
        <li>Journal d'audit immuable de toutes les actions sensibles.</li>
      </ul>

      <h2>11. Cookies</h2>
      <p>
        JURIKA n'utilise que des cookies strictement nécessaires au fonctionnement
        (session, préférences linguistiques, thème clair/sombre). Aucun cookie de
        traçage publicitaire. La bannière de consentement est affichée à la première
        visite ; le choix est conservé 13 mois.
      </p>

      <h2>12. Violation de données</h2>
      <p>
        En cas de violation de données entraînant un risque pour les droits et libertés
        des personnes, l'Éditeur s'engage à notifier la CNDP dans les 72 heures et à
        informer les personnes concernées sans délai injustifié, conformément à la
        loi 09-08 et au RGPD.
      </p>

      <h2>13. Modifications</h2>
      <p>
        La présente politique peut être amendée à tout moment. Les modifications
        substantielles seront notifiées par email aux Souscripteurs avec un préavis
        minimum de 30 jours.
      </p>
    </LegalPageLayout>
  );
}
