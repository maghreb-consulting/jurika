package ma.jurika.auth.domain.port;

import java.util.List;
import java.util.UUID;

/**
 * Lot L6 (E6, CDC 15.4) : aides masquees par un utilisateur (table
 * {@code aides_masquees}, auth V39). Le workspace courant (RLS) doit etre
 * {@code workspaceId} ; chaque requete filtre aussi explicitement sur
 * {@code user_id} et {@code workspace_id}.
 */
public interface AidesMasqueesRegistre {

    /** Cles des aides masquees par l'utilisateur, triees. */
    List<String> cles(UUID userId, UUID workspaceId);

    /** Nombre d'aides masquees par l'utilisateur. */
    int compter(UUID userId, UUID workspaceId);

    /** Masque une aide ; sans effet si elle l'est deja. */
    void masquer(UUID userId, UUID workspaceId, String cle);

    /** Reaffiche une aide ; sans effet si elle ne l'etait pas. */
    void reafficher(UUID userId, UUID workspaceId, String cle);

    /** Reaffiche toutes les aides de l'utilisateur. */
    void reafficherTout(UUID userId, UUID workspaceId);
}
