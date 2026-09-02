package ma.jurika.common.exception;

public class UnauthorizedException extends BusinessException {
    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", message);
    }

    /**
     * Variante avec code metier explicite, expose tel quel dans {@code ErrorResponse.code}.
     * Permet au frontend de distinguer les motifs de deconnexion (session revoquee par
     * un autre login, expiration pour inactivite, expiration normale) et d'afficher le
     * bon message. Voir {@code ma.jurika.auth.application.RefreshTokenUseCase}.
     */
    public UnauthorizedException(String code, String message) {
        super(code, message);
    }
}
