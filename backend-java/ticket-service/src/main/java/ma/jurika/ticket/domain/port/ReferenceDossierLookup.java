package ma.jurika.ticket.domain.port;

import java.util.UUID;

/**
 * Lot L5 (E6) : reference lisible d'un dossier (annee, numero, societe ; ticket V38 et
 * {@code ReferencesLisibles.dossier}), ou null si une de ses donnees manque.
 */
public interface ReferenceDossierLookup {

    String reference(UUID workspaceId, UUID dossierId);
}
