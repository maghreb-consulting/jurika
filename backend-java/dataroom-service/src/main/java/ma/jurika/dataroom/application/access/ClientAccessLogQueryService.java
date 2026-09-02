package ma.jurika.dataroom.application.access;

import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.api.dto.DataroomDtos.AccessLogEntry;
import ma.jurika.dataroom.api.dto.DataroomDtos.AccessLogPage;
import ma.jurika.dataroom.infrastructure.persistence.ClientAccessLogEntity;
import ma.jurika.dataroom.infrastructure.persistence.ClientAccessLogJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewEntity;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Sprint 7 / TASK 5 -- Query service pour le drawer "Activite client" cote
 * employe/superviseur. Lecture seule, pagination simple.
 * <p>
 * Defense-in-depth multi-tenant : filtre {@code workspace_id} explicite en
 * plus de la RLS Postgres (jurika_user a BYPASSRLS sous le conteneur officiel
 * - cf memory dashboard-bypassrls-fix-2026-06-05). Sans ce filtre, un user
 * authentifie pouvait lire les logs d'acces client d'un dossier d'un autre
 * cabinet en devinant son UUID.
 * <p>
 * 2026-07-01 -- "Activite client" REELLE : le panneau et le compteur "Acces
 * client" sont recentres sur le CLIENT du dossier
 * ({@code entreprise_dossiers.client_id}). Le log brut trace TOUS les roles
 * (employe/superviseur qui consultent la Data Room), ce qui rendait la vue
 * "Activite client" trompeuse. Le compteur "Acces client" est desormais
 * DERIVE du log (source de verite auto-correctrice) plutot que lu depuis la
 * colonne {@code dataroom_settings.access_count} (jamais incrementee).
 */
@Service
public class ClientAccessLogQueryService {

    private final ClientAccessLogJpaRepository repo;
    private final DossierViewJpaRepository dossiers;

    public ClientAccessLogQueryService(ClientAccessLogJpaRepository repo,
                                       DossierViewJpaRepository dossiers) {
        this.repo = repo;
        this.dossiers = dossiers;
    }

    /**
     * Compteur "Acces client" REEL, derive du log d'acces : nombre total
     * d'actions du CLIENT du dossier + date du dernier acces (max created_at).
     * Auto-correcteur : reflete toujours l'etat reel du log, sans compteur
     * denormalise a maintenir. Renvoie (0, null) si pas de tenant ou si aucun
     * client n'est rattache au dossier.
     */
    @Transactional(readOnly = true)
    public ClientAccessStats clientAccessStats(UUID dossierId) {
        UUID workspaceId = TenantContext.get();
        if (workspaceId == null) {
            return ClientAccessStats.EMPTY;
        }
        UUID clientId = resolveClientId(workspaceId, dossierId);
        if (clientId == null) {
            return ClientAccessStats.EMPTY;
        }
        long count = repo.countByWorkspaceIdAndDossierIdAndUserId(workspaceId, dossierId, clientId);
        Instant lastAt = repo
                .findFirstByWorkspaceIdAndDossierIdAndUserIdOrderByCreatedAtDesc(
                        workspaceId, dossierId, clientId)
                .map(ClientAccessLogEntity::getCreatedAt)
                .orElse(null);
        return new ClientAccessStats(count, lastAt);
    }

    /**
     * 2026-07-04 — "Acces client" repense : NOMBRE DE CLIENTS ayant acces au
     * dossier (pas le nombre de consultations). Comme un dataroom n'autorise
     * qu'UN client, la valeur est 0 (aucun client relie) ou 1 (un client relie).
     */
    @Transactional(readOnly = true)
    public int clientCount(UUID dossierId) {
        UUID workspaceId = TenantContext.get();
        if (workspaceId == null) return 0;
        return resolveClientId(workspaceId, dossierId) != null ? 1 : 0;
    }

    /**
     * Drawer "Activite client" : actions du CLIENT du dossier uniquement
     * ({@code user_id = entreprise_dossiers.client_id}), tri DESC sur
     * created_at, pagination simple. Renvoie une page vide si pas de tenant
     * ou si aucun client n'est rattache (empty-state front "Aucune activite").
     */
    @Transactional(readOnly = true)
    public AccessLogPage findByDossier(UUID dossierId, int limit, int offset) {
        UUID workspaceId = TenantContext.get();
        if (workspaceId == null) {
            // Pas de tenant -> defense-in-depth : aucune ligne retournee plutot
            // qu'une fuite globale. Audit log a part car non-critique fonctionnel.
            return new AccessLogPage(java.util.List.of(), 0L);
        }
        UUID clientId = resolveClientId(workspaceId, dossierId);
        if (clientId == null) {
            // Aucun client rattache -> aucune "activite client" par definition.
            return new AccessLogPage(java.util.List.of(), 0L);
        }
        int safeLimit = Math.max(1, Math.min(limit, 200));
        int safeOffset = Math.max(0, offset);
        PageRequest pageable = PageRequest.of(safeOffset / safeLimit, safeLimit);
        Page<ClientAccessLogEntity> page =
                repo.findAllByWorkspaceIdAndDossierIdAndUserIdOrderByCreatedAtDesc(
                        workspaceId, dossierId, clientId, pageable);
        return new AccessLogPage(
                page.getContent().stream().map(this::toEntry).toList(),
                page.getTotalElements());
    }

    /**
     * Resout le CLIENT rattache au dossier ({@code entreprise_dossiers.client_id}),
     * workspace-scoped (defense-in-depth : jamais {@code findById} brut, cf RLS
     * BYPASSRLS). {@code null} si dossier hors workspace ou sans client.
     */
    private UUID resolveClientId(UUID workspaceId, UUID dossierId) {
        return dossiers.findByWorkspaceIdAndId(workspaceId, dossierId)
                .map(DossierViewEntity::getClientId)
                .orElse(null);
    }

    private AccessLogEntry toEntry(ClientAccessLogEntity e) {
        return new AccessLogEntry(
                e.getId(), e.getDossierId(), e.getUserId(), e.getDocumentId(),
                e.getAction(), e.getIpAddress(), e.getUserAgent(), e.getCreatedAt());
    }

    /** Compteur derive "Acces client" : total d'actions + dernier acces. */
    public record ClientAccessStats(long count, Instant lastAt) {
        static final ClientAccessStats EMPTY = new ClientAccessStats(0L, null);
    }
}
