package ma.jurika.common.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.validation.ConstraintViolationException;
import ma.jurika.common.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(ex.code(), ex.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(ex.code(), ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(ex.code(), ex.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("BAD_CREDENTIALS", "Identifiants invalides"));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(ex.code(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<ErrorResponse.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_ERROR", "Donnees invalides", errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("FORBIDDEN", "Acces refuse"));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(ex.code(), ex.getMessage()));
    }

    /**
     * Sprint Beta (pricing-deploy) — quota plan depasse. HTTP 402 Payment
     * Required, semantique identique au soft-lock trial (RG-SAAS-07).
     */
    @ExceptionHandler(PlanLimitException.class)
    public ResponseEntity<ErrorResponse> handlePlanLimit(PlanLimitException ex) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(new ErrorResponse(ex.code(), ex.getMessage()));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooMany(TooManyRequestsException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new ErrorResponse(ex.code(), ex.getMessage()));
    }

    /**
     * 2026-06-02 : route inconnue → 404 silencieux (etait mappee en 500 + log ERROR
     * par {@link #handleGeneric}, ce qui polluait les logs avec des stack traces de
     * 100 lignes sur chaque requete /api/v1/public/events arrivant accidentellement
     * sur auth-service au lieu du gateway). Log au niveau DEBUG seulement.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        log.debug("404 route inconnue : {}", ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("NOT_FOUND", "Ressource introuvable"));
    }

    /**
     * 2026-06-04 : payload JSON mal forme / enum invalide → 400 Bad Request
     * avec un message explicite, plutot que 500 INTERNAL_ERROR opaque
     * (fix bug A boucle stabilisation). Couvre :
     *  - InvalidFormatException (enum non reconnu, format date invalide, …)
     *  - JSON mal formee (parse error)
     *  - missing required field detecte cote Jackson
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();
        String message;
        if (cause instanceof InvalidFormatException ife) {
            String field = ife.getPath().isEmpty() ? "?" : ife.getPath().get(ife.getPath().size() - 1).getFieldName();
            Class<?> targetType = ife.getTargetType();
            Object value = ife.getValue();
            if (targetType != null && targetType.isEnum()) {
                message = "Valeur invalide pour le champ '" + field + "' : \"" + value
                        + "\". Valeurs acceptees : "
                        + java.util.Arrays.toString(targetType.getEnumConstants());
            } else {
                message = "Format invalide pour le champ '" + field + "' : \"" + value
                        + "\" (attendu : " + (targetType != null ? targetType.getSimpleName() : "?") + ")";
            }
        } else {
            String rawMsg = ex.getMostSpecificCause() != null
                    ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
            message = "Requete JSON invalide : " + (rawMsg == null ? "format non reconnu" : rawMsg);
        }
        log.debug("400 JSON invalide : {}", message);
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("BAD_REQUEST", message));
    }

    /**
     * 2026-06-04 : conversion impossible d'un parametre query/path (ex enum invalide
     * dans une path variable) → 400 Bad Request explicite plutot que 500.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        Class<?> required = ex.getRequiredType();
        String message = "Parametre '" + ex.getName() + "' invalide : \"" + ex.getValue() + "\""
                + (required != null && required.isEnum()
                    ? " (valeurs acceptees : " + java.util.Arrays.toString(required.getEnumConstants()) + ")"
                    : (required != null ? " (attendu : " + required.getSimpleName() + ")" : ""));
        log.debug("400 parametre invalide : {}", message);
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("BAD_REQUEST", message));
    }

    /**
     * 2026-09-08 — UNE EXCEPTION QUI PORTE SON STATUT LE GARDE.
     *
     * <p><b>Le defaut.</b> Ce {@code @RestControllerAdvice} declare un
     * {@code @ExceptionHandler(Exception.class)}. Un handler d'advice prime sur la
     * gestion native de Spring MVC : TOUT ce que Spring aurait traduit en 4xx
     * tombait donc ici, et sortait en 500 « Erreur interne du serveur », avec une
     * pile d'appels journalisee en ERROR.
     *
     * <p><b>Mesure sur la pile qui tourne</b>, avant correction :
     * <pre>
     *   attendu 404 -> recu 500   DELETE /api/v1/chatbot/sources/{inconnu}
     *   attendu 405 -> recu 500   mauvaise methode HTTP
     *   attendu 415 -> recu 500   mauvais Content-Type
     * </pre>
     *
     * <p>Deux familles etaient touchees :
     * <ul>
     *   <li>les {@code ResponseStatusException} levees par les controleurs du
     *       projet — 14 occurrences dans 5 controleurs de 3 services (ai,
     *       auth, dataroom), visant 400, 401, 404, 422 et 503 ;</li>
     *   <li>les exceptions de Spring MVC elles-memes, qui n'ont pourtant aucun
     *       {@code throw} dans le projet : 405 methode non supportee, 415 type de
     *       contenu refuse, 406 non acceptable, 400 parametre manquant, 413 upload
     *       trop volumineux — la Data Room a justement une limite a 15 Mo —, 503
     *       delai asynchrone depasse.</li>
     * </ul>
     *
     * <p><b>Le correctif, a la source.</b> Depuis Spring 6, ces exceptions
     * implementent {@link ErrorResponseException}'s interface
     * {@code org.springframework.web.ErrorResponse}, qui EXPOSE le statut voulu.
     * On l'honore avant de retomber sur le 500 : une seule regle, pour les dix
     * services, plutot qu'un handler local a recopier dans chaque controleur.
     *
     * <p>Un 4xx n'est pas un incident serveur : il est journalise en WARN, sans
     * pile d'appels. Le 500 garde son ERROR et sa pile — c'est la, et la seule,
     * qu'il y a quelque chose a corriger.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        if (ex instanceof org.springframework.web.ErrorResponse porteur) {
            HttpStatus statut = HttpStatus.valueOf(porteur.getStatusCode().value());
            String message = messageDe(ex, statut);
            if (statut.is5xxServerError()) {
                log.error("{} {} : {}", statut.value(), statut.getReasonPhrase(), message, ex);
            } else {
                log.warn("{} {} : {}", statut.value(), statut.getReasonPhrase(), message);
            }
            return ResponseEntity.status(statut)
                    .body(new ErrorResponse(statut.name(), message));
        }
        log.error("Erreur interne non geree", ex);
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("INTERNAL_ERROR", "Erreur interne du serveur"));
    }

    /**
     * Message a rendre au client. {@code ResponseStatusException} porte son motif
     * dans {@code getReason()} ; les exceptions de Spring MVC, elles, portent un
     * message deja formule ({@code "Request method 'PATCH' is not supported"}).
     * A defaut, on ne renvoie que la phrase du statut plutot qu'un
     * {@code getMessage()} technique.
     */
    private static String messageDe(Exception ex, HttpStatus statut) {
        if (ex instanceof org.springframework.web.server.ResponseStatusException rse) {
            return rse.getReason() != null ? rse.getReason() : statut.getReasonPhrase();
        }
        String m = ex.getMessage();
        return m == null || m.isBlank() ? statut.getReasonPhrase() : m;
    }
}
