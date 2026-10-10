package ma.jurika.ai.workflow;

import ma.jurika.ai.document.DocxTemplateEngine;
import ma.jurika.ai.document.manifest.TemplateManifest;
import ma.jurika.ai.document.manifest.TemplateManifestLoader;
import ma.jurika.ai.workflow.WorkflowDocumentController.TemplateInfo;
import ma.jurika.ai.workflow.identity.SocieteIdentityEnricher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires (sans Spring) du endpoint de découverte
 * {@link WorkflowDocumentController#listTemplates(String)}.
 *
 * <p>On instancie le contrôleur directement avec des mocks
 * {@link TemplateManifestLoader} + {@link WorkflowDocumentMappingService}, sans
 * {@code MockMvc} ni contexte Spring. Couvre :
 * <ul>
 *   <li>Workflow connu retournant N entrées (directes + alias hérité).</li>
 *   <li>Workflow inconnu (pas de mapper) → liste vide 200.</li>
 *   <li>Filtrage des templates appartenant à un autre workflow.</li>
 * </ul>
 */
class WorkflowDocumentControllerListTemplatesTest {

    private WorkflowDocumentMappingService mappingService;
    private TemplateManifestLoader manifestLoader;
    private DocxTemplateEngine docxTemplateEngine;
    private WorkflowDocumentController controller;

    @BeforeEach
    void setUp() {
        mappingService = mock(WorkflowDocumentMappingService.class);
        manifestLoader = mock(TemplateManifestLoader.class);
        docxTemplateEngine = mock(DocxTemplateEngine.class);
        controller = new WorkflowDocumentController(mappingService, docxTemplateEngine, manifestLoader,
                new SocieteIdentityEnricher((ws, id) -> java.util.Map.of()), (ws, t, e) -> java.util.Map.of(), (w, t, wf, tpl, d) -> { });
    }

    private TemplateManifest.TemplateEntry entry(String code, String workflow, String origin,
                                                  String documentKind, String file,
                                                  String aliasOf, boolean deprecated) {
        return new TemplateManifest.TemplateEntry(
                code,
                file,
                aliasOf,
                origin,
                documentKind,
                workflow,
                "uppercase_dollar",
                deprecated,
                null,
                List.of(),
                List.of());
    }

    @Test
    void workflow_connu_retourne_templates_directs_et_alias_herites() {
        // Mapper enregistré pour PV_AGO
        when(mappingService.hasMapper(eq("PV_AGO"))).thenReturn(true);

        // Manifest : 2 templates directs sur PV_AGO + 1 alias hérité + 1 template d'un autre workflow
        TemplateManifest.TemplateEntry sarl = entry(
                "PV_APPROBATION_COMPTES_SARL", "PV_AGO", "directeur",
                "PV — Approbation des comptes — SARL pluri", "PV_APPROBATION_COMPTES_SARL.docx",
                null, false);
        TemplateManifest.TemplateEntry sarlAu = entry(
                "PV_APPROBATION_COMPTES_SARL_AU", "PV_AGO", "directeur",
                "PV — Approbation des comptes — SARL AU", "PV_APPROBATION_COMPTES_SARL_AU.docx",
                null, false);
        TemplateManifest.TemplateEntry aliasLegacy = entry(
                "PV_AGO_LEGACY", null, null, null, null,
                "PV_APPROBATION_COMPTES_SARL", true);
        TemplateManifest.TemplateEntry autreWf = entry(
                "STATUTS_CONSTITUTIFS_SARL", "CREATION_SARL", "directeur",
                "Statuts SARL", "STATUTS_CONSTITUTIFS_SARL.docx", null, false);

        when(manifestLoader.allEntries()).thenReturn(List.of(sarl, sarlAu, aliasLegacy, autreWf));
        // resolve() suit l'alias → cible SARL
        when(manifestLoader.resolve("PV_AGO_LEGACY")).thenReturn(Optional.of(sarl));

        ResponseEntity<List<TemplateInfo>> response = controller.listTemplates("PV_AGO");

        assertEquals(200, response.getStatusCode().value());
        List<TemplateInfo> body = response.getBody();
        assertNotNull(body);
        assertEquals(3, body.size(), "2 directs + 1 alias attendus, body=" + body);

        // Direct SARL
        TemplateInfo sarlInfo = body.stream()
                .filter(t -> "PV_APPROBATION_COMPTES_SARL".equals(t.code()))
                .findFirst().orElseThrow();
        assertEquals("PV — Approbation des comptes — SARL pluri", sarlInfo.documentKind());
        assertEquals("PV_APPROBATION_COMPTES_SARL.docx", sarlInfo.file());
        assertEquals("directeur", sarlInfo.origin());
        assertFalse(sarlInfo.deprecated());
        assertEquals("uppercase_dollar", sarlInfo.placeholderStyle());

        // Direct SARL_AU
        assertTrue(body.stream().anyMatch(t -> "PV_APPROBATION_COMPTES_SARL_AU".equals(t.code())));

        // Alias hérité
        TemplateInfo aliasInfo = body.stream()
                .filter(t -> "PV_AGO_LEGACY".equals(t.code()))
                .findFirst().orElseThrow();
        assertEquals("hérité", aliasInfo.origin());
        assertTrue(aliasInfo.deprecated(), "alias doit être deprecated");
        // L'alias hérite des métadonnées de la cible
        assertEquals("PV_APPROBATION_COMPTES_SARL.docx", aliasInfo.file());
        assertEquals("PV — Approbation des comptes — SARL pluri", aliasInfo.documentKind());

        // Template d'un autre workflow exclu
        assertFalse(body.stream().anyMatch(t -> "STATUTS_CONSTITUTIFS_SARL".equals(t.code())));
    }

    @Test
    void workflow_sans_mapper_retourne_liste_vide_200() {
        when(mappingService.hasMapper(eq("WORKFLOW_INCONNU"))).thenReturn(false);

        ResponseEntity<List<TemplateInfo>> response = controller.listTemplates("WORKFLOW_INCONNU");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty(), "Body doit être vide quand pas de mapper");
    }

    @Test
    void workflow_connu_mais_aucun_template_dans_manifest_retourne_liste_vide() {
        when(mappingService.hasMapper(eq("PV_AGO"))).thenReturn(true);

        // Manifest contient uniquement des entrées d'un autre workflow.
        TemplateManifest.TemplateEntry autre = entry(
                "STATUTS_CONSTITUTIFS_SARL", "CREATION_SARL", "directeur",
                "Statuts SARL", "STATUTS_CONSTITUTIFS_SARL.docx", null, false);
        when(manifestLoader.allEntries()).thenReturn(List.of(autre));

        ResponseEntity<List<TemplateInfo>> response = controller.listTemplates("PV_AGO");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }
}
