package ma.jurika.auth.domain.model;

public enum WorkspaceStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    SUSPENDED,
    DEACTIVATED,
    /**
     * Lot L7 (E5), RG-TAR-07 et RG-PLN-07 : forfait arrete (impaye, fin d'abonnement, resiliation,
     * decision). Rien n'est supprime ; les acces sont fermes, sauf au superviseur, qui se connecte
     * pour exporter ses donnees, demander leur suppression ou souscrire de nouveau (RG-TAR-09).
     */
    ARCHIVED
}
