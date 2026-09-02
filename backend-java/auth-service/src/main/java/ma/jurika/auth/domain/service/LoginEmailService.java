package ma.jurika.auth.domain.service;

import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.common.security.LoginEmailGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * BUG 7 (2026-06-08) — Orchestration generation login_email avec resolution
 * collisions intra-workspace.
 *
 * <p>Stratégie : {@code prenom.nom@jurika.ma} pour le 1er Karim Benali du
 * workspace, puis {@code prenom.nom2@jurika.ma}, {@code prenom.nom3@jurika.ma}
 * ... La generation reste deterministe a partir de (firstName, lastName) — pas
 * de random — pour que l'inviteur puisse anticiper l'identifiant qu'il
 * communiquera au membre.
 *
 * <p>Feature flag : si {@code jurika.auth.login-email-enabled=false}, la
 * generation renvoie l'email perso fourni en argument (comportement legacy) —
 * permet le rollback sans toucher au code.
 */
@Service
public class LoginEmailService {

    /** Plafond defensif sur le suffixe pour eviter une boucle infinie en cas
     *  de bug ou de spam massif sur le meme nom. Au-dela : on throw. */
    private static final int MAX_SUFFIX = 999;

    private final UserRepository userRepository;
    private final LoginEmailGenerator generator;
    private final boolean enabled;

    public LoginEmailService(UserRepository userRepository,
                              LoginEmailGenerator generator,
                              @Value("${jurika.auth.login-email-enabled:true}") boolean enabled) {
        this.userRepository = userRepository;
        this.generator = generator;
        this.enabled = enabled;
    }

    /**
     * Genere un login_email unique pour {@code workspaceId} a partir du nom.
     * Si flag off : renvoie {@code fallbackEmail} (en general l'email perso
     * fourni par l'inviteur) — comportement legacy pre-BUG 7.
     */
    public String generate(UUID workspaceId, String firstName, String lastName, String fallbackEmail) {
        if (!enabled) {
            return fallbackEmail == null ? null : fallbackEmail.toLowerCase();
        }
        String base = generator.base(firstName, lastName);
        for (int suffix = 1; suffix <= MAX_SUFFIX; suffix++) {
            String candidate = generator.withSuffix(base, suffix);
            if (!userRepository.existsByWorkspaceAndLoginEmail(workspaceId, candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Impossible de generer un login_email unique apres " + MAX_SUFFIX
                        + " essais pour " + firstName + " " + lastName
                        + " dans workspace " + workspaceId);
    }
}
