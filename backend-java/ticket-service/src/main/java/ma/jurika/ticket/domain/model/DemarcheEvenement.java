package ma.jurika.ticket.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Un geste pose sur une demarche, et conserve.
 *
 * <p>Le cochage n'est pas une case a decocher : c'est un JOURNAL. Une demarche
 * cochee, annulee, puis recochee garde la trace des TROIS evenements — et les
 * deux horodatages, celui du cochage et celui de l'annulation, sont conserves
 * tous les deux.
 *
 * <p>La raison n'est pas theorique. Sur des demarches administratives reelles,
 * « quand avons-nous depose ? » se pose des mois plus tard, et la reponse ne doit
 * pas dependre de ce qu'on a fait de la case depuis.
 *
 * @param motif obligatoire pour {@link Type#ANNULATION} et
 *              {@link Type#HORS_PERIMETRE} — la base le refuse, pas seulement le code
 */
public record DemarcheEvenement(
        UUID id,
        UUID ticketDemarcheId,
        Type type,
        String motif,
        UUID acteurId,
        Instant survenuLe,
        int justificatifs) {

    public enum Type {
        /** La demarche est accomplie. */
        COCHAGE,
        /** Un cochage est annule, par l'employe, avec motif. */
        ANNULATION,
        /** Une demarche conditionnelle est ecartee du dossier, avec motif. */
        HORS_PERIMETRE,
        /** Une demarche ecartee revient au perimetre. */
        REPRISE
    }
}
