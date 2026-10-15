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

    /**
     * Decision du 2026-10-14 (remplace la decision 23 du lot L7) : pendant l'essai, aucun fichier
     * ne parvient au navigateur, meme "inline". L'ancien comportement (PDF en apercu inline
     * servi tel quel) etait le defaut garde ici.
     */
    @Test
    void essai_fichier_inline_refuse_sur_une_route_inconnue() throws Exception {
        statut = "TRIAL_ACTIVE";
        MockHttpServletResponse res = executer("GET", "/api/v1/quelconque/documents/42/contenu", fichier("inline"));
        assertThat(controleurAppele).isTrue();
        assertThat(res.getStatus()).isEqualTo(402);
        assertThat(res.getHeader("Content-Disposition")).isNull();
        assertThat(res.getContentType()).startsWith("application/json");
        assertThat(res.getContentAsString()).doesNotContain("PDF").contains(TrialSoftLockFilter.CODE_TELECHARGEMENT);
    }

    @Test
    void essai_ancienne_route_d_apercu_refusee_avant_le_controleur() throws Exception {
        statut = "TRIAL_ACTIVE";
        for (String chemin : new String[]{
                "/api/v1/dataroom/documents/42/preview",
                "/api/v1/dataroom/documents/42/versions/7/preview",
                "/api/v1/dataroom/depots/9/preview",
                "/api/v1/ai/workflows/DISSOLUTION/documents/PV_DISSOLUTION_LIQUIDATION_SARL",
                "/api/v1/ai/documents/extract-template"}) {
            controleurAppele.set(false);
            String methode = chemin.startsWith("/api/v1/ai") ? "POST" : "GET";
            assertThat(executer(methode, chemin, fichier("inline")).getStatus()).as(chemin).isEqualTo(402);
            assertThat(controleurAppele).as(chemin).isFalse();
        }
        // Les routes d'apercu en affichage (pages filigranees, JSON) ne sont pas des telechargements.
        controleurAppele.set(false);
        executer("POST", "/api/v1/ai/workflows/DISSOLUTION/documents/PV_DISSOLUTION_LIQUIDATION_SARL/apercu",
                (q, r) -> controleurAppele.set(true));
        assertThat(controleurAppele).isTrue();
    }

    /** Controleur qui sert un corps du type donne, sans en-tete de disposition. */
    private FilterChain corps(String type, int statutHttp, byte[] octets) {
        return (req, res) -> {
            controleurAppele.set(true);
            var r = (jakarta.servlet.http.HttpServletResponse) res;
            r.setStatus(statutHttp);
            if (type != null) r.setContentType(type);
            r.getOutputStream().write(octets);
        };
    }

    @Test
    void essai_regle_positive_seuls_les_types_d_affichage_passent() throws Exception {
        statut = "TRIAL_ACTIVE";
        byte[] octets = "PK\u0003\u0004 contenu".getBytes(StandardCharsets.UTF_8);
        for (String type : new String[]{
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/pdf", "application/zip", "application/octet-stream",
                "application/vnd.oasis.opendocument.text", "text/csv", "text/html", "text/plain",
                "image/png", "image/jpeg", "application/xml"}) {
            MockHttpServletResponse res = executer("GET", "/api/v1/x/y", corps(type, 200, octets));
            assertThat(res.getStatus()).as(type).isEqualTo(402);
            assertThat(res.getContentAsString()).as(type).doesNotContain("PK")
                    .contains(TrialSoftLockFilter.CODE_TELECHARGEMENT);
        }
        // Corps sans type declare : un fichier aussi.
        assertThat(executer("GET", "/api/v1/x/y", corps(null, 200, octets)).getStatus()).isEqualTo(402);
    }

    @Test
    void essai_json_et_erreurs_texte_passent_sans_lire_l_etat() throws Exception {
        // Etat illisible : une reponse d'affichage ne depend pas d'auth-service.
        panne = new EtatEssaiIndisponibleException("auth-service injoignable", null);
        byte[] json = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletResponse res = executer("GET", "/api/v1/tickets", corps("application/json", 200, json));
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getContentAsByteArray()).isEqualTo(json);
        assertThat(executer("GET", "/api/v1/x", corps("application/problem+json", 404, json)).getStatus())
                .isEqualTo(404);
        assertThat(executer("GET", "/api/v1/x", corps("text/plain;charset=UTF-8", 500, json)).getStatus())
                .isEqualTo(500);
        assertThat(executer("GET", "/api/v1/x",
                (q, r) -> ((jakarta.servlet.http.HttpServletResponse) r).setStatus(204)).getStatus()).isEqualTo(204);
    }

    @Test
    void essai_etat_illisible_et_fichier_refuse_en_503() throws Exception {
        panne = new EtatEssaiIndisponibleException("auth-service injoignable", null);
        MockHttpServletResponse res = executer("GET", "/api/v1/x", corps("application/pdf", 200, PDF));
        assertThat(res.getStatus()).isEqualTo(503);
        assertThat(res.getContentAsString()).doesNotContain("PDF").contains(TrialSoftLockFilter.CODE_INDISPONIBLE);
    }

    @Test
    void essai_image_d_affichage_declaree_par_le_serveur_passe() throws Exception {
        statut = "TRIAL_ACTIVE";
        byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G'};
        FilterChain declaree = (req, res) -> {
            AffichageEssai.declarer((jakarta.servlet.http.HttpServletRequest) req);
            var r = (jakarta.servlet.http.HttpServletResponse) res;
            r.setContentType("image/png");
            r.getOutputStream().write(png);
        };
        MockHttpServletResponse res = executer("GET", "/api/v1/workspace/profile/logo", declaree);
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getContentAsByteArray()).isEqualTo(png);
        // Une image declaree reste refusee si elle se declare piece jointe.
        FilterChain declareePj = (req, res2) -> {
            AffichageEssai.declarer((jakarta.servlet.http.HttpServletRequest) req);
            var r = (jakarta.servlet.http.HttpServletResponse) res2;
            r.setContentType("image/png");
            r.setHeader("Content-Disposition", "attachment; filename=logo.png");
            r.getOutputStream().write(png);
        };
        assertThat(executer("GET", "/api/v1/workspace/profile/logo", declareePj).getStatus()).isEqualTo(402);
        // Seules les images sont declarables : un PDF declare reste un fichier.
        FilterChain pdfDeclare = (req, res3) -> {
            AffichageEssai.declarer((jakarta.servlet.http.HttpServletRequest) req);
            var r = (jakarta.servlet.http.HttpServletResponse) res3;
            r.setContentType("application/pdf");
            r.getOutputStream().write(PDF);
        };
        assertThat(executer("GET", "/api/v1/x", pdfDeclare).getStatus()).isEqualTo(402);
    }

    @Test
    void essai_type_change_apres_un_debut_d_ecriture_refuse() throws Exception {
        statut = "TRIAL_ACTIVE";
        FilterChain sournois = (req, res) -> {
            var r = (jakarta.servlet.http.HttpServletResponse) res;
            r.setContentType("application/json");
            r.getOutputStream().write('{');
            r.setContentType("application/pdf");
            r.getOutputStream().write(PDF);
        };
        MockHttpServletResponse res = executer("GET", "/api/v1/x", sournois);
        assertThat(res.getStatus()).isEqualTo(402);
        assertThat(res.getContentAsString()).doesNotContain("PDF");
    }

    @Test
    void essai_writer_d_un_type_fichier_refuse() throws Exception {
        statut = "TRIAL_ACTIVE";
        FilterChain csv = (req, res) -> {
            var r = (jakarta.servlet.http.HttpServletResponse) res;
            r.setContentType("text/csv");
            r.getWriter().write("nom;prenom\nsecret;secret");
        };
        MockHttpServletResponse res = executer("GET", "/api/v1/x", csv);
        assertThat(res.getStatus()).isEqualTo(402);
        assertThat(res.getContentAsString()).doesNotContain("secret");
    }

    @Test
    void cabinet_abonne_ou_sans_essai_apercu_et_fichiers_inchanges() throws Exception {
        for (String s : new String[]{"CONVERTED", null}) {
            statut = s;
            MockHttpServletResponse res = executer("GET", "/api/v1/dataroom/documents/42/preview", fichier("inline"));
            assertThat(res.getStatus()).as(String.valueOf(s)).isEqualTo(200);
            assertThat(res.getContentAsByteArray()).isEqualTo(PDF);
            MockHttpServletResponse zip = executer("GET", "/api/v1/x", corps("application/zip", 200, PDF));
            assertThat(zip.getStatus()).isEqualTo(200);
            assertThat(zip.getContentAsByteArray()).isEqualTo(PDF);
        }
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
    void lecture_seule_les_preferences_d_affichage_du_compte_restent_modifiables() throws Exception {
        // Lot L6b (E2) : le theme et l'assistant masque sont des preferences d'affichage du
        // compte, pas des donnees du cabinet : la lecture seule de l'essai ne les fige pas.
        statut = "TRIAL_EXPIRED";
        FilterChain ok = (req, res) -> {
            controleurAppele.set(true);
            res.setContentType("application/json");
            res.getWriter().write("{\"theme\":\"SOMBRE\",\"juriMasque\":false}");
        };
        MockHttpServletResponse res = executer("PUT", "/api/v1/users/me/preferences", ok);
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(controleurAppele).isTrue();
        assertThat(res.getContentAsString()).contains("SOMBRE");
        // Cette route seulement : le reste de /api/v1/users/me/** reste fige.
        controleurAppele.set(false);
        assertThat(executer("POST", "/api/v1/users/me/theme", ok).getStatus()).isEqualTo(402);
        assertThat(executer("PUT", "/api/v1/users/me/preferences/x", ok).getStatus()).isEqualTo(402);
        assertThat(controleurAppele).isFalse();
    }

    @Test
    void preferences_ecrites_sans_lire_l_etat_de_l_essai_mais_gardees_par_la_regle_d_affichage() throws Exception {
        // Ecrire une preference ne depend pas d'auth-service (etat illisible : la requete passe) ...
        panne = new EtatEssaiIndisponibleException("auth-service injoignable", null);
        FilterChain json = (req, res) -> {
            controleurAppele.set(true);
            res.setContentType("application/json");
            res.getWriter().write("{}");
        };
        assertThat(executer("PUT", "/api/v1/users/me/preferences", json).getStatus()).isEqualTo(200);
        assertThat(controleurAppele).isTrue();
        // ... mais la regle positive de l'affichage s'applique toujours a cette route.
        panne = null;
        statut = "TRIAL_ACTIVE";
        MockHttpServletResponse pj = executer("PUT", "/api/v1/users/me/preferences", fichier("attachment"));
        assertThat(pj.getStatus()).isEqualTo(402);
        assertThat(pj.getContentAsString()).doesNotContain("PDF");
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
