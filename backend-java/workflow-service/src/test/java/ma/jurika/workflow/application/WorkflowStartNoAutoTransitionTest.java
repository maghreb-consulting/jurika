package ma.jurika.workflow.application;

import jakarta.persistence.EntityManager;
import ma.jurika.workflow.domain.model.WorkflowProgress;
import ma.jurika.workflow.domain.model.WorkflowStatut;
import ma.jurika.workflow.domain.model.WorkflowType;
import ma.jurika.workflow.domain.port.WorkflowProgressRepository;
import ma.jurika.workflow.domain.strategy.WorkflowOrchestrator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Non-regression 2026-06-24 — DISSOCIATION « creer un ticket » / « prendre en charge ».
 *
 * <p>OUVRIR le wizard de workflow ({@link WorkflowUseCases#startOrResume}) ne doit
 * PLUS faire passer le ticket NOUVEAU -> EN_COURS. Un ticket cree mais pas encore
 * travaille reste visible en NOUVEAU (colonne Kanban + compteur dashboard).
 *
 * <p>L'auto-transition s'appuyait sur {@code em.createNativeQuery("UPDATE tickets ...")}.
 * On verifie donc qu'a la creation du workflow_progress, l'{@link EntityManager} n'est
 * JAMAIS sollicite — preuve directe qu'aucun UPDATE de statut n'est emis.
 *
 * <p>La prise en charge implicite se fait desormais a la validation de l'etape 1
 * (cf. {@code executeStep} -> "WORKFLOW_STEP1_VALIDATED"), couverte par les IT de
 * workflow CREATION / IMPORT, et la prise en charge explicite via le bouton
 * « Prendre en charge » (transition manuelle cote ticket-service).
 */
class WorkflowStartNoAutoTransitionTest {

    private final UUID ws = UUID.randomUUID();
    private final UUID ticket = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();

    @Test
    @DisplayName("startOrResume (ouverture du wizard) ne transitionne PAS le ticket : aucun UPDATE tickets")
    void openingWizardDoesNotTransitionTicket() {
        WorkflowProgressRepository repo = mock(WorkflowProgressRepository.class);
        WorkflowProgressLookup lookup = mock(WorkflowProgressLookup.class);
        EntityManager em = mock(EntityManager.class);

        // Pas de workflow existant -> on emprunte le chemin de creation (orElseGet).
        when(repo.findByTicket(ws, ticket)).thenReturn(Optional.empty());
        WorkflowProgress fresh = new WorkflowProgress(
                UUID.randomUUID(), ws, ticket, WorkflowType.CREATION,
                1, 9, new HashMap<>(), WorkflowStatut.EN_COURS, user, null, Instant.now());
        when(lookup.createInNewTransaction(eq(ws), eq(ticket), any(), anyInt(), eq(user)))
                .thenReturn(fresh);

        WorkflowUseCases uc = new WorkflowUseCases(
                repo, new WorkflowOrchestrator(List.of()), lookup, null, null, null);
        // L'EntityManager est injecte par @PersistenceContext en prod ; on le force ici.
        ReflectionTestUtils.setField(uc, "em", em);

        WorkflowProgress result = uc.startOrResume(ws, ticket, WorkflowType.CREATION, user);

        assertThat(result).isSameAs(fresh);
        // Avant le fix : autoTransitionTicket -> em.createNativeQuery("UPDATE tickets ...").
        // Apres le fix : aucune interaction avec l'EntityManager -> le ticket reste NOUVEAU.
        verifyNoInteractions(em);
    }
}
