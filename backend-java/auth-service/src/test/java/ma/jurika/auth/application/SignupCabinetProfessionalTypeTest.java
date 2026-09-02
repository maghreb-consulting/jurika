package ma.jurika.auth.application;

import ma.jurika.auth.domain.port.IpRateLimiter;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.auth.infrastructure.persistence.WorkspaceEntity;
import ma.jurika.auth.infrastructure.persistence.WorkspaceJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Onboarding 2026-06-24 — couvre l'ajout du type de profil au signup cabinet :
 *  - une valeur valide est normalisee + persistee sur le workspace ;
 *  - une valeur invalide est rejetee avant toute creation ;
 *  - l'absence de valeur (ancien client) est toleree (NULL persiste).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SignupCabinetProfessionalTypeTest {

    @Mock RegisterWorkspaceUseCase registerWorkspaceUseCase;
    @Mock WorkspaceJpaRepository workspaceJpaRepository;
    @Mock UserRepository userRepository;
    @Mock IpRateLimiter rateLimiter;

    private SignupCabinetUseCase useCase() {
        return new SignupCabinetUseCase(registerWorkspaceUseCase, workspaceJpaRepository,
                userRepository, rateLimiter, false);
    }

    private SignupCabinetUseCase.Command command(String professionalType) {
        return command("Cabinet Test", professionalType);
    }

    private SignupCabinetUseCase.Command command(String workspaceName, String professionalType) {
        return new SignupCabinetUseCase.Command(
                workspaceName,
                "Karim", "Benali", "+212600000000", "karim@example.ma",
                "123456789012345", "Casablanca",
                "essentiel", professionalType, "127.0.0.1", "JUnit");
    }

    private WorkspaceEntity stubCreatedWorkspace() {
        UUID wsId = UUID.randomUUID();
        when(rateLimiter.tryAcquire(eq("signup-cabinet"), any())).thenReturn(true);
        when(registerWorkspaceUseCase.execute(any())).thenReturn(
                new RegisterWorkspaceUseCase.Result(wsId, "JUR-AB123", UUID.randomUUID(), true));
        WorkspaceEntity entity = new WorkspaceEntity();
        entity.setId(wsId);
        when(workspaceJpaRepository.findById(wsId)).thenReturn(Optional.of(entity));
        when(workspaceJpaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        return entity;
    }

    @Test
    void persistsProfessionalTypeWhenProvided() {
        WorkspaceEntity entity = stubCreatedWorkspace();

        useCase().execute(command("AVOCAT"));

        ArgumentCaptor<WorkspaceEntity> saved = ArgumentCaptor.forClass(WorkspaceEntity.class);
        verify(workspaceJpaRepository).save(saved.capture());
        assertThat(saved.getValue().getProfessionalType()).isEqualTo("AVOCAT");
        assertThat(entity.getProfessionalType()).isEqualTo("AVOCAT");
    }

    @Test
    void normalizesCaseAndWhitespace() {
        WorkspaceEntity entity = stubCreatedWorkspace();

        useCase().execute(command("  expert_comptable "));

        assertThat(entity.getProfessionalType()).isEqualTo("EXPERT_COMPTABLE");
    }

    @Test
    void toleratesNullProfessionalType() {
        WorkspaceEntity entity = stubCreatedWorkspace();

        useCase().execute(command(null));

        assertThat(entity.getProfessionalType()).isNull();
    }

    @Test
    void usesFallbackNameWhenBlankForIndividualType() {
        stubCreatedWorkspace();

        // Profil individuel (AVOCAT) sans denomination -> fallback « Prenom Nom ».
        useCase().execute(command("", "AVOCAT"));

        ArgumentCaptor<RegisterWorkspaceUseCase.Command> cmd =
                ArgumentCaptor.forClass(RegisterWorkspaceUseCase.Command.class);
        verify(registerWorkspaceUseCase).execute(cmd.capture());
        assertThat(cmd.getValue().workspaceName()).isEqualTo("Karim Benali");
    }

    @Test
    void blocksBlankNameForStructureType() {
        when(rateLimiter.tryAcquire(eq("signup-cabinet"), any())).thenReturn(true);

        // Structure (ENTREPRISE) sans raison sociale -> rejet avant toute creation.
        assertThatThrownBy(() -> useCase().execute(command("   ", "ENTREPRISE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Raison sociale");

        verify(registerWorkspaceUseCase, never()).execute(any());
    }

    @Test
    void rejectsInvalidProfessionalType() {
        when(rateLimiter.tryAcquire(eq("signup-cabinet"), any())).thenReturn(true);

        assertThatThrownBy(() -> useCase().execute(command("HACKER")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Type de profil");

        // Aucune creation ne doit avoir ete tentee.
        verify(registerWorkspaceUseCase, never()).execute(any());
    }
}
