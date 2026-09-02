package ma.jurika.ticket.infrastructure.persistence;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import ma.jurika.ticket.domain.port.TicketFilter;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TicketSpecifications {

    private TicketSpecifications() {}

    public static Specification<TicketEntity> build(TicketFilter f) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.equal(root.get("workspaceId"), f.workspaceId()));
            if (f.statuts() != null && !f.statuts().isEmpty()) {
                preds.add(root.get("statut").in(f.statuts().stream().map(Enum::name).toList()));
            }
            if (f.types() != null && !f.types().isEmpty()) {
                preds.add(root.get("type").in(f.types().stream().map(Enum::name).toList()));
            }
            if (f.priorites() != null && !f.priorites().isEmpty()) {
                preds.add(root.get("priorite").in(f.priorites().stream().map(Enum::name).toList()));
            }
            if (f.assigneId() != null) {
                preds.add(cb.equal(root.get("assigneId"), f.assigneId()));
            }
            // Scoping de visibilite EMPLOYE (RG-U08) : (assigne_id = me OR
            // cree_par_id = me OR dossier.responsable_id = me). Les 3 conditions
            // forment UN SEUL groupe OR pour que le responsable d'un dossier voie
            // aussi les tickets clotures/annules qui ne lui sont pas assignes.
            List<Predicate> scope = new ArrayList<>();
            if (f.assigneOrCreeParId() != null) {
                scope.add(cb.equal(root.get("assigneId"), f.assigneOrCreeParId()));
                scope.add(cb.equal(root.get("creeParId"), f.assigneOrCreeParId()));
            }
            if (f.responsableId() != null) {
                // Sous-requete : tickets.dossier_id IN (SELECT id FROM
                // entreprise_dossiers WHERE responsable_id = X). Pas de relation JPA
                // mappee tickets->dossiers, d'ou la subquery Criteria (meme schema).
                Subquery<UUID> sub = query.subquery(UUID.class);
                Root<DossierEntity> d = sub.from(DossierEntity.class);
                sub.select(d.get("id"));
                sub.where(cb.equal(d.get("responsableId"), f.responsableId()));
                scope.add(root.get("dossierId").in(sub));
            }
            if (!scope.isEmpty()) {
                preds.add(cb.or(scope.toArray(new Predicate[0])));
            }
            if (f.dossierId() != null) {
                preds.add(cb.equal(root.get("dossierId"), f.dossierId()));
            }
            if (f.searchText() != null && !f.searchText().isBlank()) {
                String like = "%" + f.searchText().toLowerCase() + "%";
                preds.add(cb.or(
                        cb.like(cb.lower(root.get("titre")), like),
                        cb.like(cb.lower(root.get("reference")), like),
                        cb.like(cb.lower(root.get("description")), like)));
            }
            return cb.and(preds.toArray(new Predicate[0]));
        };
    }
}
