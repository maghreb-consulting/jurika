package ma.jurika.auth.domain.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Port pour les codes de recuperation 2FA.
 * 10 codes hashes BCrypt sont generes au moment de l'activation du 2FA et
 * affiches UNE SEULE FOIS au user. Chaque code est utilisable une seule fois.
 */
public interface RecoveryCodeRepository {

    /**
     * Remplace tous les codes existants par les nouveaux (regeneration totale).
     */
    void replaceAll(UUID workspaceId, UUID userId, List<String> codeHashes);

    /**
     * Liste les codes non encore utilises (pour verification ou recompte UI).
     */
    List<StoredRecoveryCode> findActive(UUID userId);

    /**
     * Marque un code comme utilise, de facon ATOMIQUE (lot L0, E10d) : la ligne
     * n'est modifiee que si le code n'est pas deja utilise. Renvoie {@code true}
     * si le code vient d'etre consomme par cet appel ; {@code false} s'il l'etait
     * deja (deux requetes simultanees avec le meme code : une seule gagne).
     */
    boolean markUsed(UUID id, Instant usedAt);

    /**
     * Supprime tous les codes d'un user (lors d'un reset 2FA par exemple).
     */
    void deleteAllForUser(UUID userId);

    record StoredRecoveryCode(
            UUID id,
            UUID workspaceId,
            UUID userId,
            String codeHash,
            Instant createdAt,
            Instant usedAt
    ) {
        public boolean isUsed() {
            return usedAt != null;
        }
    }
}
