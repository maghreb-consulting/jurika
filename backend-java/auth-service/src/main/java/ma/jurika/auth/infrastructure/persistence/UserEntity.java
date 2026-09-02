package ma.jurika.auth.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    private UUID id;
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;
    @Column(nullable = false, length = 150)
    private String email;
    /** BUG 7 (2026-06-08, V28) — identifiant de connexion (prenom.nom@jurika.ma). */
    @Column(name = "login_email", nullable = false, length = 150)
    private String loginEmail;
    /** BUG 7 (2026-06-08, V28) — email destinataire des notifications. */
    @Column(name = "contact_email", nullable = false, length = 150)
    private String contactEmail;
    @Column(name = "password_hash", nullable = false, length = 72)
    private String passwordHash;
    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;
    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;
    @Column(length = 30)
    private String phone;
    @Column(nullable = false, length = 20)
    private String role;
    @Column(name = "totp_secret_encrypted")
    private String totpSecretEncrypted;
    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled;
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;
    @Column(name = "failed_login_attempts", nullable = false)
    private short failedLoginAttempts;
    @Column(name = "locked_until")
    private Instant lockedUntil;
    @Column(name = "last_login_at")
    private Instant lastLoginAt;
    @Column(name = "is_active", nullable = false)
    private boolean active;
    // BUG 6 (V27, 2026-06-07) — statut explicite (PENDING / ACTIVE / INACTIVE).
    // Source de verite ; `is_active` est maintenue par trigger Postgres pour la
    // retro-compat des lectures legacy. Voir UserStatus.
    @Column(name = "status", nullable = false, length = 20)
    private String status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "twofa_method", length = 10)
    private String twofaMethod;
    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;
    @Column(name = "phone_verified_at")
    private Instant phoneVerifiedAt;
    @Column(name = "phone_e164", length = 20)
    private String phoneE164;

    @PrePersist
    void onPersist() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        // BUG 6 — `is_active` est maintenue par trigger DB depuis V27 ; on
        // assigne uniquement le statut a la creation, le trigger calcule le bool.
        if (status == null) status = "ACTIVE";
        active = !"INACTIVE".equals(status);
        // BUG 7 — defaut retro-compat : si le caller n'a pas set login_email
        // ou contact_email (parcours legacy), on les fait egaler email pour
        // satisfaire les NOT NULL de V28 sans changer le comportement.
        if (loginEmail == null || loginEmail.isBlank()) loginEmail = email;
        if (contactEmail == null || contactEmail.isBlank()) contactEmail = email;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(UUID workspaceId) { this.workspaceId = workspaceId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getLoginEmail() { return loginEmail; }
    public void setLoginEmail(String loginEmail) { this.loginEmail = loginEmail; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getTotpSecretEncrypted() { return totpSecretEncrypted; }
    public void setTotpSecretEncrypted(String s) { this.totpSecretEncrypted = s; }
    public boolean isTotpEnabled() { return totpEnabled; }
    public void setTotpEnabled(boolean totpEnabled) { this.totpEnabled = totpEnabled; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(boolean v) { this.mustChangePassword = v; }
    public short getFailedLoginAttempts() { return failedLoginAttempts; }
    public void setFailedLoginAttempts(short v) { this.failedLoginAttempts = v; }
    public Instant getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(Instant lockedUntil) { this.lockedUntil = lockedUntil; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public String getTwofaMethod() { return twofaMethod; }
    public void setTwofaMethod(String twofaMethod) { this.twofaMethod = twofaMethod; }
    public Instant getEmailVerifiedAt() { return emailVerifiedAt; }
    public void setEmailVerifiedAt(Instant emailVerifiedAt) { this.emailVerifiedAt = emailVerifiedAt; }
    public Instant getPhoneVerifiedAt() { return phoneVerifiedAt; }
    public void setPhoneVerifiedAt(Instant phoneVerifiedAt) { this.phoneVerifiedAt = phoneVerifiedAt; }
    public String getPhoneE164() { return phoneE164; }
    public void setPhoneE164(String phoneE164) { this.phoneE164 = phoneE164; }
}
