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
 * d'appeler le writer {@code @Transactional}. Le gestionnaire de transactions
 * (TenantAwareJpaTransactionManager, jurika-common) lit
 * {@code TenantContext.get()} a l'entree de {@code writer.persist(...)} et emet
 * {@code set_config('app.current_workspace_id', ...)} -> insert fiable sous RLS.
 *
 * <p>Best-effort : le tracage ne casse jamais le flux (execution asynchrone,
 * apres la reponse). Lot L6b (E13) : l'echec n'est plus silencieux. L'insertion a
 * echoue a chaque appel pendant des mois (adresse INET liee en VARCHAR) sans autre
 * signe qu'un avertissement d'une ligne : il est desormais journalise en ERROR avec
 * sa cause complete, et compte dans {@code jurika.dataroom.journal_acces.echecs}
 * (expose par /actuator/prometheus, donc surveillable et alertable). Le rendre a
 * la requete n'est pas possible (asynchrone) ni souhaitable (une consultation ne
 * doit pas echouer faute de trace). Utilise l'executor borne {@code clientAccessLogExecutor}
 * (cf {@link ClientAccessLogAsyncConfig}) plutot que le SimpleAsyncTaskExecutor
 * implicite (thread non borne par requete).
 */
@Component
public class ClientAccessLogDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ClientAccessLogDispatcher.class);

    /** Compteur des traces perdues (une par echec d'insertion). */
    public static final String METRIQUE_ECHECS = "jurika.dataroom.journal_acces.echecs";

    private final ClientAccessLogWriter writer;
    private final io.micrometer.core.instrument.MeterRegistry metriques;

    public ClientAccessLogDispatcher(ClientAccessLogWriter writer,
                                     io.micrometer.core.instrument.MeterRegistry metriques) {
        this.writer = writer;
        this.metriques = metriques;
    }

    @Async("clientAccessLogExecutor")
    public void persistAsync(ClientAccessLogEntity entity) {
        UUID prev = TenantContext.get();
        try {
            TenantContext.set(entity.getWorkspaceId());
            writer.persist(entity);
        } catch (Exception ex) {
            metriques.counter(METRIQUE_ECHECS, "action", String.valueOf(entity.getAction())).increment();
            log.error("ClientAccessLogDispatcher : trace d'acces client PERDUE, echec insert {} dossier={} "
                            + "workspace={} user={}", entity.getAction(), entity.getDossierId(),
                    entity.getWorkspaceId(), entity.getUserId(), ex);
        } finally {
            TenantContext.set(prev);
        }
    }
}
