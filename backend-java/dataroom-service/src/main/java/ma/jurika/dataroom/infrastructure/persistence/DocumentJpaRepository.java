package ma.jurika.dataroom.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentJpaRepository
        extends JpaRepository<DocumentEntity, UUID>, JpaSpecificationExecutor<DocumentEntity> {
    /**
     * @deprecated Defense-in-depth contre fuite cross-tenant : `jurika_user` ayant
     * BYPASSRLS dans le conteneur Postgres officiel, la clause RLS sur
     * `workspace_id` n'est PAS effective. Cette query derivee filtre uniquement
     * par `dossier_id`, ce qui expose les documents en vigueur d'un dossier
     * d'un autre workspace si l'appelant ne contrôle pas l'appartenance du
     * dossier au TenantContext (cf. JuridiqueController -> DataroomJuridiqueService.view
     * pour les rôles EMPLOYE/SUPERVISEUR/SUPER_ADMIN qui n'invoquent PAS
     * assertClientAccess). Utiliser
     * {@link #findAllByWorkspaceIdAndDossierIdAndCurrentTrueOrderByCreatedAtDesc(UUID, UUID)}
     * en passant `TenantContext.get()` ou `actor.workspaceId()`.
     */
    @Deprecated(forRemoval = false)
    List<DocumentEntity> findAllByDossierIdAndCurrentTrueOrderByCreatedAtDesc(UUID dossierId);

    /**
     * Variante workspace-scoped explicite (defense-in-depth contre BYPASSRLS).
     * À utiliser en remplacement de
     * {@link #findAllByDossierIdAndCurrentTrueOrderByCreatedAtDesc(UUID)} partout
     * où un workspaceId est disponible via le TenantContext ou l'acteur.
     */
    List<DocumentEntity> findAllByWorkspaceIdAndDossierIdAndCurrentTrueOrderByCreatedAtDesc(
            UUID workspaceId, UUID dossierId);

    /**
     * @deprecated Defense-in-depth contre fuite cross-tenant : `jurika_user` ayant
     * BYPASSRLS dans le conteneur Postgres officiel, la clause RLS sur
     * `workspace_id` n'est PAS effective. Cette query derivee filtre uniquement
     * par `ticket_id`, ce qui expose les documents d'un ticket d'un autre
     * workspace si l'appelant ne contrôle pas l'appartenance du ticket au
     * TenantContext (cf. JuridiqueController -> DataroomJuridiqueService.view
     * -> buildHistoryEntry). Utiliser
     * {@link #findAllByWorkspaceIdAndTicketIdOrderByCreatedAtAsc(UUID, UUID)}
     * en passant `TenantContext.requireWorkspaceId()` ou
     * `actor.workspaceId()`.
     */
    @Deprecated(forRemoval = false)
    List<DocumentEntity> findAllByTicketIdOrderByCreatedAtAsc(UUID ticketId);

    /**
     * Variante workspace-scoped explicite (defense-in-depth contre BYPASSRLS).
     * À utiliser en remplacement de
     * {@link #findAllByTicketIdOrderByCreatedAtAsc(UUID)} partout où un
     * workspaceId est disponible via le TenantContext ou l'acteur.
     */
    List<DocumentEntity> findAllByWorkspaceIdAndTicketIdOrderByCreatedAtAsc(UUID workspaceId, UUID ticketId);

    /**
     * Defense-in-depth contre fuite cross-tenant : findById hérite de
     * JpaRepository et n'inclut PAS workspace_id. Sous postgres officiel,
     * jurika_user a BYPASSRLS donc findById expose n'importe quel document
     * cross-tenant. Utiliser cette surcharge avec {@code TenantContext.get()}.
     */
    Optional<DocumentEntity> findByWorkspaceIdAndId(UUID workspaceId, UUID id);

    /**
     * Fiche client (2026-07-14) — TOUTES les versions (courantes + historiques)
     * d'un dossier, pour reconstruire l'historique documentaire (ajouts /
     * remplacements). Tri le plus recent d'abord ; workspace-scoped.
     */
    List<DocumentEntity> findAllByWorkspaceIdAndDossierIdOrderByCreatedAtDesc(
            UUID workspaceId, UUID dossierId);

    /**
     * Retourne le doc current du type donne (au plus 1 en theorie ; ORDER BY +
     * fetch first 1 pour eviter NonUniqueResultException en cas de race condition
     * pendant un upload batch).
     */
    @Query(value = """
        SELECT d FROM DocumentEntity d
        WHERE d.dossierId = :dossierId
          AND d.documentType = :type
          AND d.current = true
        ORDER BY d.createdAt DESC
        """)
    List<DocumentEntity> findCurrentByTypeAll(@Param("dossierId") UUID dossierId,
                                                @Param("type") String type);

    default Optional<DocumentEntity> findCurrentByType(UUID dossierId, String type) {
        var list = findCurrentByTypeAll(dossierId, type);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /**
     * Fix DR1 (2026-08-16) — document EN VIGUEUR occupant un slot logique donné.
     *
     * <p>Le slot est {@code (dossier, type, titre)} : c'est déjà la clé de lignage du
     * versioning explicite ({@link #findLineage}), et c'est la seule qui reste juste
     * pour les types à exemplaires multiples légitimes — une CIN par personne, par
     * exemple, doit pouvoir coexister en vigueur avec une autre CIN.
     *
     * <p>Sert à router un nouveau dépôt vers le VERSIONING plutôt que de créer un
     * second document courant : sans cela on pouvait afficher « deux Statuts en
     * vigueur » côte à côte. L'index unique partiel de la migration V23 fait
     * respecter la même règle au niveau de la base.
     */
    @Query(value = """
        SELECT d FROM DocumentEntity d
        WHERE d.dossierId = :dossierId
          AND d.documentType = :type
          AND d.title = :title
          AND d.current = true
        ORDER BY d.createdAt DESC
        """)
    List<DocumentEntity> findCurrentBySlotAll(@Param("dossierId") UUID dossierId,
                                              @Param("type") String type,
                                              @Param("title") String title);

    default Optional<DocumentEntity> findCurrentBySlot(UUID dossierId, String type, String title) {
        var list = findCurrentBySlotAll(dossierId, type, title);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /**
     * Flush + clear automatiques pour que la query suivante voie l'UPDATE
     * (sinon en upload batch, l'iteration N+1 verrait encore l'ancien doc current).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE DocumentEntity d SET d.current = false, d.replacedAt = :when
        WHERE d.id = :id
    """)
    int markReplaced(@Param("id") UUID id, @Param("when") Instant when);

    /**
     * Variante avec motif (Sprint 2026-06-23) — persiste la raison du
     * remplacement sur la ligne courante avant qu'elle bascule en historique.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE DocumentEntity d SET d.current = false, d.replacedAt = :when, d.motif = :motif
        WHERE d.id = :id
    """)
    int markReplacedWithMotif(@Param("id") UUID id, @Param("when") Instant when,
                              @Param("motif") String motif);

    /**
     * Lineage complet d'un Document logique (dossier + type + title) — toutes
     * les versions, du plus récent au plus ancien. Utilisé par l'endpoint
     * GET /documents/{id}/versions.
     */
    @Query("""
        SELECT d FROM DocumentEntity d
        WHERE d.workspaceId = :workspaceId
          AND d.dossierId = :dossierId
          AND d.documentType = :type
          AND d.title = :title
        ORDER BY d.version DESC
    """)
    List<DocumentEntity> findLineage(@Param("workspaceId") UUID workspaceId,
                                     @Param("dossierId") UUID dossierId,
                                     @Param("type") String type,
                                     @Param("title") String title);

    /**
     * Restauration d'une ancienne version : remet is_current=true + reset
     * replaced_at sur la ligne ciblée. Le motif est conservé tel quel
     * (l'API d'appel renseignera un nouveau motif sur l'ancien current).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE DocumentEntity d SET d.current = true, d.replacedAt = null
        WHERE d.id = :id
    """)
    int markRestored(@Param("id") UUID id);

    /**
     * Sprint 7 / RG-DR-FTS : recherche full-text PostgreSQL sur la colonne
     * tsvector GENERATED (V10). Non portable en JPA Criteria, d'ou nativeQuery.
     *
     * Le filtre workspace_id via RLS est garanti par PostgreSQL meme sans
     * predicate explicite ; on ajoute la condition `dossier_id = :dossierId`
     * pour scoper la recherche au dossier en cours.
     *
     * Retourne juste les IDs : le use case enchaine avec les Specifications
     * (types/dates/scope) via JpaSpecificationExecutor pour composer.
     */
    @Query(value = """
        SELECT id FROM dataroom_documents
        WHERE dossier_id = :dossierId
          AND search_vector @@ websearch_to_tsquery('french', :q)
        """, nativeQuery = true)
    List<UUID> ftsMatchIds(@Param("dossierId") UUID dossierId, @Param("q") String q);
}
