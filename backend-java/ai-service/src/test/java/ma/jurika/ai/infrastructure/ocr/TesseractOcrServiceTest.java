package ma.jurika.ai.infrastructure.ocr;

import ma.jurika.ai.domain.ocr.OcrDocumentType;
import ma.jurika.ai.domain.ocr.OcrExtractionResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires TesseractOcrService — vérifient le comportement de fallback gracieux
 * SANS exiger l'installation de Tesseract sur la machine de CI.
 * <p>
 * Les tests d'intégration réels (avec binaire Tesseract + tessdata) sont à exécuter
 * manuellement en environnement provisionné via {@code -Dtesseract.available=true}.
 */
class TesseractOcrServiceTest {

    @Test
    void extract_withEmptyDatapath_fallsBackToManual() {
        TesseractOcrService service = new TesseractOcrService("", "fra+ara");

        OcrExtractionResult result = service.extract(new byte[]{1, 2, 3}, "cin.jpg", OcrDocumentType.CIN_RECTO);

        assertThat(result.requiresManualEntry()).isTrue();
        assertThat(result.provider()).isEqualTo(OcrExtractionResult.PROVIDER_MANUAL_FALLBACK);
        // 2026-06-21 — Assertion rendue independante de l'environnement. Le
        // constructeur retombe sur la var d'env TESSDATA_PREFIX quand le datapath
        // passe vide (cf. TesseractOcrService lignes 77-79) : sur une machine ou
        // Tesseract est installe (TESSDATA_PREFIX defini), le warning porte sur le
        // format de l'image et non sur TESSDATA. Le contrat reellement teste est le
        // fallback manuel gracieux + warning explicite ; les deux branches
        // contiennent "saisie manuelle requise".
        assertThat(result.warnings()).anyMatch(w -> w.contains("saisie manuelle requise"));
    }

    @Test
    void extract_withNullImage_fallsBackToManual() {
        TesseractOcrService service = new TesseractOcrService("/usr/share/tessdata", "fra");

        OcrExtractionResult result = service.extract(null, "missing.jpg", OcrDocumentType.CIN_RECTO);

        assertThat(result.requiresManualEntry()).isTrue();
        assertThat(result.provider()).isEqualTo(OcrExtractionResult.PROVIDER_MANUAL_FALLBACK);
    }

    @Test
    void extract_withEmptyImage_fallsBackToManual() {
        TesseractOcrService service = new TesseractOcrService("/usr/share/tessdata", "fra");

        OcrExtractionResult result = service.extract(new byte[0], "empty.jpg", OcrDocumentType.CIN_RECTO);

        assertThat(result.requiresManualEntry()).isTrue();
        assertThat(result.provider()).isEqualTo(OcrExtractionResult.PROVIDER_MANUAL_FALLBACK);
    }

    @Test
    void extract_withUnreadableBytes_fallsBackToManual() {
        // datapath présent (sinon court-circuit avant lecture), mais bytes non-image
        TesseractOcrService service = new TesseractOcrService("/tmp/fake-tessdata", "fra");

        OcrExtractionResult result = service.extract(
                "ceci n'est pas une image".getBytes(),
                "fake.jpg",
                OcrDocumentType.CIN_RECTO
        );

        assertThat(result.requiresManualEntry()).isTrue();
        assertThat(result.provider()).isEqualTo(OcrExtractionResult.PROVIDER_MANUAL_FALLBACK);
    }

    // ========================================================================
    //  Fix 2026-06-04 — tests pour le parsing PDF + Certificat Negatif
    // ========================================================================

    @Test
    void looksLikePdf_detectsMagicBytesAndExtension() {
        // %PDF (0x25 0x50 0x44 0x46) — header standard PDF.
        byte[] pdfHeader = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x37};
        assertThat(TesseractOcrService.looksLikePdf(pdfHeader, "anything.bin")).isTrue();
        // Pas de magic bytes mais extension .pdf -> on fait confiance au filename
        assertThat(TesseractOcrService.looksLikePdf(new byte[]{1, 2, 3}, "cn.PDF")).isTrue();
        // Ni magic ni extension -> false (chemin image classique).
        assertThat(TesseractOcrService.looksLikePdf(new byte[]{0x47, 0x49, 0x46}, "anim.gif")).isFalse();
        // Null safe.
        assertThat(TesseractOcrService.looksLikePdf(null, null)).isFalse();
    }

    @Test
    void parseCnFields_withRealisticOmpicText_extractsAllSixCanonicalKeys() {
        // Texte simule typique d'un Certificat Negatif numerique OMPIC apres extraction
        // PDFBox (le format reel a un layout tabulaire, on en simule l'essentiel ici).
        String rawText = """
                ROYAUME DU MAROC
                Office Marocain de la Propriete Industrielle et Commerciale
                CERTIFICAT NEGATIF

                Numero : CN-2026-12345
                Date de delivrance : 15/05/2026

                Denomination : ATLAS TRADING SARL
                ICE : 002345678000077
                Beneficiaire : Oussama BENATIK
                Activite : Commerce de gros de materiaux de construction
                """;

        TesseractOcrService service = new TesseractOcrService("/dev/null", "fra");
        java.util.Map<String, String> fields = new java.util.HashMap<>();
        java.util.List<String> warnings = new java.util.ArrayList<>();
        service.parseCnFields(rawText, fields, warnings);

        // Les 6 cles canoniques alignees frontend-react/.../Step1Denomination.tsx
        assertThat(fields).containsKeys("ice", "denomination", "cnNumero", "cnDate", "beneficiaire", "activiteCn");
        assertThat(fields.get("ice")).isEqualTo("002345678000077");
        assertThat(fields.get("denomination")).contains("ATLAS TRADING SARL");
        assertThat(fields.get("cnNumero")).contains("2026");
        assertThat(fields.get("cnDate")).isEqualTo("2026-05-15");
        assertThat(fields.get("beneficiaire")).contains("Oussama BENATIK");
        assertThat(fields.get("activiteCn")).contains("Commerce");
    }

    @Test
    void parseCnFields_withoutCnKeywords_returnsEmptyFieldsGracefully() {
        // Document texte mais sans aucun champ CN -> parsing best-effort retourne map vide.
        // Le frontend tombera sur "requiresManualEntry=true" (decide par le seuil minOk dans extract).
        String rawText = "Lorem ipsum dolor sit amet.\nNo CN markers here.";

        TesseractOcrService service = new TesseractOcrService("/dev/null", "fra");
        java.util.Map<String, String> fields = new java.util.HashMap<>();
        java.util.List<String> warnings = new java.util.ArrayList<>();
        service.parseCnFields(rawText, fields, warnings);

        assertThat(fields).isEmpty();
        assertThat(warnings).anyMatch(w -> w.contains("ICE non detecte"));
    }

    @Test
    void extract_withPdfBytes_andEmptyDatapath_stillReturnsManualFallback() {
        // PDF detecte (magic bytes) mais datapath vide -> court-circuit precoce fallback,
        // pas d'exception. Garantit qu'on ne casse pas le flux upload si OCR mal configure.
        byte[] pdfMagic = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34};
        TesseractOcrService service = new TesseractOcrService("", "fra");

        OcrExtractionResult result = service.extract(pdfMagic, "cn.pdf", OcrDocumentType.CERTIFICAT_NEGATIF);

        assertThat(result.requiresManualEntry()).isTrue();
        assertThat(result.provider()).isEqualTo(OcrExtractionResult.PROVIDER_MANUAL_FALLBACK);
    }
}
