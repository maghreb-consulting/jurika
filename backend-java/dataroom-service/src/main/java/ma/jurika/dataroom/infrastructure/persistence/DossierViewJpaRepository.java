package ma.jurika.dataroom.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DossierViewJpaRepository extends JpaRepository<DossierViewEntity, UUID> {
    List<DossierViewEntity> findAllByWorkspaceId(UUID workspaceId);

    /**
     * Fix 2026-06-07 (BUG 3 finition) — Exclut les dossiers RADIE (sortis
     * de circulation suite a DELETE dataroom + tickets historiques
     * preserves). Sans ce filtre, la liste expose des dossiers "fantomes"
     * qui, une fois ouverts, declenchent un getOrCreate sur dataroom_settings
     * et reapparaissent comme "Actifs".
     */
    List<DossierViewEntity> findAllByWorkspaceIdAndStatutNot(UUID workspaceId, String statut);

    /**
     * Scoping EMPLOYE (2026-07-03) — un employe ne voit que les dossiers dont
     * il est responsable (responsable_id = son userId), en excluant les RADIE.
     * SUPERVISEUR/SUPER_ADMIN continuent d'utiliser la variante sans responsable.
     */
    List<DossierViewEntity> findAllByWorkspaceIdAndResponsableIdAndStatutNot(
            UUID workspaceId, UUID responsableId, String statut);

    /**
     * Defense-in-depth multi-tenant : findById ne filtre QUE par PK et
     * compte sur la RLS pour limiter au workspace courant. Sous postgres
     * conteneur officiel, jurika_user a BYPASSRLS — la RLS est court-
     * circuitee et findById exposerait n'importe quel dossier cross-tenant.
     * Utiliser cette surcharge avec {@code TenantContext.get()} a la place.
     */
    Optional<DossierViewEntity> findByWorkspaceIdAndId(UUID workspaceId, UUID id);
}
