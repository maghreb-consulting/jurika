package ma.jurika.dataroom.infrastructure.persistence.spec;

import jakarta.persistence.criteria.Predicate;
import ma.jurika.dataroom.infrastructure.persistence.TicketViewEntity;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Sprint 7 / TASK 2 -- Specifications pour filtrer l'historique des operations
 * (tickets clotures sur un dossier) cote serveur, evite de renvoyer l'ensemble
 * de l'historique a chaque appel et de filtrer cote client.
 *
 * Conventions identiques a DocumentSpecifications : null = "pas de filtre".
 */
public final class TicketSpecifications {

    private TicketSpecifications() {}

    public static Specification<TicketViewEntity> byDossier(UUID dossierId) {
        return (root, q, cb) -> cb.equal(root.get("dossierId"), dossierId);
    }

    public static Specification<TicketViewEntity> ofStatut(String statut) {
        if (statut == null || statut.isBlank()) return null;
        return (root, q, cb) -> cb.equal(root.get("statut"), statut);
    }

    public static Specification<TicketViewEntity> ofTypes(List<String> types) {
        if (types == null || types.isEmpty()) return null;
        return (root, q, cb) -> root.get("type").in(types);
    }

    /**
     * Filtre sur cloture_at (date de fermeture du ticket) -- pas created_at,
     * car la timeline est une frise des operations cloturees.
     */
    public static Specification<TicketViewEntity> closedBetween(Instant from, Instant to) {
        if (from == null && to == null) return null;
        return (root, q, cb) -> {
            Predicate p = cb.conjunction();
            if (from != null) p = cb.and(p, cb.greaterThanOrEqualTo(root.get("clotureAt"), from));
            if (to != null)   p = cb.and(p, cb.lessThanOrEqualTo(root.get("clotureAt"), to));
            return p;
        };
    }
}
