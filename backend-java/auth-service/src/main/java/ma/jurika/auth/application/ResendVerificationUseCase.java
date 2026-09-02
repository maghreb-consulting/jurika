package ma.jurika.auth.application;

import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.model.Workspace;
import ma.jurika.auth.domain.model.WorkspaceStatus;
import ma.jurika.auth.domain.port.AuditLogger;
import ma.jurika.auth.domain.port.EmailSender;
import ma.jurika.auth.domain.port.EmailVerificationTokenRepository;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.auth.domain.port.WorkspaceRepository;
import ma.jurika.auth.domain.service.VerificationTokenHasher;
import ma.jurika.common.exception.ConflictException;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Genere et envoie un nouveau lien de verification email.
 * <p>
 * Utilise quand le user a perdu/manque le 1er email, ou que le lien a expire.
 */
@Service
public class ResendVerificationUseCase {

    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final VerificationTokenHasher tokenHasher;
    private final EmailSender emailSender;
    private final AuditLogger auditLogger;

    private final long emailTokenTtlHours;
    private final String verificationBaseUrl;

    public ResendVerificationUseCase(WorkspaceRepository workspaceRepository,
                                      UserRepository userRepository,
                                      EmailVerificationTokenRepository tokenRepository,
                                      VerificationTokenHasher tokenHasher,
                                      EmailSender emailSender,
                                      AuditLogger auditLogger,
                                      @Value("${jurika.auth.email-verification-ttl-hours:24}") long emailTokenTtlHours,
                                      @Value("${jurika.email.verification-base-url:http://localhost:5173/auth/verify-email}") String verificationBaseUrl) {
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.tokenHasher = tokenHasher;
        this.emailSender = emailSender;
        this.auditLogger = auditLogger;
        this.emailTokenTtlHours = emailTokenTtlHours;
        this.verificationBaseUrl = verificationBaseUrl;
    }

    public record Command(String workspaceCode, String email, String ipAddress, String userAgent) {}

    @Transactional
    public void execute(Command cmd) {
        Workspace workspace = workspaceRepository.findByCode(cmd.workspaceCode())
                .orElseThrow(() -> new NotFoundException("Workspace inconnu"));

        if (workspace.status() != WorkspaceStatus.PENDING_VERIFICATION) {
            throw new ConflictException("Le workspace est deja verifie ou suspendu");
        }

        TenantContext.set(workspace.id());

        User user = userRepository.findByWorkspaceAndEmail(workspace.id(), cmd.email())
                .orElseThrow(() -> new NotFoundException("Utilisateur inconnu pour cet email"));

        String rawToken = tokenHasher.generateUuidToken();
        String tokenHash = tokenHasher.hash(rawToken);
        Instant expiresAt = Instant.now().plus(Duration.ofHours(emailTokenTtlHours));
        tokenRepository.create(
                workspace.id(), user.id(), cmd.email(), tokenHash,
                "EMAIL_VERIFICATION", expiresAt, cmd.ipAddress(), cmd.userAgent());

        String verificationUrl = verificationBaseUrl + "?token=" + rawToken;
        // BUG 7 (chore 2026-06-08) — notif vers contact_email + identityFooter vars.
        java.util.Map<String, Object> vars = new java.util.HashMap<>();
        vars.put("firstName", user.firstName());
        vars.put("verificationUrl", verificationUrl);
        vars.put("loginEmail", user.loginEmail());
        vars.put("contactEmail", user.contactEmail());
        emailSender.sendTemplated(
                user.contactEmail(),
                "Nouveau lien de verification - JURIKA",
                "verify-email",
                vars);

        auditLogger.log(workspace.id(), user.id(), "EMAIL_VERIFICATION_RESENT", "user",
                user.id(), cmd.ipAddress(), cmd.userAgent(),
                Map.of("email", cmd.email()));
    }
}
