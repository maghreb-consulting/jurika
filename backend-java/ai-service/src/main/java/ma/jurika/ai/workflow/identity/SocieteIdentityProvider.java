package ma.jurika.ai.workflow.identity;

import java.util.Map;
import java.util.UUID;

/**
 * Port (hexagonal) : fournit l'identité société <b>complète</b> d'un dossier depuis la
 * source de vérité BD (capital, siège, RC, ville du greffe, ICE, IF, nombre de parts,
 * valeur nominale, associés, gérants).
 *
 * <p>ai-service ne lit aucune base : l'implémentation par défaut délègue à workflow-service
 * ({@link RemoteSocieteIdentityProvider}). Best-effort par contrat — une indisponibilité
 * renvoie une map vide, jamais une exception, pour que la génération reste possible en
 * mode dégradé. Un stub in-memory est trivial à fournir en test.
 */
public interface SocieteIdentityProvider {

    /**
     * Charge l'identité société d'un dossier (clés alignées sur {@code SeancePvVarsBuilder}).
     * Renvoie {@link Map#of()} si l'identité est indisponible / introuvable.
     */
    Map<String, Object> loadIdentity(UUID workspaceId, UUID dossierId);
}
