package ma.jurika.auth.application;

import ma.jurika.auth.domain.model.Workspace;
import ma.jurika.auth.domain.port.WorkspaceRepository;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.UnauthorizedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class CheckWorkspaceUseCase {

    private final WorkspaceRepository workspaceRepository;

    public CheckWorkspaceUseCase(WorkspaceRepository workspaceRepository) {
        this.workspaceRepository = workspaceRepository;
    }

    public record Result(UUID workspaceId, String name) {}

    // Lot L0 (E13a) : lecture en transaction, pour que le workspace pose par
    // ContexteWorkspacePublic atteigne la RLS.
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Result execute(String code) {
        Workspace workspace = workspaceRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("Code workspace inconnu"));
        if (!workspace.isActive()) {
            throw new UnauthorizedException("Ce workspace est suspendu ou desactive");
        }
        return new Result(workspace.id(), workspace.name());
    }
}
