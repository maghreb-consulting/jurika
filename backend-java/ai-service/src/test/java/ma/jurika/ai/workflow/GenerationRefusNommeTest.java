package ma.jurika.ai.workflow;

import ma.jurika.ai.api.GenerationExceptionHandler;
import ma.jurika.ai.document.DocxTemplateEngine;
import ma.jurika.ai.document.DocxTemplateEngine.DocumentResult;
import ma.jurika.ai.document.GabaritIntrouvableException;
import ma.jurika.ai.document.MissingVariableMarker.Manquante;
import ma.jurika.ai.document.corpus.CorpusException;
import ma.jurika.ai.document.corpus.DictionnaireUnique;
import ma.jurika.ai.document.manifest.TemplateManifestLoader;
import ma.jurika.ai.workflow.identity.SocieteIdentityEnricher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lot L3 : la regle des variables a la generation, pour TOUS les parcours (et plus
 * seulement la creation) ; refus structure et nomme ; modele introuvable ou refuse :
 * message clair au lieu d'une erreur 500 (backlog L2, D8).
 */
class GenerationRefusNommeTest {

    private final WorkflowDocumentMappingService mapping = mock(WorkflowDocumentMappingService.class);
    private final DocxTemplateEngine engine = mock(DocxTemplateEngine.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        WorkflowDocumentController c = new WorkflowDocumentController(mapping, engine,
                mock(TemplateManifestLoader.class), new SocieteIdentityEnricher((ws, id) -> Map.of()));
        mvc = MockMvcBuilders.standaloneSetup(c).setControllerAdvice(new GenerationExceptionHandler()).build();
        when(mapping.map(any(), any(), anyMap())).thenReturn(Map.of());
        when(engine.dictionnaire()).thenReturn(new DictionnaireUnique(Set.of("$SIEGE_VILLE", "$ICE"), Map.of(),
                Map.of("$SIEGE_VILLE", "Ville du siège social", "$ICE", "Identifiant commun de l'entreprise")));
    }

    private static DocumentResult resultat(Manquante... m) {
        return new DocumentResult(new byte[]{1}, "application/octet-stream", "X.docx", true,
                java.util.Arrays.stream(m).map(Manquante::nom).toList(), List.of(m));
    }

    @Test
    void interne_manquante_hors_creation_refusee_et_nommee() throws Exception {
        when(engine.generate(eq("PV_DISSOLUTION_LIQUIDATION_SARL"), anyMap())).thenReturn(resultat(
                new Manquante("SIEGE_VILLE", "Siege social a ,", true, false)));
        mvc.perform(post("/api/v1/ai/workflows/DISSOLUTION/documents/PV_DISSOLUTION_LIQUIDATION_SARL")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("GENERATION_REFUSEE"))
                .andExpect(jsonPath("$.donneesManquantes[0].variable").value("SIEGE_VILLE"))
                .andExpect(jsonPath("$.donneesManquantes[0].libelle").value("Ville du siège social"))
                .andExpect(jsonPath("$.donneesManquantes[0].endroit").value("Siege social a ,"));
    }

    @Test
    void externe_seule_le_document_sort_et_la_donnee_est_annoncee() throws Exception {
        when(engine.generate(eq("ANNONCE_LEGALE_DISSOLUTION_SARL"), anyMap())).thenReturn(resultat(
                new Manquante("ICE", "ICE : ...", false, true)));
        String entete = mvc.perform(post("/api/v1/ai/workflows/DISSOLUTION/documents/ANNONCE_LEGALE_DISSOLUTION_SARL")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("X-Donnees-A-Obtenir");
        assertThat(URLDecoder.decode(entete, StandardCharsets.UTF_8))
                .contains("\"variable\":\"ICE\"").contains("Identifiant commun de l'entreprise");
    }

    @Test
    void modele_introuvable_404_lisible() throws Exception {
        when(engine.generate(eq("INCONNU"), anyMap())).thenThrow(new GabaritIntrouvableException("INCONNU"));
        mvc.perform(post("/api/v1/ai/workflows/DISSOLUTION/documents/INCONNU")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MODELE_INTROUVABLE"));
    }

    @Test
    void modele_du_corpus_refuse_409_au_lieu_de_500() throws Exception {
        when(engine.generate(eq("PV_X"), anyMap())).thenThrow(new CorpusException("empreinte modifiee : PV_X"));
        mvc.perform(post("/api/v1/ai/workflows/DISSOLUTION/documents/PV_X")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MODELE_REFUSE"));
    }
}
