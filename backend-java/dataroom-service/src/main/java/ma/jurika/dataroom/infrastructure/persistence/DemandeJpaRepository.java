package ma.jurika.dataroom.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DemandeJpaRepository extends JpaRepository<DemandeEntity, UUID> {

    /**
     * @deprecated Defense-in-depth contre fuite cross-tenant : sous postgres
     *             officiel, {@code jurika_user} a {@code BYPASSRLS} et la RLS
     *             sur {@code workspace_id} ne filtre PAS. Cette query ne
     *             contraint que {@code dossier_id} : un attaquant qui devine
     *             l'UUID d'un dossier d'un autre workspace lit les demandes
     *             client de ce dossier (PII / contenu confidentiel). Utiliser
     *             {@link #findAllByWorkspaceIdAndDossierIdOrderByCreatedAtDesc(UUID, UUID)}.
     */
    @Deprecated(forRemoval = false)
    List<DemandeEntity> findAllByDossierIdOrderByCreatedAtDesc(UUID dossierId);

    /**
     * Variante workspace-scoped (defense-in-depth). À utiliser partout où un
     * {@code workspaceId} est disponible via {@code TenantContext.get()} ou
     * {@code actor.workspaceId()}.
     */
    List<DemandeEntity> findAllByWorkspaceIdAndDossierIdOrderByCreatedAtDesc(UUID workspaceId, UUID dossierId);

    List<DemandeEntity> findAllByWorkspaceIdAndStatutOrderByCreatedAtDesc(UUID workspaceId, String statut);
    List<DemandeEntity> findAllByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    /**
     * Scoping EMPLOYE (2026-07-03) — demandes des dossiers d'un responsable.
     * L'appelant fournit la liste des {@code dossierId} dont
     * {@code entreprise_dossiers.responsable_id = employeeId} (via
     * {@link DossierViewJpaRepository#findAllByWorkspaceIdAndResponsableIdAndStatutNot}).
     * Ainsi un dossier transfere a un nouvel employe amene ses demandes avec lui.
     *
     * <p><b>Contrat</b> : ne JAMAIS appeler avec une collection vide — Spring Data
     * genererait un {@code IN ()} invalide. L'appelant court-circuite en amont.
     */
    List<DemandeEntity> findAllByWorkspaceIdAndDossierIdInOrderByCreatedAtDesc(
            UUID workspaceId, Collection<UUID> dossierIds);

    List<DemandeEntity> findAllByWorkspaceIdAndDossierIdInAndStatutOrderByCreatedAtDesc(
            UUID workspaceId, Collection<UUID> dossierIds, String statut);

    /** Lookup workspace-scoped d'une demande par id (eviter findById direct). */
    Optional<DemandeEntity> findByWorkspaceIdAndId(UUID workspaceId, UUID id);
}
