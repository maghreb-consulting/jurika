package ma.jurika.common.security;

public final class JwtClaims {

    public static final String WORKSPACE_ID = "wsid";
    public static final String USER_ID = "uid";
    public static final String ROLE = "role";
    /** Historique : claim {@code email} = login_email (compatibilite filtres
     *  preexistants qui comparent sur user.email). Sera deprecie quand tous
     *  les call-sites consommeront {@link #LOGIN_EMAIL}. */
    public static final String EMAIL = "email";
    /** BUG 7 (2026-06-08) — identifiant de connexion ({@code prenom.nom@jurika.ma}). */
    public static final String LOGIN_EMAIL = "login_email";
    /** BUG 7 (2026-06-08) — email destinataire des notifications. */
    public static final String CONTACT_EMAIL = "contact_email";
    public static final String TYPE = "typ";
    public static final String MUST_CHANGE_PASSWORD = "mcp";
    /** CRIT-2 (audit auth) : 2FA non encore configure -> tous les endpoints non whiteliste 403. */
    public static final String REQUIRES_2FA_SETUP = "r2s";

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    public static final String REQUEST_ATTR_MUST_CHANGE_PASSWORD = "ma.jurika.mustChangePassword";
    public static final String REQUEST_ATTR_REQUIRES_2FA_SETUP = "ma.jurika.requires2faSetup";

    public static final String HEADER_WORKSPACE_ID = "X-Workspace-Id";
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_ROLE = "X-User-Role";
    public static final String HEADER_EMAIL = "X-User-Email";

    private JwtClaims() {}
}
