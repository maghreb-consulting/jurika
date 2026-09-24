package ma.jurika.workflow.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DossierVariableJpaRepository extends JpaRepository<DossierVariableEntity, UUID> {

    List<DossierVariableEntity> findByWorkspaceIdAndTicketId(UUID workspaceId, UUID ticketId);

    /** Variable simple — hors boucle. */
    @Query("""
        SELECT v FROM DossierVariableEntity v
         WHERE v.workspaceId = :ws AND v.ticketId = :ticket
           AND v.variable = :variable AND v.boucle IS NULL
        """)
    Optional<DossierVariableEntity> findSimple(@Param("ws") UUID workspaceId,
                                                @Param("ticket") UUID ticketId,
                                                @Param("variable") String variable);

    /** Occurrence de boucle, à son rang. */
    @Query("""
        SELECT v FROM DossierVariableEntity v
         WHERE v.workspaceId = :ws AND v.ticketId = :ticket
           AND v.boucle = :boucle AND v.rang = :rang AND v.variable = :variable
        """)
    Optional<DossierVariableEntity> findEnBoucle(@Param("ws") UUID workspaceId,
                                                  @Param("ticket") UUID ticketId,
                                                  @Param("boucle") String boucle,
                                                  @Param("rang") short rang,
                                                  @Param("variable") String variable);

    /**
     * Purge les occurrences d'une boucle au-delà du rang conservé.
     *
     * <p>Réduire une liste (deux associés puis un seul) doit retirer
     * l'occurrence en trop, sinon le document rendrait un associé fantôme.
     */
    @Modifying
    @Query("""
        DELETE FROM DossierVariableEntity v
         WHERE v.workspaceId = :ws AND v.ticketId = :ticket
           AND v.boucle = :boucle AND v.rang >= :aPartirDu
        """)
    int purgerBoucleAuDela(@Param("ws") UUID workspaceId,
                            @Param("ticket") UUID ticketId,
                            @Param("boucle") String boucle,
                            @Param("aPartirDu") short aPartirDu);

    /** Rattache au dossier toutes les variables du ticket, à la finalisation. */
    @Modifying
    @Query("""
        UPDATE DossierVariableEntity v SET v.dossierId = :dossier
         WHERE v.workspaceId = :ws AND v.ticketId = :ticket AND v.dossierId IS NULL
        """)
    int rattacherAuDossier(@Param("ws") UUID workspaceId,
                            @Param("ticket") UUID ticketId,
                            @Param("dossier") UUID dossierId);
}
