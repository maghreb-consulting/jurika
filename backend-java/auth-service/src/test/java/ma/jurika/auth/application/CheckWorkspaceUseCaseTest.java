package ma.jurika.auth.application;

import ma.jurika.auth.domain.model.Workspace;
import ma.jurika.auth.domain.model.WorkspaceStatus;
import ma.jurika.auth.domain.port.WorkspaceRepository;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckWorkspaceUseCaseTest {

    @Mock
    WorkspaceRepository workspaceRepository;

    @Test
    void returnsWorkspaceWhenFoundAndActive() {
        Workspace ws = new Workspace(UUID.randomUUID(), "JUR-DEMO1", "Cabinet Demo",
                "demo@jurika.ma", null, WorkspaceStatus.ACTIVE, Instant.now(), Instant.now());
        when(workspaceRepository.findByCode("JUR-DEMO1")).thenReturn(Optional.of(ws));

        var result = new CheckWorkspaceUseCase(workspaceRepository).execute("JUR-DEMO1");

        assertThat(result.workspaceId()).isEqualTo(ws.id());
        assertThat(result.name()).isEqualTo("Cabinet Demo");
    }

    @Test
    void throwsNotFoundForUnknownCode() {
        when(workspaceRepository.findByCode("JUR-XXXXX")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new CheckWorkspaceUseCase(workspaceRepository).execute("JUR-XXXXX"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void throwsUnauthorizedForSuspended() {
        Workspace ws = new Workspace(UUID.randomUUID(), "JUR-DEMO1", "Cabinet",
                "demo@jurika.ma", null, WorkspaceStatus.SUSPENDED, Instant.now(), Instant.now());
        when(workspaceRepository.findByCode("JUR-DEMO1")).thenReturn(Optional.of(ws));

        assertThatThrownBy(() -> new CheckWorkspaceUseCase(workspaceRepository).execute("JUR-DEMO1"))
                .isInstanceOf(UnauthorizedException.class);
    }
}
