package ma.jurika.billing.application.facture;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L7 (E5), RG-PAY-03 : le PDF de la facture porte les mentions obligatoires (lues dans le texte
 * du document, pas dans les donnees d'entree), et le rendu est deterministe.
 */
class FacturePdfTest {

    static FacturePdf.Donnees donnees() {
        return new FacturePdf.Donnees("F2026-000042", LocalDate.of(2026, 10, 14),
                new FactureService.Emetteur("Maghreb Consulting", "SARL", "100 000 DH", "1 avenue de test", "Casablanca",
                        "RC 12345", "Tribunal de commerce de Casablanca", "001122334455667", "IF 7788", "TP 9900"),
                new FactureService.Client("Cabinet Atlas", "009988776655443", null, null, "12 rue des Juristes", "Rabat"),
                "Abonnement JURIKA, forfait Professionnel (paiement mensuel) du 14/10/2026 au 13/11/2026",
                LocalDate.of(2026, 10, 14), LocalDate.of(2026, 11, 14), "Carte bancaire (CMI)",
                750_00, new BigDecimal("20.00"), 150_00, 900_00, "Transaction CMI SIM123");
    }

    @Test
    void mentions_obligatoires_dans_le_document() throws Exception {
        byte[] pdf = new FacturePdf().rendre(donnees());
        Path sortie = Path.of("target", "facture-exemple.pdf");
        Files.createDirectories(sortie.getParent());
        Files.write(sortie, pdf);
        String texte;
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            texte = new PDFTextStripper().getText(doc);
        }
        assertThat(texte).contains("Facture").contains("F2026-000042").contains("14/10/2026")
                .contains("Maghreb Consulting").contains("001122334455667").contains("IF 7788").contains("RC 12345")
                .contains("TP 9900").contains("Cabinet Atlas").contains("009988776655443")
                .contains("Abonnement JURIKA, forfait Professionnel")
                .contains("750,00 DH").contains("TVA au taux de 20 %").contains("150,00 DH").contains("900,00 DH")
                .contains("Total TTC").contains("Carte bancaire (CMI)");
    }

    @Test
    void rendu_deterministe() {
        assertThat(new FacturePdf().rendre(donnees())).isEqualTo(new FacturePdf().rendre(donnees()));
    }

    @Test
    void montants_en_dirhams() {
        assertThat(FacturePdf.dirhams(1_234_567)).isEqualTo("12 345,67 DH");
        assertThat(FacturePdf.dirhams(5)).isEqualTo("0,05 DH");
        assertThat(FacturePdf.taux(new BigDecimal("20.00"))).isEqualTo("20");
        assertThat(FacturePdf.taux(new BigDecimal("7.50"))).isEqualTo("7,5");
    }
}
