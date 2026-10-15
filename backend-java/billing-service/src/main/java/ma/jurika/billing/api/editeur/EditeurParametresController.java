package ma.jurika.billing.api.editeur;

import ma.jurika.billing.application.editeur.EditeurParametresService;
import ma.jurika.billing.application.editeur.EditeurParametresService.EditeurException;
import ma.jurika.common.dto.ErrorResponse;
import ma.jurika.common.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Lot L7 (E6) : parametres de l'editeur (identite de Maghreb Consulting, taux de TVA).
 * Reserve au super-admin (P6 : controle cote serveur) ; chaque modification est tracee
 * (declencheur billing V14) et la trace est consultable.
 */
@RestController
@RequestMapping("/api/v1/billing/admin/editeur")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class EditeurParametresController {

    private final EditeurParametresService service;

    public EditeurParametresController(EditeurParametresService service) {
        this.service = service;
    }

    @GetMapping
    public EditeurParametresService.Vue lire() {
        return service.lire();
    }

    @PutMapping
    public EditeurParametresService.Vue modifier(@RequestBody EditeurParametresService.Commande commande,
                                                 @AuthenticationPrincipal AuthenticatedUser user) {
        return service.modifier(commande, user.userId());
    }

    @GetMapping("/historique")
    public List<EditeurParametresService.Trace> historique() {
        return service.historique();
    }

    @ExceptionHandler(EditeurException.class)
    public ResponseEntity<ErrorResponse> refus(EditeurException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse(ex.code(), ex.getMessage()));
    }
}
