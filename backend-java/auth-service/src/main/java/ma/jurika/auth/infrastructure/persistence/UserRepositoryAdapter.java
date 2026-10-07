package ma.jurika.auth.infrastructure.persistence;

import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.model.UserStatus;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.common.security.Role;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpa;

    public UserRepositoryAdapter(UserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<User> findByWorkspaceAndEmail(UUID workspaceId, String email) {
        return jpa.findByWorkspaceIdAndEmail(workspaceId, email.toLowerCase()).map(this::toDomain);
    }

    @Override
    public Optional<User> findByWorkspaceAndLoginEmail(UUID workspaceId, String loginEmail) {
        if (loginEmail == null || loginEmail.isBlank()) return Optional.empty();
        return jpa.findByWorkspaceIdAndLoginEmail(workspaceId, loginEmail.toLowerCase())
                .map(this::toDomain);
    }

    @Override
    public boolean existsByWorkspaceAndLoginEmail(UUID workspaceId, String loginEmail) {
        if (loginEmail == null || loginEmail.isBlank()) return false;
        return jpa.existsByWorkspaceIdAndLoginEmail(workspaceId, loginEmail.toLowerCase());
    }

    @Override
    public User createUser(UUID workspaceId, String email, String passwordHash,
                            String firstName, String lastName, String phone, Role role) {
        // BUG 7 legacy : login_email = contact_email = email (rendu 1:1 via @PrePersist).
        return saveUser(workspaceId, email, /*loginEmail=*/null, /*contactEmail=*/null,
                passwordHash, firstName, lastName, phone, role, false);
    }

    @Override
    public User createPendingUser(UUID workspaceId, String email, String passwordHash,
                                   String firstName, String lastName, String phone, Role role) {
        return saveUser(workspaceId, email, /*loginEmail=*/null, /*contactEmail=*/null,
                passwordHash, firstName, lastName, phone, role, true);
    }

    @Override
    public User createInvitedUser(UUID workspaceId, String loginEmail, String contactEmail,
                                   String passwordHash, String firstName, String lastName,
                                   String phone, Role role) {
        // L'email legacy stocke le loginEmail (consistency : audit log, X-User-Email header).
        return saveUser(workspaceId, loginEmail, loginEmail, contactEmail,
                passwordHash, firstName, lastName, phone, role, true);
    }

    @Override
    public User createSignupAdmin(UUID workspaceId, String loginEmail, String contactEmail,
                                   String passwordHash, String firstName, String lastName,
                                   String phone, Role role) {
        return saveUser(workspaceId, loginEmail, loginEmail, contactEmail,
                passwordHash, firstName, lastName, phone, role, false);
    }

    private User saveUser(UUID workspaceId, String email, String loginEmail, String contactEmail,
                           String passwordHash, String firstName, String lastName, String phone, Role role,
                           boolean mustChangePassword) {
        UserEntity entity = new UserEntity();
        entity.setWorkspaceId(workspaceId);
        entity.setEmail(email.toLowerCase());
        if (loginEmail != null) entity.setLoginEmail(loginEmail.toLowerCase());
        if (contactEmail != null) entity.setContactEmail(contactEmail.toLowerCase());
        entity.setPasswordHash(passwordHash);
        entity.setFirstName(firstName);
        entity.setLastName(lastName);
        entity.setPhone(phone);
        entity.setPhoneE164(phone);
        entity.setRole(role.name());
        entity.setActive(true);
        entity.setMustChangePassword(mustChangePassword);
        // BUG 6 (2026-06-07) — un user qui doit changer son MDP a sa creation est
        // necessairement un invite (cf InviteEmploye/InviteClient) -> PENDING.
        // Un user cree en mode "self-signup" (RegisterWorkspaceUseCase) choisit
        // son MDP a l'inscription => mustChangePassword=false => ACTIVE direct.
        entity.setStatus(mustChangePassword ? UserStatus.PENDING.name() : UserStatus.ACTIVE.name());
        return toDomain(jpa.save(entity));
    }

    @Override
    public void updateContactEmail(UUID userId, String contactEmail) {
        if (contactEmail == null || contactEmail.isBlank()) return;
        jpa.updateContactEmail(userId, contactEmail.toLowerCase());
    }

    @Override
    public void updatePasswordHash(UUID userId, String passwordHash) {
        jpa.updatePasswordHash(userId, passwordHash);
    }

    @Override
    public void updatePasswordHashAndClearMustChange(UUID userId, String passwordHash) {
        jpa.updatePasswordHashAndClearMustChange(userId, passwordHash);
    }

    @Override
    public void setMustChangePassword(UUID userId, boolean value) {
        jpa.setMustChangePassword(userId, value);
    }

    @Override
    public void markEmailVerified(UUID userId, Instant verifiedAt) {
        jpa.markEmailVerified(userId, verifiedAt);
    }

    @Override
    public void updateTwofaMethod(UUID userId, String method) {
        jpa.updateTwofaMethod(userId, method);
    }

    @Override
    public void markPhoneVerified(UUID userId, Instant verifiedAt) {
        jpa.markPhoneVerified(userId, verifiedAt);
    }

    @Override
    public void updateTotpSecret(UUID userId, String encryptedSecret, boolean enabled) {
        jpa.updateTotpSecret(userId, encryptedSecret, enabled);
    }

    @Override
    public boolean consommerPasTotp(UUID userId, long pas) {
        return jpa.consommerPasTotp(userId, pas) == 1;
    }

    @Override
    public void registerSuccessfulLogin(UUID userId, Instant when) {
        jpa.registerSuccessfulLogin(userId, when);
    }

    @Override
    public void incrementFailedLogin(UUID userId, Instant lockUntilOrNull) {
        jpa.incrementFailedLogin(userId, lockUntilOrNull);
    }

    @Override
    public void resetFailedLogin(UUID userId) {
        jpa.resetFailedLogin(userId);
    }

    @Override
    public void setStatus(UUID userId, UserStatus status) {
        jpa.setStatus(userId, status.name());
    }

    @Override
    public List<User> findByWorkspaceAndRoles(UUID workspaceId, Set<Role> roles) {
        if (roles == null || roles.isEmpty()) return List.of();
        Set<String> roleNames = roles.stream().map(Role::name).collect(Collectors.toSet());
        return jpa.findByWorkspaceIdAndRoleIn(workspaceId, roleNames).stream()
                .map(this::toDomain)
                .toList();
    }

    private User toDomain(UserEntity e) {
        return new User(
                e.getId(), e.getWorkspaceId(), e.getEmail(),
                e.getLoginEmail(), e.getContactEmail(),
                e.getPasswordHash(),
                e.getFirstName(), e.getLastName(), e.getPhone(),
                Role.valueOf(e.getRole()),
                e.getTotpSecretEncrypted(), e.isTotpEnabled(), e.isMustChangePassword(),
                e.getTwofaMethod(), e.getEmailVerifiedAt(), e.getPhoneVerifiedAt(), e.getPhoneE164(),
                e.getFailedLoginAttempts(), e.getLockedUntil(), e.getLastLoginAt(),
                UserStatus.fromString(e.getStatus()), e.getCreatedAt());
    }
}
