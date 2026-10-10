package ma.jurika.ticket.domain.port;

import java.util.UUID;

/**
 * Lot L1 : permission de consultation du client sur un dossier (dataroom_settings,
 * dataroom V34 ; table partagee de jurika_db). Sans reglage, TRUE (valeur par defaut).
 */
public interface PermissionsClientLookup {

    boolean consultationPermise(UUID workspaceId, UUID dossierId);
}
