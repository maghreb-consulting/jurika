package ma.jurika.ticket.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DossierJpaRepository extends JpaRepository<DossierEntity, UUID> {

    /** Projection legere id -> responsable durable (V9), pour resolution batch. */
    interface DossierResponsableView {
        UUID getId();
        UUID getResponsableId();
    }

    /**
     * Liste des tickets (2026-07) — resout en UNE requete le responsable durable
     * (entreprise_dossiers.responsable_id) des dossiers presents dans la page,
     * afin d'afficher le « responsable » d'un ticket meme quand aucun assigne_id
     * n'a ete pose (cas majoritaire : le responsable du dossier est l'owner
     * durable, cf. transfert V9). Scope workspace explicite (BYPASSRLS).
     */
    @Query("""
            select d.id as id, d.responsableId as responsableId
              from DossierEntity d
             where d.workspaceId = :ws
               and d.id in :ids
            """)
    List<DossierResponsableView> findResponsablesByWorkspaceAndIdIn(@Param("ws") UUID ws,
                                                                    @Param("ids") Collection<UUID> ids);

    /**
     * Fiche client (2026-07-14) — lookup workspace-scoped pour l'edition des
     * identifiants post-immatriculation (RC, IF, patente, CNSS...). Defense-in-
     * depth : jurika_user a BYPASSRLS sous le conteneur officiel, on filtre donc
     * explicitement par workspace_id.
     */
    Optional<DossierEntity> findByWorkspaceIdAndId(UUID workspaceId, UUID id);

    /**
     * Fix 2026-06-07 (BUG 2) — Recherche un dossier vivant par
     * (workspaceId, raisonSociale case-insensitive). Statuts "vivants" =
     * EN_CONSTITUTION / ACTIVE / EN_LIQUIDATION.
     *
     * Tri par createdAt ASC pour, en cas de course concurrente ayant
     * laisse passer 2 lignes avant l'index unique partiel, retourner
     * deterministiquement la PLUS ANCIENNE (qui est la "vraie" — la
     * seconde sera supprimee lors de la consolidation).
     */
    @Query("""
            select d from DossierEntity d
             where d.workspaceId = :ws
               and lower(d.raisonSociale) = lower(:raison)
               and d.statut in ('EN_CONSTITUTION','ACTIVE','EN_LIQUIDATION')
             order by d.createdAt asc
            """)
    List<DossierEntity> findAliveByWorkspaceAndRaison(@Param("ws") UUID ws,
                                                     @Param("raison") String raison);

    default Optional<DossierEntity> findFirstAliveByWorkspaceAndRaison(UUID ws, String raison) {
        List<DossierEntity> matches = findAliveByWorkspaceAndRaison(ws, raison);
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
    }

    /**
     * Fix 2026-06-07 (BUG 2) — Marque le ticket d'origine UNIQUEMENT si
     * la colonne est encore nulle ET que le dossier est EN_CONSTITUTION.
     * Garantit l'idempotence : un dossier reutilise via
     * {@code DossierIdempotenceLookup} ne se voit pas marquer
     * retroactivement comme autocreate par un nouveau ticket.
     *
     * Retourne le nombre de lignes mises a jour (0 ou 1).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update DossierEntity d
               set d.createdByTicketId = :ticketId,
                   d.updatedAt = CURRENT_TIMESTAMP
             where d.id = :dossierId
               and d.workspaceId = :ws
               and d.createdByTicketId is null
               and d.statut = 'EN_CONSTITUTION'
            """)
    int markCreatedByTicket(@Param("ws") UUID ws,
                            @Param("dossierId") UUID dossierId,
                            @Param("ticketId") UUID ticketId);

    /**
     * V9 (transfert de dossier) — UPDATE de l'owner durable. clearAutomatically
     * pour eviter que le cache L1 ne renvoie l'ancien responsable au re-read.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update DossierEntity d
               set d.responsableId = :responsableId,
                   d.updatedAt = CURRENT_TIMESTAMP
             where d.id = :dossierId
               and d.workspaceId = :ws
            """)
    int updateResponsable(@Param("ws") UUID ws,
                          @Param("dossierId") UUID dossierId,
                          @Param("responsableId") UUID responsableId);
}
