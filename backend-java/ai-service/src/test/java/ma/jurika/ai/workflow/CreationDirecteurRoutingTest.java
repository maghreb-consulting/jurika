package ma.jurika.ai.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.ai.document.DocxTemplateEngine;
import ma.jurika.ai.document.manifest.TemplateManifestLoader;
import ma.jurika.ai.workflow.WorkflowDocumentController.TemplateInfo;
import ma.jurika.ai.workflow.identity.SocieteIdentityEnricher;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 2-B (B.2) — Le workflow CRÉATION est routé vers la VOIE DIRECTEUR : la
 * découverte {@code listTemplates("CREATION_SARL")} expose les 4 codes
 * {@code *_DIRECTEUR} et n'expose PLUS les codes legacy remplacés (retagués
 * {@code CREATION_SARL_LEGACY} dans le manifest). Charge le manifest réel.
 */
class CreationDirecteurRoutingTest {

    private static final Set<String> DIRECTEUR = Set.of(
            "STATUTS_SARL_DIRECTEUR", "STATUTS_SARL_AU_DIRECTEUR",
            "ACTE_NOMINATION_GERANT_DIRECTEUR", "ANNONCE_LEGALE_DIRECTEUR");

    private static final Set<String> LEGACY_REMPLACES = Set.of(
            "STATUTS_CONSTITUTIFS_SARL", "STATUTS_CONSTITUTIFS_SARL_AU",
            "ACTE_NOMINATION_GERANT", "ACTE_NOMINATION_GERANT_SARL", "ACTE_NOMINATION_GERANT_SARL_AU",
            "AVIS_CONSTITUTION_SARL", "ANNONCE_JAL_SARL", "ANNONCE_JAL_SARL_AU");

    @Test
    void creation_sarl_route_vers_les_codes_directeur() {
        TemplateManifestLoader loader = new TemplateManifestLoader(new ObjectMapper());
        loader.load();
        WorkflowDocumentMappingService mapping = mock(WorkflowDocumentMappingService.class);
        when(mapping.hasMapper("CREATION_SARL")).thenReturn(true);
        WorkflowDocumentController controller =
                new WorkflowDocumentController(mapping, mock(DocxTemplateEngine.class), loader,
                        new SocieteIdentityEnricher((ws, id) -> java.util.Map.of()));

        ResponseEntity<List<TemplateInfo>> resp = controller.listTemplates("CREATION_SARL");
        Set<String> codes = resp.getBody().stream().map(TemplateInfo::code).collect(Collectors.toSet());

        assertTrue(codes.containsAll(DIRECTEUR),
                "CREATION_SARL doit exposer les 4 codes directeur, obtenu = " + codes);
        for (String legacy : LEGACY_REMPLACES) {
            assertTrue(!codes.contains(legacy),
                    "CREATION_SARL ne doit plus exposer le code legacy remplacé : " + legacy);
        }
    }
}
