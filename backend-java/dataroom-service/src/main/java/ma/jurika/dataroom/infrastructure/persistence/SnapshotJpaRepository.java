package ma.jurika.dataroom.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SnapshotJpaRepository extends JpaRepository<TicketSnapshotEntity, UUID> {

    /**
     * @deprecated Defense-in-depth contre fuite cross-tenant : sous postgres
     *             officiel, {@code jurika_user} a {@code BYPASSRLS} et la RLS
     *             sur {@code workspace_id} ne filtre PAS. Cette query ne
     *             contraint que {@code ticket_id} : un attaquant qui devine
     *             l'UUID d'un ticket d'un autre workspace en recupere les
     *             snapshots de l'historique. Utiliser
     *             {@link #findAllByWorkspaceIdAndTicketId(UUID, UUID)}.
     */
    @Deprecated(forRemoval = false)
    List<TicketSnapshotEntity> findAllByTicketId(UUID ticketId);

    /**
     * Variante workspace-scoped (defense-in-depth). À utiliser partout où un
     * {@code workspaceId} est disponible via {@code TenantContext.get()} ou
     * {@code actor.workspaceId()} — c'est le cas systematique dans les
     * controllers REST authentifies.
     */
    List<TicketSnapshotEntity> findAllByWorkspaceIdAndTicketId(UUID workspaceId, UUID ticketId);
}
