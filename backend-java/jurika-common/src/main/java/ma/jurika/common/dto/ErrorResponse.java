package ma.jurika.common.dto;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        Instant timestamp,
        List<FieldError> errors
) {
    public ErrorResponse(String code, String message) {
        this(code, message, Instant.now(), List.of());
    }

    public ErrorResponse(String code, String message, List<FieldError> errors) {
        this(code, message, Instant.now(), errors);
    }

    public record FieldError(String field, String message) {}
}
