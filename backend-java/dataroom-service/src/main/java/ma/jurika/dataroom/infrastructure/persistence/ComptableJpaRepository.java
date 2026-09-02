package ma.jurika.dataroom.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ComptableJpaRepository extends JpaRepository<ComptableDocumentEntity, UUID> {

    @Query("""
        SELECT c FROM ComptableDocumentEntity c
        WHERE c.dossierId = :dossierId
          AND c.annee = :annee
          AND c.categorie = :categorie
          AND c.deletedAt IS NULL
        ORDER BY c.createdAt DESC
    """)
    List<ComptableDocumentEntity> findByYearCategory(@Param("dossierId") UUID dossierId,
                                                      @Param("annee") short annee,
                                                      @Param("categorie") String categorie);

    @Query("""
        SELECT DISTINCT c.annee FROM ComptableDocumentEntity c
        WHERE c.dossierId = :dossierId AND c.deletedAt IS NULL
        ORDER BY c.annee DESC
    """)
    List<Short> findDistinctYears(@Param("dossierId") UUID dossierId);

    @Query("""
        SELECT c.categorie, COUNT(c) FROM ComptableDocumentEntity c
        WHERE c.dossierId = :dossierId AND c.annee = :annee AND c.deletedAt IS NULL
        GROUP BY c.categorie
    """)
    List<Object[]> countByYearGroupedByCategory(@Param("dossierId") UUID dossierId,
                                                 @Param("annee") short annee);

    @Modifying
    @Query("""
        UPDATE ComptableDocumentEntity c SET c.deletedAt = :when
        WHERE c.id = :id AND c.deletedAt IS NULL
    """)
    int softDelete(@Param("id") UUID id, @Param("when") Instant when);

    /** RG-DC26 : tous documents non supprimes d'une annee, toutes categories confondues. */
    @Query("""
        SELECT c FROM ComptableDocumentEntity c
        WHERE c.dossierId = :dossierId AND c.annee = :annee AND c.deletedAt IS NULL
        ORDER BY c.categorie ASC, c.createdAt DESC
    """)
    List<ComptableDocumentEntity> findAllByYear(@Param("dossierId") UUID dossierId,
                                                  @Param("annee") short annee);
}
