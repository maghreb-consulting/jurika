package ma.jurika.common.trial;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import ma.jurika.common.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Lot L7 (E4) : controles SERVEUR de l'essai gratuit (P6), places dans la
 * chaine de securite de chaque service juste apres {@code JwtAuthFilter} (qui
 * pose le workspace courant).
 *
 * <ul>
 *   <li><b>RG-ESS-05</b> : aucun telechargement pendant l'essai (en cours ou
 *       en lecture seule), y compris par lien direct. Deux verrous :
 *       les routes de telechargement connues sont refusees avant le
 *       controleur ; toute autre reponse qui se declare piece jointe
 *       ({@code Content-Disposition: attachment}) est interceptee avant
 *       qu'un octet ne parte, et remplacee par le refus. L'apercu
 *       ({@code inline}) reste permis.</li>
 *   <li><b>RG-ESS-10</b> : en lecture seule, toute requete qui modifie
 *       (POST, PUT, PATCH, DELETE) est refusee, hors routes de connexion, de
 *       souscription et internes.</li>
 * </ul>
 * Refus : HTTP 402, corps {@code {code, message}} lisible par l'ecran. Etat
 * de l'essai illisible : HTTP 503 (jamais de laisser-passer silencieux).
 *
 * <p>Ce filtre ne doit tourner QUE dans la chaine de securite : son
 * enregistrement automatique comme filtre de servlet est desactive par
 * {@link EssaiAutoConfiguration} (sinon il s'executerait avant
 * JwtAuthFilter, sans workspace, et serait marque deja passe).
 */
public class TrialSoftLockFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TrialSoftLockFilter.class);

    public static final String CODE_TELECHARGEMENT = "ESSAI_TELECHARGEMENT_INTERDIT";
    public static final String CODE_LECTURE_SEULE = "ESSAI_LECTURE_SEULE";
    public static final String CODE_INDISPONIBLE = "ESSAI_ETAT_INDISPONIBLE";

    /** Jamais controlees : connexion, pages publiques, souscription, etat de l'essai, appels internes. */
    static final List<String> ROUTES_LIBRES = List.of(
            "/api/v1/auth/**",
            "/api/v1/public/**",
            "/api/v1/billing/**",
            "/api/v1/workspace/essai",
            "/internal/**",
            "/actuator/**",
            "/v3/api-docs/**",
            "/swagger-ui/**"
    );

    /** RG-ESS-05 : routes de telechargement connues, refusees avant le controleur. */
    static final List<String> ROUTES_TELECHARGEMENT = List.of(
            "/api/v1/**/download",
            "/api/v1/**/export-zip",
            "/api/v1/**/export/**",
            "/api/v1/dataroom/dossiers/*/juridique/fiche-client/pdf",
            "/api/v1/dataroom/dossiers/*/juridique/fiche-dossier/pdf",
            "/api/v1/tickets/*/debours/etat-pdf",
            "/api/v1/ai/documents/generate/**",
            "/api/v1/ai/document-edit/**",
            "/api/v1/ai/document-render/**"
    );

    private static final Set<String> METHODES_SURES = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final TrialAccessChecker checker;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public TrialSoftLockFilter(TrialAccessChecker checker, ObjectMapper objectMapper) {
        this.checker = checker;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return correspond(ROUTES_LIBRES, chemin(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        UUID workspaceId = TenantContext.get();
        if (workspaceId == null) {
            chain.doFilter(request, response);
            return;
        }
        String chemin = chemin(request);
        boolean mutation = !METHODES_SURES.contains(request.getMethod().toUpperCase(Locale.ROOT));
        if (correspond(ROUTES_TELECHARGEMENT, chemin) || mutation) {
            Optional<TrialAccessChecker.TrialState> etat;
            try {
                etat = checker.getState(workspaceId);
            } catch (RuntimeException ex) {
                log.error("Etat d'essai illisible (workspace={}) : {} {} refuse", workspaceId,
                        request.getMethod(), chemin, ex);
                refuser(response, 503, CODE_INDISPONIBLE, EssaiRegles.MESSAGE_VERIFICATION_IMPOSSIBLE);
                return;
            }
            boolean enEssai = etat.map(TrialAccessChecker.TrialState::enEssai).orElse(false);
            if (enEssai && correspond(ROUTES_TELECHARGEMENT, chemin)) {
                log.info("Essai : telechargement refuse (workspace={}) {} {}", workspaceId, request.getMethod(), chemin);
                refuser(response, 402, CODE_TELECHARGEMENT, EssaiRegles.MESSAGE_TELECHARGEMENT_INTERDIT);
                return;
            }
            if (mutation && etat.map(TrialAccessChecker.TrialState::isExpired).orElse(false)) {
                log.info("Essai en lecture seule : {} {} refuse (workspace={})", request.getMethod(), chemin, workspaceId);
                refuser(response, 402, CODE_LECTURE_SEULE, EssaiRegles.MESSAGE_LECTURE_SEULE);
                return;
            }
        }

        // Second verrou : toute reponse declaree piece jointe pendant l'essai.
        GardePieceJointe garde = new GardePieceJointe(response, workspaceId);
        chain.doFilter(request, garde);
        if (garde.bloque) {
            if (response.isCommitted()) {
                log.error("Essai : piece jointe detectee apres l'envoi de la reponse (workspace={}) {} {}",
                        workspaceId, request.getMethod(), chemin);
                return;
            }
            response.reset();
            if (garde.indisponible) {
                refuser(response, 503, CODE_INDISPONIBLE, EssaiRegles.MESSAGE_VERIFICATION_IMPOSSIBLE);
            } else {
                log.info("Essai : piece jointe refusee (workspace={}) {} {}", workspaceId, request.getMethod(), chemin);
                refuser(response, 402, CODE_TELECHARGEMENT, EssaiRegles.MESSAGE_TELECHARGEMENT_INTERDIT);
            }
        }
    }

    private boolean correspond(List<String> motifs, String chemin) {
        for (String m : motifs) {
            if (matcher.match(m, chemin)) return true;
        }
        return false;
    }

    private static String chemin(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contexte = request.getContextPath();
        return contexte != null && !contexte.isEmpty() && uri.startsWith(contexte) ? uri.substring(contexte.length()) : uri;
    }

    private void refuser(HttpServletResponse response, int statut, String code, String message) throws IOException {
        response.setStatus(statut);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("code", code);
        corps.put("message", message);
        response.getWriter().write(objectMapper.writeValueAsString(corps));
    }

    /** Reponse qui refuse de devenir une piece jointe pendant l'essai. */
    private final class GardePieceJointe extends HttpServletResponseWrapper {

        private final UUID workspaceId;
        private boolean bloque;
        private boolean indisponible;

        GardePieceJointe(HttpServletResponse response, UUID workspaceId) {
            super(response);
            this.workspaceId = workspaceId;
        }

        private boolean intercepter(String nom, String valeur) {
            if (bloque) return true;
            if (nom == null || valeur == null || !HttpHeaders.CONTENT_DISPOSITION.equalsIgnoreCase(nom)) return false;
            if (!valeur.trim().toLowerCase(Locale.ROOT).startsWith("attachment")) return false;
            try {
                bloque = checker.getState(workspaceId).map(TrialAccessChecker.TrialState::enEssai).orElse(false);
            } catch (RuntimeException ex) {
                log.error("Etat d'essai illisible (workspace={}) : piece jointe refusee", workspaceId, ex);
                bloque = true;
                indisponible = true;
            }
            return bloque;
        }

        @Override
        public void setHeader(String name, String value) {
            if (!intercepter(name, value)) super.setHeader(name, value);
        }

        @Override
        public void addHeader(String name, String value) {
            if (!intercepter(name, value)) super.addHeader(name, value);
        }

        @Override
        public void setStatus(int sc) {
            if (!bloque) super.setStatus(sc);
        }

        @Override
        public void setContentType(String type) {
            if (!bloque) super.setContentType(type);
        }

        @Override
        public void setContentLength(int len) {
            if (!bloque) super.setContentLength(len);
        }

        @Override
        public void setContentLengthLong(long len) {
            if (!bloque) super.setContentLengthLong(len);
        }

        @Override
        public void flushBuffer() throws IOException {
            if (!bloque) super.flushBuffer();
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (!bloque) {
                ServletOutputStream reel = super.getOutputStream();
                return new ServletOutputStream() {
                    @Override public boolean isReady() { return reel.isReady(); }
                    @Override public void setWriteListener(WriteListener l) { reel.setWriteListener(l); }
                    @Override public void write(int b) throws IOException { if (!bloque) reel.write(b); }
                    @Override public void write(byte[] b, int off, int len) throws IOException {
                        if (!bloque) reel.write(b, off, len);
                    }
                    @Override public void flush() throws IOException { if (!bloque) reel.flush(); }
                    @Override public void close() throws IOException { if (!bloque) reel.close(); }
                };
            }
            return new ServletOutputStream() {
                @Override public boolean isReady() { return true; }
                @Override public void setWriteListener(WriteListener l) { }
                @Override public void write(int b) { }
                @Override public void write(byte[] b, int off, int len) { }
            };
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (!bloque) {
                PrintWriter reel = super.getWriter();
                return new PrintWriter(new Writer() {
                    @Override public void write(char[] c, int off, int len) { if (!bloque) reel.write(c, off, len); }
                    @Override public void flush() { if (!bloque) reel.flush(); }
                    @Override public void close() { if (!bloque) reel.close(); }
                });
            }
            return new PrintWriter(OutputStream.nullOutputStream());
        }
    }
}
