package ma.jurika.dataroom.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExerciceFiscalJpaRepository extends JpaRepository<ExerciceFiscalEntity, UUID> {

    List<ExerciceFiscalEntity> findAllByDossierIdOrderByAnneeDesc(UUID dossierId);

    Optional<ExerciceFiscalEntity> findByDossierIdAndAnnee(UUID dossierId, short annee);

    boolean existsByDossierIdAndAnnee(UUID dossierId, short annee);
}
