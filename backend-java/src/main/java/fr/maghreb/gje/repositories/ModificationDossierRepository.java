package fr.maghreb.gje.repositories;

import fr.maghreb.gje.models.ModificationDossier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ModificationDossierRepository extends JpaRepository<ModificationDossier, UUID> {
    List<ModificationDossier> findByDossierIdOrderByCreatedAtDesc(UUID dossierId);
}
