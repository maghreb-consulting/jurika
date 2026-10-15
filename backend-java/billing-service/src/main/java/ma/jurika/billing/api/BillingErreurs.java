package ma.jurika.billing.api;

import ma.jurika.billing.application.RefusNommeException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lot L7 (E5) : refus nommes de la facturation (422, avec la liste de ce qui manque ou depasse) et
 * argument invalide (400, message affichable). Prioritaire sur le gestionnaire commun.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BillingErreurs {

    @ExceptionHandler(RefusNommeException.class)
    public ResponseEntity<Map<String, Object>> refusNomme(RefusNommeException ex) {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("code", ex.code());
        corps.put("message", ex.getMessage());
        corps.put("elements", ex.elements());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(corps);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> argument(IllegalArgumentException ex) {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("code", "REQUETE_INVALIDE");
        corps.put("message", ex.getMessage());
        corps.put("elements", List.of());
        return ResponseEntity.badRequest().body(corps);
    }
}
