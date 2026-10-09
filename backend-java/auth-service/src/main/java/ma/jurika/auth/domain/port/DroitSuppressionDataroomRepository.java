package ma.jurika.auth.domain.port;

import java.util.UUID;

/** Lot L1 : droit de suppression en Data Room d'un employe (colonne users.droit_suppression_dataroom, V35). */
public interface DroitSuppressionDataroomRepository {

    /** Valeur actuelle (false si le compte est inconnu dans ce workspace). */
    boolean lire(UUID workspaceId, UUID userId);

    void definir(UUID workspaceId, UUID userId, boolean accorde);
}
