package ma.jurika.ticket.api.dto;

import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketPriorite;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.model.TicketType;

import java.time.Instant;
import java.util.UUID;

public record TicketDto(
        UUID id,
        UUID workspaceId,
        String reference,
        String titre,
        TicketType type,
        TicketStatut statut,
        TicketPriorite priorite,
        UUID dossierId,
        UUID assigneId,
        UUID creeParId,
        String description,
        Instant deadline,
        String annulationMotif,
        Instant clotureAt,
        Instant annuleAt,
        Instant createdAt,
        /** V10 — vrai si le ticket a ete repris via un transfert de dossier. */
        boolean transferred,
        /**
         * Nom de l'employe responsable (assigne_id) resolu depuis la table users,
         * pour affichage direct dans la liste (Kanban / Tableau) sans lookup
         * supplementaire cote front. {@code null} si le ticket n'est pas assigne
         * ou si l'acteur est introuvable (compte purge).
         */
        String assignePrenom,
        String assigneNom,
        /**
         * Motif de la reprise la plus recente (transition {@code ANNULE -> EN_COURS}),
         * lu depuis {@code ticket_comments}. {@code null} si le ticket n'a jamais ete
         * repris. Distinct de {@link #annulationMotif} (historique de la derniere
         * annulation) : le front affiche l'un OU l'autre selon le statut courant.
         */
        String repriseMotif
) {
    /** Variante sans resolution de nom (create/update/transition/get d'un seul ticket). */
    public static TicketDto from(Ticket t) {
        return from(t, null, null);
    }

    /** Variante enrichie avec le « Prenom Nom » du responsable (liste des tickets). */
    public static TicketDto from(Ticket t, String assignePrenom, String assigneNom) {
        return from(t, assignePrenom, assigneNom, null);
    }

    /** Variante detail : ajoute le motif de reprise (vue detail d'un seul ticket). */
    public static TicketDto from(Ticket t, String assignePrenom, String assigneNom, String repriseMotif) {
        return new TicketDto(t.id(), t.workspaceId(), t.reference(), t.titre(),
                t.type(), t.statut(), t.priorite(), t.dossierId(), t.assigneId(),
                t.creeParId(), t.description(), t.deadline(), t.annulationMotif(),
                t.clotureAt(), t.annuleAt(), t.createdAt(),
                t.transferredAt() != null, assignePrenom, assigneNom, repriseMotif);
    }
}
