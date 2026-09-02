package ma.jurika.auth.application;

import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.model.Workspace;
import ma.jurika.auth.domain.port.AuditLogger;
import ma.jurika.auth.domain.port.EmailSender;
import ma.jurika.auth.domain.port.PasswordHasher;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.auth.domain.port.WorkspaceRepository;
import ma.jurika.auth.domain.service.TemporaryPasswordGenerator;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * HIGH-9 (audit 2026-06-02) — Endpoint admin SUPER_ADMIN pour debloquer un cabinet
 * dont le mail welcome initial a echoue (SMTP down, gmail rate-limit, mauvais
 * email, etc.). Regenere un mot de passe temporaire BCrypt + force
 * must_change_password=TRUE + renvoie l'email welcome.
 *
 * <p>Avant ce fix, un workspace dont l'email avait echoue restait orphelin :
 * - BCrypt en base, MDP temp irrecuperable
 * - Reset-password exige le code workspace (que le user ignore puisqu'email perdu)
 * Le seul recours etait l'intervention SQL manuelle.
 */
@Service
public class ResendWelcomeUseCase {

    private static final Logger log = LoggerFactory.getLogger(ResendWelcomeUseCase.class);

    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final TemporaryPasswordGenerator tempPasswordGenerator;
    private final PasswordHasher passwordHasher;
    private final EmailSender emailSender;
    private final AuditLogger auditLogger;

    public ResendWelcomeUseCase(WorkspaceRepository workspaceRepository,
                                 UserRepository userRepository,
                                 TemporaryPasswordGenerator tempPasswordGenerator,
                                 PasswordHasher passwordHasher,
                                 EmailSender emailSender,
                                 AuditLogger auditLogger) {
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
        this.tempPasswordGenerator = tempPasswordGenerator;
        this.passwordHasher = passwordHasher;
        this.emailSender = emailSender;
        this.auditLogger = auditLogger;
    }

    public record Result(boolean emailDelivered) {}

    @Transactional
    public Result execute(UUID workspaceId, UUID adminUserId, String adminEmail,
                           String ipAddress, String userAgent) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new NotFoundException("Workspace introuvable"));
        TenantContext.set(workspace.id());

        User adminUser = userRepository.findById(adminUserId)
                .orElseThrow(() -> new NotFoundException("User admin introuvable pour ce workspace"));

        // Regenere un MDP temp et force must_change_password
        String newTemp = tempPasswordGenerator.generate();
        String hash = passwordHasher.hash(newTemp);
        userRepository.updatePasswordHash(adminUser.id(), hash);
        userRepository.setMustChangePassword(adminUser.id(), true);

        Map<String, Object> emailVars = new HashMap<>();
        emailVars.put("brand", "JURIKA");
        emailVars.put("firstName", adminUser.firstName() == null ? adminUser.email() : adminUser.firstName());
        emailVars.put("workspaceCode", workspace.code());
        emailVars.put("email", adminUser.email());
        emailVars.put("temporaryPassword", newTemp);
        emailVars.put("verificationUrl", "");  // resend ne renvoie pas un lien (deja active s'il l'etait)
        emailVars.put("ttlHours", "0");
        emailVars.put("phishingWarning",
                "Email envoye sur demande du support JURIKA. "
                        + "JURIKA ne vous demandera JAMAIS votre mot de passe par email, SMS ou telephone.");
        emailVars.put("subject", "Votre nouvel acces JURIKA (renvoye par support)");
        emailVars.put("planLabel", null);

        boolean delivered;
        try {
            emailSender.sendTemplated(adminUser.email(),
                    "JURIKA - Votre nouvel acces (renvoye par support)",
                    "welcome", emailVars);
            delivered = true;
        } catch (RuntimeException ex) {
            log.error("ResendWelcome echec pour workspace={} user={} : {}",
                    workspace.code(), adminUser.id(), ex.getMessage());
            delivered = false;
        }

        auditLogger.log(workspace.id(), adminUser.id(), "ADMIN_RESEND_WELCOME", "workspace",
                workspace.id(), ipAddress, userAgent,
                Map.of("emailDelivered", delivered, "by", adminEmail == null ? "?" : adminEmail));

        return new Result(delivered);
    }
}
