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
                .orElseThrow(() -> new NotFoundException("Code workspace inconnu : v\u00e9rifiez le code de votre cabinet."));
        // Lot L7 (E5), RG-TAR-09 : un workspace archive accepte la connexion de son superviseur.
        if (!workspace.isActive() && !workspace.isArchived()) {
            throw new UnauthorizedException("Ce workspace est suspendu ou d\u00e9sactiv\u00e9 : adressez-vous au support JURIKA.");
        }
        return new Result(workspace.id(), workspace.name());
    }
}
