package ma.jurika.auth.domain.port;

import java.util.List;
import java.util.UUID;

/**
 * Manages the per-workspace CORS allowlist persisted in {@code workspace_allowed_origins}.
 *
 * <p>Read by the gateway via {@code GET /api/v1/admin/cors-origins} (5-min cache),
 * written by super-admin via {@code POST /api/v1/admin/workspaces/{id}/allowed-origins}.
 */
public interface AllowedOriginRepository {

    /** Returns all distinct origins across every workspace (flat list, dedup). */
    List<String> findAllDistinct();

    /** Returns the origins authorized for a given workspace. */
    List<AllowedOrigin> findByWorkspace(UUID workspaceId);

    /** Persists a new origin, returning it with its generated id. */
    AllowedOrigin add(UUID workspaceId, String origin, UUID createdBy);

    /** Removes the origin row by id, returning {@code true} if a row was deleted. */
    boolean delete(UUID workspaceId, UUID originId);

    record AllowedOrigin(UUID id, UUID workspaceId, String origin) {}
}
