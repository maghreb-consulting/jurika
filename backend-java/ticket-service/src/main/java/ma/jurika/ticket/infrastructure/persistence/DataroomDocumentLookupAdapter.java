package ma.jurika.ticket.infrastructure.persistence;

import ma.jurika.ticket.domain.port.DataroomDocumentLookup;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class DataroomDocumentLookupAdapter implements DataroomDocumentLookup {

    private final DataroomDocumentViewJpaRepository jpa;

    public DataroomDocumentLookupAdapter(DataroomDocumentViewJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<DocumentVu> findByIds(UUID workspaceId, List<UUID> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) return List.of();
        // Le filtre workspace est EXPLICITE : la RLS est inerte en runtime
        // (l'application se connecte avec un role qui la contourne).
        return jpa.findByWorkspaceIdAndIdIn(workspaceId, documentIds).stream()
                .map(e -> new DocumentVu(e.getId(), e.getDossierId(), e.getTicketId(),
                        e.getDocumentType(), e.getTitle()))
                .toList();
    }
}
