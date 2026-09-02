package ma.jurika.dataroom.application.access;

import ma.jurika.dataroom.infrastructure.persistence.ClientAccessLogEntity;
import ma.jurika.dataroom.infrastructure.persistence.ClientAccessLogJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2026-07-01 -- Ecriture transactionnelle du log d'acces client.
 *
 * <p>Pourquoi une classe dediee ? {@link ClientAccessLogger#log} s'execute sur
 * un thread {@code @Async} et re-positionne le {@code TenantContext}, mais un
 * {@code repo.save(...)} appele directement n'est PAS intercepte par le
 * {@code RlsAspect} (son pointcut ne matche que
 * {@code @Transactional && execution(* ma.jurika..*(..))}). Resultat : la
 * variable de session {@code app.current_workspace_id} n'etait pas positionnee,
 * et l'INSERT ne fonctionnait que grace au {@code BYPASSRLS} du conteneur dev.
 *
 * <p>En passant par cette methode {@code @Transactional} d'un bean
 * {@code ma.jurika..}, le {@code RlsAspect} emet le {@code set_config(...)} et
 * l'insertion reste fiable meme sous RLS stricte (prod). L'appel reste
 * best-effort : {@link ClientAccessLogger} avale toute exception.
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
