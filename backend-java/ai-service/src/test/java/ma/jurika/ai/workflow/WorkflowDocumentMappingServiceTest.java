package ma.jurika.ai.workflow;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires du dispatcher {@link WorkflowDocumentMappingService}.
 *
 * <p>On enregistre 2 mappers stubs (PV_AGO et CREATION_SARL) et on valide :
 * <ul>
 *   <li>Le dispatch correct sur la paire (workflow, template) → mapper attendu.</li>
 *   <li>L'erreur {@link IllegalArgumentException} si workflow inconnu.</li>
 *   <li>L'erreur {@link IllegalArgumentException} si template non supporté.</li>
 *   <li>Le rejet startup si 2 mappers déclarent le même workflowCode.</li>
 * </ul>
 */
class WorkflowDocumentMappingServiceTest {

    private static class StubPvAgoMapper implements WorkflowDocumentMapper {
        boolean called = false;
        String lastTemplate;
        Map<String, Object> lastPayload;

        @Override
        public String workflowCode() {
            return "PV_AGO";
        }

        @Override
        public Set<String> supportedTemplates() {
            return Set.of(
                    "PV_APPROBATION_COMPTES_SARL",
                    "PV_APPROBATION_COMPTES_SARL_AU");
        }

        @Override
        public Map<String, Object> map(String templateCode, Map<String, Object> payload) {
            called = true;
            lastTemplate = templateCode;
            lastPayload = payload;
            return Map.of(
                    "WORKFLOW", "PV_AGO",
                    "TEMPLATE", templateCode,
                    "DENOMINATION", payload.getOrDefault("denomination", "?"));
        }
    }

    private static class StubCreationSarlMapper implements WorkflowDocumentMapper {
        @Override
        public String workflowCode() {
            return "CREATION_SARL";
        }

        @Override
        public Set<String> supportedTemplates() {
            return Set.of("STATUTS_CONSTITUTIFS_SARL");
        }

        @Override
        public Map<String, Object> map(String templateCode, Map<String, Object> payload) {
            return Map.of("WORKFLOW", "CREATION_SARL", "TEMPLATE", templateCode);
        }
    }

    @Test
    void dispatch_routes_to_correct_mapper() {
        StubPvAgoMapper pvAgo = new StubPvAgoMapper();
        StubCreationSarlMapper creation = new StubCreationSarlMapper();
        WorkflowDocumentMappingService service = new WorkflowDocumentMappingService(
                List.of(pvAgo, creation));

        Map<String, Object> payload = Map.of("denomination", "ACME SARL");
        Map<String, Object> result = service.map(
                "PV_AGO", "PV_APPROBATION_COMPTES_SARL_AU", payload);

        assertTrue(pvAgo.called, "Le mapper PV_AGO doit avoir été appelé");
        assertEquals("PV_APPROBATION_COMPTES_SARL_AU", pvAgo.lastTemplate);
        assertEquals("ACME SARL", pvAgo.lastPayload.get("denomination"));
        assertEquals("PV_AGO", result.get("WORKFLOW"));
        assertEquals("PV_APPROBATION_COMPTES_SARL_AU", result.get("TEMPLATE"));
        assertEquals("ACME SARL", result.get("DENOMINATION"));
    }

    @Test
    void dispatch_other_workflow_routes_to_other_mapper() {
        StubPvAgoMapper pvAgo = new StubPvAgoMapper();
        StubCreationSarlMapper creation = new StubCreationSarlMapper();
        WorkflowDocumentMappingService service = new WorkflowDocumentMappingService(
                List.of(pvAgo, creation));

        Map<String, Object> result = service.map(
                "CREATION_SARL", "STATUTS_CONSTITUTIFS_SARL", Map.of());

        assertEquals("CREATION_SARL", result.get("WORKFLOW"));
        assertEquals("STATUTS_CONSTITUTIFS_SARL", result.get("TEMPLATE"));
        // Le mapper PV_AGO ne doit pas avoir été touché.
        assertFalse(pvAgo.called, "Le mapper PV_AGO ne doit PAS avoir été appelé");
    }

    @Test
    void unknown_workflow_throws_illegal_argument() {
        WorkflowDocumentMappingService service = new WorkflowDocumentMappingService(
                List.of(new StubPvAgoMapper()));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.map("WORKFLOW_QUI_NEXISTE_PAS", "ANY", Map.of()));
        assertTrue(ex.getMessage().contains("Workflow inconnu"));
    }

    @Test
    void unknown_template_for_known_workflow_throws_illegal_argument() {
        WorkflowDocumentMappingService service = new WorkflowDocumentMappingService(
                List.of(new StubPvAgoMapper()));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.map("PV_AGO", "TEMPLATE_NON_SUPPORTE", Map.of()));
        assertTrue(ex.getMessage().contains("non supporté"),
                "message attendu : non supporté ; reçu : " + ex.getMessage());
    }

    @Test
    void null_payload_is_accepted_and_passed_as_empty_map() {
        StubPvAgoMapper pvAgo = new StubPvAgoMapper();
        WorkflowDocumentMappingService service = new WorkflowDocumentMappingService(
                List.of(pvAgo));
        Map<String, Object> result = service.map(
                "PV_AGO", "PV_APPROBATION_COMPTES_SARL", null);
        assertTrue(pvAgo.called);
        assertEquals(Map.of(), pvAgo.lastPayload);
        assertEquals("PV_AGO", result.get("WORKFLOW"));
    }

    @Test
    void duplicate_workflow_mapper_fails_at_startup() {
        StubPvAgoMapper m1 = new StubPvAgoMapper();
        StubPvAgoMapper m2 = new StubPvAgoMapper();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new WorkflowDocumentMappingService(List.of(m1, m2)));
        assertTrue(ex.getMessage().contains("Plusieurs WorkflowDocumentMapper"));
    }

    @Test
    void supports_methods_reflect_registry() {
        WorkflowDocumentMappingService service = new WorkflowDocumentMappingService(
                List.of(new StubPvAgoMapper()));
        assertTrue(service.supportsWorkflow("PV_AGO"));
        assertFalse(service.supportsWorkflow("UNKNOWN"));
        assertTrue(service.supports("PV_AGO", "PV_APPROBATION_COMPTES_SARL"));
        assertFalse(service.supports("PV_AGO", "OTHER_TEMPLATE"));
        assertFalse(service.supports("UNKNOWN", "ANY"));
        assertTrue(service.knownWorkflows().contains("PV_AGO"));
    }
}
