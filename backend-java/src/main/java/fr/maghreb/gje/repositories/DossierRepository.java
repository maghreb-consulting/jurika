package fr.maghreb.gje.repositories;
import fr.maghreb.gje.models.EntrepriseDossier;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface DossierRepository
    extends JpaRepository<EntrepriseDossier, UUID> {
    List<EntrepriseDossier> findByWorkspaceIdAndAuteurId(
        UUID workspaceId, UUID auteurId);
    List<EntrepriseDossier> findByWorkspaceId(UUID workspaceId);
    @Query("SELECT COUNT(d) FROM EntrepriseDossier d WHERE d.workspaceId = :wsId")
    long countByWorkspaceId(@Param("wsId") UUID workspaceId);
    boolean existsByReference(String reference);
}
