package ma.jurika.auth.application;

import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.port.AuditLogger;
import ma.jurika.auth.domain.port.EmailSender;
import ma.jurika.auth.domain.port.PasswordHasher;
import ma.jurika.auth.domain.port.RecoveryCodeRepository;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.auth.domain.service.RecoveryCodeGenerator;
import ma.jurika.common.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Genere N (defaut 10) codes de recuperation 2FA et les sauve hashes (BCrypt).
 * Les codes en clair sont retournes UNE SEULE FOIS au caller pour affichage UI.
 */
@Service
public class GenerateRecoveryCodesUseCase {

    private static final Logger log = LoggerFactory.getLogger(GenerateRecoveryCodesUseCase.class);
    private static final DateTimeFormatter EVENT_AT_FMT = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm 'GMT'")
            .withZone(ZoneId.of("Africa/Casablanca"));

    private final RecoveryCodeRepository recoveryCodeRepository;
    private final RecoveryCodeGenerator generator;
    private final PasswordHasher passwordHasher;
    private final AuditLogger auditLogger;
    private final UserRepository userRepository;
    private final EmailSender emailSender;
    private final int count;

    public GenerateRecoveryCodesUseCase(RecoveryCodeRepository recoveryCodeRepository,
                                         RecoveryCodeGenerator generator,
                                         PasswordHasher passwordHasher,
                                         AuditLogger auditLogger,
                                         UserRepository userRepository,
                                         EmailSender emailSender,
                                         @Value("${jurika.auth.recovery-codes-count:10}") int count) {
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.generator = generator;
        this.passwordHasher = passwordHasher;
        this.auditLogger = auditLogger;
        this.userRepository = userRepository;
        this.emailSender = emailSender;
        this.count = count;
    }

    public record Command(UUID userId, UUID workspaceId, String ipAddress, String userAgent) {}

    public record Result(List<String> plainCodes) {}

    @Transactional
    public Result execute(Command cmd) {
        TenantContext.set(cmd.workspaceId());

        // Detection : 1ere generation (apres setup 2FA) vs regeneration (post-activation).
        // L'email "recovery-codes-regenerated" est envoye SEULEMENT pour la regeneration
        // (RG-AU33), car la 1ere creation est deja signalee par l'email "2fa-enabled".
        boolean isRegeneration = !recoveryCodeRepository.findActive(cmd.userId()).isEmpty();

        List<String> plain = Stream.generate(generator::generateOne).limit(count).toList();
        List<String> hashes = plain.stream().map(passwordHasher::hash).toList();

        recoveryCodeRepository.replaceAll(cmd.workspaceId(), cmd.userId(), hashes);

        auditLogger.log(cmd.workspaceId(), cmd.userId(),
                isRegeneration ? "RECOVERY_CODES_REGENERATED" : "RECOVERY_CODES_GENERATED",
                "user", cmd.userId(), cmd.ipAddress(), cmd.userAgent(),
                Map.of("count", String.valueOf(count), "regeneration", String.valueOf(isRegeneration)));

        if (isRegeneration) {
            sendRegenerationEmail(cmd);
        }

        return new Result(plain);
    }

    private void sendRegenerationEmail(Command cmd) {
        try {
            User user = userRepository.findById(cmd.userId()).orElse(null);
            if (user == null) return;
            Map<String, Object> vars = new HashMap<>();
            vars.put("firstName", user.firstName() != null ? user.firstName() : user.email());
            vars.put("regeneratedAt", EVENT_AT_FMT.format(Instant.now()));
            vars.put("count", String.valueOf(count));
            vars.put("ipAddress", cmd.ipAddress());
            vars.put("unsubscribeUrl", null);
            // BUG 7 (chore 2026-06-08) — fragment identityFooter
            vars.put("loginEmail", user.loginEmail());
            vars.put("contactEmail", user.contactEmail());
            // BUG 7 (2026-06-08) — notif vers contact_email.
            emailSender.sendTemplated(user.contactEmail(),
                    "JURIKA - codes de recuperation regeneres",
                    "recovery-codes-regenerated",
                    vars);
        } catch (RuntimeException ex) {
            log.warn("Echec envoi email recovery-codes-regenerated pour user={} : {}",
                    cmd.userId(), ex.getMessage());
        }
    }
}
