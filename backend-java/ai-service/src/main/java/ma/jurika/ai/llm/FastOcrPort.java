package ma.jurika.ai.llm;

/**
 * Port abstrait pour le moteur OCR <b>rapide CPU</b> (image → texte).
 * <p>
 * Cette voie remplace le binôme vision-LLM pour les scans / images dans les cas où la
 * latence prime sur la richesse multimodale (CIN, Certificat Négatif, RC scanné). Le texte
 * renvoyé est ensuite passé à {@link LlmExtractionPort} pour la projection sur le schéma —
 * conformément au principe « 2 étapes séparées » : OCR image→texte, puis texte→champs.
 * <p>
 * Implémentations Spring :
 * <ul>
 *   <li>{@link HttpFastOcrProvider} — appelle le microservice Python {@code ocr-service}
 *       (PaddleOCR / docTR) en HTTP. Activé quand {@code jurika.ocr.fast.enabled=true}.</li>
 *   <li>{@link NoopFastOcrProvider} — toujours actif en repli ; signale simplement
 *       qu'aucune voie OCR rapide n'est disponible. {@link GenericExtractionService}
 *       continue alors avec la chaîne vision/Tesseract historique.</li>
 * </ul>
 * <p>
 * <b>Robustesse</b> : aucune implémentation ne propage d'exception réseau ; en cas d'échec
 * (ocr-service down, timeout, payload illisible) elle renvoie un {@link FastOcrResult}
 * dégradé que {@link GenericExtractionService} interprète comme un repli silencieux.
 */
public interface FastOcrPort {

    /**
     * Indique si la voie OCR rapide est opérationnelle (config activée + best-effort up).
     * <p>
     * Doit être non bloquant — basé sur la config + un cache léger côté implémentation.
     * Le caller appelle {@link #extract} ensuite ; si {@code isOperational} renvoie false,
     * la voie est sautée d'office.
     */
    boolean isOperational();

    /**
     * Lance l'OCR sur l'image fournie.
     *
     * @param imageBytes Bytes image (PNG / JPEG). PDFs déjà rasterisés en amont.
     * @param filename   Nom de fichier original (transmis au microservice pour MIME-hint).
     * @param mimeType   MIME hint (ex {@code image/png}).
     * @return Résultat OCR (jamais null). En cas d'échec → {@link FastOcrResult#degraded}.
     */
    FastOcrResult extract(byte[] imageBytes, String filename, String mimeType);
}
