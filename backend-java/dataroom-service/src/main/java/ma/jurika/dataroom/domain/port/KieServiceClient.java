package ma.jurika.dataroom.domain.port;

import java.util.List;
import java.util.Map;

/**
 * Port sortant vers le microservice kie-service (Donut KIE Python).
 * <p>
 * Le mapping {@code type métier → doc_type kie-service} est de la responsabilité
 * de la couche application, pas de l'adapter HTTP.
 */
public interface KieServiceClient {

    /**
     * Appelle {@code POST /api/v1/kie/extract} de kie-service.
     *
     * @param docType    valeur exacte attendue côté Python
     *                   ({@code cn}, {@code cin_anc_recto}, {@code cin_anc_verso},
     *                   {@code cin_nouv_recto}, {@code cin_nouv_verso}).
     * @param filename   nom du fichier d'origine (pour le multipart).
     * @param contentType type MIME (peut être {@code null}).
     * @param content    octets bruts (image ou PDF, 1re page utilisée par kie-service).
     * @return réponse normalisée. Ne renvoie jamais {@code null}.
     * @throws KieServiceUnavailableException si kie-service est injoignable ou répond &gt;= 500.
     */
    KieExtractResponse extract(String docType, String filename, String contentType, byte[] content);

    /**
     * Réponse normalisée. Miroir 1:1 du JSON Python.
     *
     * @param docType   doc_type effectivement retourné par kie-service (= ce qu'on a envoyé).
     * @param fields    champs extraits (clé -&gt; valeur). Valeurs stockées en String : Donut peut
     *                  renvoyer des Number, on les convertit côté adapter.
     * @param source    "kie" ou "merged".
     * @param warnings  anomalies non bloquantes (jamais {@code null} ; liste vide si rien).
     */
    record KieExtractResponse(String docType,
                              Map<String, String> fields,
                              String source,
                              List<String> warnings) {}

    /** Levée quand kie-service est KO (timeout, 5xx, connexion refusée). */
    class KieServiceUnavailableException extends RuntimeException {
        public KieServiceUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
        public KieServiceUnavailableException(String message) {
            super(message);
        }
    }
}
