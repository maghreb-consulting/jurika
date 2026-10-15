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
 * Lot L7 : controles SERVEUR de l'essai gratuit (P6), places dans la chaine de
 * securite de chaque service juste apres {@code JwtAuthFilter} (qui pose le
 * workspace courant).
 *
 * <ul>
 *   <li><b>RG-ESS-05</b> (decision du 2026-10-14, qui remplace la decision 23 :
 *       "pendant l'essai, aucun fichier d'acte ne doit parvenir au navigateur ;
 *       l'apercu est un affichage seulement") : REGLE POSITIVE. Pendant l'essai
 *       (en cours ou en lecture seule), une reponse ne passe que si elle est
 *       d'affichage :
 *       <ul>
 *         <li>JSON ({@code application/json}, {@code application/*+json}) :
 *             donnees des ecrans, et pages d'apercu filigranees (images PNG
 *             produites par le serveur, filigrane incruste) ;</li>
 *         <li>texte ou HTML d'une ERREUR (statut 400 et plus) ;</li>
 *         <li>image PNG ou JPEG que le serveur a declaree d'affichage
 *             ({@link AffichageEssai}) : code QR, logo reencode ;</li>
 *         <li>reponse sans corps.</li>
 *       </ul>
 *       Tout autre corps (DOCX, PDF, ODT, ZIP, CSV, octets d'un fichier depose,
 *       type absent...), et toute reponse qui se declare piece jointe, est
 *       intercepte avant qu'un octet ne parte, quel que soit l'en-tete
 *       {@code Content-Disposition} (inline ou attachment) et sur toutes les
 *       routes, y compris les liens directs. Une liste de routes ne suffit
 *       pas : une route oubliee laisserait passer un fichier (motif 9). Les
 *       routes de telechargement connues restent refusees AVANT le
 *       controleur (aucun effet de bord : journal d'acces, conversion).</li>
 *   <li><b>RG-ESS-10</b> : en lecture seule, toute requete qui modifie
 *       (POST, PUT, PATCH, DELETE) est refusee, hors routes de connexion, de
 *       souscription et internes.</li>
 * </ul>
 * Refus : HTTP 402, corps {@code {code, message}} lisible par l'ecran. Etat
 * de l'essai illisible : HTTP 503 (jamais de laisser-passer silencieux). Une
 * reponse d'affichage ne lit pas l'etat de l'essai : elle ne depend donc pas
 * d'auth-service.
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

    /**
     * RG-ESS-05 : routes connues qui rendent un fichier, refusees avant le controleur. La regle
     * positive (types d'affichage) couvre toutes les autres.
     */
    static final List<String> ROUTES_TELECHARGEMENT = List.of(
            "/api/v1/**/download",
            "/api/v1/**/export-zip",
            "/api/v1/**/export/**",
            "/api/v1/**/preview",
            "/api/v1/dataroom/dossiers/*/juridique/fiche-client/pdf",
            "/api/v1/dataroom/dossiers/*/juridique/fiche-dossier/pdf",
            "/api/v1/tickets/*/debours/etat-pdf",
            "/api/v1/ai/documents/generate/**",
            "/api/v1/ai/documents/extract-template",
            "/api/v1/ai/workflows/*/documents/*",
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

    /**
     * Regle positive de l'essai : vrai si la reponse est d'affichage et peut parvenir au navigateur
     * pendant l'essai.
     *
     * @param type           type de contenu de la reponse (parametres compris), ou null
     * @param statut         statut HTTP
     * @param imageDeclaree  le serveur a declare une image d'affichage ({@link AffichageEssai})
     * @param corpsEcrit     un octet du corps est sur le point de partir
     */
    static boolean typeAffichage(String type, int statut, boolean imageDeclaree, boolean corpsEcrit) {
        if (type == null || type.isBlank()) return !corpsEcrit;
        String t = type.toLowerCase(Locale.ROOT);
        int pv = t.indexOf(';');
        if (pv >= 0) t = t.substring(0, pv);
        t = t.trim();
        if (t.equals("application/json") || (t.startsWith("application/") && t.endsWith("+json"))) return true;
        if (statut >= 400 && (t.equals("text/plain") || t.equals("text/html"))) return true;
        return imageDeclaree && (t.equals("image/png") || t.equals("image/jpeg"));
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

        // Regle positive : toute reponse qui n'est pas d'affichage, pendant l'essai.
        GardeAffichage garde = new GardeAffichage(request, response, workspaceId);
        chain.doFilter(request, garde);
        garde.terminer();
        if (garde.bloque) {
            if (response.isCommitted()) {
                log.error("Essai : fichier detecte apres l'envoi de la reponse (workspace={}) {} {}",
                        workspaceId, request.getMethod(), chemin);
                return;
            }
            response.reset();
            if (garde.indisponible) {
                refuser(response, 503, CODE_INDISPONIBLE, EssaiRegles.MESSAGE_VERIFICATION_IMPOSSIBLE);
            } else {
                log.info("Essai : fichier refuse (workspace={}) {} {} type={}", workspaceId, request.getMethod(),
                        chemin, garde.typeRefuse);
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

    /**
     * Reponse qui, pendant l'essai, ne laisse partir que l'affichage : le controle a lieu avant
     * chaque ecriture du corps, chaque vidage et a la fin de la chaine ; l'etat de l'essai n'est
     * lu que pour une reponse qui n'est pas d'affichage.
     */
    private final class GardeAffichage extends HttpServletResponseWrapper {

        private final HttpServletRequest requete;
        private final UUID workspaceId;
        private Boolean enEssai;
        private boolean pieceJointe;
        private boolean bloque;
        private boolean indisponible;
        private String typeRefuse;

        GardeAffichage(HttpServletRequest requete, HttpServletResponse response, UUID workspaceId) {
            super(response);
            this.requete = requete;
            this.workspaceId = workspaceId;
        }

        private boolean enEssai() {
            if (enEssai == null) {
                try {
                    enEssai = checker.getState(workspaceId).map(TrialAccessChecker.TrialState::enEssai).orElse(false);
                } catch (RuntimeException ex) {
                    log.error("Etat d'essai illisible (workspace={}) : fichier refuse", workspaceId, ex);
                    indisponible = true;
                    enEssai = Boolean.TRUE;
                }
            }
            return enEssai;
        }

        /** Controle avant qu'un octet du corps ne parte (ou a la fin, sans corps). */
        private void controler(boolean corpsEcrit) {
            if (bloque) return;
            String type = super.getContentType();
            if (!pieceJointe && typeAffichage(type, super.getStatus(), AffichageEssai.estDeclaree(requete), corpsEcrit)) {
                return;
            }
            if (enEssai()) {
                bloque = true;
                typeRefuse = pieceJointe ? "piece jointe" : type;
            }
        }

        void terminer() {
            controler(false);
        }

        private boolean intercepter(String nom, String valeur) {
            if (bloque) return true;
            if (nom == null || valeur == null || !HttpHeaders.CONTENT_DISPOSITION.equalsIgnoreCase(nom)) return false;
            if (!valeur.trim().toLowerCase(Locale.ROOT).startsWith("attachment")) return false;
            pieceJointe = true;
            controler(false);
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
            controler(false);
            if (!bloque) super.flushBuffer();
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (bloque) return fluxNul();
            ServletOutputStream reel = super.getOutputStream();
            return new ServletOutputStream() {
                @Override public boolean isReady() { return reel.isReady(); }
                @Override public void setWriteListener(WriteListener l) { reel.setWriteListener(l); }
                @Override public void write(int b) throws IOException {
                    controler(true);
                    if (!bloque) reel.write(b);
                }
                @Override public void write(byte[] b, int off, int len) throws IOException {
                    if (len <= 0) return;
                    controler(true);
                    if (!bloque) reel.write(b, off, len);
                }
                @Override public void flush() throws IOException {
                    controler(false);
                    if (!bloque) reel.flush();
                }
                @Override public void close() throws IOException {
                    controler(false);
                    if (!bloque) reel.close();
                }
            };
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (bloque) return new PrintWriter(OutputStream.nullOutputStream());
            PrintWriter reel = super.getWriter();
            return new PrintWriter(new Writer() {
                @Override public void write(char[] c, int off, int len) {
                    if (len <= 0) return;
                    controler(true);
                    if (!bloque) reel.write(c, off, len);
                }
                @Override public void flush() {
                    controler(false);
                    if (!bloque) reel.flush();
                }
                @Override public void close() {
                    controler(false);
                    if (!bloque) reel.close();
                }
            });
        }

        private ServletOutputStream fluxNul() {
            return new ServletOutputStream() {
                @Override public boolean isReady() { return true; }
                @Override public void setWriteListener(WriteListener l) { }
                @Override public void write(int b) { }
                @Override public void write(byte[] b, int off, int len) { }
            };
        }
    }
}
