package ma.jurika.dataroom.application.access;

import jakarta.servlet.http.HttpServletRequest;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.infrastructure.persistence.ClientAccessLogEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * Sprint 7 / TASK 5 -- Service de tracage des acces client au Data Room.
 *
 * <p>Approche : appel direct depuis les use cases (preview, download, view).
 *
 * <p><b>2026-07-03 -- fiabilisation "Activite client vide".</b> L'ancienne
 * version portait {@code @Async} directement sur {@code log(...)} : le workspace
 * et les entetes HTTP etaient alors resolus SUR LE THREAD ASYNC, ou
 * {@link TenantContext} (ThreadLocal) n'est PAS propage et ou le
 * {@code RequestContextHolder} est vide. Consequence : quand
 * {@code user.workspaceId()} etait absent, l'insert etait silencieusement saute
 * ("no workspace context") -> aucune ligne -> compteur "Acces client" a 0.
 *
 * <p>Desormais ce front-door est <b>synchrone</b> : il capture le workspaceId,
 * le userId et l'IP/UA SUR LE THREAD DE LA REQUETE (ou ces donnees sont fiables)
 * puis delegue le seul INSERT au {@link ClientAccessLogDispatcher} {@code @Async}
 * (pool borne). Le dispatcher re-pose {@link TenantContext} a partir du
 * workspaceId porte par l'entite -> le {@code RlsAspect} positionne
 * {@code app.current_workspace_id} et l'insert reste fiable meme sous RLS
 * stricte (prod). Best-effort conserve : toute exception est avalee, le tracage
 * ne casse jamais la requete.
 *
 * <p>Actions valides (CHECK en DB V11) :
 * <ul>
 *   <li>VIEW_DOSSIER    -- GET /juridique</li>
 *   <li>PREVIEW_DOC     -- GET /documents/{id}/preview</li>
 *   <li>DOWNLOAD_DOC    -- GET /documents/{id}/download</li>
 *   <li>DOWNLOAD_VERSION-- GET /documents/{id}/versions/{v}/download</li>
 *   <li>PRINT_DOC       -- declenche cote frontend lors d'un print() (best-effort)</li>
 * </ul>
 */
@Component
public class ClientAccessLogger {

    private static final Logger log = LoggerFactory.getLogger(ClientAccessLogger.class);

    private final ClientAccessLogDispatcher dispatcher;

    public ClientAccessLogger(ClientAccessLogDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    /**
     * Trace un acces client. S'execute sur le thread de la requete : capture le
     * contexte (workspace + IP/UA) puis dispatche l'INSERT en async. Best-effort.
     */
    public void log(UUID dossierId, UUID documentId, String action, AuthenticatedUser user) {
        // Capture SUR LE THREAD REQUETE : user.workspaceId() (claim JWT, non null
        // pour tout principal authentifie) + user.userId(). Le repli sur le
        // TenantContext (pose par le JwtAuthFilter) est fait dans l'overload.
        log(dossierId, documentId, action,
                user != null ? user.workspaceId() : null,
                user != null ? user.userId() : null);
    }

    /**
     * Variante pour les appelants de la couche application qui n'ont pas
     * d'{@link AuthenticatedUser} sous la main mais disposent deja du workspace
     * (via {@link TenantContext}) et de l'identite de l'acteur (ex.
     * {@code DataroomDepotService} = depot client, {@code DemandesClientService}
     * = creation d'une demande). {@code userId} DOIT etre l'id de l'acteur reel
     * pour que le compteur "Acces client" (filtre {@code user_id = client_id})
     * s'incremente. Best-effort : ne casse jamais l'action metier.
     */
    public void log(UUID dossierId, UUID documentId, String action,
                    UUID workspaceId, UUID userId) {
        try {
            if (workspaceId == null) workspaceId = TenantContext.get();
            if (workspaceId == null) {
                // Warn EXPLICITE (pas un debug muet) : pour un principal
                // authentifie, l'absence simultanee de claim workspace ET de
                // TenantContext est anormale (token CLIENT sans claim workspace,
                // ou filtre multi-tenant non passe). Signale sans casser le flux.
                log.warn("ClientAccessLogger : skip {} dossier={} (aucun workspace "
                        + "resolu : ni claim JWT ni TenantContext) -- le journal "
                        + "d'acces restera vide tant que ce contexte manque", action, dossierId);
                return;
            }

            ClientAccessLogEntity e = new ClientAccessLogEntity();
            e.setWorkspaceId(workspaceId);
            e.setDossierId(dossierId);
            e.setDocumentId(documentId);
            e.setUserId(userId);
            e.setAction(action);
            applyRequestMetadata(e);

            // Dispatch async (bean distinct -> le proxy @Async s'applique bien ;
            // une auto-invocation aurait ete executee en synchrone).
            dispatcher.persistAsync(e);
        } catch (Exception ex) {
            log.warn("ClientAccessLogger : echec preparation {} dossier={} : {}",
                    action, dossierId, ex.getMessage());
        }
    }

    /**
     * IP + User-Agent depuis la requete HTTP courante (thread requete). Silencieux
     * si aucun {@code RequestContext} (appel hors servlet).
     */
    private void applyRequestMetadata(ClientAccessLogEntity e) {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return;
            HttpServletRequest req = attrs.getRequest();
            String ip = req.getHeader("X-Forwarded-For");
            if (ip == null || ip.isBlank()) ip = req.getRemoteAddr();
            // X-Forwarded-For peut etre une liste -- on prend la 1ere IP
            if (ip != null && ip.contains(",")) ip = ip.split(",")[0].trim();
            e.setIpAddress(ip);
            String ua = req.getHeader("User-Agent");
            if (ua != null && ua.length() > 255) ua = ua.substring(0, 255);
            e.setUserAgent(ua);
        } catch (IllegalStateException ignored) {
            // pas de RequestContext -> on s'en passe (IP/UA nulls)
        }
    }
}
