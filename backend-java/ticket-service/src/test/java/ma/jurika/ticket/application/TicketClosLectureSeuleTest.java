package ma.jurika.ticket.application;

import ma.jurika.common.exception.ConflictException;
import ma.jurika.common.security.TenantContext;
import ma.jurika.ticket.domain.model.Debours;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.port.DeboursRepository;
import ma.jurika.ticket.domain.port.TicketRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lot L0, etape E23 (perimetre § E, RG-TKT-11) : un ticket clos
 * ({@code CLOTURE_DOSSIER}) est en lecture seule, garde cote serveur : aucune
 * ecriture de debours (ajout, modification, suppression).
 */
class TicketClosLectureSeuleTest {

    private final UUID ws = UUID.randomUUID();
    private final UUID ticketId = UUID.randomUUID();
    private final UUID deboursId = UUID.randomUUID();
    private DeboursRepository debours;
    private TicketRepository tickets;
    private DeboursUseCase useCase;

    @BeforeEach
    void setUp() {
        debours = mock(DeboursRepository.class);
        tickets = mock(TicketRepository.class);
        useCase = new DeboursUseCase(debours, tickets);
        Debours d = mock(Debours.class);
        when(d.ticketId()).thenReturn(ticketId);
        when(debours.findById(ws, deboursId)).thenReturn(Optional.of(d));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void ticketEn(TicketStatut statut) {
        Ticket t = mock(Ticket.class);
        when(t.statut()).thenReturn(statut);
        when(tickets.findById(ws, ticketId)).thenReturn(Optional.of(t));
    }

    private DeboursUseCase.CreateCommand ajout() {
        return new DeboursUseCase.CreateCommand(ws, ticketId, "Timbre", null,
                new BigDecimal("100"), LocalDate.of(2026, 10, 8), null, null, null, UUID.randomUUID());
    }

    @Test
    void ticket_clos_refuse_toute_ecriture_de_debours() {
        ticketEn(TicketStatut.CLOTURE_DOSSIER);

        assertThatThrownBy(() -> useCase.create(ajout())).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> useCase.update(new DeboursUseCase.UpdateCommand(ws, deboursId, "x", null,
                BigDecimal.ONE, LocalDate.of(2026, 10, 8), null))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> useCase.delete(ws, deboursId)).isInstanceOf(ConflictException.class);
        verify(debours, never()).create(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(debours, never()).delete(any(), any());
    }

    @Test
    void ticket_en_cours_accepte_les_ecritures() {
        ticketEn(TicketStatut.DEROULEMENT_DEMARCHE);
        assertThatCode(() -> useCase.create(ajout())).doesNotThrowAnyException();
        assertThatCode(() -> useCase.delete(ws, deboursId)).doesNotThrowAnyException();
    }
}
