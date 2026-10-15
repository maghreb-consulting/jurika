package ma.jurika.dataroom.application;

import ma.jurika.common.exception.BusinessException;
import ma.jurika.common.security.TenantContext;
import ma.jurika.common.trial.TrialAccessChecker;
import ma.jurika.dataroom.domain.port.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Lot L7 (E4), RG-ESS-10 : suppression des objets MinIO d'un essai non souscrit,
 * au terme de la lecture seule (appel d'auth-service, juste avant la
 * suppression des lignes de la base).
 *
 * <p>Garde-fou propre a ce service : le cabinet doit etre un essai en lecture
 * seule (etat lu aupres d'auth-service) ; jamais un cabinet abonne. Objets
 * supprimes : tout ce qui est range sous {@code ws/{workspace}/}, plus les
 * cles enregistrees en base hors de ce prefixe, et leurs apercus PDF en cache.
 * Toute erreur remonte : auth-service ne supprime alors pas les lignes.
 */
@Service
public class PurgeObjetsEssaiService {

    private static final Logger log = LoggerFactory.getLogger(PurgeObjetsEssaiService.class);

    private final ObjectStorage storage;
    private final JdbcTemplate jdbc;
    private final ObjectProvider<TrialAccessChecker> checker;
    private final TransactionTemplate transaction;

    public PurgeObjetsEssaiService(ObjectStorage storage, JdbcTemplate jdbc, ObjectProvider<TrialAccessChecker> checker,
                                   PlatformTransactionManager transactions) {
        this.storage = storage;
        this.jdbc = jdbc;
        this.checker = checker;
        this.transaction = new TransactionTemplate(transactions);
        this.transaction.setReadOnly(true);
    }

    public int purger(UUID workspaceId) {
        TrialAccessChecker c = checker.getIfAvailable();
        if (c == null) {
            throw new IllegalStateException("Etat d'essai indisponible dans dataroom-service : purge refusee");
        }
        boolean lectureSeule = c.getState(workspaceId).map(TrialAccessChecker.TrialState::isExpired).orElse(false);
        if (!lectureSeule) {
            throw new BusinessException("ESSAI_PURGE_REFUSEE",
                    "Purge refus\u00e9e : ce cabinet n\u2019est pas un essai gratuit en lecture seule.");
        }
        Set<String> cles = new TreeSet<>(storage.lister("ws/" + workspaceId + "/"));
        TenantContext.set(workspaceId);
        try {
            List<String> enBase = transaction.execute(s -> jdbc.queryForList("""
                    SELECT object_key FROM dataroom_documents WHERE workspace_id = ?
                    UNION
                    SELECT object_key FROM dataroom_depots WHERE workspace_id = ?
                    """, String.class, workspaceId, workspaceId));
            if (enBase != null) {
                for (String k : enBase) {
                    cles.add(k);
                    cles.add(k + OfficePreviewSupport.PREVIEW_SUFFIX);
                }
            }
        } finally {
            TenantContext.clear();
        }
        for (String k : cles) {
            storage.delete(k);
        }
        log.warn("Essai {} : {} objet(s) MinIO supprime(s) definitivement", workspaceId, cles.size());
        return cles.size();
    }
}
