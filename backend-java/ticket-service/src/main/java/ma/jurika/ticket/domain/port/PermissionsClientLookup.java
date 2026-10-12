package ma.jurika.ticket.domain.port;

import java.util.UUID;

/**
 * Lot L1 : permissions du client sur un dossier (dataroom_settings, table partagee de
 * jurika_db ; RG-CLI-01). Sans reglage, les valeurs par defaut des colonnes s'appliquent
 * (consultation et telechargement permis).
 */
public interface PermissionsClientLookup {

    boolean consultationPermise(UUID workspaceId, UUID dossierId);

    /**
     * Lot L5 (E6, RG-DEB-03) : telechargement des documents (perm_download, dataroom V6),
     * qui suppose la consultation et un acces actif.
     */
    boolean telechargementPermis(UUID workspaceId, UUID dossierId);
}
