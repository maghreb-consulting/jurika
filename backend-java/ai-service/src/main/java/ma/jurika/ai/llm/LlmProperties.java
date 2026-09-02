package ma.jurika.ai.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriétés de configuration LLM (prefix {@code jurika.llm}).
 * <p>
 * Toutes les valeurs sont lisibles via env vars (LLM_ENABLED, LLM_PROVIDER, LLM_BASE_URL,
 * LLM_API_KEY, LLM_MODEL, LLM_TIMEOUT, LLM_VISION_MODEL, LLM_VISION_TIMEOUT, LLM_PDF_RENDER_DPI,
 * LLM_MIN_PDF_TEXT_CHARS) — voir {@code application.yml}.
 *
 * <h2>Vision (2026-06-05 — branche feat/ocr-vision-precision)</h2>
 * Quand {@code visionModel} est positionné ET {@code enabled=true} ET que le {@code baseUrl}
 * pointe vers un endpoint multimodal (Ollama / vLLM / LM Studio compatibles), un second appel
 * {@code POST /chat/completions} avec un message multimodal (image_url base64 + texte instruction)
 * remplace le binôme Tesseract+LLM pour les documents scannés / images. Cela élimine la perte de
 * précision Tesseract sur les CIN/Certificats Négatifs.
 * <p>
 * Le pipeline reste 100% local (Ollama qwen3-vl) → conforme CNDP (loi 09-08).
 *
 * @param enabled            Active l'appel réseau LLM texte. Si false, NoopLlmProvider est utilisé.
 * @param provider           Identifiant logique du provider (groq / openai / ollama / custom).
 * @param baseUrl            URL de base de l'API OpenAI-compatible (incluant {@code /v1}).
 * @param apiKey             Clé API (peut être vide pour Ollama local).
 * @param model              Nom du modèle TEXTE à utiliser (ex llama-3.1-70b-versatile, qwen2.5:7b).
 * @param timeoutSeconds     Timeout HTTP total (connect + read) en secondes pour LLM texte.
 * @param temperature        Température LLM (0.0 = déterministe, recommandé pour l'extraction).
 * @param visionModel        Nom du modèle VISION (multimodal). Vide = pas de route vision, fallback Tesseract+LLM.
 *                           Exemple : {@code qwen3-vl:2b} (Ollama). Reset à "" pour désactiver explicitement.
 * @param visionTimeoutSeconds Timeout HTTP en secondes pour appels vision (inférence image plus lente).
 * @param pdfRenderDpi       DPI pour rasteriser les pages PDF avant envoi au modèle vision.
 *                           Recommandation : 200-300 (équilibre qualité / latence).
 * @param minPdfTextLayerChars Seuil minimum de chars dans la couche texte d'un PDF pour le considérer
 *                              numérique (sinon → route scannée / vision). Défaut 60.
 * @param visionMaxImageWidth Largeur max (en px) de l'image envoyee au modele vision. Defaut 1600.
 *                            EX1 2026-06-09 — Le modele qwen3-vl ne tire AUCUN benefice d'une
 *                            resolution > 1600px pour une CIN ; downscaling reduit la latence
 *                            de 40-60% et la taille du base64 d'autant. 0 = pas de downscale.
 */
@ConfigurationProperties(prefix = "jurika.llm")
public record LlmProperties(
        boolean enabled,
        String provider,
        String baseUrl,
        String apiKey,
        String model,
        int timeoutSeconds,
        double temperature,
        String visionModel,
        int visionTimeoutSeconds,
        int pdfRenderDpi,
        int minPdfTextLayerChars,
        int visionMaxImageWidth
) {

    public LlmProperties {
        if (provider == null || provider.isBlank()) provider = "groq";
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api.groq.com/openai/v1";
        if (apiKey == null) apiKey = "";
        if (model == null || model.isBlank()) model = "llama-3.1-70b-versatile";
        if (timeoutSeconds <= 0) timeoutSeconds = 30;
        if (temperature < 0.0d) temperature = 0.0d;
        if (temperature > 2.0d) temperature = 2.0d;
        if (visionModel == null) visionModel = "";
        if (visionTimeoutSeconds <= 0) visionTimeoutSeconds = 120;
        // EX1 2026-06-09 — Default DPI abaisse de 220 a 150 (une CIN n'a pas besoin
        // de plus, le modele vision plafonne sa lecture autour de 1500px). Le user
        // peut surcharger via LLM_PDF_RENDER_DPI s'il veut.
        if (pdfRenderDpi <= 0) pdfRenderDpi = 150;
        if (pdfRenderDpi > 600) pdfRenderDpi = 600;
        if (minPdfTextLayerChars <= 0) minPdfTextLayerChars = 60;
        if (visionMaxImageWidth < 0) visionMaxImageWidth = 0;
        if (visionMaxImageWidth == 0) visionMaxImageWidth = 1600;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** True si le pipeline vision est activé (modèle vision défini et LLM enabled). */
    public boolean visionEnabled() {
        return enabled && visionModel != null && !visionModel.isBlank();
    }
}
