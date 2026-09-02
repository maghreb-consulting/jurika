package ma.jurika.ticket.infrastructure.persistence;

import ma.jurika.ticket.domain.model.Debours;
import ma.jurika.ticket.domain.model.DeboursCategorie;
import ma.jurika.ticket.domain.port.DeboursRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DeboursRepositoryAdapter implements DeboursRepository {

    private final DeboursJpaRepository jpa;

    public DeboursRepositoryAdapter(DeboursJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Debours> findById(UUID workspaceId, UUID id) {
        return jpa.findById(id).filter(e -> e.getWorkspaceId().equals(workspaceId)).map(this::toDomain);
    }

    @Override
    public List<Debours> findByTicket(UUID workspaceId, UUID ticketId) {
        return jpa.findByWorkspaceIdAndTicketIdOrderByDateEngagementDesc(workspaceId, ticketId)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Debours create(UUID workspaceId, UUID ticketId, String libelle, DeboursCategorie categorie,
                           BigDecimal montant, LocalDate dateEngagement, String pieceJointeUrl,
                           String filename, String notes, UUID createdById) {
        DeboursEntity e = new DeboursEntity();
        e.setWorkspaceId(workspaceId);
        e.setTicketId(ticketId);
        e.setLibelle(libelle);
        e.setCategorie(categorie.name());
        e.setMontantMad(montant);
        e.setDateEngagement(dateEngagement);
        e.setPieceJointeUrl(pieceJointeUrl);
        e.setPieceJointeFilename(filename);
        e.setNotes(notes);
        e.setCreatedById(createdById);
        return toDomain(jpa.save(e));
    }

    @Override
    public Debours update(UUID id, String libelle, DeboursCategorie categorie, BigDecimal montant,
                           LocalDate dateEngagement, String notes) {
        DeboursEntity e = jpa.findById(id).orElseThrow();
        if (libelle != null) e.setLibelle(libelle);
        if (categorie != null) e.setCategorie(categorie.name());
        if (montant != null) e.setMontantMad(montant);
        if (dateEngagement != null) e.setDateEngagement(dateEngagement);
        if (notes != null) e.setNotes(notes);
        return toDomain(jpa.save(e));
    }

    @Override
    public void delete(UUID workspaceId, UUID id) {
        jpa.findById(id).filter(e -> e.getWorkspaceId().equals(workspaceId)).ifPresent(jpa::delete);
    }

    @Override
    public BigDecimal sumByTicket(UUID workspaceId, UUID ticketId) {
        BigDecimal s = jpa.sumForTicket(workspaceId, ticketId);
        return s == null ? BigDecimal.ZERO : s;
    }

    private Debours toDomain(DeboursEntity e) {
        return new Debours(e.getId(), e.getWorkspaceId(), e.getTicketId(), e.getLibelle(),
                DeboursCategorie.valueOf(e.getCategorie()), e.getMontantMad(), e.getDateEngagement(),
                e.getPieceJointeUrl(), e.getPieceJointeFilename(), e.getNotes(),
                e.getCreatedById(), e.getCreatedAt());
    }
}
