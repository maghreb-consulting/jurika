package fr.maghreb.gje.repositories;

import fr.maghreb.gje.models.FicheJuridique;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface FicheJuridiqueRepository extends JpaRepository<FicheJuridique, UUID> {
    Optional<FicheJuridique> findByDossierIdAndWorkspaceId(UUID dossierId, UUID workspaceId);
}
