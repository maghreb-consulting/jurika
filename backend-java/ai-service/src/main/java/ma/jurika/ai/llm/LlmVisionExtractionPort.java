package ma.jurika.ai.llm;

import ma.jurika.ai.llm.schema.DocumentSchema;

/**
 * Port d'extraction structurée par <b>LLM multimodal vision</b>.
 * <p>
 * Contrairement à {@link LlmExtractionPort} qui prend un texte déjà extrait par OCR, ce port reçoit
 * directement les <i>bytes</i> d'une image (ou d'une page PDF rasterisée) et fait extraire les champs
 * du schéma par un modèle vision (qwen3-vl, llava, llama3.2-vision, ...) via une API OpenAI-compatible
 * (Ollama / vLLM / LM Studio) à messages multimodaux ({@code image_url} en data URL base64).
 * <p>
 * Avantage : précision largement supérieure à <b>Tesseract+LLM</b> sur les scans / CIN / Certificats
 * Négatifs (zones manuscrites, sceaux, qualité dégradée, multi-colonnes).
 * <p>
 * <b>RGPD/CNDP (loi 09-08)</b> : conçu pour Ollama local — aucun byte ne quitte la machine.
 *
 * <h3>Contrat de robustesse</h3>
 * L'implémentation ne doit JAMAIS lever d'exception non gérée. En cas d'erreur réseau / HTTP 5xx /
 * parsing JSON, retourner un {@link LlmExtractionResult#degraded(String, String, String)} avec un
 * warning explicite. Le caller s'attend à pouvoir continuer le pipeline (fallback OCR ou Noop).
 */
public interface LlmVisionExtractionPort {

    /**
     * Extrait les champs du schéma à partir d'une image binaire.
     *
     * @param imageBytes  Bytes de l'image (PNG / JPEG / WebP recommandés ; redimensionnés / encodés
     *                    base64 par l'implémentation).
     * @param mimeType    Type MIME ({@code image/png}, {@code image/jpeg}, ...). Si null/blank, défaut "image/png".
     * @param schema      Schéma cible.
     * @return Résultat structuré (jamais null).
     */
    LlmExtractionResult extractFromImage(byte[] imageBytes, String mimeType, DocumentSchema schema);

    /** True si le provider est opérationnel (model défini, enabled=true, etc.). */
    boolean isOperational();
}
