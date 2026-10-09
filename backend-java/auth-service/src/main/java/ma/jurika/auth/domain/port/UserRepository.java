package ma.jurika.auth.domain.port;

import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.model.UserStatus;
import ma.jurika.common.security.Role;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface UserRepository {

    Optional<User> findById(UUID id);

    Optional<User> findByWorkspaceAndEmail(UUID workspaceId, String email);

    /**
     * BUG 7 (2026-06-08) — Lookup principal au login : on match l'identifiant
     * saisi contre {@code users.login_email} (case-insensitive). Pour les comptes
     * legacy backfillees en V28, {@code login_email == email}, donc cette
     * methode subsume {@link #findByWorkspaceAndEmail} dans le parcours auth.
     */
    Optional<User> findByWorkspaceAndLoginEmail(UUID workspaceId, String loginEmail);

    /**
     * BUG 7 (2026-06-08) — Verifie si un {@code login_email} est deja utilise
     * dans le workspace (pour la generation avec suffixe collisions).
     */
    boolean existsByWorkspaceAndLoginEmail(UUID workspaceId, String loginEmail);

    User createUser(UUID workspaceId, String email, String passwordHash,
                    String firstName, String lastName, String phone, Role role);

    /**
     * Cree un user avec MDP temporaire :
     * - must_change_password = TRUE (force changement au 1er login)
     * - email_verified = FALSE (sera mis a TRUE apres verification email)
     *
     * <p>Cette signature legacy fait egaler login_email = contact_email = email
     * (via le {@code @PrePersist} de UserEntity). Preferer le nouveau
     * {@link #createInvitedUser} qui distingue explicitement les deux roles.
     */
    User createPendingUser(UUID workspaceId, String email, String passwordHash,
                            String firstName, String lastName, String phone, Role role);

    /**
     * BUG 7 (2026-06-08) — Cree un user invite avec dissociation explicite :
     * - {@code loginEmail}    : identifiant de connexion stable (genere par
     *                            {@link ma.jurika.common.security.LoginEmailGenerator}).
     * - {@code contactEmail}  : email perso fourni par l'inviteur, destinataire
     *                            des notifications.
     * - {@code mustChangePassword=TRUE}, status=PENDING (cf BUG 6).
     * <p>La colonne legacy {@code email} est backfillee = {@code loginEmail}
     * pour preserver les lectures historiques (logs, audit metadata).
     */
    User createInvitedUser(UUID workspaceId, String loginEmail, String contactEmail,
                            String passwordHash, String firstName, String lastName,
                            String phone, Role role);

    /**
     * BUG 7 (2026-06-08) — Cree le user fondateur d'un workspace (signup
     * self-service) avec login_email genere. Variante de {@link #createUser}
     * (must_change_password=FALSE car le user choisit son MDP au signup).
     */
    User createSignupAdmin(UUID workspaceId, String loginEmail, String contactEmail,
                            String passwordHash, String firstName, String lastName,
                            String phone, Role role);

    /**
     * BUG 7 (2026-06-08) — Met a jour le {@code contact_email} sans toucher au
     * login_email. Permet a un user de mettre a jour son email perso depuis le
     * profil sans casser son login. (UI : Phase 2 BUG 7, endpoint exposable
     * plus tard ; le port est defini des maintenant pour permettre l'usage
     * par d'autres flux internes comme `IssueCredentialsUseCase`.)
     */
    void updateContactEmail(UUID userId, String contactEmail);

    void updatePasswordHash(UUID userId, String passwordHash);

    /**
     * Met a jour le MDP ET decoche must_change_password (passe a FALSE).
     * Utilise apres un /auth/change-password reussi.
     */
    void updatePasswordHashAndClearMustChange(UUID userId, String passwordHash);

    /**
     * HIGH-9 (audit 2026-06-02) : force le flag must_change_password sans changer le MDP.
     * Utilise par ResendWelcomeUseCase apres regen d'un MDP temp pour s'assurer que
     * l'user devra le changer au prochain login.
     */
    void setMustChangePassword(UUID userId, boolean value);

    void updateTotpSecret(UUID userId, String encryptedSecret, boolean enabled);

    /**
     * Lot L0 (E10d), anti-rejeu TOTP : enregistre le pas de temps du code accepte
     * en UNE requete atomique, et seulement s'il est strictement superieur au
     * dernier pas accepte. Renvoie {@code true} si une ligne a ete modifiee : le
     * code est alors accepte ; {@code false} : code deja consomme (rejeu, ou
     * requete concurrente gagnee par une autre).
     */
    boolean consommerPasTotp(UUID userId, long pas);

    /**
     * Marque l'email comme verifie (set email_verified_at = NOW).
     */
    void markEmailVerified(UUID userId, Instant verifiedAt);

    /**
     * Defini la methode 2FA choisie par l'utilisateur (SMS ou TOTP) ou null pour reset.
     */
    void updateTwofaMethod(UUID userId, String method);

    /**
     * Marque le telephone comme verifie (apres reception OK d'un code SMS).
     */
    void markPhoneVerified(UUID userId, Instant verifiedAt);

    void registerSuccessfulLogin(UUID userId, Instant when);

    void incrementFailedLogin(UUID userId, Instant lockUntilOrNull);

    void resetFailedLogin(UUID userId);

    /**
     * BUG 6 (2026-06-07) — Met a jour le statut d'un compte (PENDING / ACTIVE /
     * INACTIVE). Le trigger DB met automatiquement {@code is_active} en accord.
     * Utilise par :
     *  - {@code SetUserStatusUseCase} (activer / desactiver par le superviseur)
     *  - {@code LoginUseCase} (PENDING -> ACTIVE a la premiere connexion reussie)
     */
    void setStatus(UUID userId, UserStatus status);

    /**
     * BUG 6 (2026-06-07) — Liste les membres d'un workspace ayant un des roles
     * demandes (en general {@code EMPLOYE} + {@code SUPERVISEUR} pour la vue
     * Equipe ; jamais CLIENT). Tri stable par createdAt ASC.
     */
    List<User> findByWorkspaceAndRoles(UUID workspaceId, Set<Role> roles);
}
