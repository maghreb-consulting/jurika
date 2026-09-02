import { LegalPageLayout } from './LegalPageLayout';

/**
 * Sprint Beta (pricing-deploy) — TASK 6.
 *
 * <p>Conditions Generales d'Utilisation JURIKA. Base juridique SaaS B2B
 * marocain : Loi 09-08 (protection des donnees personnelles), Loi 53-05
 * (echanges electroniques + signature electronique), Code de Commerce,
 * CGI (art. 211 retention 10 ans), Loi 31-08 (consommateur — N/A B2B mais
 * structure de reference).
 *
 * <p>⚠️ Texte type "premiere edition" pour la beta. Mention 'declaration
 * CNDP en cours' assumee. A faire valider par conseil juridique avant
 * mise en production grand public (cf. PLAN_5_JOURS_BETA_PRIVEE.md).
 */
export function CguPage() {
  return (
    <LegalPageLayout
      title="Conditions Générales d'Utilisation"
      subtitle="Applicables à toute utilisation de la plateforme JURIKA — SaaS B2B de gestion juridique"
      lastUpdated="1er juin 2026"
    >
      <h2>1. Objet</h2>
      <p>
        Les présentes Conditions Générales d'Utilisation (« <strong>CGU</strong> ») régissent
        l'accès et l'utilisation de la plateforme <strong>JURIKA</strong>, service en ligne
        de gestion juridique destiné aux professionnels du droit (cabinets, juristes
        d'entreprise, fiduciaires) édité par Maghreb Consulting.
      </p>
      <p>
        L'utilisation de la plateforme implique l'acceptation pleine et entière des
        présentes CGU. Tout abonnement ou création de compte vaut acceptation.
      </p>

      <h2>2. Définitions</h2>
      <ul>
        <li><strong>Éditeur</strong> : Maghreb Consulting, cabinet sis à Casablanca, Maroc.</li>
        <li><strong>Plateforme</strong> : l'application web JURIKA et ses services associés.</li>
        <li><strong>Souscripteur</strong> : la personne morale ayant souscrit un abonnement.</li>
        <li><strong>Utilisateur</strong> : toute personne physique disposant d'un compte rattaché à un Souscripteur.</li>
        <li><strong>Workspace</strong> : espace de travail isolé alloué à un Souscripteur (RG-SAAS-01).</li>
        <li><strong>Données</strong> : ensemble des contenus saisis ou téléversés par les Utilisateurs.</li>
      </ul>

      <h2>3. Inscription et compte</h2>
      <h3>3.1 Conditions d'éligibilité</h3>
      <p>
        L'inscription est ouverte aux professionnels et entités juridiques régulièrement
        immatriculés (RC, ICE, identifiant fiscal valides au Maroc). L'Éditeur se réserve
        le droit de refuser ou suspendre toute inscription en cas de doute sérieux sur la
        qualité du Souscripteur.
      </p>
      <h3>3.2 Sécurité du compte</h3>
      <p>
        L'Utilisateur s'engage à protéger ses identifiants. Une authentification à
        deux facteurs (TOTP ou SMS) est obligatoire pour tous les comptes. Toute action
        effectuée depuis un compte est réputée engager l'Utilisateur. Les tentatives de
        connexion suspectes sont tracées dans le journal d'audit.
      </p>

      <h2>4. Formules et abonnement</h2>
      <p>
        La plateforme est commercialisée sous trois formules (Essentiel, Business, Entreprise)
        détaillées sur la page <em>Tarifs</em>. Les tarifs sont exprimés en dirhams marocains
        hors taxes (MAD HT) ; la TVA marocaine au taux en vigueur (20% à la date de
        publication) est facturée en sus.
      </p>
      <p>
        L'abonnement est conclu pour une durée mensuelle ou annuelle, renouvelable par
        tacite reconduction. Le Souscripteur peut résilier à tout moment via le portail
        client Stripe ; la résiliation prend effet à la fin de la période de facturation
        en cours.
      </p>
      <h3>4.1 Limites de plan</h3>
      <p>
        Chaque formule applique des quotas (utilisateurs, dossiers actifs, stockage). En
        cas de dépassement, l'opération est bloquée avec un code d'erreur explicite
        (<code>PLAN_LIMIT_USERS</code>, <code>PLAN_LIMIT_DOSSIERS</code>,
        <code>PLAN_LIMIT_STORAGE</code>) et un lien vers la mise à niveau.
      </p>
      <h3>4.2 Modalités de paiement</h3>
      <p>
        Les paiements sont opérés via le prestataire Stripe (PCI DSS niveau 1). L'Éditeur
        n'a accès ni au numéro de carte ni aux informations sensibles de paiement. Les
        factures sont émises automatiquement à chaque période et accessibles via le
        portail de facturation.
      </p>

      <h2>5. Obligations de l'Utilisateur</h2>
      <ul>
        <li>Utiliser la plateforme conformément à sa destination professionnelle.</li>
        <li>Ne pas tenter d'accéder à des données d'un autre Workspace (isolation RG-SAAS-01).</li>
        <li>Ne pas téléverser de contenu illicite, diffamatoire ou portant atteinte à autrui.</li>
        <li>Respecter la confidentialité des données clients (secret pro).</li>
        <li>Signaler sans délai toute compromission présumée du compte (legal@jurika.ma).</li>
      </ul>

      <h2>6. Propriété intellectuelle</h2>
      <p>
        La plateforme, son code, ses interfaces, les modèles documentaires fournis par
        l'Éditeur et tout élément non saisi par l'Utilisateur sont la propriété exclusive
        de Maghreb Consulting. Toute reproduction non autorisée est prohibée.
      </p>
      <p>
        Les données saisies ou téléversées par le Souscripteur restent sa propriété. Le
        Souscripteur accorde à l'Éditeur une licence non exclusive strictement limitée
        à l'hébergement, au traitement et à l'exécution du service.
      </p>

      <h2>7. Disponibilité et maintenance</h2>
      <p>
        L'Éditeur s'engage à maintenir un taux de disponibilité indicatif de 99,5% sur les
        plans Essentiel et Business, et 99,9% sur le plan Entreprise (SLA dédié). Les
        opérations de maintenance programmée sont annoncées au moins 48 heures à l'avance.
      </p>

      <h2>8. Données et confidentialité</h2>
      <p>
        Le traitement des données personnelles est régi par la <strong>Politique de
        Confidentialité</strong> accessible <a href="/confidentialite">ici</a>, et
        conforme à la loi marocaine 09-08 ainsi qu'au RGPD pour les Souscripteurs
        établis dans l'UE. La déclaration auprès de la CNDP (Commission Nationale de
        contrôle de la protection des Données à caractère Personnel) est en cours.
      </p>

      <h2>9. Rétention et restitution</h2>
      <p>
        Les documents à caractère comptable et fiscal sont conservés au minimum 10 ans
        conformément à l'article 211 du Code Général des Impôts marocain. À la résiliation,
        le Souscripteur dispose de 30 jours pour exporter ses données via la fonction
        <em>Export Workspace</em>. Au-delà, les données sont supprimées de manière sécurisée
        sauf obligation légale contraire.
      </p>

      <h2>10. Responsabilité</h2>
      <p>
        L'Éditeur est soumis à une obligation de moyens. Sa responsabilité ne peut être
        engagée pour les dommages indirects (perte de chiffre d'affaires, perte de clientèle).
        La responsabilité totale de l'Éditeur, toutes causes confondues, est plafonnée au
        montant des sommes effectivement payées par le Souscripteur au cours des douze
        mois précédant le fait générateur.
      </p>

      <h2>11. Résiliation</h2>
      <p>
        En cas de manquement grave aux présentes CGU non régularisé sous 15 jours suivant
        mise en demeure, l'Éditeur pourra suspendre puis résilier l'accès sans préjudice
        de tous dommages-intérêts.
      </p>

      <h2>12. Droit applicable et juridiction</h2>
      <p>
        Les présentes CGU sont régies par le droit marocain. Tout litige relatif à leur
        exécution ou interprétation sera soumis à la compétence exclusive des tribunaux
        de Casablanca, après tentative préalable de résolution amiable.
      </p>

      <h2>13. Modifications</h2>
      <p>
        L'Éditeur se réserve le droit de modifier les présentes CGU. Toute modification
        substantielle sera notifiée aux Souscripteurs par email au moins 30 jours avant
        son entrée en vigueur, conformément à RG-SAAS-09.
      </p>

      <h2>14. Contact</h2>
      <p>
        Maghreb Consulting — Casablanca, Maroc.<br />
        Courriel juridique : <a href="mailto:legal@jurika.ma">legal@jurika.ma</a><br />
        Courriel commercial : <a href="mailto:contact@jurika.ma">contact@jurika.ma</a>
      </p>
    </LegalPageLayout>
  );
}
