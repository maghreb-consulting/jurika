package ma.jurika.ai.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration de la voie OCR <b>rapide CPU</b> (cf. {@link FastOcrPort}).
 * <p>
 * Préfixe {@code jurika.ocr.fast.*} — variables d'env :
 * <ul>
 *   <li>{@code OCR_SERVICE_URL} → {@link #serviceUrl()}</li>
 *   <li>{@code OCR_FAST_ENABLED} → {@link #enabled()}</li>
 *   <li>{@code OCR_FAST_TIMEOUT} → {@link #timeoutSeconds()} (secondes, défaut 30)</li>
 *   <li>{@code OCR_FAST_LANG} → {@link #lang()} (défaut {@code latin})</li>
 *   <li>{@code OCR_FAST_HEALTH_CACHE_SECONDS} → période min entre 2 ping {@code /health}</li>
 * </ul>
 *
 * <h3>Sécurité par défaut</h3>
 * Activée par défaut <b>uniquement</b> si {@link #serviceUrl()} est explicitement positionné
 * et {@code OCR_FAST_ENABLED!=false}. Sans config, {@link NoopFastOcrProvider} prend le relai
 * (la chaîne historique vision→Tesseract→manuel n'est jamais cassée).
 */
@ConfigurationProperties(prefix = "jurika.ocr.fast")
public record FastOcrProperties(
        boolean enabled,
        String serviceUrl,
        int timeoutSeconds,
        String lang,
        int healthCacheSeconds
) {

    public FastOcrProperties {
        if (serviceUrl == null) serviceUrl = "";
        if (lang == null || lang.isBlank()) lang = "latin";
        if (timeoutSeconds <= 0) timeoutSeconds = 30;
        if (timeoutSeconds > 600) timeoutSeconds = 600;
        if (healthCacheSeconds < 0) healthCacheSeconds = 0;
        if (healthCacheSeconds > 600) healthCacheSeconds = 600;
        if (healthCacheSeconds == 0) healthCacheSeconds = 30;
    }

    /** Vrai si la config permet l'appel réseau (URL renseignée + enabled). */
    public boolean isConfigured() {
        return enabled && serviceUrl != null && !serviceUrl.isBlank();
    }
}
