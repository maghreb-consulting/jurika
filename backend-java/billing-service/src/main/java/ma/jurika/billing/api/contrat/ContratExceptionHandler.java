package ma.jurika.billing.api.contrat;

import ma.jurika.billing.application.contrat.ContratException;
import ma.jurika.common.dto.ErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Lot L7 (E6) : refus nommes du contrat (code stable, message, donnees manquantes). Prioritaire
 * sur le gestionnaire general, qui traiterait l'exception comme une erreur interne.
 */
@RestControllerAdvice(basePackageClasses = ContratExceptionHandler.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ContratExceptionHandler {

    @ExceptionHandler(ContratException.class)
    public ResponseEntity<ErrorResponse> refus(ContratException ex) {
        return ResponseEntity.status(ex.statut()).body(new ErrorResponse(ex.code(), ex.getMessage(),
                ex.donnees().stream().map(d -> new ErrorResponse.FieldError("donnee", d)).toList()));
    }
}
