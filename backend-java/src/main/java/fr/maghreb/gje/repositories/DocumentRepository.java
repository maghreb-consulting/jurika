package fr.maghreb.gje.repositories;

import fr.maghreb.gje.models.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface DocumentRepository
    extends JpaRepository<Document, UUID> {

    List<Document> findByDossierIdOrderByVersionNumberDesc(
        UUID dossierId);

    List<Document> findByDossierIdAndIsCurrent(
        UUID dossierId, Boolean isCurrent);

    @Query("SELECT MAX(d.versionNumber) FROM Document d " +
        "WHERE d.dossierId = :dossierId")
    Integer findMaxVersionByDossierId(
        @Param("dossierId") UUID dossierId);

    long countByWorkspaceId(UUID workspaceId);
}
