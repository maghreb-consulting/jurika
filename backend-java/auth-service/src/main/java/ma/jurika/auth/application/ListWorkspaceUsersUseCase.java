package ma.jurika.auth.application;

import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.model.UserStatus;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * BUG 6 (2026-06-07) — Liste les membres internes (EMPLOYE + SUPERVISEUR) du
 * workspace courant pour la page "Equipe" du superviseur. CLIENT exclu : il se
 * gere via le flux dataroom dedie ({@code DELETE /auth/dossiers/{id}/client}).
 */
@Service
public class ListWorkspaceUsersUseCase {

    private static final Set<Role> INTERNAL_ROLES = Set.of(Role.EMPLOYE, Role.SUPERVISEUR);

    /**
     * Traçabilité (2026-07-15) — annuaire COMPLET du workspace : internes
     * (EMPLOYE + SUPERVISEUR) ET clients, tous statuts confondus (ACTIVE /
     * PENDING / INACTIVE). Sert a resoudre par leur nom TOUS les acteurs d'un
     * event audit, y compris un client ou un employe desactive. Ne PAS utiliser
     * pour le chat/annuaire interne (voir {@link #INTERNAL_ROLES}).
     */
    private static final Set<Role> DIRECTORY_ROLES = Set.of(Role.EMPLOYE, Role.SUPERVISEUR, Role.CLIENT);

    private final UserRepository userRepository;

    public ListWorkspaceUsersUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public record Member(UUID userId, String email, String firstName, String lastName,
                          String phone, Role role, UserStatus status,
                          boolean mustChangePassword, Instant lastLoginAt, Instant createdAt) {}

    @Transactional(readOnly = true)
    public List<Member> execute(UUID workspaceId) {
        TenantContext.set(workspaceId);
        return userRepository.findByWorkspaceAndRoles(workspaceId, INTERNAL_ROLES).stream()
                .map(this::toMember)
                .toList();
    }

    /**
     * Traçabilité (2026-07-15) — annuaire complet : internes + clients, tous
     * statuts (y compris INACTIVE). Permet la resolution nom pour tout acteur
     * d'un event audit. Reserve SUPERVISEUR cote controller.
     */
    @Transactional(readOnly = true)
    public List<Member> executeDirectory(UUID workspaceId) {
        TenantContext.set(workspaceId);
        return userRepository.findByWorkspaceAndRoles(workspaceId, DIRECTORY_ROLES).stream()
                .map(this::toMember)
                .toList();
    }

    private Member toMember(User u) {
        return new Member(u.id(), u.email(), u.firstName(), u.lastName(), u.phone(),
                u.role(), u.status(), u.mustChangePassword(),
                u.lastLoginAt(), u.createdAt());
    }
}
