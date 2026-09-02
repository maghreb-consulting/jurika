package ma.jurika.auth.domain.port;

/**
 * Limiteur applicatif pour {@code POST /auth/verify-recovery-code} (RG-AU42).
 * Sliding window : N tentatives autorisees par fenetre, scope par cle composite
 * {@code workspaceCode + email + ip}.
 *
 * <p>Defaut JURIKA : 5 tentatives / 15 min. Au-dela : {@code TooManyRequestsException}.
 *
 * <p>Le rate limit gateway (10 req/min/IP) reste actif en amont et bloque les
 * floods naifs. Ce limiteur in-service capture les attaques distribues qui
 * varient l'IP mais ciblent un meme compte (credential stuffing par recovery).
 */
public interface RecoveryCodeRateLimiter {

    /**
     * Verifie qu'on est encore sous le quota pour {@code key}. Doit etre appele
     * AVANT toute tentative de match. Si {@code false}, la couche application
     * doit lever {@link ma.jurika.common.exception.TooManyRequestsException}.
     *
     * @param key cle de bucket (par ex. {@code "JUR-XXXXX:user@example.com:1.2.3.4"})
     * @return {@code true} si la requete peut continuer, {@code false} si quota atteint
     */
    boolean tryAcquire(String key);

    /**
     * Enregistre un echec : incremente le compteur et ouvre/prolonge la fenetre.
     * A appeler apres l'echec de match d'un code (RG-AU41).
     */
    void recordFailure(String key);

    /**
     * Remet le compteur a zero pour {@code key}. A appeler apres un succes de
     * verification (le user a prouve qu'il est legit).
     */
    void reset(String key);
}
