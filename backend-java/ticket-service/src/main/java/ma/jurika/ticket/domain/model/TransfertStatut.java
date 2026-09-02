package ma.jurika.ticket.domain.model;

/**
 * Cycle de vie d'une demande de transfert de dossier (V9).
 * EN_ATTENTE -> ACCEPTE | REFUSE | ANNULE. Un transfert direct superviseur
 * (VOIE B) nait directement ACCEPTE (flag {@code direct} sur la ligne).
 */
public enum TransfertStatut {
    EN_ATTENTE,
    ACCEPTE,
    REFUSE,
    ANNULE
}
