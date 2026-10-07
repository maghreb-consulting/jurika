package ma.jurika.dataroom.application.access;

import ma.jurika.dataroom.infrastructure.persistence.ClientAccessLogEntity;
import ma.jurika.dataroom.infrastructure.persistence.ClientAccessLogJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2026-07-01 -- Ecriture transactionnelle du log d'acces client.
 *
 * <p>Pourquoi une classe dediee ? {@link ClientAccessLogger#log} s'execute sur
 * un thread {@code @Async} et re-positionne le {@code TenantContext} ; cette
 * methode {@code @Transactional} ouvre ensuite la transaction, et le
 * gestionnaire de transactions de jurika-common (TenantAwareJpaTransactionManager,
 * lot L0) y pose {@code app.current_workspace_id} : l'insertion reste fiable
 * sous RLS stricte. (Avant L0, l'ancien RlsAspect s'executait hors de la
 * transaction et l'INSERT ne fonctionnait que grace au superutilisateur.)
 * L'appel reste best-effort : {@link ClientAccessLogger} avale toute exception.
 */
@Component
public class ClientAccessLogWriter {

    private final ClientAccessLogJpaRepository repo;

    public ClientAccessLogWriter(ClientAccessLogJpaRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void persist(ClientAccessLogEntity entity) {
        repo.save(entity);
    }
}
