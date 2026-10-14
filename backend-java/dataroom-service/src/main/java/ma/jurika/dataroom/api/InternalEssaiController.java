package ma.jurika.dataroom.api;

import ma.jurika.dataroom.application.PurgeObjetsEssaiService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Lot L7 (E4), RG-ESS-10 : route INTERNE appelee par auth-service avant la
 * suppression definitive d'un essai. Non routee par la passerelle
 * ({@code /internal/**}) ; le garde-fou metier est dans le service.
 */
@RestController
public class InternalEssaiController {

    private final PurgeObjetsEssaiService purge;

    public InternalEssaiController(PurgeObjetsEssaiService purge) {
        this.purge = purge;
    }

    @DeleteMapping("/internal/essais/{workspaceId}/objets")
    public Map<String, Object> purger(@PathVariable UUID workspaceId) {
        return Map.of("objetsSupprimes", purge.purger(workspaceId));
    }
}
