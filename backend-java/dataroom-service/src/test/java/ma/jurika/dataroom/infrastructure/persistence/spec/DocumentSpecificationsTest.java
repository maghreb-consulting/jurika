package ma.jurika.dataroom.infrastructure.persistence.spec;

import ma.jurika.dataroom.infrastructure.persistence.DocumentEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 7 / TASK 1.4 -- Tests unitaires sur la composition des Specifications.
 *
 * Verifie le contrat "null = pas de filtre, non-null = predicate applicable".
 * La verification SQL/semantique des predicates est couverte par JuridiqueSearchIT
 * (TestContainers) dans la suite test du Sprint 7 (TASK 7).
 */
class DocumentSpecificationsTest {

    @Test
    @DisplayName("byDossier retourne toujours un Specification non-null")
    void byDossier_alwaysNonNull() {
        Specification<DocumentEntity> spec = DocumentSpecifications.byDossier(UUID.randomUUID());
        assertThat(spec).isNotNull();
    }

    @Test
    @DisplayName("ofTypes(null) et ofTypes([]) -> null (pas de filtre)")
    void ofTypes_emptyOrNull_returnsNull() {
        assertThat(DocumentSpecifications.ofTypes(null)).isNull();
        assertThat(DocumentSpecifications.ofTypes(List.of())).isNull();
    }

    @Test
    @DisplayName("ofTypes([STATUTS]) -> Specification non-null")
    void ofTypes_withValues_returnsNonNull() {
        Specification<DocumentEntity> spec = DocumentSpecifications.ofTypes(List.of("STATUTS", "PV_AGE"));
        assertThat(spec).isNotNull();
    }

    @Test
    @DisplayName("currentOnly + oldVersionsOnly retournent toujours non-null")
    void scopeSpecs_alwaysNonNull() {
        assertThat(DocumentSpecifications.currentOnly()).isNotNull();
        assertThat(DocumentSpecifications.oldVersionsOnly()).isNotNull();
    }

    @Test
    @DisplayName("createdBetween(null,null) -> null ; avec au moins une borne -> non-null")
    void createdBetween_borderCases() {
        assertThat(DocumentSpecifications.createdBetween(null, null)).isNull();
        assertThat(DocumentSpecifications.createdBetween(Instant.now(), null)).isNotNull();
        assertThat(DocumentSpecifications.createdBetween(null, Instant.now())).isNotNull();
        assertThat(DocumentSpecifications.createdBetween(
                Instant.now().minusSeconds(60), Instant.now())).isNotNull();
    }

    @Test
    @DisplayName("idIn(null) -> null ; idIn(empty) -> Specification non-null (force match vide)")
    void idIn_borderCases() {
        assertThat(DocumentSpecifications.idIn(null)).isNull();
        // empty != null : on veut activement bloquer (sinon le filtre etre absent)
        assertThat(DocumentSpecifications.idIn(Set.of())).isNotNull();
        assertThat(DocumentSpecifications.idIn(Set.of(UUID.randomUUID()))).isNotNull();
    }

    @Test
    @DisplayName("Composition Specification.where(byDossier).and(ofTypes).and(currentOnly) reste non-null")
    void compositeSpec_chainsCorrectly() {
        UUID dossierId = UUID.randomUUID();
        Specification<DocumentEntity> spec = Specification
                .where(DocumentSpecifications.byDossier(dossierId))
                .and(DocumentSpecifications.ofTypes(List.of("STATUTS")))
                .and(DocumentSpecifications.currentOnly())
                .and(DocumentSpecifications.createdBetween(Instant.now().minusSeconds(3600), null));
        assertThat(spec).isNotNull();
    }
}
