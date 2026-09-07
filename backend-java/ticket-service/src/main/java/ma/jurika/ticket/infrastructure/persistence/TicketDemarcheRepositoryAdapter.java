package ma.jurika.ticket.infrastructure.persistence;

import ma.jurika.ticket.domain.model.DemarcheEtat;
import ma.jurika.ticket.domain.model.TicketDemarche;
import ma.jurika.ticket.domain.port.TicketDemarcheRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class TicketDemarcheRepositoryAdapter implements TicketDemarcheRepository {

    private final TicketDemarcheJpaRepository demarches;
    private final TicketDemarcheJustificatifJpaRepository justificatifs;

    public TicketDemarcheRepositoryAdapter(TicketDemarcheJpaRepository demarches,
                                            TicketDemarcheJustificatifJpaRepository justificatifs) {
        this.demarches = demarches;
        this.justificatifs = justificatifs;
    }

    /**
     * Renvoie les etats EXISTANTS, indexes par identifiant de demarche du
     * referentiel. La {@code Demarche} portee est {@code null} : c'est l'appelant
     * qui la rapproche du referentiel, seul a connaitre le workflow concerne.
     */
    @Override
    @Transactional(readOnly = true)
    public Map<UUID, TicketDemarche> findByTicket(UUID workspaceId, UUID ticketId) {
        List<TicketDemarcheEntity> lignes = demarches.findByWorkspaceIdAndTicketId(workspaceId, ticketId);
        if (lignes.isEmpty()) return Map.of();

        Map<UUID, List<UUID>> docsParLigne =
                justificatifs.findByTicketDemarcheIdIn(lignes.stream()
                                .map(TicketDemarcheEntity::getId).toList()).stream()
                        .collect(Collectors.groupingBy(
                                TicketDemarcheJustificatifEntity::getTicketDemarcheId,
                                Collectors.mapping(TicketDemarcheJustificatifEntity::getDocumentId,
                                        Collectors.toList())));

        Map<UUID, TicketDemarche> out = new HashMap<>();
        for (TicketDemarcheEntity e : lignes) {
            out.put(e.getDemarcheId(), new TicketDemarche(
                    e.getId(),
                    null,
                    DemarcheEtat.valueOf(e.getEtat()),
                    e.getMotif(),
                    e.getActeurId(),
                    e.getCocheAt(),
                    docsParLigne.getOrDefault(e.getId(), List.of())));
        }
        return out;
    }

    @Override
    @Transactional
    public UUID upsert(UUID workspaceId, UUID ticketId, UUID demarcheId, DemarcheEtat etat,
                        String motif, UUID acteurId, List<JustificatifDepose> deposes) {
        TicketDemarcheEntity e = demarches
                .findByWorkspaceIdAndTicketIdAndDemarcheId(workspaceId, ticketId, demarcheId)
                .orElseGet(() -> {
                    TicketDemarcheEntity n = new TicketDemarcheEntity();
                    n.setWorkspaceId(workspaceId);
                    n.setTicketId(ticketId);
                    n.setDemarcheId(demarcheId);
                    return n;
                });
        e.setEtat(etat.name());
        e.setMotif(motif);
        e.setActeurId(acteurId);
        // Le decochage remet la date a null : le CHECK de la migration exige une
        // date des que l'etat n'est plus A_FAIRE, et l'inverse doit rester vrai.
        e.setCocheAt(etat == DemarcheEtat.A_FAIRE ? null : Instant.now());
        TicketDemarcheEntity saved = demarches.save(e);

        // Les rattachements sont remplaces integralement : le client envoie l'etat
        // final voulu, pas un delta.
        justificatifs.deleteByTicketDemarcheId(saved.getId());
        justificatifs.flush();
        for (JustificatifDepose d : deposes) {
            TicketDemarcheJustificatifEntity j = new TicketDemarcheJustificatifEntity();
            j.setWorkspaceId(workspaceId);
            j.setTicketDemarcheId(saved.getId());
            j.setDocumentId(d.documentId());
            j.setDocumentType(d.documentType());
            j.setAlternativeGroupe((short) d.alternativeGroupe());
            justificatifs.save(j);
        }
        return saved.getId();
    }
}
