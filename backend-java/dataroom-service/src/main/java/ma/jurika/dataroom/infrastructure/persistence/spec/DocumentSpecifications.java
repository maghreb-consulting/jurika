package ma.jurika.dataroom.infrastructure.persistence.spec;

import jakarta.persistence.criteria.Predicate;
import ma.jurika.dataroom.infrastructure.persistence.DocumentEntity;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Sprint 7 / TASK 1 -- Specification pattern pour les filtres composables
 * de la recherche juridique.
 *
 * NOTE (audit A3) : le FTS (tsvector @@ websearch_to_tsquery) n'est PAS
 * couvert ici car non portable en JPA Criteria. Il est implemente en
 * @Query nativeQuery dans DocumentJpaRepository.ftsMatchIds(...).
 * Le use case combine : (1) IDs FTS via repo natif, puis (2) Specifications
 * structurelles ici via idIn(...) + autres filtres.
 */
public final class DocumentSpecifications {

    private DocumentSpecifications() {}

    public static Specification<DocumentEntity> byDossier(UUID dossierId) {
        return (root, q, cb) -> cb.equal(root.get("dossierId"), dossierId);
    }

    public static Specification<DocumentEntity> ofTypes(List<String> types) {
        if (types == null || types.isEmpty()) return null;
        return (root, q, cb) -> root.get("documentType").in(types);
    }

    public static Specification<DocumentEntity> currentOnly() {
        return (root, q, cb) -> cb.isTrue(root.get("current"));
    }

    public static Specification<DocumentEntity> oldVersionsOnly() {
        return (root, q, cb) -> cb.isFalse(root.get("current"));
    }

    public static Specification<DocumentEntity> createdBetween(Instant from, Instant to) {
        if (from == null && to == null) return null;
        return (root, q, cb) -> {
            Predicate p = cb.conjunction();
            if (from != null) p = cb.and(p, cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            if (to != null)   p = cb.and(p, cb.lessThanOrEqualTo(root.get("createdAt"), to));
            return p;
        };
    }

    public static Specification<DocumentEntity> idIn(Collection<UUID> ids) {
        if (ids == null) return null;
        if (ids.isEmpty()) {
            // FTS a tourne mais aucun match : forcer un set vide
            return (root, q, cb) -> cb.disjunction();
        }
        return (root, q, cb) -> root.get("id").in(ids);
    }
}
