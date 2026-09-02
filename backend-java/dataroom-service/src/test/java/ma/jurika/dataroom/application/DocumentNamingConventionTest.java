package ma.jurika.dataroom.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentNamingConventionTest {

    @Test
    @DisplayName("slugDenomination : accents + ponctuation + espaces → MAJ_UNDERSCORE")
    void slug_normalise() {
        assertThat(DocumentNamingConvention.slugDenomination("Atlas Société SARL"))
                .isEqualTo("ATLAS_SOCIETE_SARL");
        assertThat(DocumentNamingConvention.slugDenomination("Société à Responsabilité Limitée"))
                .isEqualTo("SOCIETE_A_RESPONSABILITE_LIMITEE");
        assertThat(DocumentNamingConvention.slugDenomination("  J&K Conseil — 2026  "))
                .isEqualTo("J_K_CONSEIL_2026");
    }

    @Test
    @DisplayName("slugDenomination : null / vide → \"SOCIETE\"")
    void slug_fallback_null() {
        assertThat(DocumentNamingConvention.slugDenomination(null)).isEqualTo("SOCIETE");
        assertThat(DocumentNamingConvention.slugDenomination("")).isEqualTo("SOCIETE");
        assertThat(DocumentNamingConvention.slugDenomination("   ")).isEqualTo("SOCIETE");
        // Que des caractères filtrés → fallback aussi.
        assertThat(DocumentNamingConvention.slugDenomination("---!")).isEqualTo("SOCIETE");
    }

    @Test
    @DisplayName("JURIDIQUE : <TYPE>__<SLUG>__<DATE>.<ext>")
    void juridique_avec_date() {
        String n = DocumentNamingConvention.forJuridique(
                "STATUTS", "ATLAS_SARL", "2026-06-23", "pdf");
        assertThat(n).isEqualTo("STATUTS__ATLAS_SARL__2026-06-23.pdf");
    }

    @Test
    @DisplayName("JURIDIQUE : sans date → <TYPE>__<SLUG>.<ext>")
    void juridique_sans_date() {
        String n = DocumentNamingConvention.forJuridique(
                "PV_AGE", "BETA_SOCIETE", null, "docx");
        assertThat(n).isEqualTo("PV_AGE__BETA_SOCIETE.docx");
    }

    @Test
    @DisplayName("JURIDIQUE : type null → AUTRE ; slug vide → SOCIETE ; ext null → bin")
    void juridique_fallbacks() {
        String n = DocumentNamingConvention.forJuridique(null, "", null, null);
        assertThat(n).isEqualTo("AUTRE__SOCIETE.bin");
    }

    @Test
    @DisplayName("COMPTABLE : <ANNEE>/<CATEGORIE>__<SLUG>.<ext>")
    void comptable_format() {
        String n = DocumentNamingConvention.forComptable(2023, "BANQUE", "ATLAS_SARL", "xlsx");
        assertThat(n).isEqualTo("2023/BANQUE__ATLAS_SARL.xlsx");
    }

    @Test
    @DisplayName("COMPTABLE : année null → 0000 ; categorie minuscule → upper")
    void comptable_normalisation() {
        String n = DocumentNamingConvention.forComptable(null, "ventes", "X", "csv");
        assertThat(n).isEqualTo("0000/VENTES__X.csv");
    }

    @Test
    @DisplayName("FISCAL : <ANNEE>/<CATEGORIE_FISCALE>__<SLUG>.<ext>")
    void fiscal_format() {
        String n = DocumentNamingConvention.forFiscal(2024, "TVA", "BETA_SOCIETE", "pdf");
        assertThat(n).isEqualTo("2024/TVA__BETA_SOCIETE.pdf");
    }

    @Test
    @DisplayName("Extension : extrait du filename original, lowercased, sans alphanums filtrés")
    void extension_extraction() {
        assertThat(DocumentNamingConvention.extensionOf("bilan-2023.XLSX")).isEqualTo("xlsx");
        assertThat(DocumentNamingConvention.extensionOf("statuts.docx")).isEqualTo("docx");
        assertThat(DocumentNamingConvention.extensionOf("noext")).isEqualTo("bin");
        assertThat(DocumentNamingConvention.extensionOf("trailing.")).isEqualTo("bin");
        assertThat(DocumentNamingConvention.extensionOf(null)).isEqualTo("bin");
    }
}
