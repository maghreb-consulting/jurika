package ma.jurika.ticket.domain.model;

/**
 * Lot L1 : nature d'un changement de responsable de dossier (table
 * {@code dossier_reaffectations}, migration V28).
 */
public enum NatureReaffectation {
    /** Transfert propose par le responsable et accepte par le destinataire (RG-DOS-02). */
    ACCEPTEE,
    /** Reaffectation d'office par le superviseur (RG-DOS-03). */
    FORCEE,
    /** Attribution par la migration V28 (dossier sans responsable). */
    RATTRAPAGE
}
