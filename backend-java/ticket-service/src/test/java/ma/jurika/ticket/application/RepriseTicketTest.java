package ma.jurika.ticket.application;

import ma.jurika.common.exception.ConflictException;
import ma.jurika.common.observability.BusinessMetrics;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketPriorite;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.model.TicketType;
import ma.jurika.ticket.domain.port.CommentRepository;
import ma.jurika.ticket.domain.port.RepriseTicket;
import ma.jurika.ticket.domain.port.TicketEventPublisher;
import ma.jurika.ticket.domain.port.TicketRepository;
import ma.jurika.ticket.domain.state.TicketStateMachine;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lot L4a (E4, RG-TKT-03 a 05) : l'annulation conserve le statut atteint et son auteur ;
 * la reprise ramene le ticket a CE statut et a lui seul, et elle est refusee si un retrait
 * a ete effectue. Avant : statut anterieur jamais conserve, reprise vers n'importe quel
 * statut (l'ecran forcait GENERATION_DOCUMENTS), aucun controle de retrait.
 */
class RepriseTicketTest {

    private static final UUID WS = UUID.randomUUID();
    private static final UUID TICKET = UUID.randomUUID();
    private static final UUID EMPLOYE = UUID.randomUUID();

    private final TicketRepository tickets = mock(TicketRepository.class);
    private final TicketStateMachine machine = mock(TicketStateMachine.class);
    private final RepriseTicket reprise = mock(RepriseTicket.class);
    private final TransitionTicketUseCase useCase = new TransitionTicketUseCase(tickets, machine,
            mock(CommentRepository.class), mock(TicketEventPublisher.class), mock(TicketDeadlineHooks.class),
            mock(BusinessMetrics.class), reprise);

    private static Ticket ticket(TicketStatut statut) {
        return new Ticket(TICKET, WS, "T-2026-00042", "Ticket", TicketType.MODIFICATION, statut,
                TicketPriorite.NORMALE, null, EMPLOYE, EMPLOYE, null, null, null, null, null, Instant.now());
    }

    @Test
    void l_annulation_conserve_le_statut_atteint_et_son_auteur() {
        when(tickets.findById(WS, TICKET)).thenReturn(Optional.of(ticket(TicketStatut.DEROULEMENT_DEMARCHE)));
        when(tickets.updateStatut(eq(TICKET), eq(TicketStatut.ANNULE), any(), any(), any()))
                .thenReturn(ticket(TicketStatut.ANNULE));
        useCase.execute(new TransitionTicketUseCase.Command(WS, TICKET, TicketStatut.ANNULE,
                "Le client renonce au projet.", EMPLOYE));
        verify(reprise).enregistrerAnnulation(WS, TICKET, TicketStatut.DEROULEMENT_DEMARCHE, EMPLOYE);
    }

    @Test
    void la_reprise_ne_ramene_qu_au_statut_anterieur() {
        when(tickets.findById(WS, TICKET)).thenReturn(Optional.of(ticket(TicketStatut.ANNULE)));
        when(reprise.statutAnterieur(WS, TICKET)).thenReturn(Optional.of(TicketStatut.DEROULEMENT_DEMARCHE));
        assertThatThrownBy(() -> useCase.execute(new TransitionTicketUseCase.Command(WS, TICKET,
                TicketStatut.GENERATION_DOCUMENTS, "Reprise demandee par le client", EMPLOYE)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("statut ant");
        verify(tickets, never()).updateStatut(any(), any(), any(), any(), any());
    }

    @Test
    void la_reprise_est_refusee_apres_un_retrait() {
        when(tickets.findById(WS, TICKET)).thenReturn(Optional.of(ticket(TicketStatut.ANNULE)));
        when(reprise.statutAnterieur(WS, TICKET)).thenReturn(Optional.of(TicketStatut.DEROULEMENT_DEMARCHE));
        when(reprise.retraitEffectue(WS, TICKET)).thenReturn(true);
        assertThatThrownBy(() -> useCase.execute(new TransitionTicketUseCase.Command(WS, TICKET,
                TicketStatut.DEROULEMENT_DEMARCHE, "Reprise demandee par le client", EMPLOYE)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("retrait");
    }

    @Test
    void la_reprise_au_statut_anterieur_aboutit_et_le_consomme() {
        when(tickets.findById(WS, TICKET)).thenReturn(Optional.of(ticket(TicketStatut.ANNULE)));
        when(reprise.statutAnterieur(WS, TICKET)).thenReturn(Optional.of(TicketStatut.DEROULEMENT_DEMARCHE));
        when(tickets.updateStatut(eq(TICKET), eq(TicketStatut.DEROULEMENT_DEMARCHE), any(), any(), any()))
                .thenReturn(ticket(TicketStatut.DEROULEMENT_DEMARCHE));
        useCase.execute(new TransitionTicketUseCase.Command(WS, TICKET, TicketStatut.DEROULEMENT_DEMARCHE,
                "Reprise demandee par le client", EMPLOYE));
        verify(reprise).effacer(WS, TICKET);
    }

    @Test
    void sans_statut_anterieur_connu_la_reprise_est_refusee() {
        when(tickets.findById(WS, TICKET)).thenReturn(Optional.of(ticket(TicketStatut.ANNULE)));
        when(reprise.statutAnterieur(WS, TICKET)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute(new TransitionTicketUseCase.Command(WS, TICKET,
                TicketStatut.CREATION_TICKET, "Reprise demandee par le client", EMPLOYE)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("introuvable");
    }

    @Test
    void l_etat_de_reprise_annonce_le_statut_retrouve_ou_la_raison_du_refus() {
        when(tickets.findById(WS, TICKET)).thenReturn(Optional.of(ticket(TicketStatut.ANNULE)));
        when(reprise.statutAnterieur(WS, TICKET)).thenReturn(Optional.of(TicketStatut.GENERATION_DOCUMENTS));
        org.assertj.core.api.Assertions.assertThat(useCase.etatReprise(WS, TICKET))
                .isEqualTo(new TransitionTicketUseCase.EtatReprise(true, TicketStatut.GENERATION_DOCUMENTS, true, null));
        when(reprise.retraitEffectue(WS, TICKET)).thenReturn(true);
        TransitionTicketUseCase.EtatReprise refus = useCase.etatReprise(WS, TICKET);
        org.assertj.core.api.Assertions.assertThat(refus.possible()).isFalse();
        org.assertj.core.api.Assertions.assertThat(refus.motif()).contains("nouveau ticket");
    }
}
