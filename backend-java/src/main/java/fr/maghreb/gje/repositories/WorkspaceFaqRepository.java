package fr.maghreb.gje.repositories;

import fr.maghreb.gje.models.WorkspaceFaq;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkspaceFaqRepository extends JpaRepository<WorkspaceFaq, UUID> {
    List<WorkspaceFaq> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);
}
