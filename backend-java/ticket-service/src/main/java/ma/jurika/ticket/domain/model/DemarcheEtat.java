package ma.jurika.ticket.domain.model;

/** Etat d'une demarche sur un ticket donne. */
public enum DemarcheEtat {
    /** Aucun geste de l'employe : c'est aussi l'etat des demarches sans ligne en base. */
    A_FAIRE,
    /** Accomplie, justificatifs deposes. */
    COCHEE,
    /**
     * Ecartee du dossier avec un motif. Reserve aux demarches CONDITIONNELLES :
     * une demarche obligatoire ne peut pas etre mise hors perimetre.
     */
    NON_APPLICABLE
}
