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
                mock(TemplateManifestLoader.class), new SocieteIdentityEnricher((ws, id) -> Map.of()), (ws, t, e) -> Map.of(), (w, t, wf, tpl, d) -> { });
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

    // ---- Lot L3 (P2) : la creation est generee depuis le magasin du ticket ----

    @Test
    @SuppressWarnings("unchecked")
    void creation_la_charge_du_serveur_remplace_celle_du_navigateur() {
        java.util.UUID ws = java.util.UUID.randomUUID();
        java.util.UUID employe = java.util.UUID.randomUUID();
        java.util.UUID ticket = java.util.UUID.randomUUID();
        Map<String, Object> serveur = Map.of("societe", Map.of("denomination", "NOVA (magasin)"), "ticketId", ticket.toString());
        WorkflowDocumentController c = new WorkflowDocumentController(mapping, engine,
                mock(TemplateManifestLoader.class), new SocieteIdentityEnricher((w, id) -> Map.of()),
                (w, t, e) -> {
                    assertThat(List.of(w, t, e)).containsExactly(ws, ticket, employe);
                    return serveur;
                }, (w, t, wf, tpl, d) -> { });
        when(engine.generate(eq("ANNONCE_LEGALE_CONSTITUTION"), anyMap())).thenReturn(resultat());
        var user = new ma.jurika.common.security.AuthenticatedUser(employe, ws, "e@x.ma", ma.jurika.common.security.Role.EMPLOYE);

        c.generate("CREATION_SARL", "ANNONCE_LEGALE_CONSTITUTION", user,
                Map.of("ticketId", ticket.toString(), "societe", Map.of("denomination", "VALEUR DU NAVIGATEUR")));

        org.mockito.ArgumentCaptor<Map<String, Object>> charge = org.mockito.ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(mapping).map(eq("CREATION_SARL"), eq("ANNONCE_LEGALE_CONSTITUTION"), charge.capture());
        assertThat((Map<String, Object>) charge.getValue().get("societe")).containsEntry("denomination", "NOVA (magasin)");
    }

    @Test
    void creation_sans_ticket_refusee() {
        WorkflowDocumentController c = new WorkflowDocumentController(mapping, engine,
                mock(TemplateManifestLoader.class), new SocieteIdentityEnricher((w, id) -> Map.of()), (w, t, e) -> Map.of(), (w, t, wf, tpl, d) -> { });
        var user = new ma.jurika.common.security.AuthenticatedUser(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                "e@x.ma", ma.jurika.common.security.Role.EMPLOYE);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> c.generate("CREATION_SARL", "STATUTS_SARL", user,
                        Map.of("societe", Map.of("denomination", "X"))))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("depuis le ticket");
    }

    // ---- Lot L3 : la plateforme reclame les donnees externes, et clot celles qui arrivent ----

    @Test
    void les_donnees_externes_manquantes_sont_reclamees_sur_le_ticket() {
        java.util.List<Object[]> appels = new java.util.ArrayList<>();
        WorkflowDocumentController c = new WorkflowDocumentController(mapping, engine,
                mock(TemplateManifestLoader.class), new SocieteIdentityEnricher((w, id) -> Map.of()), (w, t, e) -> Map.of(),
                (w, t, wf, tpl, d) -> appels.add(new Object[]{t, wf, tpl, d}));
        java.util.UUID ticket = java.util.UUID.randomUUID();
        var user = new ma.jurika.common.security.AuthenticatedUser(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                "e@x.ma", ma.jurika.common.security.Role.EMPLOYE);
        when(engine.generate(eq("ANNONCE_LEGALE_DISSOLUTION_SARL"), anyMap()))
                .thenReturn(resultat(new Manquante("ICE", "ICE : ...", false, true)))
                .thenReturn(resultat());

        c.generate("DISSOLUTION", "ANNONCE_LEGALE_DISSOLUTION_SARL", user, Map.of("ticketId", ticket.toString()));
        c.generate("DISSOLUTION", "ANNONCE_LEGALE_DISSOLUTION_SARL", user, Map.of("ticketId", ticket.toString()));

        assertThat(appels).hasSize(2);
        assertThat(appels.get(0)[0]).isEqualTo(ticket);
        assertThat(appels.get(0)[3].toString()).contains("variable=ICE").contains("Identifiant commun de l'entreprise");
        assertThat((List<?>) appels.get(1)[3]).as("rien ne manque plus : la reclamation est close").isEmpty();
    }
}
