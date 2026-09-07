package ma.jurika.ticket.domain.state;

import ma.jurika.common.exception.ConflictException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketPriorite;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.model.TicketType;
import ma.jurika.ticket.domain.port.TransitionChecks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Machine a etats des CINQ statuts du guide cabinet.
 *
 * <p>Les points de controle metier ({@link TransitionChecks}) sont neutralises
 * ici : ils ont leur propre test. Ce qui est verifie ici, c'est la TOPOLOGIE du
 * parcours et les motifs obligatoires.
 */
class TicketStateMachineTest {

    /** Feu vert systematique : on isole la topologie des controles metier. */
    private TransitionChecks sansObstacle;
    /** Trace des appels, pour prouver QUAND les controles sont sollicites. */
    private List<String> appels;
    private TicketStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        appels = new ArrayList<>();
        sansObstacle = (ticket, cible) -> {
            appels.add(ticket.statut() + "->" + cible);
            return List.of();
        };
        stateMachine = new TicketStateMachine(List.of(
                new CreationTicketHandler(sansObstacle),
                new GenerationDocumentsHandler(sansObstacle),
                new DeroulementDemarcheHandler(sansObstacle),
                new ClotureDossierHandler(sansObstacle),
                new AnnuleHandler()));
    }

    private Ticket ticket(TicketStatut statut, UUID assignee) {
        return new Ticket(UUID.randomUUID(), UUID.randomUUID(), "T-2026-00001",
                "Demo", TicketType.CREATION, statut, TicketPriorite.NORMALE,
                null, assignee, UUID.randomUUID(), null, null, null, null, null, Instant.now());
    }

    private void transition(Ticket t, TicketStatut cible, String motif) {
        stateMachine.assertCanTransition(t, cible,
                new TicketStatutHandler.TransitionContext(motif, UUID.randomUUID()));
    }

    // =================================================================
    //  Parcours nominal
    // =================================================================

    @Test
    @DisplayName("Creation du ticket -> Generation des documents : ok si assigne")
    void etape1Vers2() {
        Ticket t = ticket(TicketStatut.CREATION_TICKET, UUID.randomUUID());
        assertThatCode(() -> transition(t, TicketStatut.GENERATION_DOCUMENTS, ""))
                .doesNotThrowAnyException();
        assertThat(appels).containsExactly("CREATION_TICKET->GENERATION_DOCUMENTS");
    }

    @Test
    @DisplayName("Creation du ticket -> Generation des documents : bloque si non assigne")
    void etape1Vers2SansAssigne() {
        Ticket t = ticket(TicketStatut.CREATION_TICKET, null);
        assertThatThrownBy(() -> transition(t, TicketStatut.GENERATION_DOCUMENTS, ""))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("assigne");
    }

    @Test
    @DisplayName("Generation des documents -> Deroulement de la demarche : ok")
    void etape2Vers3() {
        Ticket t = ticket(TicketStatut.GENERATION_DOCUMENTS, UUID.randomUUID());
        assertThatCode(() -> transition(t, TicketStatut.DEROULEMENT_DEMARCHE, null))
                .doesNotThrowAnyException();
        assertThat(appels).containsExactly("GENERATION_DOCUMENTS->DEROULEMENT_DEMARCHE");
    }

    @Test
    @DisplayName("Deroulement de la demarche -> Cloture de dossier : ok")
    void etape3Vers4() {
        Ticket t = ticket(TicketStatut.DEROULEMENT_DEMARCHE, UUID.randomUUID());
        assertThatCode(() -> transition(t, TicketStatut.CLOTURE_DOSSIER, null))
                .doesNotThrowAnyException();
        assertThat(appels).containsExactly("DEROULEMENT_DEMARCHE->CLOTURE_DOSSIER");
    }

    // =================================================================
    //  Enonce, test 1 : toute transition non prevue est refusee
    // =================================================================

    @Test
    @DisplayName("Sauter un statut est refuse : generation -> cloture")
    void sautDeStatutRefuse() {
        Ticket t = ticket(TicketStatut.GENERATION_DOCUMENTS, UUID.randomUUID());
        assertThatThrownBy(() -> transition(t, TicketStatut.CLOTURE_DOSSIER, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Transition invalide");
        assertThat(appels).as("le controle metier ne doit meme pas etre sollicite").isEmpty();
    }

    @Test
    @DisplayName("Revenir en arriere est refuse : deroulement -> generation")
    void retourArriereRefuse() {
        Ticket t = ticket(TicketStatut.DEROULEMENT_DEMARCHE, UUID.randomUUID());
        assertThatThrownBy(() -> transition(t, TicketStatut.GENERATION_DOCUMENTS, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Transition invalide");
    }

    @Test
    @DisplayName("Sauter deux statuts est refuse : creation -> deroulement")
    void doubleSautRefuse() {
        Ticket t = ticket(TicketStatut.CREATION_TICKET, UUID.randomUUID());
        assertThatThrownBy(() -> transition(t, TicketStatut.DEROULEMENT_DEMARCHE, null))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("Rester au meme statut est refuse")
    void memeStatutRefuse() {
        Ticket t = ticket(TicketStatut.DEROULEMENT_DEMARCHE, UUID.randomUUID());
        assertThatThrownBy(() -> transition(t, TicketStatut.DEROULEMENT_DEMARCHE, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("deja au statut");
    }

    @Test
    @DisplayName("Un point de controle non satisfait bloque, et son libelle remonte")
    void obstacleMetierBloque() {
        TicketStateMachine avecObstacle = new TicketStateMachine(List.of(
                new CreationTicketHandler(sansObstacle),
                new GenerationDocumentsHandler((t, c) ->
                        List.of("Demarche 2 (obligatoire) non cochee : KYC")),
                new DeroulementDemarcheHandler(sansObstacle),
                new ClotureDossierHandler(sansObstacle),
                new AnnuleHandler()));
        Ticket t = ticket(TicketStatut.CREATION_TICKET, UUID.randomUUID());
        assertThatThrownBy(() -> avecObstacle.assertCanTransition(t,
                TicketStatut.GENERATION_DOCUMENTS,
                new TicketStatutHandler.TransitionContext("", UUID.randomUUID())))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Demarche 2 (obligatoire) non cochee : KYC");
    }

    // =================================================================
    //  Enonce, test 7 : annulation depuis chacun des quatre autres statuts
    // =================================================================

    @ParameterizedTest(name = "annulation depuis {0}")
    @EnumSource(value = TicketStatut.class, names = {
            "CREATION_TICKET", "GENERATION_DOCUMENTS", "DEROULEMENT_DEMARCHE", "CLOTURE_DOSSIER"})
    @DisplayName("Ticket annule est accessible depuis chacun des quatre autres statuts")
    void annulationDepuisChaqueStatut(TicketStatut source) {
        Ticket t = ticket(source, UUID.randomUUID());
        assertThatCode(() -> transition(t, TicketStatut.ANNULE, "Abandon du client, dossier sans suite"))
                .doesNotThrowAnyException();
        assertThat(appels).as("l'annulation n'attend aucune demarche accomplie").isEmpty();
    }

    @ParameterizedTest(name = "motif exige depuis {0}")
    @EnumSource(value = TicketStatut.class, names = {
            "CREATION_TICKET", "GENERATION_DOCUMENTS", "DEROULEMENT_DEMARCHE", "CLOTURE_DOSSIER"})
    @DisplayName("Le motif d'annulation est obligatoire, quel que soit le statut de depart")
    void annulationExigeUnMotif(TicketStatut source) {
        Ticket t = ticket(source, UUID.randomUUID());
        assertThatThrownBy(() -> transition(t, TicketStatut.ANNULE, "court"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("10 caracteres");
    }

    // =================================================================
    //  Reprise d'un ticket annule
    // =================================================================

    @ParameterizedTest(name = "reprise vers {0}")
    @EnumSource(value = TicketStatut.class, names = {
            "CREATION_TICKET", "GENERATION_DOCUMENTS", "DEROULEMENT_DEMARCHE", "CLOTURE_DOSSIER"})
    @DisplayName("Un ticket annule peut etre repris vers chacun des quatre statuts, avec motif")
    void repriseVersChaqueStatut(TicketStatut cible) {
        Ticket t = ticket(TicketStatut.ANNULE, null);
        assertThatCode(() -> transition(t, cible, "Reprise du dossier suite a accord client"))
                .doesNotThrowAnyException();
        assertThat(appels).as("une reprise ne rejoue pas les points de controle du statut vise")
                .isEmpty();
    }

    @ParameterizedTest(name = "reprise sans motif vers {0}")
    @EnumSource(value = TicketStatut.class, names = {
            "CREATION_TICKET", "GENERATION_DOCUMENTS", "DEROULEMENT_DEMARCHE", "CLOTURE_DOSSIER"})
    @DisplayName("La reprise sans motif est refusee")
    void repriseSansMotifRefusee(TicketStatut cible) {
        Ticket t = ticket(TicketStatut.ANNULE, UUID.randomUUID());
        assertThatThrownBy(() -> transition(t, cible, "   "))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("reprendre");
    }

    @Test
    @DisplayName("Aucun statut n'est terminal : cloture et annule gardent une sortie")
    void aucunStatutTerminal() {
        for (TicketStatut s : TicketStatut.values()) {
            assertThat(s.isTerminal()).as("statut %s", s).isFalse();
        }
    }
}
