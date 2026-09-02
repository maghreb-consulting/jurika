package ma.jurika.auth.domain.model;

/**
 * BUG 6 (2026-06-07) — Statut d'un compte utilisateur. Source de verite en DB
 * (colonne {@code users.status}), backed par la migration V27.
 *
 * <ul>
 *   <li>{@link #PENDING}  : invite (employe / client) qui n'a jamais valide
 *       son 1er login. Transition automatique vers {@link #ACTIVE} dans
 *       {@code LoginUseCase} apres authentification reussie.</li>
 *   <li>{@link #ACTIVE}   : compte fonctionnel, autorise a se connecter.</li>
 *   <li>{@link #INACTIVE} : desactive explicitement par le superviseur. Login
 *       refuse (401), le compte ne consomme plus de quota plan.</li>
 * </ul>
 */
public enum UserStatus {
    PENDING,
    ACTIVE,
    INACTIVE;

    public static UserStatus fromString(String s) {
        if (s == null || s.isBlank()) return ACTIVE;
        try {
            return UserStatus.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ACTIVE;
        }
    }
}
