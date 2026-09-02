package fr.maghreb.gje.repositories;

import fr.maghreb.gje.models.EvenementJuridique;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface EvenementJuridiqueRepository extends JpaRepository<EvenementJuridique, UUID> {
    List<EvenementJuridique> findByFicheJuridiqueIdOrderByDateDesc(UUID ficheJuridiqueId);
}
