package ma.jurika.ai.workflow;

import java.util.Map;
import java.util.Set;

/**
 * Contrat L4 : un {@code WorkflowDocumentMapper} encapsule la connaissance d'un
 * workflow métier (ex: {@code PV_AGO}, {@code CREATION_SARL}) et sait transformer
 * le payload reçu de la couche application (frontend / orchestrateur) en un
 * {@code Map<String,Object>} aligné avec les variables déclarées par le manifest L3
 * pour un template donné.
 *
 * <p>Un même workflow peut piloter plusieurs templates (variantes par forme
 * juridique, par exemple {@code PV_APPROBATION_COMPTES_SARL} et
 * {@code PV_APPROBATION_COMPTES_SARL_AU}). Chaque mapper déclare l'ensemble
 * des codes templates qu'il sait alimenter via {@link #supportedTemplates()}.
 *
 * <p>Les implémentations sont des beans Spring auto-collectés par
 * {@link WorkflowDocumentMappingService}.
 *
 * <p><b>Contrat</b> :
 * <ul>
 *   <li>{@link #workflowCode()} : identifiant unique du workflow (MAJUSCULES_STRICT).</li>
 *   <li>{@link #supportedTemplates()} : codes templates manifest L3 que ce mapper
 *       sait produire. Doit être stable et idempotent.</li>
 *   <li>{@link #map(String, Map)} : transforme le payload en variables manifest.
 *       Le payload est considéré comme déjà validé en amont (DTO / contrôleur).
 *       Aucune lecture DB côté ai-service : tout est dans le payload.</li>
 * </ul>
 */
public interface WorkflowDocumentMapper {

    /**
     * @return le code du workflow piloté par ce mapper (ex: {@code "PV_AGO"}).
     *         Doit être MAJUSCULES_STRICT, non-blank.
     */
    String workflowCode();

    /**
     * @return l'ensemble des codes templates manifest L3 que ce mapper sait
     *         alimenter. Ex: {@code Set.of("PV_APPROBATION_COMPTES_SARL",
     *         "PV_APPROBATION_COMPTES_SARL_AU")}.
     */
    Set<String> supportedTemplates();

    /**
     * Convertit le payload du workflow en variables prêtes à être consommées par le
     * {@code DocxTemplateEngine}.
     *
     * @param templateCode le code template demandé (l'un de {@link #supportedTemplates()}).
     * @param payload      les données métier passées par l'appelant.
     * @return un {@code Map<String,Object>} aligné avec les variables du manifest L3.
     */
    Map<String, Object> map(String templateCode, Map<String, Object> payload);
}
