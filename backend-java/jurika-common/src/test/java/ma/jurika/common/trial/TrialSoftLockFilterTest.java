package ma.jurika.common.trial;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import ma.jurika.common.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/** Lot L7 (E4) : RG-ESS-05 (aucun telechargement, y compris lien direct) et RG-ESS-10 (lecture seule). */
class TrialSoftLockFilterTest {

    private static final UUID WS = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final byte[] PDF = "%PDF-1.7 contenu secret".getBytes(StandardCharsets.UTF_8);

    private String statut;
    private RuntimeException panne;
    private final AtomicBoolean controleurAppele = new AtomicBoolean();

    private final TrialAccessChecker checker = ws -> {
        if (panne != null) throw panne;
        return statut == null ? Optional.empty()
                : Optional.of(new TrialAccessChecker.TrialState(ws, statut, 3, Instant.now(), null));
    };
    private final TrialSoftLockFilter filtre = new TrialSoftLockFilter(checker, new ObjectMapper());

    @BeforeEach
    void poser() {
        TenantContext.set(WS);
        statut = null;
        panne = null;
        controleurAppele.set(false);
    }

    @AfterEach
    void vider() {
        TenantContext.clear();
    }

    /** Controleur qui sert un fichier, en piece jointe ou en apercu. */
    private FilterChain fichier(String disposition) {
        return (req, res) -> {
            controleurAppele.set(true);
            var r = (jakarta.servlet.http.HttpServletResponse) res;
            r.setStatus(200);
            r.setContentType("application/pdf");
            r.addHeader("Content-Disposition", disposition + "; filename=\"acte.pdf\"");
            r.setContentLength(PDF.length);
            r.getOutputStream().write(PDF);
        };
    }

    private MockHttpServletResponse executer(String methode, String chemin, FilterChain chaine) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(methode, chemin);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filtre.doFilter(req, res, chaine);
        return res;
    }

    @Test
    void essai_route_de_telechargement_refusee_avant_le_controleur() throws Exception {
        statut = "TRIAL_ACTIVE";
        MockHttpServletResponse res = executer("GET", "/api/v1/dataroom/documents/42/download", fichier("attachment"));
        assertThat(res.getStatus()).isEqualTo(402);
        assertThat(res.getContentAsString()).contains(TrialSoftLockFilter.CODE_TELECHARGEMENT)
                .contains("Aucun t\u00e9l\u00e9chargement");
        assertThat(controleurAppele).isFalse();
    }

    @Test
    void essai_toutes_les_routes_de_telechargement_connues_refusees() throws Exception {
        statut = "TRIAL_ACTIVE";
        for (String[] r : new String[][]{
                {"GET", "/api/v1/dataroom/documents/1/versions/2/download"},
                {"GET", "/api/v1/dataroom/depots/1/download"},
                {"POST", "/api/v1/dataroom/dossiers/1/juridique/export-zip"},
                {"POST", "/api/v1/dataroom/dossiers/1/juridique/fiche-client/pdf"},
                {"POST", "/api/v1/dataroom/dossiers/1/juridique/fiche-dossier/pdf"},
                {"GET", "/api/v1/tickets/1/debours/etat-pdf"},
                {"GET", "/api/v1/dashboards/export/csv"},
                {"POST", "/api/v1/ai/document-edit/html-to-pdf"},
                {"POST", "/api/v1/ai/document-render/docx-to-pdf"},
                {"POST", "/api/v1/ai/documents/generate/STATUTS"}}) {
            controleurAppele.set(false);
            assertThat(executer(r[0], r[1], fichier("attachment")).getStatus()).as(r[1]).isEqualTo(402);
            assertThat(controleurAppele).as(r[1]).isFalse();
        }
    }

    @Test
    void essai_lien_direct_inconnu_qui_rend_une_piece_jointe_intercepte_sans_fuite() throws Exception {
        statut = "TRIAL_ACTIVE";
        MockHttpServletResponse res = executer("GET", "/api/v1/quelconque/fichier/7", fichier("attachment"));
        assertThat(controleurAppele).isTrue();
        assertThat(res.getStatus()).isEqualTo(402);
        assertThat(res.getHeader("Content-Disposition")).isNull();
        assertThat(res.getContentAsString()).doesNotContain("PDF").contains(TrialSoftLockFilter.CODE_TELECHARGEMENT);
    }

    @Test
    void essai_apercu_inline_permis() throws Exception {
        statut = "TRIAL_ACTIVE";
        MockHttpServletResponse res = executer("GET", "/api/v1/dataroom/documents/42/preview", fichier("inline"));
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getContentAsByteArray()).isEqualTo(PDF);
    }

    @Test
    void cabinet_abonne_ou_sans_essai_telecharge_normalement() throws Exception {
        for (String s : new String[]{"CONVERTED", null}) {
            statut = s;
            MockHttpServletResponse res = executer("GET", "/api/v1/dataroom/documents/42/download", fichier("attachment"));
            assertThat(res.getStatus()).as(String.valueOf(s)).isEqualTo(200);
            assertThat(res.getContentAsByteArray()).isEqualTo(PDF);
            assertThat(res.getHeader("Content-Disposition")).startsWith("attachment");
        }
    }

    @Test
    void lecture_seule_refuse_les_modifications_et_laisse_lire() throws Exception {
        statut = "TRIAL_EXPIRED";
        FilterChain ok = (req, res) -> controleurAppele.set(true);
        for (String m : new String[]{"POST", "PUT", "PATCH", "DELETE"}) {
            controleurAppele.set(false);
            MockHttpServletResponse res = executer(m, "/api/v1/tickets", ok);
            assertThat(res.getStatus()).as(m).isEqualTo(402);
            assertThat(res.getContentAsString()).contains(TrialSoftLockFilter.CODE_LECTURE_SEULE);
            assertThat(controleurAppele).as(m).isFalse();
        }
        assertThat(executer("GET", "/api/v1/tickets", ok).getStatus()).isEqualTo(200);
        assertThat(controleurAppele).isTrue();
        // Connexion et souscription restent possibles.
        controleurAppele.set(false);
        assertThat(executer("POST", "/api/v1/billing/subscribe", ok).getStatus()).isEqualTo(200);
        assertThat(executer("POST", "/api/v1/auth/logout", ok).getStatus()).isEqualTo(200);
    }

    @Test
    void essai_en_cours_peut_modifier() throws Exception {
        statut = "TRIAL_ACTIVE";
        FilterChain ok = (req, res) -> controleurAppele.set(true);
        assertThat(executer("POST", "/api/v1/tickets", ok).getStatus()).isEqualTo(200);
        assertThat(controleurAppele).isTrue();
    }

    @Test
    void etat_illisible_refuse_au_lieu_de_laisser_passer() throws Exception {
        panne = new EtatEssaiIndisponibleException("auth-service injoignable", null);
        MockHttpServletResponse dl = executer("GET", "/api/v1/dataroom/documents/42/download", fichier("attachment"));
        assertThat(dl.getStatus()).isEqualTo(503);
        assertThat(executer("POST", "/api/v1/tickets", (q, r) -> controleurAppele.set(true)).getStatus())
                .isEqualTo(503);
        assertThat(controleurAppele).isFalse();
        MockHttpServletResponse pj = executer("GET", "/api/v1/quelconque/7", fichier("attachment"));
        assertThat(pj.getStatus()).isEqualTo(503);
        assertThat(pj.getContentAsString()).doesNotContain("PDF");
    }

    @Test
    void sans_workspace_aucun_controle() throws Exception {
        TenantContext.clear();
        statut = "TRIAL_ACTIVE";
        assertThat(executer("GET", "/api/v1/dataroom/documents/42/download", fichier("attachment")).getStatus())
                .isEqualTo(200);
    }
}
