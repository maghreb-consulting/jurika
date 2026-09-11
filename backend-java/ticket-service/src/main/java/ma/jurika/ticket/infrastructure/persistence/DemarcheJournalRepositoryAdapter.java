package ma.jurika.ticket.infrastructure.persistence;

import ma.jurika.ticket.domain.model.DemarcheEvenement;
import ma.jurika.ticket.domain.port.DemarcheJournalRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class DemarcheJournalRepositoryAdapter implements DemarcheJournalRepository {

    private final TicketDemarcheEvenementJpaRepository evenements;

    public DemarcheJournalRepositoryAdapter(TicketDemarcheEvenementJpaRepository evenements) {
        this.evenements = evenements;
    }

    @Override
    @Transactional
    public UUID enregistrer(UUID workspaceId, UUID ticketDemarcheId, DemarcheEvenement.Type type,
                             String motif, UUID acteurId, int justificatifs) {
        TicketDemarcheEvenementEntity e = new TicketDemarcheEvenementEntity();
        e.setWorkspaceId(workspaceId);
        e.setTicketDemarcheId(ticketDemarcheId);
        e.setType(type.name());
        e.setMotif(motif == null || motif.isBlank() ? null : motif.trim());
        e.setActeurId(acteurId);
        e.setJustificatifs((short) Math.max(0, justificatifs));
        return evenements.save(e).getId();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<DemarcheEvenement>> parDemarche(UUID workspaceId,
                                                           Collection<UUID> ticketDemarcheIds) {
        if (ticketDemarcheIds == null || ticketDemarcheIds.isEmpty()) return Map.of();
        return evenements.findByTicketDemarcheIdInOrderBySurvenuLeAsc(ticketDemarcheIds).stream()
                .filter(e -> workspaceId.equals(e.getWorkspaceId()))
                .collect(Collectors.groupingBy(
                        TicketDemarcheEvenementEntity::getTicketDemarcheId,
                        Collectors.mapping(e -> new DemarcheEvenement(
                                        e.getId(),
                                        e.getTicketDemarcheId(),
                                        DemarcheEvenement.Type.valueOf(e.getType()),
                                        e.getMotif(),
                                        e.getActeurId(),
                                        e.getSurvenuLe(),
                                        e.getJustificatifs() == null ? 0 : e.getJustificatifs()),
                                Collectors.toList())));
    }
}
