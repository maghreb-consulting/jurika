package ma.jurika.ticket.infrastructure.directory;

import feign.FeignException;
import ma.jurika.common.trial.WorkspaceStatusFeignClient;
import ma.jurika.ticket.domain.port.MemberDirectory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * 2026-06-30 — Adapter {@link MemberDirectory} sur auth-service via le Feign
 * client interne partagé {@link WorkspaceStatusFeignClient}
 * ({@code GET /internal/workspaces/{ws}/users/{id}/role}).
 *
 * <p>Le bean {@code WorkspaceStatusFeignClient} est déjà fourni dans le contexte
 * ticket-service par {@code TrialRemoteAutoConfiguration} (jurika-common).
 *
 * <p>404 (membre non interne / inconnu) → {@link Optional#empty()}. Toute autre
 * {@link FeignException} (auth-service injoignable) est propagée : on refuse de
 * valider un transfert sans avoir pu confirmer le rôle de la cible (fail-closed).
 */
@Component
public class FeignMemberDirectory implements MemberDirectory {

    private final WorkspaceStatusFeignClient client;

    public FeignMemberDirectory(WorkspaceStatusFeignClient client) {
        this.client = client;
    }

    @Override
    public Optional<String> roleOf(UUID workspaceId, UUID userId) {
        try {
            WorkspaceStatusFeignClient.MemberRoleDto dto = client.getUserRole(workspaceId, userId);
            return Optional.ofNullable(dto).map(WorkspaceStatusFeignClient.MemberRoleDto::role);
        } catch (FeignException.NotFound e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean estActif(UUID workspaceId, UUID userId) {
        try {
            WorkspaceStatusFeignClient.MemberRoleDto dto = client.getUserRole(workspaceId, userId);
            return dto != null && "ACTIVE".equals(dto.status());
        } catch (FeignException.NotFound e) {
            return false;
        }
    }
}
