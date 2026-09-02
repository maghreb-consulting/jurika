package ma.jurika.common.exception;

/**
 * 429 Too Many Requests. Leve quand un caller depasse le quota d'une operation
 * sensible cote service (rate limit applicatif, distinct du rate limit gateway).
 */
public class TooManyRequestsException extends RuntimeException {

    private final String code;

    public TooManyRequestsException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
