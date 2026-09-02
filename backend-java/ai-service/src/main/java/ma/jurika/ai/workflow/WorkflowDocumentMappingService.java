package ma.jurika.ai.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dispatcher central qui route une demande de génération
 * {@code (workflowCode, templateCode, payload)} vers le {@link WorkflowDocumentMapper}
 * approprié et retourne le {@code Map<String,Object>} de variables prêt pour le
 * {@code DocxTemplateEngine}.
 *
 * <p>Construction : Spring auto-collecte tous les beans implémentant
 * {@link WorkflowDocumentMapper} et les indexe :
 * <ul>
 *   <li>{@code byWorkflow} : workflowCode → mapper (1 mapper par workflow code,
 *       sinon {@link IllegalStateException} au startup).</li>
 *   <li>{@code byWorkflowAndTemplate} : (workflowCode, templateCode) → mapper,
 *       pour dispatch O(1) lors des appels.</li>
 * </ul>
 */
@Component
public class WorkflowDocumentMappingService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowDocumentMappingService.class);

    /** Index workflow code → mapper. */
    private final Map<String, WorkflowDocumentMapper> byWorkflow;
    /** Index (workflow code, template code) → mapper. */
    private final Map<String, Map<String, WorkflowDocumentMapper>> byWorkflowAndTemplate;

    public WorkflowDocumentMappingService(List<WorkflowDocumentMapper> mappers) {
        Map<String, WorkflowDocumentMapper> wf = new HashMap<>();
        Map<String, Map<String, WorkflowDocumentMapper>> wfTpl = new HashMap<>();

        for (WorkflowDocumentMapper m : mappers) {
            String code = m.workflowCode();
            if (code == null || code.isBlank()) {
                throw new IllegalStateException(
                        "WorkflowDocumentMapper " + m.getClass().getName()
                                + " : workflowCode() retourne null ou blank");
            }
            WorkflowDocumentMapper previous = wf.put(code, m);
            if (previous != null) {
                throw new IllegalStateException(
                        "Plusieurs WorkflowDocumentMapper enregistrés pour le workflow "
                                + code + " : " + previous.getClass().getName()
                                + " et " + m.getClass().getName());
            }
            Set<String> templates = m.supportedTemplates();
            if (templates == null || templates.isEmpty()) {
                throw new IllegalStateException(
                        "WorkflowDocumentMapper " + m.getClass().getName()
                                + " (workflow " + code + ") : supportedTemplates() vide");
            }
            Map<String, WorkflowDocumentMapper> tplMap = new HashMap<>();
            for (String tpl : templates) {
                if (tpl == null || tpl.isBlank()) continue;
                tplMap.put(tpl, m);
            }
            wfTpl.put(code, tplMap);
        }

        this.byWorkflow = Map.copyOf(wf);
        Map<String, Map<String, WorkflowDocumentMapper>> snapshot = new HashMap<>();
        for (Map.Entry<String, Map<String, WorkflowDocumentMapper>> e : wfTpl.entrySet()) {
            snapshot.put(e.getKey(), Map.copyOf(e.getValue()));
        }
        this.byWorkflowAndTemplate = Map.copyOf(snapshot);

        log.info("WorkflowDocumentMappingService initialisé : {} workflow(s) enregistré(s) — {}",
                byWorkflow.size(), byWorkflow.keySet());
    }

    /**
     * Dispatch principal : route vers le mapper qui couvre
     * {@code (workflowCode, templateCode)} et retourne ses variables.
     *
     * @throws IllegalArgumentException si le workflow est inconnu ou si le template
     *         n'est pas supporté par le mapper du workflow.
     */
    public Map<String, Object> map(String workflowCode, String templateCode,
                                    Map<String, Object> payload) {
        if (workflowCode == null || workflowCode.isBlank()) {
            throw new IllegalArgumentException("workflowCode requis");
        }
        if (templateCode == null || templateCode.isBlank()) {
            throw new IllegalArgumentException("templateCode requis");
        }
        WorkflowDocumentMapper mapper = byWorkflow.get(workflowCode);
        if (mapper == null) {
            throw new IllegalArgumentException(
                    "Workflow inconnu : " + workflowCode
                            + " (workflows connus : " + byWorkflow.keySet() + ")");
        }
        Map<String, WorkflowDocumentMapper> templates = byWorkflowAndTemplate.get(workflowCode);
        if (templates == null || !templates.containsKey(templateCode)) {
            throw new IllegalArgumentException(
                    "Template " + templateCode + " non supporté par le workflow "
                            + workflowCode + " (templates supportés : "
                            + (templates == null ? Set.of() : templates.keySet()) + ")");
        }
        Map<String, Object> safePayload = payload == null ? Map.of() : payload;
        Map<String, Object> variables = mapper.map(templateCode, safePayload);
        return variables == null ? Map.of() : variables;
    }

    /** Indique si un workflow est connu (utile pour les checks contrôleur). */
    public boolean supportsWorkflow(String workflowCode) {
        return workflowCode != null && byWorkflow.containsKey(workflowCode);
    }

    /**
     * Indique si un mapper est enregistré pour ce workflow. Alias de
     * {@link #supportsWorkflow(String)} introduit pour expressivité côté contrôleur
     * de découverte (liste de templates).
     */
    public boolean hasMapper(String workflowCode) {
        return supportsWorkflow(workflowCode);
    }

    /**
     * Indique si la paire (workflow, template) est supportée.
     */
    public boolean supports(String workflowCode, String templateCode) {
        if (workflowCode == null || templateCode == null) return false;
        Map<String, WorkflowDocumentMapper> templates = byWorkflowAndTemplate.get(workflowCode);
        return templates != null && templates.containsKey(templateCode);
    }

    /** Snapshot immuable des workflows connus. */
    public Set<String> knownWorkflows() {
        return new HashSet<>(byWorkflow.keySet());
    }
}
