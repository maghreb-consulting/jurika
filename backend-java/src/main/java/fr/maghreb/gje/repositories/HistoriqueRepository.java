package fr.maghreb.gje.repositories;
import fr.maghreb.gje.models.Historique;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface HistoriqueRepository
    extends JpaRepository<Historique, UUID> {
    List<Historique> findByDossierIdOrderByTimestampDesc(
        UUID dossierId);
    List<Historique> findByWorkspaceIdOrderByTimestampDesc(
        UUID workspaceId);
}
