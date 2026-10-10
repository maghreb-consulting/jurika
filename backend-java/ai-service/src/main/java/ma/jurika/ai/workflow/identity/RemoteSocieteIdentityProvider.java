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
 * <p>Lot L3 (motif 9) : une erreur (workflow-service indisponible, timeout) n'est plus
 * avalee : elle leve {@link IdentiteSocieteIndisponibleException}. Un dossier introuvable
 * rend une map vide (le service le dit par une reponse vide, pas par une erreur).
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
            // Lot L3 (motif 9) : plus de « mode degrade » silencieux -- l'acte partait sans
            // les donnees de la societe. La generation echoue et le dit.
            log.error("Identite societe illisible (dossier={}) : {}", dossierId, ex.getMessage());
            throw new IdentiteSocieteIndisponibleException(
                    "Les donnees de la societe n'ont pas pu etre lues (dossier " + dossierId + ")", ex);
        }
    }
}
