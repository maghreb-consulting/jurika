package ma.jurika.dataroom.infrastructure.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import ma.jurika.dataroom.api.dto.FicheClientDtos.DocumentEnVigueur;
import ma.jurika.dataroom.api.dto.FicheClientDtos.DocumentHistoryEntry;
import ma.jurika.dataroom.api.dto.FicheClientDtos.FicheClientView;
import ma.jurika.dataroom.api.dto.FicheClientDtos.Identity;
import ma.jurika.dataroom.api.dto.FicheClientDtos.Operation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rendu PDF de la Fiche client : structure, charte (« jamais de blanc »),
 * sous-type précis, pagination. Écrit aussi un exemplaire dans output/ pour
 * validation visuelle.
 */
class FicheClientPdfTest {

    private static final Instant T0 = Instant.parse("2024-01-15T09:00:00Z");

    private String extractText(byte[] bytes) throws Exception {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private FicheClientView completeView(String statut) {
        Identity id = new Identity(
                "ACME CONSEIL SARL", "SARL", "002345678901234",
                statut.equals("EN_CONSTITUTION") ? null : "RC-45219",
                statut.equals("EN_CONSTITUTION") ? null : "Tribunal de commerce de Casablanca",
                statut.equals("EN_CONSTITUTION") ? null : "IF-1122334",
                statut.equals("EN_CONSTITUTION") ? null : "TP-778899",
                statut.equals("EN_CONSTITUTION") ? null : "CNSS-556677",
                "12 rue des Consultants, Maarif", "Casablanca",
                new BigDecimal("100000.00"), LocalDate.of(2024, 1, 10), statut);

        Operation modif = new Operation("MODIFICATION", "Modification statutaire",
                "nomination d'un gérant", "TCK-2024-014",
                T0, T0.plus(20, ChronoUnit.DAYS), LocalDate.of(2024, 3, 15), true);
        Operation creation = new Operation("CREATION", "Création de société",
                null, "TCK-2024-001", T0.minus(30, ChronoUnit.DAYS),
                T0.minus(5, ChronoUnit.DAYS), null, true);
        Operation enCours = new Operation("DISSOLUTION", "Dissolution",
                null, "TCK-2025-088", T0.plus(300, ChronoUnit.DAYS), null, null, false);

        DocumentEnVigueur d1 = new DocumentEnVigueur("STATUTS", "Statuts mis à jour", T0, (short) 2);
        DocumentEnVigueur d2 = new DocumentEnVigueur("PV_AGO", "PV assemblée 2024", T0, (short) 1);

        DocumentHistoryEntry h1 = new DocumentHistoryEntry(
                T0, "REMPLACEMENT", "STATUTS", "Statuts mis à jour", (short) 2,
                "Karim Alaoui", "Modification : nomination d'un gérant");
        DocumentHistoryEntry h2 = new DocumentHistoryEntry(
                T0.minus(30, ChronoUnit.DAYS), "AJOUT", "STATUTS", "Statuts", (short) 1,
                "Karim Alaoui", null);

        return new FicheClientView(UUID.randomUUID(),
                ma.jurika.common.pdf.CabinetIdentity.ofName(null, "Maghreb Consulting"), id,
                List.of(modif, creation, enCours), List.of(d1, d2), List.of(h1, h2),
                T0.plus(400, ChronoUnit.DAYS));
    }

    @Test
    void genere_un_pdf_valide_avec_les_quatre_sections() throws Exception {
        byte[] pdf = new FicheClientPdf(completeView("ACTIVE")).generate();

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5)).startsWith("%PDF-");

        String text = extractText(pdf);
        assertThat(text).contains("Fiche client");
        // Couche texte exacte malgré la ligature « fi » du titre serif Playfair.
        assertThat(text).contains("Identifiants");
        assertThat(text).doesNotContain("Identiants");
        assertThat(text).contains("ACME CONSEIL SARL");
        // Nom du cabinet en en-tête (papier à en-tête, casse préservée).
        assertThat(text).contains("Maghreb Consulting");
        // Section 2 : sous-type précis présent.
        assertThat(text).contains("nomination d'un");
        // Section 4 : auteur résolu.
        assertThat(text).contains("Karim Alaoui");
        // Pagination.
        assertThat(text).contains("Page 1 /");
    }

    @Test
    void en_constitution_affiche_en_cours_d_attribution_pour_les_champs_manquants() throws Exception {
        byte[] pdf = new FicheClientPdf(completeView("EN_CONSTITUTION")).generate();
        String text = extractText(pdf);
        assertThat(text).contains("En cours d'attribution");
        assertThat(text).doesNotContain("Non renseign");
    }

    @Test
    void statut_actif_affiche_non_renseigne_pour_un_champ_vide() throws Exception {
        // ACTIVE avec RC/IF/etc. nuls -> « — Non renseigné — ».
        Identity id = new Identity("VIDE SARL", "SARL", null, null, null, null, null, null,
                null, null, null, null, "ACTIVE");
        FicheClientView v = new FicheClientView(UUID.randomUUID(),
                ma.jurika.common.pdf.CabinetIdentity.ofName(null, "Cabinet"), id,
                List.of(), List.of(), List.of(), Instant.now());
        String text = extractText(new FicheClientPdf(v).generate());
        assertThat(text).contains("Non renseign");
        assertThat(text).doesNotContain("En cours d'attribution");
        // États vides des sections.
        assertThat(text).contains("Aucune op");   // "Aucune opération..."
        assertThat(text).contains("Aucun document en vigueur");
    }

    @Test
    void ecrit_un_exemplaire_pour_validation_visuelle() throws Exception {
        byte[] pdf = new FicheClientPdf(completeView("ACTIVE")).generate();
        try {
            Path out = Path.of("target", "samples", "Fiche_Client_SAMPLE.pdf");
            Files.createDirectories(out.getParent());
            Files.write(out, pdf);
        } catch (Exception ignore) {
            // Best-effort : la génération d'un exemplaire ne doit pas faire échouer le test.
        }
        assertThat(pdf).isNotEmpty();
    }
}
