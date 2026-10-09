package ma.jurika.ticket.application;

import ma.jurika.common.audit.AuditEventEmitter;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.ticket.domain.model.DossierStatut;
import ma.jurika.ticket.domain.model.EntrepriseDossier;
import ma.jurika.ticket.domain.model.FormeJuridique;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketPriorite;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.model.TicketType;
import ma.jurika.ticket.domain.port.DossierRepository;
import ma.jurika.ticket.domain.port.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lot L1, etape E5 : fin de la reassignation libre des tickets.
 *
 * <p>RG-TKT-08 : les tickets suivent leur dossier ; un changement de responsable
 * se fait au niveau du dossier (transfert accepte ou reaffectation du superviseur).
 * {@code PATCH /tickets/{id}} ne change donc plus l'assigne (contournement de
 * RG-DOS-02 releve par l'ANALYSE). RG-DOS-01 : seul l'employe responsable du dossier
 * edite le ticket.
 */
@ExtendWith(MockitoExtension.class)
class UpdateTicketUseCaseTest {

    private static final UUID WS = UUID.randomUUID();
    private static final UUID TICKET = UUID.randomUUID();
    private static final UUID DOSSIER = UUID.randomUUID();
    private static final UUID RESPONSABLE = UUID.randomUUID();
    private static final UUID AUTRE = UUID.randomUUID();

    @Mock TicketRepository ticketRepository;
    @Mock DossierRepository dossierRepository;
    @Mock AuditEventEmitter auditEmitter;

    private UpdateTicketUseCase useCase() {
        return new UpdateTicketUseCase(ticketRepository, dossierRepository, auditEmitter);
    }

    @Test
    void changer_l_assigne_est_refuse_et_rien_n_est_modifie() {
        when(ticketRepository.findById(WS, TICKET)).thenReturn(Optional.of(ticket(RESPONSABLE)));
        when(dossierRepository.findById(WS, DOSSIER)).thenReturn(Optional.of(dossier()));

        assertThatThrownBy(() -> useCase().execute(new UpdateTicketUseCase.Command(
                WS, TICKET, "Ticket", "desc", TicketPriorite.NORMALE, AUTRE, null, RESPONSABLE)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("dossier");
        verify(ticketRepository, never()).updateAssignment(any(), any(), any(), any(), any(), any());
    }

    @Test
    void employe_non_responsable_du_dossier_404() {
        when(ticketRepository.findById(WS, TICKET)).thenReturn(Optional.of(ticket(RESPONSABLE)));
        when(dossierRepository.findById(WS, DOSSIER)).thenReturn(Optional.of(dossier()));

        assertThatThrownBy(() -> useCase().execute(new UpdateTicketUseCase.Command(
                WS, TICKET, "Titre", "desc", TicketPriorite.NORMALE, null, null, AUTRE)))
                .isInstanceOf(NotFoundException.class);
        verify(ticketRepository, never()).updateAssignment(any(), any(), any(), any(), any(), any());
    }

    @Test
    void le_responsable_edite_les_champs_sans_toucher_l_assigne() {
        when(ticketRepository.findById(WS, TICKET)).thenReturn(Optional.of(ticket(RESPONSABLE)));
        when(dossierRepository.findById(WS, DOSSIER)).thenReturn(Optional.of(dossier()));
        when(ticketRepository.updateAssignment(any(), any(), any(), any(), any(), any()))
                .thenReturn(ticket(RESPONSABLE));

        // assigneId egal a l'actuel (ou absent) : simple edition.
        useCase().execute(new UpdateTicketUseCase.Command(
                WS, TICKET, "Nouveau titre", "desc", TicketPriorite.NORMALE, RESPONSABLE, null, RESPONSABLE));

        verify(ticketRepository).updateAssignment(eq(TICKET), eq(null), eq(TicketPriorite.NORMALE),
                eq(null), eq("Nouveau titre"), eq("desc"));
        verify(auditEmitter).emit(eq(WS), eq(RESPONSABLE), eq("TICKET_UPDATED"), eq("ticket"), eq(TICKET), anyMap());
        verify(auditEmitter, never()).emit(any(), any(), eq("TICKET_ASSIGNED"), any(), any(), anyMap());
    }

    private Ticket ticket(UUID assigne) {
        return new Ticket(TICKET, WS, "T-2026-00042", "Ticket", TicketType.MODIFICATION,
                TicketStatut.CREATION_TICKET, TicketPriorite.NORMALE, DOSSIER, assigne, RESPONSABLE, "desc", null,
                null, null, null, Instant.now());
    }

    private EntrepriseDossier dossier() {
        Instant now = Instant.now();
        return new EntrepriseDossier(DOSSIER, WS, "ACME SARL", FormeJuridique.SARL,
                null, null, null, null, null, null, null, null, null, null,
                DossierStatut.ACTIVE, null, RESPONSABLE, now, now);
    }
}
