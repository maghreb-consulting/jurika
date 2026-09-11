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
                .map(DataroomDocumentLookupAdapter::vu)
                .toList();
    }

    @Override
    public List<DocumentVu> findByTicket(UUID workspaceId, UUID ticketId) {
        if (ticketId == null) return List.of();
        return jpa.findByWorkspaceIdAndTicketIdAndCurrentTrue(workspaceId, ticketId).stream()
                .map(DataroomDocumentLookupAdapter::vu)
                .toList();
    }

    private static DocumentVu vu(DataroomDocumentViewEntity e) {
        return new DocumentVu(e.getId(), e.getDossierId(), e.getTicketId(),
                e.getDocumentType(), e.getTitle(), e.isVisibleClient());
    }
}
