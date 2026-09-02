package ma.jurika.auth.domain.port;

/**
 * Sprint 11 — Rate limiter générique par adresse IP (ou clé arbitraire).
 *
 * Distinct du {@link RecoveryCodeRateLimiter} (sémantique "échecs successifs sur
 * un compte cible") : ici on borne le nombre de requêtes initiées par une même
 * IP pendant une fenêtre, peu importe leur succès/échec.
 *
 * Implémentations attendues :
 *  - {@code InMemoryIpRateLimiter} (Sprint 11, hypothèse 1 replica)
 *  - {@code RedisIpRateLimiter} (Sprint futur quand scale-out >1)
 *
 * La signature ne change pas lors de la migration.
 */
public interface IpRateLimiter {

    /**
     * Tente de "consommer" un crédit pour la clé donnée dans le bucket nommé.
     *
     * @param bucket  identifiant logique du quota (ex: "demo-request", "signup-cabinet", "public-events")
     * @param key     clé à limiter (typiquement l'IP, mais peut être email/UA composite)
     * @return {@code true} si le crédit est consommé et l'action peut procéder,
     *         {@code false} si la limite est atteinte (renvoyer HTTP 429).
     */
    boolean tryAcquire(String bucket, String key);
}
