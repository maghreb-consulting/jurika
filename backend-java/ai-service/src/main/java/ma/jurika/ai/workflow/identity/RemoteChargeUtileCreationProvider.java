package ma.jurika.ai.workflow.identity;

import feign.FeignException;
import ma.jurika.common.exception.NotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Lot L3 : charge utile de la creation lue chez workflow-service. Aucune erreur n'est
 * avalee : ticket inconnu (404), employe qui n'est pas en charge (403), service
 * indisponible (503, message clair) -- jamais une generation sur une charge vide.
 */
@Component
public class RemoteChargeUtileCreationProvider implements ChargeUtileCreationProvider {

    private final WorkflowIdentityClient client;

    public RemoteChargeUtileCreationProvider(WorkflowIdentityClient client) {
        this.client = client;
    }

    @Override
    public Map<String, Object> charger(UUID workspaceId, UUID ticketId, UUID employeId) {
        try {
            Map<String, Object> charge = client.chargeUtileCreation(ticketId, workspaceId, employeId);
            if (charge == null || charge.isEmpty()) {
                throw new IdentiteSocieteIndisponibleException("Charge utile vide pour le ticket " + ticketId, null);
            }
            return charge;
        } catch (FeignException.NotFound e) {
            throw new NotFoundException("Ticket inconnu");
        } catch (FeignException.Forbidden e) {
            throw new AccessDeniedException("Seul l\u2019employ\u00e9 en charge du ticket g\u00e9n\u00e8re ses documents.");
        } catch (FeignException e) {
            throw new IdentiteSocieteIndisponibleException(
                    "Les donn\u00e9es du ticket n\u2019ont pas pu \u00eatre lues (ticket " + ticketId + ")", e);
        }
    }

    @Override
    public Map<String, Object> chargerAnnulation(UUID workspaceId, UUID ticketId, UUID employeId) {
        try {
            Map<String, Object> charge = client.chargeUtileAnnulation(ticketId, workspaceId, employeId);
            if (charge == null || charge.isEmpty()) {
                throw new IdentiteSocieteIndisponibleException("Charge d\u2019annulation vide pour le ticket " + ticketId, null);
            }
            return charge;
        } catch (FeignException.NotFound e) {
            throw new NotFoundException("Ticket inconnu");
        } catch (FeignException.Forbidden e) {
            throw new AccessDeniedException("Seul l\u2019employ\u00e9 en charge du ticket g\u00e9n\u00e8re ses documents.");
        } catch (FeignException e) {
            throw new IdentiteSocieteIndisponibleException(
                    "Les donn\u00e9es de l\u2019annulation n\u2019ont pas pu \u00eatre lues (ticket " + ticketId + ")", e);
        }
    }

}
