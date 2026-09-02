package ma.jurika.ticket.domain.model;

import java.time.Duration;
import java.time.Period;

/**
 * Catalogue des regles de calcul automatique d'echeance.
 * <p>
 * Chaque regle est declenchee par un evenement metier (transition statut ticket,
 * upload document, completion d'etape workflow...) et calcule une date d'echeance
 * relative a l'evenement.
 * <p>
 * Reference : AGENT_BRIEF.md section 6.3 + Regles de Gestion V2 (RG-CN-90j, RG-RC-3M, RG-CNSS-30j).
 */
public enum DeadlineRule {

    /**
     * Le Certificat Negatif (OMPIC) est valide 90 jours a compter de sa delivrance.
     * Declenche : reception CN (etape 1 workflow Creation).
     * Severite : WARNING a J-15, CRITICAL a J-3.
     */
    CN_EXPIRY_90D(
            "Expiration du Certificat Negatif",
            "Le CN doit etre utilise pour deposer au RC avant son expiration (90 jours).",
            Period.ofDays(90),
            DeadlineSeverity.WARNING),

    /**
     * Le depot au Registre de Commerce doit intervenir dans les 3 mois suivant la signature
     * des statuts (loi 5-96).
     * Declenche : ticket Creation passe en signatures completes.
     */
    RC_DEPOT_3M(
            "Depot au Registre de Commerce",
            "L'immatriculation au RC doit etre realisee dans les 3 mois suivant la signature des statuts.",
            Period.ofMonths(3),
            DeadlineSeverity.WARNING),

    /**
     * Declaration CNSS obligatoire dans les 30 jours suivant l'immatriculation au RC.
     * Declenche : remplissage du numero RC.
     */
    CNSS_DECL_30D(
            "Declaration CNSS",
            "La declaration d'affiliation CNSS doit etre faite dans les 30 jours suivant le RC.",
            Period.ofDays(30),
            DeadlineSeverity.WARNING),

    /**
     * Cloture comptable annuelle : 6 mois apres la fin de l'exercice (art. 21 loi 9-88).
     * Declenche : changement annee exercice (uplaod premier document comptable).
     */
    CLOTURE_COMPTABLE_6M(
            "Cloture comptable annuelle",
            "Les comptes annuels doivent etre approuves dans les 6 mois suivant la cloture de l'exercice.",
            Period.ofMonths(6),
            DeadlineSeverity.INFO),

    /**
     * Liquidation : delai non-bloquant de 16 jours pour la publication.
     * Declenche : ticket Liquidation passe en EN_COURS.
     */
    LIQUIDATION_PUBLI_16J(
            "Publication de la liquidation",
            "La publication de la decision de liquidation doit intervenir dans les 16 jours (delai indicatif).",
            Period.ofDays(16),
            DeadlineSeverity.INFO),

    /**
     * Alerte de retard : un ticket sans transition de statut depuis 7 jours est suspect.
     * Declenche : evaluation periodique (a brancher Phase 5+).
     */
    STEP_STALE_7D(
            "Ticket sans progression",
            "Aucune transition depuis 7 jours. A relancer ou cloturer.",
            Period.ofDays(7),
            DeadlineSeverity.WARNING);

    private final String defaultTitle;
    private final String defaultDescription;
    private final Period offset;
    private final DeadlineSeverity defaultSeverity;

    DeadlineRule(String defaultTitle, String defaultDescription,
                 Period offset, DeadlineSeverity defaultSeverity) {
        this.defaultTitle = defaultTitle;
        this.defaultDescription = defaultDescription;
        this.offset = offset;
        this.defaultSeverity = defaultSeverity;
    }

    public String defaultTitle() { return defaultTitle; }
    public String defaultDescription() { return defaultDescription; }
    public Period offset() { return offset; }
    public DeadlineSeverity defaultSeverity() { return defaultSeverity; }
}
