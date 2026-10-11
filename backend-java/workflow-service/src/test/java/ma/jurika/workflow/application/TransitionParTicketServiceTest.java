package ma.jurika.workflow.application;

import jakarta.persistence.EntityManager;
import ma.jurika.workflow.domain.port.WorkflowProgressRepository;
import ma.jurika.workflow.domain.strategy.WorkflowOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Lot L4c (E1, RG-TKT-02, 06) : un parcours ne modifie plus le statut d'un ticket en base ;
 * il DEMANDE la transition a ticket-service (machine d'etats, controles de passage).
 * Avant : UPDATE tickets SET statut ... conditionne au seul statut de depart, et saut direct
 * de la generation a la cloture hors creation.
 */
class TransitionParTicketServiceTest {

    @Test
    void la_transition_est_demandee_a_ticket_service_sans_ecriture_en_base() {
        EntityManager em = mock(EntityManager.class);
        TransitionsTicket port = mock(TransitionsTicket.class);
        UUID ws = UUID.randomUUID(), ticket = UUID.randomUUID(), user = UUID.randomUUID();
        when(port.demander(ws, ticket, "GENERATION_DOCUMENTS", "DEROULEMENT_DEMARCHE", "Parcours : WORKFLOW_COMPLETED", user))
                .thenReturn(new TransitionsTicket.Reponse("REFUSEE", "GENERATION_DOCUMENTS", "Demarche 2 non cochee"));
        WorkflowUseCases uc = new WorkflowUseCases(mock(WorkflowProgressRepository.class),
                new WorkflowOrchestrator(List.of()), mock(WorkflowProgressLookup.class), null, null, null);
        ReflectionTestUtils.setField(uc, "em", em);
        uc.setTransitionsTicket(port);

        ReflectionTestUtils.invokeMethod(uc, "autoTransitionTicket", ws, ticket, user,
                "GENERATION_DOCUMENTS", "DEROULEMENT_DEMARCHE", "WORKFLOW_COMPLETED");

        verify(port).demander(ws, ticket, "GENERATION_DOCUMENTS", "DEROULEMENT_DEMARCHE",
                "Parcours : WORKFLOW_COMPLETED", user);
        verifyNoInteractions(em);
    }

    @Test
    void aucun_service_hors_ticket_n_ecrit_le_statut_d_un_ticket() throws IOException {
        Path racine = Path.of("..").toAbsolutePath().normalize();
        try (Stream<Path> sources = Files.walk(racine)) {
            List<Path> fautifs = sources
                    .filter(p -> p.toString().endsWith(".java") && p.toString().contains("/src/main/")
                            && !p.toString().contains("/ticket-service/"))
                    .filter(p -> {
                        try {
                            return Files.readString(p).matches("(?s).*UPDATE\\s+tickets\\s+SET\\s+statut\\b.*");
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    }).toList();
            assertThat(fautifs).as("UPDATE du statut d'un ticket hors ticket-service").isEmpty();
        }
    }
}
