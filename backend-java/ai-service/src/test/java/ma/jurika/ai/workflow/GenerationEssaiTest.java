package ma.jurika.ai.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import ma.jurika.ai.api.GenerationExceptionHandler;
import ma.jurika.ai.document.DocxTemplateEngine;
import ma.jurika.ai.document.DocxTemplateEngine.DocumentResult;
import ma.jurika.ai.document.corpus.DictionnaireUnique;
import ma.jurika.ai.document.manifest.TemplateManifestLoader;
import ma.jurika.ai.workflow.depot.DepotDataroomClient;
import ma.jurika.ai.workflow.identity.SocieteIdentityEnricher;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import ma.jurika.common.trial.EssaiRegles;
import ma.jurika.common.trial.TrialAccessChecker;
import ma.jurika.common.trial.TrialSoftLockFilter;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Decision du 2026-10-14 (remplace la decision 23 du lot L7) : pendant l'essai, aucun fichier
 * d'acte ne parvient au navigateur. La generation du .docx est refusee (402, meme "inline") ;
 * l'apercu rend des pages PNG filigranees en JSON ; le depot dans la Data Room se fait par le
 * serveur. Cabinet abonne : acte inchange, en piece jointe, apercu sans mention.
 */
class GenerationEssaiTest {

    private static final UUID WS_ESSAI = UUID.randomUUID();
    private static final UUID WS_ABONNE = UUID.randomUUID();
    private static final String ROUTE = "/api/v1/ai/workflows/DISSOLUTION/documents/PV_DISSOLUTION_LIQUIDATION_SARL";

    private final WorkflowDocumentMappingService mapping = mock(WorkflowDocumentMappingService.class);
    private final DocxTemplateEngine engine = mock(DocxTemplateEngine.class);
    private final DepotDataroomClient depot = mock(DepotDataroomClient.class);
    private final ObjectMapper json = new ObjectMapper();
    private final TrialAccessChecker checker = ws -> Optional.of(new TrialAccessChecker.TrialState(ws,
            WS_ESSAI.equals(ws) ? "TRIAL_ACTIVE" : "CONVERTED", 3, Instant.now(), null));
    private WorkflowDocumentController controleur;
    private MockMvc mvc;
    private byte[] acte;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        controleur = new WorkflowDocumentController(mapping, engine,
                mock(TemplateManifestLoader.class), new SocieteIdentityEnricher((ws, id) -> Map.of()),
                (ws, t, e) -> Map.of(), (w, t, wf, tpl, d) -> { }, (w, t) -> List.of());
        ObjectProvider<TrialAccessChecker> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(checker);
        controleur.setEssais(p);
        controleur.setDepotDataroom(depot);
        mvc = construire();
        when(mapping.map(any(), any(), anyMap())).thenReturn(Map.of());
        when(mapping.gabarit(any(), any(), any())).thenAnswer(inv -> inv.getArgument(1));
        when(engine.dictionnaire()).thenReturn(new DictionnaireUnique(Set.of(), Map.of(), Map.of()));
        try (XWPFDocument doc = new XWPFDocument()) {
            doc.createParagraph().createRun().setText("Proc\u00e8s-verbal de l\u2019assembl\u00e9e g\u00e9n\u00e9rale");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            acte = out.toByteArray();
        }
        when(engine.generate(any(), anyMap())).thenAnswer(inv -> new DocumentResult(acte,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "PV.docx", true,
                List.of(), List.of()));
    }

    private MockMvc construire(Filter... filtres) {
        return MockMvcBuilders.standaloneSetup(controleur)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GenerationExceptionHandler())
                .addFilters(filtres).build();
    }

    @AfterEach
    void vider() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private MvcResult appeler(UUID ws, String suffixe, String corps) throws Exception {
        AuthenticatedUser u = new AuthenticatedUser(UUID.randomUUID(), ws, "employe@cabinet.ma", Role.EMPLOYE);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u, null, List.of()));
        TenantContext.set(ws);
        return mvc.perform(post(ROUTE + suffixe).header("Authorization", "Bearer jeton-de-l-employe")
                .contentType(MediaType.APPLICATION_JSON).content(corps)).andReturn();
    }

    private static boolean estDocx(byte[] octets) {
        return octets.length > 2 && octets[0] == 'P' && octets[1] == 'K';
    }

    @Test
    void essai_la_generation_du_docx_est_refusee_meme_en_apercu_inline() throws Exception {
        MvcResult r = appeler(WS_ESSAI, "", "{}");
        assertThat(r.getResponse().getStatus()).isEqualTo(402);
        assertThat(r.getResponse().getContentType()).startsWith("application/json");
        assertThat(r.getResponse().getHeader("Content-Disposition")).isNull();
        assertThat(estDocx(r.getResponse().getContentAsByteArray())).isFalse();
        assertThat(r.getResponse().getContentAsString()).contains(TrialSoftLockFilter.CODE_TELECHARGEMENT);
    }

    @Test
    void essai_filtre_commun_branche_refuse_la_route_avant_le_controleur_et_laisse_l_apercu() throws Exception {
        mvc = construire(new TrialSoftLockFilter(checker, json));
        MvcResult r = appeler(WS_ESSAI, "", "{}");
        assertThat(r.getResponse().getStatus()).isEqualTo(402);
        verify(engine, org.mockito.Mockito.never()).generate(any(), anyMap());
        MvcResult a = appeler(WS_ESSAI, "/apercu", "{}");
        assertThat(a.getResponse().getStatus()).isEqualTo(200);
        assertThat(a.getResponse().getContentType()).startsWith("application/json");
    }

    @Test
    void essai_apercu_en_pages_png_avec_la_mention_incrustee_et_deterministe() throws Exception {
        MvcResult r = appeler(WS_ESSAI, "/apercu", "{}");
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(r.getResponse().getContentType()).startsWith("application/json");
        JsonNode corps = json.readTree(r.getResponse().getContentAsByteArray());
        assertThat(corps.at("/apercu/filigrane").asBoolean()).isTrue();
        assertThat(corps.at("/apercu/mention").asText()).isEqualTo(EssaiRegles.MENTION_FILIGRANE);
        assertThat(corps.at("/nomFichier").asText()).isEqualTo("PV.docx");
        String image = corps.at("/apercu/pages/0/image").asText();
        assertThat(image).startsWith("data:image/png;base64,");
        assertThat(rouges(image)).isGreaterThan(5_000);
        // Aucun octet du .docx dans la reponse.
        assertThat(r.getResponse().getContentAsString()).doesNotContain(Base64.getEncoder().encodeToString(acte));
        // Memes donnees : meme apercu.
        assertThat(appeler(WS_ESSAI, "/apercu", "{}").getResponse().getContentAsString())
                .isEqualTo(r.getResponse().getContentAsString());
    }

    @Test
    void essai_depot_par_le_serveur_avec_la_mention_et_sans_fichier_rendu() throws Exception {
        UUID dossier = UUID.randomUUID();
        UUID ticket = UUID.randomUUID();
        when(depot.deposer(any(), any(), any())).thenReturn(Map.of("id", "doc-1", "status", "BROUILLON"));
        String demande = """
                {"payload":{"x":1},"depot":{"dossierId":"%s","ticketId":"%s","documentType":"PV_AGE",
                "title":"PV de dissolution","filename":"PV de dissolution.docx","mode":"BROUILLON"}}
                """.formatted(dossier, ticket);
        MvcResult r = appeler(WS_ESSAI, "/depot", demande);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode corps = json.readTree(r.getResponse().getContentAsByteArray());
        assertThat(corps.at("/document/id").asText()).isEqualTo("doc-1");
        assertThat(corps.at("/filigrane").asBoolean()).isTrue();
        ArgumentCaptor<byte[]> octets = ArgumentCaptor.forClass(byte[].class);
        ArgumentCaptor<DepotDataroomClient.Depot> parametres = ArgumentCaptor.forClass(DepotDataroomClient.Depot.class);
        verify(depot).deposer(eq("Bearer jeton-de-l-employe"), parametres.capture(), octets.capture());
        assertThat(parametres.getValue().mode()).isEqualTo(DepotDataroomClient.Mode.BROUILLON);
        assertThat(parametres.getValue().filename()).isEqualTo("PV de dissolution.docx");
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(octets.getValue()))) {
            assertThat(doc.getHeaderList().get(0).getText()).contains(EssaiRegles.MENTION_FILIGRANE);
        }
    }

    @Test
    void depot_incomplet_refuse_et_nomme() throws Exception {
        MvcResult r = appeler(WS_ESSAI, "/depot", "{\"depot\":{\"mode\":\"BROUILLON\",\"dossierId\":\""
                + UUID.randomUUID() + "\"}}");
        assertThat(r.getResponse().getStatus()).isEqualTo(400);
        assertThat(r.getResponse().getContentAsString()).contains("ticket du brouillon");
    }

    @Test
    void depot_refuse_par_la_data_room_rendu_tel_quel() throws Exception {
        when(depot.deposer(any(), any(), any())).thenThrow(new DepotDataroomClient.DepotRefuseException(402,
                Map.of("code", "PLAN_LIMIT", "message", "Stockage de l\u2019essai atteint (500 Mo).")));
        String demande = """
                {"depot":{"dossierId":"%s","documentType":"PV_AGE","title":"PV","mode":"NOUVEAU"}}
                """.formatted(UUID.randomUUID());
        MvcResult r = appeler(WS_ESSAI, "/depot", demande);
        assertThat(r.getResponse().getStatus()).isEqualTo(402);
        assertThat(r.getResponse().getContentAsString()).contains("500 Mo");
    }

    @Test
    void cabinet_abonne_acte_inchange_en_piece_jointe_et_apercu_sans_mention() throws Exception {
        MvcResult r = appeler(WS_ABONNE, "", "{}");
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(r.getResponse().getHeader("Content-Disposition")).startsWith("attachment");
        assertThat(r.getResponse().getContentAsByteArray()).isEqualTo(acte);
        // Avec le filtre commun branche aussi.
        mvc = construire(new TrialSoftLockFilter(checker, json));
        assertThat(appeler(WS_ABONNE, "", "{}").getResponse().getContentAsByteArray()).isEqualTo(acte);
        JsonNode a = json.readTree(appeler(WS_ABONNE, "/apercu", "{}").getResponse().getContentAsByteArray());
        assertThat(a.at("/apercu/filigrane").asBoolean()).isFalse();
        assertThat(rouges(a.at("/apercu/pages/0/image").asText())).isZero();
    }

    private static long rouges(String dataUrl) throws Exception {
        byte[] png = Base64.getDecoder().decode(dataUrl.substring("data:image/png;base64,".length()));
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        long n = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                Color c = new Color(img.getRGB(x, y));
                if (c.getRed() - c.getGreen() > 25 && c.getRed() - c.getBlue() > 25) n++;
            }
        }
        return n;
    }
}
