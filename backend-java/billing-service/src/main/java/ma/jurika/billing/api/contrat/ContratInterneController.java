package ma.jurika.billing.api.contrat;

import ma.jurika.billing.application.contrat.ContratAbonnementPort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Lot L7 (E6, RG-CTR-03) : controle de la passerelle. Elle demande, pour chaque requete d'un
 * superviseur ou d'un employe, si le cabinet a un contrat en attente d'acceptation ; si oui,
 * seules les routes du contrat et de la session passent.
 *
 * <p>Chemin {@code /internal/**} : appel entre services sur le reseau interne, refuse a
 * l'entree publique par la passerelle (InternalPathBlockingFilter). Reponse minimale (un
 * booleen) : rien d'autre ne sort.
 */
@RestController
@RequestMapping("/internal/contrat")
public class ContratInterneController {

    private final ContratAbonnementPort port;

    public ContratInterneController(ContratAbonnementPort port) {
        this.port = port;
    }

    public record Acces(boolean enAttente) {}

    @GetMapping("/workspaces/{workspaceId}/acces")
    public Acces acces(@PathVariable UUID workspaceId) {
        return new Acces(port.acceptationEnAttente(workspaceId));
    }
}
