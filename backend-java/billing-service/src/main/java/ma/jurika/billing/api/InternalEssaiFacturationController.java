package ma.jurika.billing.api;

import ma.jurika.billing.application.essai.SuppressionFacturationEssaiService;
import ma.jurika.billing.application.essai.SuppressionFacturationEssaiService.Bilan;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Lot L7 (E14), RG-ESS-10 : route INTERNE appelee par auth-service avant la suppression
 * definitive d'un essai (avant les objets MinIO et les lignes de jurika_db). Non routee par la
 * passerelle ({@code /internal/**} refuse a l'entree publique) ; les garde-fous metier sont dans
 * le service et dans la fonction de la base. Reponse : ce qui a ete supprime et conserve.
 */
@RestController
public class InternalEssaiFacturationController {

    private final SuppressionFacturationEssaiService service;

    public InternalEssaiFacturationController(SuppressionFacturationEssaiService service) {
        this.service = service;
    }

    @DeleteMapping("/internal/essais/{workspaceId}/paiements")
    public Bilan supprimer(@PathVariable UUID workspaceId) {
        return service.supprimer(workspaceId);
    }
}
