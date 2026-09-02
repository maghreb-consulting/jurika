package fr.maghreb.gje.repositories;

import fr.maghreb.gje.models.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {
    Optional<Workspace> findByCodeWorkspace(String code);
    boolean existsByCodeWorkspace(String code);
}
