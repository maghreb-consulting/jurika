package ma.jurika.ai.workflow.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Implémentation par défaut du {@link SocieteIdentityProvider} : délègue à workflow-service
 * via {@link WorkflowIdentityClient}.
 *
 * <p><b>Best-effort strict</b> : toute erreur (workflow-service indisponible, timeout,
 * dossier introuvable) est avalée et renvoie une map vide. L'enrichissement de l'en-tête
 * PV ne doit jamais faire échouer la génération du document.
 */
@Component
public class RemoteSocieteIdentityProvider implements SocieteIdentityProvider {

    private static final Logger log = LoggerFactory.getLogger(RemoteSocieteIdentityProvider.class);

    private final WorkflowIdentityClient client;

    public RemoteSocieteIdentityProvider(WorkflowIdentityClient client) {
        this.client = client;
    }

    @Override
    public Map<String, Object> loadIdentity(UUID workspaceId, UUID dossierId) {
        if (workspaceId == null || dossierId == null) return Map.of();
        try {
            Map<String, Object> identity = client.identity(dossierId, workspaceId);
            return identity == null ? Map.of() : identity;
        } catch (Exception ex) {
            log.warn("Enrichissement identite societe indisponible (dossier={}) : {} — "
                    + "generation en mode degrade", dossierId, ex.getMessage());
            return Map.of();
        }
    }
}
