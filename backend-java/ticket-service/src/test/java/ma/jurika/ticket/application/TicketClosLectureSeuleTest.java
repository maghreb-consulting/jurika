package ma.jurika.ticket.application;

import ma.jurika.common.audit.AuditEventEmitter;
import ma.jurika.common.exception.ConflictException;
import ma.jurika.common.security.TenantContext;
import ma.jurika.ticket.domain.model.Debours;
import ma.jurika.ticket.domain.model.DeboursCategorie;
import ma.jurika.ticket.domain.model.DeboursSaisie;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.port.DataroomDocumentLookup;
import ma.jurika.ticket.domain.port.DeboursRepository;
import ma.jurika.ticket.domain.port.TicketRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Lot L0, etape E23, puis lot L5 (E6) (RG-TKT-11) : un ticket clos ({@code CLOTURE_DOSSIER})
 * ou annule ({@code ANNULE}) est en lecture seule, garde cote serveur : aucune ecriture de
 * debours (ajout, modification, suppression), et rien n'est trace.
 */
class TicketClosLectureSeuleTest {

    private final UUID ws = UUID.randomUUID();
    private final UUID ticketId = UUID.randomUUID();
    private final UUID deboursId = UUID.randomUUID();
    private final UUID employe = UUID.randomUUID();
    private DeboursRepository debours;
    private TicketRepository tickets;
    private AuditEventEmitter audit;
    private DeboursUseCase useCase;

    @BeforeEach
    void setUp() {
        debours = mock(DeboursRepository.class);
        tickets = mock(TicketRepository.class);
        audit = mock(AuditEventEmitter.class);
        useCase = new DeboursUseCase(debours, tickets, mock(DataroomDocumentLookup.class), audit);
        Debours d = new Debours(deboursId, ws, ticketId, "Timbre", DeboursCategorie.TIMBRES, BigDecimal.TEN,
                LocalDate.of(2026, 10, 8), null, null, null, employe, null, null, null);
        when(debours.findById(ws, deboursId)).thenReturn(Optional.of(d));
        when(debours.create(any(), any(), any(), any())).thenReturn(d);
        when(debours.update(any(), any(), any())).thenReturn(d);
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

    private static DeboursSaisie saisie() {
        return new DeboursSaisie("Timbre", DeboursCategorie.TIMBRES, new BigDecimal("100"),
                LocalDate.of(2026, 10, 8), null, null, null);
    }

    @ParameterizedTest
    @EnumSource(value = TicketStatut.class, names = {"CLOTURE_DOSSIER", "ANNULE"})
    void ticket_clos_ou_annule_refuse_toute_ecriture_de_debours(TicketStatut statut) {
        ticketEn(statut);

        assertThatThrownBy(() -> useCase.ajouter(ws, employe, ticketId, saisie())).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> useCase.modifier(ws, employe, ticketId, deboursId, saisie()))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> useCase.supprimer(ws, employe, ticketId, deboursId))
                .isInstanceOf(ConflictException.class);
        verify(debours, never()).create(any(), any(), any(), any());
        verify(debours, never()).update(any(), any(), any());
        verify(debours, never()).delete(any(), any());
        verifyNoInteractions(audit);
    }

    @Test
    void ticket_en_cours_accepte_les_ecritures() {
        ticketEn(TicketStatut.DEROULEMENT_DEMARCHE);
        assertThatCode(() -> useCase.ajouter(ws, employe, ticketId, saisie())).doesNotThrowAnyException();
        assertThatCode(() -> useCase.modifier(ws, employe, ticketId, deboursId, saisie())).doesNotThrowAnyException();
        assertThatCode(() -> useCase.supprimer(ws, employe, ticketId, deboursId)).doesNotThrowAnyException();
    }
}
