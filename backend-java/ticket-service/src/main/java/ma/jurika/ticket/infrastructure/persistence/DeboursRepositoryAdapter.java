package ma.jurika.ticket.infrastructure.persistence;

import ma.jurika.ticket.domain.model.Debours;
import ma.jurika.ticket.domain.model.DeboursCategorie;
import ma.jurika.ticket.domain.model.DeboursSaisie;
import ma.jurika.ticket.domain.port.DeboursRepository;
import org.springframework.stereotype.Repository;

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
        return entite(workspaceId, id).map(this::toDomain);
    }

    @Override
    public List<Debours> findByTicket(UUID workspaceId, UUID ticketId) {
        return jpa.findByWorkspaceIdAndTicketIdOrderByDateEngagementAscCreatedAtAscIdAsc(workspaceId, ticketId)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Debours create(UUID workspaceId, UUID ticketId, DeboursSaisie saisie, UUID createdById) {
        DeboursEntity e = new DeboursEntity();
        e.setWorkspaceId(workspaceId);
        e.setTicketId(ticketId);
        e.setCreatedById(createdById);
        appliquer(e, saisie);
        return toDomain(jpa.save(e));
    }

    @Override
    public Debours update(UUID workspaceId, UUID id, DeboursSaisie saisie) {
        DeboursEntity e = entite(workspaceId, id).orElseThrow();
        if (!saisie.categorie().name().equals(e.getCategorie())) {
            // Reclassement par l'employe : la categorie reprise par V40 n'a plus d'objet
            // (l'ancienne et la nouvelle valeur restent dans la trace d'audit).
            e.setCategorieReprise(null);
        }
        appliquer(e, saisie);
        return toDomain(jpa.save(e));
    }

    @Override
    public void delete(UUID workspaceId, UUID id) {
        entite(workspaceId, id).ifPresent(jpa::delete);
    }

    /** Filtre workspace explicite, en plus de la RLS. */
    private Optional<DeboursEntity> entite(UUID workspaceId, UUID id) {
        return jpa.findById(id).filter(e -> e.getWorkspaceId().equals(workspaceId));
    }

    private static void appliquer(DeboursEntity e, DeboursSaisie s) {
        e.setLibelle(s.libelle().trim());
        e.setCategorie(s.categorie().name());
        e.setMontantMad(s.montant());
        e.setDateEngagement(s.dateEngagement());
        e.setPieceDocumentId(s.pieceDocumentId());
        if (s.pieceDocumentId() != null) {
            e.setPieceJointeFilename(s.pieceJointeFilename());
            e.setPieceJointeUrl(null);
        } else if (e.getPieceJointeUrl() == null) {
            e.setPieceJointeFilename(null);
        } // sinon : piece anterieure au lot L5 (adresse et nom), conservee telle quelle
        e.setNotes(s.notes());
    }

    private Debours toDomain(DeboursEntity e) {
        return new Debours(e.getId(), e.getWorkspaceId(), e.getTicketId(), e.getLibelle(),
                DeboursCategorie.valueOf(e.getCategorie()), e.getMontantMad(), e.getDateEngagement(),
                e.getPieceJointeUrl(), e.getPieceJointeFilename(), e.getNotes(),
                e.getCreatedById(), e.getCreatedAt(), e.getCategorieReprise(), e.getPieceDocumentId());
    }
}
