package ma.jurika.auth.domain.model;

import ma.jurika.common.security.Role;

import java.time.Instant;
import java.util.UUID;

public final class User {

    private final UUID id;
    private final UUID workspaceId;
    private final String email;
    /** BUG 7 (2026-06-08) — identifiant de connexion stable (format {@code prenom.nom@jurika.ma}). */
    private final String loginEmail;
    /** BUG 7 (2026-06-08) — email de contact pour les notifications (perso ou pro). */
    private final String contactEmail;
    private final String passwordHash;
    private final String firstName;
    private final String lastName;
    private final String phone;
    private final Role role;
    private final String totpSecretEncrypted;
    private final boolean totpEnabled;
    private final boolean mustChangePassword;
    private final String twofaMethod;
    private final Instant emailVerifiedAt;
    private final Instant phoneVerifiedAt;
    private final String phoneE164;
    private final short failedLoginAttempts;
    private final Instant lockedUntil;
    private final Instant lastLoginAt;
    private final UserStatus status;
    private final Instant createdAt;

    /**
     * Constructeur historique (avant BUG 7). Conserve pour ne pas casser les
     * call-sites de tests et de code legacy qui ne connaissent que {@code email}.
     * En interne : {@code loginEmail = contactEmail = email} (1:1 avec l'ancien
     * comportement). Les nouveaux call-sites doivent utiliser le constructeur
     * complet ci-dessous pour distinguer les deux roles.
     */
    public User(UUID id, UUID workspaceId, String email, String passwordHash,
                String firstName, String lastName, String phone, Role role,
                String totpSecretEncrypted, boolean totpEnabled, boolean mustChangePassword,
                String twofaMethod, Instant emailVerifiedAt, Instant phoneVerifiedAt, String phoneE164,
                short failedLoginAttempts, Instant lockedUntil, Instant lastLoginAt,
                UserStatus status, Instant createdAt) {
        this(id, workspaceId, email, email, email, passwordHash,
                firstName, lastName, phone, role,
                totpSecretEncrypted, totpEnabled, mustChangePassword, twofaMethod,
                emailVerifiedAt, phoneVerifiedAt, phoneE164,
                failedLoginAttempts, lockedUntil, lastLoginAt, status, createdAt);
    }

    /** BUG 7 — constructeur complet avec login_email + contact_email distincts. */
    public User(UUID id, UUID workspaceId, String email, String loginEmail, String contactEmail,
                String passwordHash, String firstName, String lastName, String phone, Role role,
                String totpSecretEncrypted, boolean totpEnabled, boolean mustChangePassword,
                String twofaMethod, Instant emailVerifiedAt, Instant phoneVerifiedAt, String phoneE164,
                short failedLoginAttempts, Instant lockedUntil, Instant lastLoginAt,
                UserStatus status, Instant createdAt) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.email = email;
        this.loginEmail = loginEmail != null ? loginEmail : email;
        this.contactEmail = contactEmail != null ? contactEmail : email;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.role = role;
        this.totpSecretEncrypted = totpSecretEncrypted;
        this.totpEnabled = totpEnabled;
        this.mustChangePassword = mustChangePassword;
        this.twofaMethod = twofaMethod;
        this.emailVerifiedAt = emailVerifiedAt;
        this.phoneVerifiedAt = phoneVerifiedAt;
        this.phoneE164 = phoneE164;
        this.failedLoginAttempts = failedLoginAttempts;
        this.lockedUntil = lockedUntil;
        this.lastLoginAt = lastLoginAt;
        this.status = status == null ? UserStatus.ACTIVE : status;
        this.createdAt = createdAt;
    }

    public UUID id() { return id; }
    public UUID workspaceId() { return workspaceId; }
    /** Email historique (= login_email en V1, garde pour retro-compat lectures). */
    public String email() { return email; }
    /** BUG 7 — identifiant utilise pour l'authentification ({@code prenom.nom@jurika.ma}). */
    public String loginEmail() { return loginEmail; }
    /** BUG 7 — email destinataire des notifications (peut etre modifie sans casser le login). */
    public String contactEmail() { return contactEmail; }
    public String passwordHash() { return passwordHash; }
    public String firstName() { return firstName; }
    public String lastName() { return lastName; }
    public String phone() { return phone; }
    public Role role() { return role; }
    public String totpSecretEncrypted() { return totpSecretEncrypted; }
    public boolean totpEnabled() { return totpEnabled; }
    public boolean mustChangePassword() { return mustChangePassword; }
    public String twofaMethod() { return twofaMethod; }
    public Instant emailVerifiedAt() { return emailVerifiedAt; }
    public boolean isEmailVerified() { return emailVerifiedAt != null; }
    public Instant phoneVerifiedAt() { return phoneVerifiedAt; }
    public boolean isPhoneVerified() { return phoneVerifiedAt != null; }
    public String phoneE164() { return phoneE164; }
    public short failedLoginAttempts() { return failedLoginAttempts; }
    public Instant lockedUntil() { return lockedUntil; }
    public Instant lastLoginAt() { return lastLoginAt; }
    public UserStatus status() { return status; }
    /** BUG 6 — {@code active()} reste l'API existante consommee par
     *  LoginUseCase + autres : derive du statut (PENDING + ACTIVE -> autorise,
     *  INACTIVE -> bloque). */
    public boolean active() { return status != UserStatus.INACTIVE; }
    public Instant createdAt() { return createdAt; }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    public boolean requires2faSetup() {
        return twofaMethod == null && !totpEnabled;
    }
}
