package ma.jurika.ticket.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Etat d'une demarche du referentiel sur un ticket donne.
 *
 * <p>{@code id} vaut {@code null} tant qu'aucun geste n'a ete pose : l'absence de
 * ligne en base vaut {@link DemarcheEtat#A_FAIRE}. On evite ainsi de creer 36
 * lignes mortes par ticket et de re-synchroniser a chaque version du guide.
 */
public record TicketDemarche(
        UUID id,
        Demarche demarche,
        DemarcheEtat etat,
        String motif,
        UUID acteurId,
        Instant cocheAt,
        List<UUID> justificatifsDeposes) {

    public static TicketDemarche aFaire(Demarche demarche) {
        return new TicketDemarche(null, demarche, DemarcheEtat.A_FAIRE, null, null, null, List.of());
    }

    /** Une demarche est « traitee » si elle est cochee ou explicitement ecartee. */
    public boolean traitee() {
        return etat == DemarcheEtat.COCHEE || etat == DemarcheEtat.NON_APPLICABLE;
    }
}
