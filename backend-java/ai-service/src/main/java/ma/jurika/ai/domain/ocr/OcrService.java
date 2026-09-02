package ma.jurika.ai.domain.ocr;

import java.util.Map;

/**
 * Port domain pour l'extraction OCR de documents.
 * <p>
 * Une seule méthode primaire {@link #extract(byte[], String, OcrDocumentType)} retournant un
 * {@link OcrExtractionResult} structuré. Les méthodes legacy {@link #extractCertificatNegatif(byte[])}
 * et {@link #extractCin(byte[])} sont des default methods conservées pour préserver la compatibilité
 * de l'API REST existante ({@code POST /api/v1/ai/extract-cn|extract-cin}) qui retourne {@code Map<String, Object>}.
 * <p>
 * Implémentations Spring (activation par {@code jurika.ocr.provider}) :
 * <ul>
 *   <li>{@code tesseract} → TesseractOcrService (Tess4J + tessdata fra+ara)</li>
 *   <li>(absent / autre) → ManualEntryFallbackOcrService (saisie manuelle)</li>
 * </ul>
 */
public interface OcrService {

    /**
     * Extrait les champs structurés d'un document image ou PDF.
     *
     * @param image    Bytes du document. Formats supportés (Tesseract impl) :
     *                 PNG, JPEG, TIFF, BMP, et PDF (couche texte via PDFBox
     *                 ou rendu Tesseract de la 1ère page si scanné).
     * @param filename Nom du fichier original (pour logs et heuristiques d'extension).
     * @param type     Type de document attendu (CIN_RECTO, CIN_VERSO, PASSPORT,
     *                 CERTIFICAT_NEGATIF, OTHER). CERTIFICAT_NEGATIF déclenche
     *                 le parseur OMPIC dédié (cles lowercase ice / denomination / cnNumero / …).
     * @return Résultat OCR (jamais null). En cas d'échec, {@link OcrExtractionResult#manualFallback(String)}.
     */
    OcrExtractionResult extract(byte[] image, String filename, OcrDocumentType type);

    // ------------------------------------------------------------------------
    // API legacy — préserve le contrat REST AiController existant
    // ------------------------------------------------------------------------

    /**
     * Legacy : extraction de Certificat Négatif (PDF). Retourne une Map plate.
     * Par défaut, délègue à {@link #extract(byte[], String, OcrDocumentType)} avec type OTHER
     * et adapte le résultat. Les implémentations peuvent surcharger pour conserver une logique
     * spécifique (ex : champs ICE, dénomination).
     */
    default Map<String, Object> extractCertificatNegatif(byte[] pdfBytes) {
        OcrExtractionResult result = extract(pdfBytes, "certificat-negatif.pdf", OcrDocumentType.OTHER);
        return toLegacyMap(result, pdfBytes == null ? 0 : pdfBytes.length, "filenameSize");
    }

    /**
     * Legacy : extraction de CIN (image). Retourne une Map plate.
     * Par défaut, délègue à {@link #extract(byte[], String, OcrDocumentType)} avec type CIN_RECTO.
     */
    default Map<String, Object> extractCin(byte[] imageBytes) {
        OcrExtractionResult result = extract(imageBytes, "cin.jpg", OcrDocumentType.CIN_RECTO);
        return toLegacyMap(result, imageBytes == null ? 0 : imageBytes.length, "imageSize");
    }

    /**
     * Convertit un {@link OcrExtractionResult} en Map legacy compatible avec l'API REST historique.
     */
    private static Map<String, Object> toLegacyMap(OcrExtractionResult result, int sizeBytes, String sizeKey) {
        java.util.Map<String, Object> out = new java.util.HashMap<>();
        out.putAll(result.fields());
        out.put("source", "OCR_" + result.provider().toUpperCase().replace('-', '_'));
        out.put("confidence", result.confidence());
        out.put("requiresManualEntry", result.requiresManualEntry());
        out.put("warnings", result.warnings());
        out.put(sizeKey, sizeBytes);
        return out;
    }
}
