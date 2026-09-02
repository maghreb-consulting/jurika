package ma.jurika.ticket.domain.state;

import ma.jurika.common.exception.ConflictException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketPriorite;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.model.TicketType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketStateMachineTest {

    private TicketStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new TicketStateMachine(List.of(
                new NouveauHandler(), new EnCoursHandler(),
                new ClotureHandler(), new AnnuleHandler()));
    }

    private Ticket ticket(TicketStatut statut, UUID assignee) {
        return new Ticket(UUID.randomUUID(), UUID.randomUUID(), "T-2026-00001",
                "Demo", TicketType.CREATION, statut, TicketPriorite.NORMALE,
                null, assignee, UUID.randomUUID(), null, null, null, null, null, Instant.now());
    }

    @Test
    @DisplayName("NOUVEAU -> EN_COURS ok si assigne")
    void nouveauToEnCoursOk() {
        Ticket t = ticket(TicketStatut.NOUVEAU, UUID.randomUUID());
        assertThatCode(() -> stateMachine.assertCanTransition(t, TicketStatut.EN_COURS,
                new TicketStatutHandler.TransitionContext("", UUID.randomUUID())))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("NOUVEAU -> EN_COURS bloque si non assigne")
    void nouveauToEnCoursRequiresAssignee() {
        Ticket t = ticket(TicketStatut.NOUVEAU, null);
        assertThatThrownBy(() -> stateMachine.assertCanTransition(t, TicketStatut.EN_COURS,
                new TicketStatutHandler.TransitionContext("", UUID.randomUUID())))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("assigne");
    }

    @Test
    @DisplayName("CLOTURE -> ANNULE autorise avec motif (>= 10 car.)")
    void clotureToAnnuleOk() {
        Ticket t = ticket(TicketStatut.CLOTURE, UUID.randomUUID());
        assertThatCode(() -> stateMachine.assertCanTransition(t, TicketStatut.ANNULE,
                new TicketStatutHandler.TransitionContext("Erreur de cloture, reouverture demandee", UUID.randomUUID())))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("CLOTURE -> ANNULE bloque sans motif suffisant")
    void clotureToAnnuleRequiresComment() {
        Ticket t = ticket(TicketStatut.CLOTURE, UUID.randomUUID());
        assertThatThrownBy(() -> stateMachine.assertCanTransition(t, TicketStatut.ANNULE,
                new TicketStatutHandler.TransitionContext("court", UUID.randomUUID())))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("10 caracteres");
    }

    @Test
    @DisplayName("ANNULE -> EN_COURS (reprise) autorise avec motif, meme sans assigne")
    void annuleToEnCoursOk() {
        Ticket t = ticket(TicketStatut.ANNULE, null);
        assertThatCode(() -> stateMachine.assertCanTransition(t, TicketStatut.EN_COURS,
                new TicketStatutHandler.TransitionContext("Reprise du dossier suite a accord client", UUID.randomUUID())))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ANNULE -> EN_COURS bloque sans motif")
    void annuleToEnCoursRequiresComment() {
        Ticket t = ticket(TicketStatut.ANNULE, UUID.randomUUID());
        assertThatThrownBy(() -> stateMachine.assertCanTransition(t, TicketStatut.EN_COURS,
                new TicketStatutHandler.TransitionContext("   ", UUID.randomUUID())))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("reprendre");
    }

    @Test
    @DisplayName("ANNULE -> CLOTURE autorise avec motif")
    void annuleToClotureOk() {
        Ticket t = ticket(TicketStatut.ANNULE, UUID.randomUUID());
        assertThatCode(() -> stateMachine.assertCanTransition(t, TicketStatut.CLOTURE,
                new TicketStatutHandler.TransitionContext("Cloture administrative du ticket annule", UUID.randomUUID())))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ANNULE -> CLOTURE bloque sans motif")
    void annuleToClotureRequiresComment() {
        Ticket t = ticket(TicketStatut.ANNULE, UUID.randomUUID());
        assertThatThrownBy(() -> stateMachine.assertCanTransition(t, TicketStatut.CLOTURE,
                new TicketStatutHandler.TransitionContext(null, UUID.randomUUID())))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cloturer");
    }

    @Test
    @DisplayName("Annulation sans commentaire bloque")
    void annuleRequiresComment() {
        Ticket t = ticket(TicketStatut.EN_COURS, UUID.randomUUID());
        assertThatThrownBy(() -> stateMachine.assertCanTransition(t, TicketStatut.ANNULE,
                new TicketStatutHandler.TransitionContext("court", UUID.randomUUID())))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("10 caracteres");
    }

    @Test
    @DisplayName("Annulation avec commentaire valide passe")
    void annuleWithCommentOk() {
        Ticket t = ticket(TicketStatut.EN_COURS, UUID.randomUUID());
        assertThatCode(() -> stateMachine.assertCanTransition(t, TicketStatut.ANNULE,
                new TicketStatutHandler.TransitionContext("Client a annule la procedure", UUID.randomUUID())))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("EN_COURS -> CLOTURE ok")
    void enCoursToClotureOk() {
        Ticket t = ticket(TicketStatut.EN_COURS, UUID.randomUUID());
        assertThatCode(() -> stateMachine.assertCanTransition(t, TicketStatut.CLOTURE,
                new TicketStatutHandler.TransitionContext(null, UUID.randomUUID())))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("NOUVEAU -> CLOTURE bloque (transition non autorisee)")
    void nouveauToClotureForbidden() {
        Ticket t = ticket(TicketStatut.NOUVEAU, UUID.randomUUID());
        assertThatThrownBy(() -> stateMachine.assertCanTransition(t, TicketStatut.CLOTURE,
                new TicketStatutHandler.TransitionContext(null, UUID.randomUUID())))
                .isInstanceOf(ConflictException.class);
    }
}
