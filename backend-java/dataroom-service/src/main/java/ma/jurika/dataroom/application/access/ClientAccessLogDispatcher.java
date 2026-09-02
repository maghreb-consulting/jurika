package ma.jurika.dataroom.application.access;

import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.infrastructure.persistence.ClientAccessLogEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 2026-07-03 -- Frontiere {@code @Async} du tracage d'acces client.
 *
 * <p>Bean DISTINCT du {@link ClientAccessLogger} : c'est indispensable pour que
 * le proxy Spring {@code @Async} s'applique. Si {@code persistAsync} vivait dans
 * le meme bean que l'appelant, l'auto-invocation contournerait le proxy et
 * l'insert s'executerait en synchrone sur le thread requete.
 *
 * <p>Role unique : re-poser le {@link TenantContext} a partir du workspaceId
 * DEJA porte par l'entite (capture sur le thread requete par le logger) AVANT
 * d'appeler le writer {@code @Transactional}. Le {@code RlsAspect} lit
 * {@code TenantContext.get()} a l'entree de {@code writer.persist(...)} et emet
 * {@code set_config('app.current_workspace_id', ...)} -> insert fiable sous RLS.
 *
 * <p>Best-effort : toute exception est avalee (le tracage ne casse jamais le
 * flux). Utilise l'executor borne {@code clientAccessLogExecutor}
 * (cf {@link ClientAccessLogAsyncConfig}) plutot que le SimpleAsyncTaskExecutor
 * implicite (thread non borne par requete).
 */
@Component
public class ClientAccessLogDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ClientAccessLogDispatcher.class);

    private final ClientAccessLogWriter writer;

    public ClientAccessLogDispatcher(ClientAccessLogWriter writer) {
        this.writer = writer;
    }

    @Async("clientAccessLogExecutor")
    public void persistAsync(ClientAccessLogEntity entity) {
        UUID prev = TenantContext.get();
        try {
            TenantContext.set(entity.getWorkspaceId());
            writer.persist(entity);
        } catch (Exception ex) {
            log.warn("ClientAccessLogDispatcher : echec insert {} dossier={} : {}",
                    entity.getAction(), entity.getDossierId(), ex.getMessage());
        } finally {
            TenantContext.set(prev);
        }
    }
}
