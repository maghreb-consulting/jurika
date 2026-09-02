package ma.jurika.ai.domain.ocr;

import java.util.List;
import java.util.Map;

/**
 * Résultat d'une extraction OCR.
 * <p>
 * Immuable. Tous les champs sont garantis non-null (listes/maps vides plutôt que null).
 *
 * @param fields              Champs structurés extraits (clés métier : NOM, PRENOM, CIN, DATE_NAISSANCE, NATIONALITE, ADRESSE, ICE, …).
 * @param confidence          Score de confiance global, 0.0 (rien extrait) .. 1.0 (tous les champs attendus trouvés).
 * @param requiresManualEntry True si l'extraction est insuffisante : le frontend doit présenter un formulaire de saisie manuelle.
 * @param warnings            Avertissements lisibles (ex : "Faible confiance sur DATE_NAISSANCE", "OCR non configuré").
 * @param rawText             Texte brut détecté par le moteur OCR (peut être null/vide si pas applicable).
 * @param provider            Identifiant du fournisseur d'OCR ayant produit le résultat : "tesseract" | "manual-fallback".
 */
public record OcrExtractionResult(
        Map<String, String> fields,
        double confidence,
        boolean requiresManualEntry,
        List<String> warnings,
        String rawText,
        String provider
) {

    public static final String PROVIDER_TESSERACT = "tesseract";
    public static final String PROVIDER_MANUAL_FALLBACK = "manual-fallback";

    public OcrExtractionResult {
        fields = fields == null ? Map.of() : Map.copyOf(fields);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        if (confidence < 0.0d) {
            confidence = 0.0d;
        }
        if (confidence > 1.0d) {
            confidence = 1.0d;
        }
    }

    /**
     * Construit un résultat de fallback "saisie manuelle requise".
     *
     * @param reason Raison utilisateur lisible (sera ajoutée aux warnings).
     * @return Résultat avec fields vide, confidence 0.0, requiresManualEntry=true, provider=manual-fallback.
     */
    public static OcrExtractionResult manualFallback(String reason) {
        return new OcrExtractionResult(
                Map.of(),
                0.0d,
                true,
                List.of(reason == null ? "Saisie manuelle requise" : reason),
                "",
                PROVIDER_MANUAL_FALLBACK
        );
    }
}
