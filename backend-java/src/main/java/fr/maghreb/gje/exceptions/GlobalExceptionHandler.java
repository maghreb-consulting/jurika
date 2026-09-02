package fr.maghreb.gje.exceptions;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(AccessDeniedException ex) {
        return ResponseEntity.status(403)
            .body(Map.of("error", "Super Admin n'a pas accès aux dossiers internes des workspaces"));
    }

    @ExceptionHandler(fr.maghreb.gje.exceptions.QuotaExceededException.class)
    public ResponseEntity<Map<String, Object>> handleQuotaExceeded(fr.maghreb.gje.exceptions.QuotaExceededException ex) {
        return ResponseEntity.status(422)
            .body(Map.of(
                "error", "quota_exceeded",
                "type", ex.getType(),
                "used", ex.getUsed(),
                "limit", ex.getLimit()
            ));
    }
}
