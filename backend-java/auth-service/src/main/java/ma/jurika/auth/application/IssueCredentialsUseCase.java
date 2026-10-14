package ma.jurika.auth.application;

import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.model.Workspace;
import ma.jurika.auth.domain.port.AuditLogger;
import ma.jurika.auth.domain.port.EmailSender;
import ma.jurika.auth.domain.port.PasswordHasher;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.auth.domain.port.WorkspaceRepository;
import ma.jurika.auth.domain.service.TemporaryPasswordGenerator;
import ma.jurika.auth.infrastructure.persistence.UserJpaRepository;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BUG 14 (2026-06-07) — Re-emission des identifiants d'un workspace pour le
 * SUPERVISEUR admin du cabinet, declenchee apres validation d'un paiement
 * hors-ligne (virement / cheque / espece) ou apres conversion CARD si le
 * signup avait differe l'envoi.
 *
 * <p>Difference avec {@link ResendWelcomeUseCase} :
 *  - public via {@code /internal/workspaces/{id}/issue-credentials} (Feign
 *    depuis billing-service) sans necessiter l'userId admin
 *  - identifie automatiquement le SUPERVISEUR primaire du workspace (premier
 *    cree) et regenere son MDP temporaire
 *  - audit avec {@code source=PAYMENT_VALIDATION} (vs {@code SUPPORT})
 *  - idempotent best-effort : log + return si SMTP echoue, ne casse pas la
 *    transaction billing
 */
@Service
public class IssueCredentialsUseCase {

    private static final Logger log = LoggerFactory.getLogger(IssueCredentialsUseCase.class);

    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final UserJpaRepository userJpaRepository;
    private final TemporaryPasswordGenerator tempPasswordGenerator;
    private final PasswordHasher passwordHasher;
    private final EmailSender emailSender;
    private final AuditLogger auditLogger;

    public IssueCredentialsUseCase(WorkspaceRepository workspaceRepository,
                                    UserRepository userRepository,
                                    UserJpaRepository userJpaRepository,
                                    TemporaryPasswordGenerator tempPasswordGenerator,
                                    PasswordHasher passwordHasher,
                                    EmailSender emailSender,
                                    AuditLogger auditLogger) {
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
        this.userJpaRepository = userJpaRepository;
        this.tempPasswordGenerator = tempPasswordGenerator;
        this.passwordHasher = passwordHasher;
        this.emailSender = emailSender;
        this.auditLogger = auditLogger;
    }

    @Transactional
    public void execute(UUID workspaceId, String reason) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new NotFoundException("Workspace introuvable : " + workspaceId));
        TenantContext.set(workspace.id());

        List<String> superviseurEmails = userJpaRepository.findSuperviseurEmailsByWorkspaceId(workspaceId);
        if (superviseurEmails.isEmpty()) {
            log.warn("issue-credentials sans SUPERVISEUR pour workspace={} — skip", workspaceId);
            return;
        }
        String adminEmail = superviseurEmails.get(0);
        User admin = userRepository.findByWorkspaceAndEmail(workspaceId, adminEmail)
                .orElseThrow(() -> new NotFoundException("SUPERVISEUR introuvable: " + adminEmail));

        String newTemp = tempPasswordGenerator.generate();
        String hash = passwordHasher.hash(newTemp);
        userRepository.updatePasswordHash(admin.id(), hash);
        userRepository.setMustChangePassword(admin.id(), true);

        // BUG 14 fix (2026-06-09) : envoi A admin.contactEmail() (email reel)
        // PAS A admin.email() qui est le loginEmail @jurika.ma genere par BUG 7
        // (identifiant interne, pas une boite mail livrable). Sans ce fix
        // l'email partait vers karim.benali@jurika.ma -> SMTP rejet silencieux.
        String deliveryEmail = admin.contactEmail() != null && !admin.contactEmail().isBlank()
                ? admin.contactEmail()
                : admin.email();

        Map<String, Object> emailVars = new HashMap<>();
        emailVars.put("brand", "JURIKA");
        emailVars.put("firstName", admin.firstName() == null ? deliveryEmail : admin.firstName());
        emailVars.put("workspaceCode", workspace.code());
        // BUG 7 : "email" dans le template = identifiant de connexion (loginEmail
        // @jurika.ma) que l'utilisateur saisira au login. contactEmail est
        // l'adresse REELLE de livraison du mail (ailleurs dans le template).
        emailVars.put("email", admin.loginEmail() != null ? admin.loginEmail() : admin.email());
        emailVars.put("contactEmail", deliveryEmail);
        emailVars.put("loginEmail", admin.loginEmail() != null ? admin.loginEmail() : admin.email());
        emailVars.put("temporaryPassword", newTemp);
        emailVars.put("verificationUrl", "");
        emailVars.put("ttlHours", "0");
        emailVars.put("phishingWarning",
                "JURIKA ne vous demandera jamais votre mot de passe, ni par courriel, ni par SMS, ni par t\u00e9l\u00e9phone.");
        emailVars.put("subject", "Vos identifiants JURIKA - paiement valid\u00e9");
        emailVars.put("planLabel", null);
        // Fix 2026-06-07 (BUG 5) : welcome.html attend roleLabel + roleHint
        // (sinon ${roleLabel} rend "null" cote Thymeleaf). Ici on est forcement
        // SUPERVISEUR (admin cabinet) puisque c'est le compte cree au signup.
        emailVars.put("roleLabel", "Superviseur");
        emailVars.put("roleHint",
                "Ce compte est le compte superviseur du cabinet. Une fois "
                        + "connect\u00e9, vous pourrez inviter vos employ\u00e9s "
                        + "depuis Param\u00e8tres, onglet \u00c9quipe : chacun recevra "
                        + "ses propres identifiants par courriel.");

        try {
            emailSender.sendTemplated(deliveryEmail,
                    "Bienvenue sur JURIKA - vos identifiants de connexion",
                    "welcome", emailVars);
            log.info("Identifiants emis post-paiement workspace={} deliveryTo={} loginEmail={} reason={}",
                    workspace.code(), deliveryEmail, admin.loginEmail(), reason);
        } catch (RuntimeException ex) {
            log.error("issue-credentials SMTP echoue workspace={} deliveryTo={} : {}",
                    workspace.code(), deliveryEmail, ex.getMessage(), ex);
        }

        auditLogger.log(workspace.id(), admin.id(), "CREDENTIALS_ISSUED", "workspace",
                workspace.id(), null, null,
                Map.of("reason", reason == null ? "MANUAL" : reason,
                        "deliveryEmail", deliveryEmail,
                        "loginEmail", admin.loginEmail() != null ? admin.loginEmail() : admin.email(),
                        "source", "billing"));
    }
}
