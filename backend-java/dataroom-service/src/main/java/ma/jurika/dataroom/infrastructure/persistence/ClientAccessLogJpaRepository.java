package ma.jurika.dataroom.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ClientAccessLogJpaRepository
        extends JpaRepository<ClientAccessLogEntity, UUID> {

    /**
     * @deprecated Defense-in-depth contre fuite cross-tenant : sous postgres
     *             officiel, {@code jurika_user} a {@code BYPASSRLS} et la RLS
     *             sur {@code workspace_id} ne filtre PAS. Cette query ne
     *             contraint que {@code dossier_id} : un attaquant qui devine
     *             l'UUID d'un dossier d'un autre workspace lit ses logs
     *             d'acces (IP, UA, actions client). Utiliser
     *             {@link #findAllByWorkspaceIdAndDossierIdOrderByCreatedAtDesc(UUID, UUID, Pageable)}.
     */
    @Deprecated(forRemoval = false)
    Page<ClientAccessLogEntity> findAllByDossierIdOrderByCreatedAtDesc(
            UUID dossierId, Pageable pageable);

    /**
     * Variante workspace-scoped (defense-in-depth). À utiliser partout où un
     * {@code workspaceId} est disponible via {@code TenantContext.get()} ou
     * {@code actor.workspaceId()}.
     */
    Page<ClientAccessLogEntity> findAllByWorkspaceIdAndDossierIdOrderByCreatedAtDesc(
            UUID workspaceId, UUID dossierId, Pageable pageable);

    // ====================================================================
    // 2026-07-01 -- "Activite client" REELLE : le drawer et le compteur
    // "Acces client" ne doivent refleter QUE les actions du CLIENT du
    // dossier (entreprise_dossiers.client_id), pas celles de l'employe ou
    // du superviseur qui consultent la Data Room cote cabinet.
    // Ces trois requetes sont donc user-scoped en plus du workspace.
    // Index couvrant : idx_access_log_user (workspace_id, user_id, created_at DESC).
    // ====================================================================

    /** Actions du CLIENT du dossier, plus recentes d'abord (drawer "Activite client"). */
    Page<ClientAccessLogEntity> findAllByWorkspaceIdAndDossierIdAndUserIdOrderByCreatedAtDesc(
            UUID workspaceId, UUID dossierId, UUID userId, Pageable pageable);

    /** Nombre total d'acces du CLIENT du dossier (compteur "Acces client"). */
    long countByWorkspaceIdAndDossierIdAndUserId(UUID workspaceId, UUID dossierId, UUID userId);

    /** Dernier acces du CLIENT (lastAccessedAt derive = max(created_at)). */
    Optional<ClientAccessLogEntity> findFirstByWorkspaceIdAndDossierIdAndUserIdOrderByCreatedAtDesc(
            UUID workspaceId, UUID dossierId, UUID userId);
}
