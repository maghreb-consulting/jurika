package ma.jurika.auth.domain.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Port pour la persistence des tokens de verification d'email.
 * <p>
 * Le {@code rawToken} (UUID) est genere cote service, envoye au user par email.
 * Le repository stocke le hash SHA-256 (pas le clair) et la {@code expiresAt}.
 */
public interface EmailVerificationTokenRepository {

    /**
     * Cree un nouveau token. Retourne l'identifiant DB (UUID).
     */
    UUID create(UUID workspaceId, UUID userId, String email, String tokenHash,
                String purpose, Instant expiresAt, String ipAddress, String userAgent);

    /**
     * Recherche un token par son hash + purpose. Renvoie l'enregistrement complet.
     */
    Optional<StoredToken> findByHash(String tokenHash);

    /**
     * Lot L0 (E12b) : usage unique ATOMIQUE. Ne modifie la ligne que si le jeton
     * n'est pas deja utilise ; renvoie {@code true} si cet appel l'a consomme,
     * {@code false} sinon (rejeu, ou requete concurrente gagnee par une autre).
     */
    boolean markUsed(UUID id, Instant usedAt);

    /**
     * Lot L5 (E10) : retire les jetons encore ouverts d'un utilisateur pour un
     * motif (nouvelle demande, ou demande annulee) ; ils deviennent inutilisables.
     * Renvoie le nombre de jetons retires.
     */
    int revokePending(UUID userId, String purpose, Instant when);

    /**
     * Lot L5 (E10) : demande en attente de confirmation (jeton non utilise, non
     * expire) la plus recente d'un utilisateur pour un motif.
     */
    Optional<StoredToken> findPending(UUID userId, String purpose, Instant now);

    record StoredToken(
            UUID id,
            UUID workspaceId,
            UUID userId,
            String email,
            String purpose,
            Instant expiresAt,
            Instant usedAt
    ) {
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }

        public boolean isUsed() {
            return usedAt != null;
        }
    }
}
