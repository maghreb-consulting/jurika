package ma.jurika.dataroom.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface FiscalDocumentJpaRepository extends JpaRepository<FiscalDocumentEntity, UUID> {

    @Query("""
            SELECT d FROM FiscalDocumentEntity d
            WHERE d.dossierId = :dossierId
              AND d.exerciceFiscalId = :exerciceId
              AND d.deleted = false
            ORDER BY d.createdAt DESC
            """)
    List<FiscalDocumentEntity> findByDossierAndExercice(@Param("dossierId") UUID dossierId,
                                                        @Param("exerciceId") UUID exerciceId);

    @Query("""
            SELECT d FROM FiscalDocumentEntity d
            WHERE d.dossierId = :dossierId
              AND d.exerciceFiscalId = :exerciceId
              AND d.categorie = :categorie
              AND d.deleted = false
            ORDER BY d.createdAt DESC
            """)
    List<FiscalDocumentEntity> findByDossierExerciceAndCategorie(@Param("dossierId") UUID dossierId,
                                                                  @Param("exerciceId") UUID exerciceId,
                                                                  @Param("categorie") String categorie);

    /** Compteur par categorie pour la vue grille. */
    @Query("""
            SELECT d.categorie, COUNT(d)
            FROM FiscalDocumentEntity d
            WHERE d.dossierId = :dossierId
              AND d.exerciceFiscalId = :exerciceId
              AND d.deleted = false
            GROUP BY d.categorie
            """)
    List<Object[]> countByCategorie(@Param("dossierId") UUID dossierId,
                                     @Param("exerciceId") UUID exerciceId);

    @Modifying
    @Query("""
            UPDATE FiscalDocumentEntity d
            SET d.deleted = true, d.deletedAt = :when, d.deletedBy = :by
            WHERE d.id = :id AND d.deleted = false
            """)
    int softDelete(@Param("id") UUID id, @Param("when") Instant when, @Param("by") UUID by);

    /** Documents soft-deleted dont la date depasse le cutoff (purge CGI Art. 211). */
    @Query("""
            SELECT d FROM FiscalDocumentEntity d
            WHERE d.deleted = true AND d.createdAt < :cutoff
            """)
    List<FiscalDocumentEntity> findExpiredSoftDeleted(@Param("cutoff") Instant cutoff);
}
