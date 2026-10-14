package ma.jurika.ai.workflow;

import ma.jurika.ai.api.GenerationExceptionHandler;
import ma.jurika.ai.document.DocxTemplateEngine;
import ma.jurika.ai.document.DocxTemplateEngine.DocumentResult;
import ma.jurika.ai.document.corpus.DictionnaireUnique;
import ma.jurika.ai.document.manifest.TemplateManifestLoader;
import ma.jurika.ai.workflow.identity.SocieteIdentityEnricher;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.Role;
import ma.jurika.common.trial.EssaiRegles;
import ma.jurika.common.trial.TrialAccessChecker;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lot L7 (E4) : un acte genere pendant l'essai sort pour l'APERCU (inline,
 * jamais en piece jointe : RG-ESS-05) et porte la mention d'evaluation
 * (RG-ESS-06), de facon deterministe ; abonne : acte inchange, en piece jointe.
 */
class GenerationEssaiTest {

    private static final UUID WS_ESSAI = UUID.randomUUID();
    private static final UUID WS_ABONNE = UUID.randomUUID();

    private final WorkflowDocumentMappingService mapping = mock(WorkflowDocumentMappingService.class);
    private final DocxTemplateEngine engine = mock(DocxTemplateEngine.class);
    private MockMvc mvc;
    private byte[] acte;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        WorkflowDocumentController c = new WorkflowDocumentController(mapping, engine,
                mock(TemplateManifestLoader.class), new SocieteIdentityEnricher((ws, id) -> Map.of()),
                (ws, t, e) -> Map.of(), (w, t, wf, tpl, d) -> { }, (w, t) -> List.of());
        TrialAccessChecker checker = ws -> Optional.of(new TrialAccessChecker.TrialState(ws,
                WS_ESSAI.equals(ws) ? "TRIAL_ACTIVE" : "CONVERTED", 3, Instant.now(), null));
        ObjectProvider<TrialAccessChecker> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(checker);
        c.setEssais(p);
        mvc = MockMvcBuilders.standaloneSetup(c)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GenerationExceptionHandler()).build();
        when(mapping.map(any(), any(), anyMap())).thenReturn(Map.of());
        when(mapping.gabarit(any(), any(), any())).thenAnswer(inv -> inv.getArgument(1));
        when(engine.dictionnaire()).thenReturn(new DictionnaireUnique(Set.of(), Map.of(), Map.of()));
        try (XWPFDocument doc = new XWPFDocument()) {
            doc.createParagraph().createRun().setText("Proces-verbal de l'assemblee");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            acte = out.toByteArray();
        }
        when(engine.generate(any(), anyMap())).thenAnswer(inv -> new DocumentResult(acte,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "PV.docx", true,
                List.of(), List.of()));
    }

    @AfterEach
    void vider() {
        SecurityContextHolder.clearContext();
    }

    private MvcResult generer(UUID ws) throws Exception {
        AuthenticatedUser u = new AuthenticatedUser(UUID.randomUUID(), ws, "employe@cabinet.ma", Role.EMPLOYE);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(u, null, List.of()));
        return mvc.perform(post("/api/v1/ai/workflows/DISSOLUTION/documents/PV_DISSOLUTION_LIQUIDATION_SARL")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andReturn();
    }

    @Test
    void pendant_l_essai_acte_en_apercu_avec_la_mention() throws Exception {
        MvcResult r = generer(WS_ESSAI);
        assertThat(r.getResponse().getHeader("Content-Disposition")).startsWith("inline");
        assertThat(r.getResponse().getHeader("X-Jurika-Essai")).isEqualTo("VERSION_EVALUATION");
        byte[] corps = r.getResponse().getContentAsByteArray();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(corps))) {
            assertThat(doc.getHeaderList().get(0).getText()).contains(EssaiRegles.MENTION_FILIGRANE);
        }
        assertThat(generer(WS_ESSAI).getResponse().getContentAsByteArray()).isEqualTo(corps);
    }

    @Test
    void cabinet_abonne_acte_inchange_en_piece_jointe() throws Exception {
        MvcResult r = generer(WS_ABONNE);
        assertThat(r.getResponse().getHeader("Content-Disposition")).startsWith("attachment");
        assertThat(r.getResponse().getContentAsByteArray()).isEqualTo(acte);
    }
}
