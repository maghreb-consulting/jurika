package ma.jurika.ticket.application;

import ma.jurika.ticket.domain.model.DeadlineRule;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketPriorite;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.model.TicketType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Cablage des regles d'echeance par type de ticket / evenement de transition.
 * <p>
 * Correctif 2026-07-19 : un ticket CREATION passant en EN_COURS doit generer
 * l'echeance CN_EXPIRY_90D (le panneau "Echeances du ticket" etait toujours vide).
 */
class TicketDeadlineHooksTest {

    private final DeadlineUseCase deadlineUseCase = mock(DeadlineUseCase.class);
    private final TicketDeadlineHooks hooks = new TicketDeadlineHooks(deadlineUseCase);

    private final Instant createdAt = Instant.parse("2026-07-01T09:00:00Z");

    private Ticket ticket(TicketType type) {
        return new Ticket(UUID.randomUUID(), UUID.randomUUID(), "T-1", "Titre",
                type, TicketStatut.EN_COURS, TicketPriorite.NORMALE,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "desc",
                null, null, null, null, createdAt);
    }

    @Test
    void creation_en_cours_declenche_cn_expiry_90j() {
        hooks.onTransition(ticket(TicketType.CREATION), TicketStatut.NOUVEAU, TicketStatut.EN_COURS);

        ArgumentCaptor<DeadlineUseCase.AutoComputeCommand> cap =
                ArgumentCaptor.forClass(DeadlineUseCase.AutoComputeCommand.class);
        verify(deadlineUseCase).computeAuto(cap.capture());
        assertThat(cap.getValue().rule()).isEqualTo(DeadlineRule.CN_EXPIRY_90D);
        assertThat(cap.getValue().anchor()).isEqualTo(createdAt);
    }

    @Test
    void liquidation_en_cours_conserve_publi_16j() {
        hooks.onTransition(ticket(TicketType.LIQUIDATION), TicketStatut.NOUVEAU, TicketStatut.EN_COURS);

        ArgumentCaptor<DeadlineUseCase.AutoComputeCommand> cap =
                ArgumentCaptor.forClass(DeadlineUseCase.AutoComputeCommand.class);
        verify(deadlineUseCase).computeAuto(cap.capture());
        assertThat(cap.getValue().rule()).isEqualTo(DeadlineRule.LIQUIDATION_PUBLI_16J);
    }

    @Test
    void autre_type_ne_declenche_aucune_echeance() {
        hooks.onTransition(ticket(TicketType.MODIFICATION), TicketStatut.NOUVEAU, TicketStatut.EN_COURS);
        verify(deadlineUseCase, never()).computeAuto(any());
    }

    @Test
    void transition_non_en_cours_ne_declenche_rien() {
        hooks.onTransition(ticket(TicketType.CREATION), TicketStatut.EN_COURS, TicketStatut.CLOTURE);
        verify(deadlineUseCase, never()).computeAuto(any());
    }

    @Test
    void echec_calcul_ne_propage_pas_l_exception() {
        // best-effort : une exception du calcul ne doit pas casser la transition
        org.mockito.Mockito.when(deadlineUseCase.computeAuto(any()))
                .thenThrow(new RuntimeException("boom"));
        hooks.onTransition(ticket(TicketType.CREATION), TicketStatut.NOUVEAU, TicketStatut.EN_COURS);
        // pas d'exception remontee = OK
    }
}
