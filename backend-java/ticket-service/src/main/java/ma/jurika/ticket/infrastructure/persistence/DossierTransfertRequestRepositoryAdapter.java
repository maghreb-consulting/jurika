package ma.jurika.ticket.infrastructure.persistence;

import ma.jurika.ticket.domain.model.DossierTransfertRequest;
import ma.jurika.ticket.domain.model.DossierTransfertRequestView;
import ma.jurika.ticket.domain.model.TransfertStatut;
import ma.jurika.ticket.domain.port.DossierTransfertRequestRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class DossierTransfertRequestRepositoryAdapter implements DossierTransfertRequestRepository {

    private static final String PENDING = TransfertStatut.EN_ATTENTE.name();

    private final DossierTransfertRequestJpaRepository jpa;
    private final DossierJpaRepository dossierJpa;

    public DossierTransfertRequestRepositoryAdapter(DossierTransfertRequestJpaRepository jpa,
                                                    DossierJpaRepository dossierJpa) {
        this.jpa = jpa;
        this.dossierJpa = dossierJpa;
    }

    @Override
    public DossierTransfertRequest save(DossierTransfertRequest r) {
        DossierTransfertRequestEntity e = (r.id() != null)
                ? jpa.findById(r.id()).orElseGet(DossierTransfertRequestEntity::new)
                : new DossierTransfertRequestEntity();
        if (r.id() != null) e.setId(r.id());
        e.setWorkspaceId(r.workspaceId());
        e.setDossierId(r.dossierId());
        e.setFromUserId(r.fromUserId());
        e.setToUserId(r.toUserId());
        e.setStatut(r.statut().name());
        e.setDirect(r.direct());
        e.setMotif(r.motif());
        if (r.createdAt() != null) e.setCreatedAt(r.createdAt());
        e.setDecidedAt(r.decidedAt());
        return toDomain(jpa.save(e));
    }

    @Override
    public Optional<DossierTransfertRequest> findById(UUID workspaceId, UUID id) {
        return jpa.findById(id)
                .filter(e -> e.getWorkspaceId().equals(workspaceId))
                .map(this::toDomain);
    }

    @Override
    public boolean existsPendingForDossier(UUID workspaceId, UUID dossierId) {
        return jpa.existsByWorkspaceIdAndDossierIdAndStatut(workspaceId, dossierId, PENDING);
    }

    @Override
    public List<DossierTransfertRequestView> listInbox(UUID workspaceId, UUID toUserId) {
        return enrich(jpa.findByWorkspaceIdAndToUserIdAndStatutOrderByCreatedAtDesc(
                workspaceId, toUserId, PENDING));
    }

    @Override
    public List<DossierTransfertRequestView> listOutbox(UUID workspaceId, UUID fromUserId) {
        return enrich(jpa.findByWorkspaceIdAndFromUserIdAndStatutOrderByCreatedAtDesc(
                workspaceId, fromUserId, PENDING));
    }

    private List<DossierTransfertRequestView> enrich(List<DossierTransfertRequestEntity> rows) {
        if (rows.isEmpty()) return List.of();
        Map<UUID, String> raisons = dossierJpa.findAllById(
                        rows.stream().map(DossierTransfertRequestEntity::getDossierId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(DossierEntity::getId, DossierEntity::getRaisonSociale,
                        (a, b) -> a));
        return rows.stream()
                .map(e -> new DossierTransfertRequestView(toDomain(e), raisons.get(e.getDossierId())))
                .toList();
    }

    private DossierTransfertRequest toDomain(DossierTransfertRequestEntity e) {
        return new DossierTransfertRequest(
                e.getId(), e.getWorkspaceId(), e.getDossierId(), e.getFromUserId(),
                e.getToUserId(), TransfertStatut.valueOf(e.getStatut()), e.isDirect(),
                e.getMotif(), e.getCreatedAt(), e.getDecidedAt());
    }
}
