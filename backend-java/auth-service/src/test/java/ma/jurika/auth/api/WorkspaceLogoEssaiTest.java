package ma.jurika.auth.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.auth.application.WorkspaceLogoService;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import ma.jurika.common.trial.TrialAccessChecker;
import ma.jurika.common.trial.TrialSoftLockFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Decision du 2026-10-14 (RG-ESS-05) : pendant l'essai, le logo depose ne parvient pas tel quel au
 * navigateur ; le serveur le redessine en PNG (image d'affichage declaree au filtre commun de
 * l'essai) ; cabinet abonne : octets deposes inchanges.
 */
class WorkspaceLogoEssaiTest {

    private static final UUID WS_ESSAI = UUID.randomUUID();
    private static final UUID WS_ABONNE = UUID.randomUUID();
    private final WorkspaceLogoService service = mock(WorkspaceLogoService.class);
    private MockMvc mvc;
    private byte[] jpeg;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        TrialAccessChecker checker = ws -> Optional.of(new TrialAccessChecker.TrialState(ws,
                WS_ESSAI.equals(ws) ? "TRIAL_ACTIVE" : "CONVERTED", 3, Instant.now(), null));
        ObjectProvider<TrialAccessChecker> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(checker);
        WorkspaceLogoController c = new WorkspaceLogoController(service);
        c.setEssais(p);
        mvc = MockMvcBuilders.standaloneSetup(c)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .addFilters(new TrialSoftLockFilter(checker, new ObjectMapper()))
                .build();
        BufferedImage img = new BufferedImage(60, 20, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", out);
        jpeg = out.toByteArray();
        when(service.get(WS_ESSAI)).thenReturn(Optional.of(new WorkspaceLogoService.Logo(jpeg, "image/jpeg")));
        when(service.get(WS_ABONNE)).thenReturn(Optional.of(new WorkspaceLogoService.Logo(jpeg, "image/jpeg")));
    }

    @AfterEach
    void vider() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private MvcResult lire(UUID ws) throws Exception {
        AuthenticatedUser u = new AuthenticatedUser(UUID.randomUUID(), ws, "sup@cabinet.ma", Role.SUPERVISEUR);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, List.of()));
        TenantContext.set(ws);
        return mvc.perform(get("/api/v1/workspace/profile/logo")).andReturn();
    }

    @Test
    void essai_logo_redessine_en_png_par_le_serveur() throws Exception {
        MvcResult r = lire(WS_ESSAI);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(r.getResponse().getContentType()).isEqualTo("image/png");
        byte[] corps = r.getResponse().getContentAsByteArray();
        assertThat(corps).isNotEqualTo(jpeg);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(corps));
        assertThat(img.getWidth()).isEqualTo(60);
    }

    @Test
    void cabinet_abonne_logo_inchange() throws Exception {
        MvcResult r = lire(WS_ABONNE);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(r.getResponse().getContentAsByteArray()).isEqualTo(jpeg);
    }
}
