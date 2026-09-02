package ma.jurika.dataroom.application;

import ma.jurika.dataroom.api.dto.DataroomDtos.DocumentSummary;
import ma.jurika.dataroom.api.dto.DataroomDtos.SearchJuridiqueInput;
import ma.jurika.dataroom.api.dto.DataroomDtos.SearchJuridiqueOutput;
import ma.jurika.dataroom.api.dto.DataroomDtos.VersionScope;
import ma.jurika.dataroom.infrastructure.persistence.DocumentEntity;
import ma.jurika.dataroom.infrastructure.persistence.DocumentJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.spec.DocumentSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Sprint 7 / TASK 1 -- Use case "Recherche full-text + filtres avances Juridique".
 *
 * Strategie composite (audit A3) :
 *   1) Si q non-blank -> appel @Query nativeQuery `ftsMatchIds` pour obtenir
 *      les IDs matchant le tsvector. Si zero match -> retour immediat output vide.
 *   2) Construction d'une Specification combinant :
 *      - byDossier(dossierId) toujours present
 *      - ofTypes(types) si fourni
 *      - currentOnly()/oldVersionsOnly() selon versionScope
 *      - createdBetween(from, to) si bornes presentes
 *      - idIn(ftsIds) UNIQUEMENT si q non-blank
 *   3) Page<DocumentEntity> via JpaSpecificationExecutor, tri createdAt DESC.
 *
 * RLS PostgreSQL (workspace_id = current_setting('app.current_workspace_id'))
 * filtre les rangees au niveau base, donc on ne re-verifie pas ici.
 */
@Service
public class SearchJuridiqueDocumentsUseCase {

    private final DocumentJpaRepository documents;
    private final DataroomJuridiqueService juridique; // pour reutiliser summary()

    public SearchJuridiqueDocumentsUseCase(DocumentJpaRepository documents,
                                            DataroomJuridiqueService juridique) {
        this.documents = documents;
        this.juridique = juridique;
    }

    @Transactional(readOnly = true)
    public SearchJuridiqueOutput execute(SearchJuridiqueInput in) {
        Specification<DocumentEntity> spec = Specification.where(
                DocumentSpecifications.byDossier(in.dossierId()));

        if (in.types() != null && !in.types().isEmpty()) {
            spec = spec.and(DocumentSpecifications.ofTypes(in.types()));
        }

        VersionScope scope = in.versionScope();
        if (scope == VersionScope.CURRENT) {
            spec = spec.and(DocumentSpecifications.currentOnly());
        } else if (scope == VersionScope.OLD) {
            spec = spec.and(DocumentSpecifications.oldVersionsOnly());
        } // ALL = no extra predicate

        if (in.from() != null || in.to() != null) {
            spec = spec.and(DocumentSpecifications.createdBetween(in.from(), in.to()));
        }

        boolean hasQuery = in.q() != null && !in.q().isBlank();
        if (hasQuery) {
            List<UUID> ftsIds = documents.ftsMatchIds(in.dossierId(), in.q().trim());
            if (ftsIds.isEmpty()) {
                return new SearchJuridiqueOutput(List.of(), 0L);
            }
            spec = spec.and(DocumentSpecifications.idIn(ftsIds));
        }

        PageRequest page = PageRequest.of(
                in.offset() / Math.max(in.limit(), 1),
                in.limit(),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<DocumentEntity> result = documents.findAll(spec, page);
        List<DocumentSummary> items = result.getContent().stream()
                .map(juridique::summary)
                .toList();
        return new SearchJuridiqueOutput(items, result.getTotalElements());
    }
}
