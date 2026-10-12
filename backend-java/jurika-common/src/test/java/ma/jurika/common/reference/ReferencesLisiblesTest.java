package ma.jurika.common.reference;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Lot L4d (E6) : references lisibles du dossier et du document. */
class ReferencesLisiblesTest {

    @Test
    void dossier_annee_numero_societe() {
        assertThat(ReferencesLisibles.dossier(2026, 7, "PARACOSME")).isEqualTo("2026-0007 \u2014 PARACOSME");
    }

    @Test
    void dossier_sans_numero_n_a_pas_de_reference() {
        assertThat(ReferencesLisibles.dossier(null, 7, "PARACOSME")).isNull();
        assertThat(ReferencesLisibles.dossier(2026, null, "PARACOSME")).isNull();
        assertThat(ReferencesLisibles.dossier(2026, 7, " ")).isNull();
    }

    @Test
    void document_type_societe_version() {
        assertThat(ReferencesLisibles.document("Statuts", "PARACOSME", 2))
                .isEqualTo("Statuts \u2014 PARACOSME \u2014 version 2");
        assertThat(ReferencesLisibles.document(null, "PARACOSME", 1)).isNull();
    }
}
