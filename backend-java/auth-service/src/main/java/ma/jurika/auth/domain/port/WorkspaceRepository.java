package ma.jurika.auth.domain.port;

import ma.jurika.auth.domain.model.Workspace;

import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRepository {

    Optional<Workspace> findById(UUID id);

    Optional<Workspace> findByCode(String code);

    Workspace create(String code, String name, String contactEmail, UUID subscriptionId);

    /**
     * Cree un workspace en status PENDING_VERIFICATION.
     * Sera activate via {@link #activate(UUID)} apres clic du lien email.
     */
    /**
     * Lot L0 (E13a) : l'identifiant est fourni par l'appelant ; c'est le
     * workspace courant (TenantContext), pour que l'INSERT passe la RLS.
     */
    Workspace createPending(UUID id, String code, String name, String contactEmail, UUID subscriptionId);

    /**
     * Passe le workspace de PENDING_VERIFICATION a ACTIVE.
     */
    void activate(UUID workspaceId);

    boolean codeExists(String code);
}
