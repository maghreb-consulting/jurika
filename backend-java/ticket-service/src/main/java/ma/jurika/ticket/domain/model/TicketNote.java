package ma.jurika.ticket.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Lot L1 : note interne d'un ticket (RG-TKT-07). {@code modifieLe} null : note jamais
 * enregistree (vide, ouverte des la creation du ticket).
 */
public record TicketNote(UUID ticketId, String contenu, UUID modifiePar, Instant modifieLe) {
}
