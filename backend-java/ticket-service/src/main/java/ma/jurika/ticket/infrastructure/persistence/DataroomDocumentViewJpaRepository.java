package ma.jurika.ticket.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DataroomDocumentViewJpaRepository extends JpaRepository<DataroomDocumentViewEntity, UUID> {

    List<DataroomDocumentViewEntity> findByWorkspaceIdAndIdIn(UUID workspaceId, List<UUID> ids);
}
