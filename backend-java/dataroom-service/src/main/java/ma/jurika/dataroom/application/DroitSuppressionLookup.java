package ma.jurika.dataroom.application;

import java.util.UUID;

/**
 * Lot L1 : droit de suppression en Data Room accorde par le superviseur
 * (users.droit_suppression_dataroom, auth V35).
 */
public interface DroitSuppressionLookup {

    boolean aLeDroit(UUID workspaceId, UUID userId);
}
