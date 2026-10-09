package ma.jurika.ticket.infrastructure.persistence;

import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketPriorite;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.model.TicketType;
import ma.jurika.ticket.domain.port.TicketFilter;
import ma.jurika.ticket.domain.port.TicketRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TicketRepositoryAdapter implements TicketRepository {

    private final TicketJpaRepository jpa;

    @PersistenceContext
    private EntityManager em;

    public TicketRepositoryAdapter(TicketJpaRepository jpa) {
        this.jpa = jpa;
    }

    /**
     * Lot L0 (E21, P9) : ticket relu dans le workspace courant (filtre applicatif
     * explicite, en plus de la RLS). Absent, ou d'un autre cabinet : 404.
     */
    private TicketEntity duWorkspaceCourant(UUID ticketId) {
        UUID ws = TenantContext.get();
        return (ws == null ? Optional.<TicketEntity>empty() : jpa.findByIdAndWorkspaceId(ticketId, ws))
                .orElseThrow(() -> new NotFoundException("Ticket introuvable : " + ticketId));
    }

    @Override
    public Optional<Ticket> findById(UUID workspaceId, UUID ticketId) {
        // Lot L0 (E21) : filtre dans la requete plutot qu'apres lecture.
        return jpa.findByIdAndWorkspaceId(ticketId, workspaceId).map(this::toDomain);
    }

    @Override
    public Ticket create(UUID workspaceId, String reference, String titre, TicketType type,
                          TicketPriorite priorite, UUID dossierId, UUID assigneId, UUID creeParId,
                          String description, Instant deadline) {
        TicketEntity e = new TicketEntity();
        e.setWorkspaceId(workspaceId);
        e.setReference(reference);
        e.setTitre(titre);
        e.setType(type.name());
        e.setStatut(TicketStatut.CREATION_TICKET.name());
        e.setPriorite(priorite.name());
        e.setDossierId(dossierId);
        e.setAssigneId(assigneId);
        e.setCreeParId(creeParId);
        e.setDescription(description);
        e.setDeadline(deadline);
        return toDomain(jpa.save(e));
    }

    @Override
    public Ticket updateStatut(UUID ticketId, TicketStatut statut, String motif,
                                Instant clotureAt, Instant annuleAt) {
        TicketEntity e = duWorkspaceCourant(ticketId);
        e.setStatut(statut.name());
        e.setAnnulationMotif(motif);
        e.setClotureAt(clotureAt);
        e.setAnnuleAt(annuleAt);
        return toDomain(jpa.save(e));
    }

    @Override
    public Ticket updateAssignment(UUID ticketId, UUID assigneId, TicketPriorite priorite,
                                    Instant deadline, String titre, String description) {
        TicketEntity e = duWorkspaceCourant(ticketId);
        if (assigneId != null) e.setAssigneId(assigneId);
        if (priorite != null) e.setPriorite(priorite.name());
        if (deadline != null) e.setDeadline(deadline);
        if (titre != null) e.setTitre(titre);
        if (description != null) e.setDescription(description);
        return toDomain(jpa.save(e));
    }

    @Override
    public int realignerSurResponsable(UUID workspaceId, UUID dossierId, UUID responsableId) {
        return em.createNativeQuery("""
                UPDATE tickets SET assigne_id = ?1, updated_at = NOW()
                 WHERE workspace_id = ?2 AND dossier_id = ?3
                   AND assigne_id IS DISTINCT FROM ?1
                """)
                .setParameter(1, responsableId)
                .setParameter(2, workspaceId)
                .setParameter(3, dossierId)
                .executeUpdate();
    }

    @Override
    public Ticket markTransferred(UUID ticketId, UUID assigneId, Instant transferredAt) {
        TicketEntity e = duWorkspaceCourant(ticketId);
        // Reassignation + marquage transfert. Le statut reste inchange (un dossier
        // transfere garde son etat NOUVEAU/EN_COURS, cf. applyTransfer).
        e.setAssigneId(assigneId);
        e.setTransferredAt(transferredAt);
        return toDomain(jpa.save(e));
    }

    @Override
    public List<Ticket> search(TicketFilter filter, int limit, int offset) {
        Sort sort = Sort.by(filter.sortDesc() ? Sort.Direction.DESC : Sort.Direction.ASC,
                normalizeSort(filter.sortBy()));
        return jpa.findAll(TicketSpecifications.build(filter), PageRequest.of(offset / Math.max(limit, 1), Math.max(limit, 1), sort))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public long count(TicketFilter filter) {
        return jpa.count(TicketSpecifications.build(filter));
    }

    @Override
    public String generateReference() {
        Number n = (Number) em.createNativeQuery("SELECT nextval('ticket_ref_seq')").getSingleResult();
        int year = LocalDate.now(ZoneOffset.UTC).getYear();
        return String.format("T-%d-%05d", year, n.longValue());
    }

    private String normalizeSort(String sortBy) {
        return switch (sortBy == null ? "createdAt" : sortBy) {
            case "deadline" -> "deadline";
            case "priorite" -> "priorite";
            case "reference" -> "reference";
            case "titre" -> "titre";
            default -> "createdAt";
        };
    }

    private Ticket toDomain(TicketEntity e) {
        return new Ticket(e.getId(), e.getWorkspaceId(), e.getReference(), e.getTitre(),
                TicketType.valueOf(e.getType()), TicketStatut.valueOf(e.getStatut()),
                TicketPriorite.valueOf(e.getPriorite()), e.getDossierId(), e.getAssigneId(),
                e.getCreeParId(), e.getDescription(), e.getDeadline(), e.getAnnulationMotif(),
                e.getClotureAt(), e.getAnnuleAt(), e.getTransferredAt(), e.getCreatedAt());
    }
}
