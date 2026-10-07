package ma.jurika.auth.application;

import ma.jurika.auth.domain.model.AuthTokens;
import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.model.Workspace;
import ma.jurika.auth.domain.port.AuditLogger;
import ma.jurika.auth.domain.port.EmailSender;
import ma.jurika.auth.domain.port.EmailVerificationTokenRepository;
import ma.jurika.auth.domain.port.EventPublisher;
import ma.jurika.auth.domain.port.PasswordHasher;
import ma.jurika.auth.domain.port.TokenIssuer;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.auth.domain.port.WorkspaceRepository;
import ma.jurika.auth.domain.service.LoginEmailService;
import ma.jurika.auth.domain.service.TemporaryPasswordGenerator;
import ma.jurika.auth.domain.service.VerificationTokenHasher;
import ma.jurika.auth.domain.service.WorkspaceCodeGenerator;
import ma.jurika.common.exception.ConflictException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import ma.jurika.common.validation.DnsMxValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Cycle d'inscription V2 strict (RG-AU25 a RG-AU36 + RG-AU40) :
 * <ol>
 *     <li>RG-AU25/36 : Validation email (format + DNS MX) + telephone (+212XXXXXXXXX)</li>
 *     <li>RG-AU26/27 : Creation workspace <b>PENDING_VERIFICATION</b> (PAS ACTIVE)</li>
 *     <li>RG-AU05 : Generation MDP temporaire 12 chars (le password fourni est ignore)</li>
 *     <li>RG-AU29 : Creation user avec <b>must_change_password=TRUE</b> via createPendingUser</li>
 *     <li>RG-AU26 : Creation token de verification email (TTL 24h)</li>
 *     <li>RG-AU40 : Envoi email avec warning phishing + code workspace + MDP temp + lien activation</li>
 * </ol>
 */
@Service
public class RegisterWorkspaceUseCase {

    private static final Logger log = LoggerFactory.getLogger(RegisterWorkspaceUseCase.class);

    /**
     * RG-AU36 (revise 2026-07-28) : phone au format international E.164
     * (`+<indicatif><national>`, 7 a 15 chiffres). Auparavant restreint au Maroc
     * (`+212[5-7]XXXXXXXX`) ; ouvert a l'international car le signup propose
     * desormais un selecteur d'indicatif pays (Maroc reste le defaut). La
     * validation fine par pays est faite cote front (libphonenumber-js) ;
     * l'eligibilite au 2FA-SMS reste, elle, restreinte aux indicatifs supportes.
     */
    private static final Pattern PHONE_E164_PATTERN = Pattern.compile("^\\+[1-9]\\d{6,14}$");

    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final WorkspaceCodeGenerator codeGenerator;
    private final TemporaryPasswordGenerator tempPasswordGenerator;
    private final VerificationTokenHasher tokenHasher;
    private final PasswordHasher passwordHasher;
    private final DnsMxValidator emailValidator;
    private final EmailSender emailSender;
    private final EventPublisher eventPublisher;
    private final AuditLogger auditLogger;
    private final LoginEmailService loginEmailService;
    private final TokenIssuer tokenIssuer;

    private final long emailTokenTtlHours;
    private final String verificationBaseUrl;

    public RegisterWorkspaceUseCase(WorkspaceRepository workspaceRepository,
                                     UserRepository userRepository,
                                     EmailVerificationTokenRepository tokenRepository,
                                     WorkspaceCodeGenerator codeGenerator,
                                     TemporaryPasswordGenerator tempPasswordGenerator,
                                     VerificationTokenHasher tokenHasher,
                                     PasswordHasher passwordHasher,
                                     DnsMxValidator emailValidator,
                                     EmailSender emailSender,
                                     EventPublisher eventPublisher,
                                     AuditLogger auditLogger,
                                     LoginEmailService loginEmailService,
                                     TokenIssuer tokenIssuer,
                                     @Value("${jurika.auth.email-verification-ttl-hours:24}") long emailTokenTtlHours,
                                     @Value("${jurika.email.verification-base-url:http://localhost:5173/auth/verify-email}") String verificationBaseUrl) {
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.codeGenerator = codeGenerator;
        this.tempPasswordGenerator = tempPasswordGenerator;
        this.tokenHasher = tokenHasher;
        this.passwordHasher = passwordHasher;
        this.emailValidator = emailValidator;
        this.emailSender = emailSender;
        this.eventPublisher = eventPublisher;
        this.auditLogger = auditLogger;
        this.loginEmailService = loginEmailService;
        this.tokenIssuer = tokenIssuer;
        this.emailTokenTtlHours = emailTokenTtlHours;
        this.verificationBaseUrl = verificationBaseUrl;
    }

    public record Command(
            String workspaceName,
            String contactEmail,
            UUID subscriptionId,
            String firstName,
            String lastName,
            String phone,
            String email,
            String password, // ignore en V2 strict
            String ipAddress,
            String userAgent,
            // Sprint 11 + spec 2026-06-02 — code plan tarifaire selectionne au
            // wizard (essentiel / business / entreprise). null pour les chemins
            // legacy (POST /register sans plan) — l'email welcome n'affiche
            // alors pas de section forfait.
            String selectedPlan,
            // Type de profil declare au signup (onboarding 2026-06-24). null
            // pour les chemins legacy (POST /register). Trace dans l'audit
            // WORKSPACE_REGISTER ; la persistance sur le workspace est faite par
            // SignupCabinetUseCase (meme pattern qu'ICE/IF/RC/city).
            String professionalType,
            // BUG 14 (2026-06-07) — si true, le welcome email (avec MDP temp)
            // n'est PAS envoye ici. Un appel a IssueCredentialsUseCase post-
            // paiement regenere un MDP temp + envoie l'email welcome.
            boolean deferCredentials
    ) {
        public Command(String workspaceName, String contactEmail, UUID subscriptionId,
                       String firstName, String lastName, String phone, String email,
                       String password, String ipAddress, String userAgent, String selectedPlan) {
            this(workspaceName, contactEmail, subscriptionId, firstName, lastName, phone,
                 email, password, ipAddress, userAgent, selectedPlan, null, false);
        }
    }

    /**
     * Libelle commercial du plan affiche dans l'email welcome. Source unique
     * cote backend, miroir des labels SPA dans SignupStep5Recap.tsx.
     * Spec directeur 2026-06-02 : essentiel / business / entreprise.
     */
    private static String planLabel(String code) {
        if (code == null) return null;
        return switch (ma.jurika.common.billing.PlanCatalog.normalize(code)) {
            case "essentiel" -> "Essentiel (499 DH/mois)";
            case "business"  -> "Business (1 199 DH/mois)";
            case "entreprise" -> "Entreprise (sur devis)";
            default -> code;
        };
    }

    /**
     * HIGH-4 (audit 2026-06-02) : {@code emailDelivered} reflete le succes reel de
     * l'envoi du mail welcome. Avant ce fix, le controller hardcodait
     * {@code verifyEmailSent=true} -> le frontend invitait l'utilisateur a
     * consulter sa boite mail meme quand SMTP avait silencieusement plante.
     */
    /**
     * BUG 7 (2026-06-08) — Result expose loginEmail + contactEmail genere a
     * l'inscription pour que le frontend puisse afficher "voici votre
     * identifiant" sur l'ecran de succes du wizard signup.
     *
     * BUG 14 (2026-06-07) — Result expose accessToken/refreshToken transitoires
     * quand deferCredentials=true (le wizard signup les utilise pour appeler
     * /api/v1/billing/prepare-payment depuis le Step Paiement).
     */
    public record Result(UUID workspaceId, String workspaceCode, UUID userId, boolean emailDelivered,
                          String loginEmail, String contactEmail,
                          String accessToken, String refreshToken) {
        /** Compat retro - chemin classique sans tokens ni emails enrichis. */
        public Result(UUID workspaceId, String workspaceCode, UUID userId, boolean emailDelivered) {
            this(workspaceId, workspaceCode, userId, emailDelivered, null, null, null, null);
        }
    }

    @Transactional
    public Result execute(Command cmd) {
        // 1. RG-AU25 : validation email format + DNS MX (selon config jurika.validation.dns-mx-enabled)
        DnsMxValidator.Result emailCheck = emailValidator.validate(cmd.email());
        if (!emailCheck.valid()) {
            throw new ValidationException(emailCheck.reason());
        }

        // 2. RG-AU36 : validation phone format E.164 international
        if (cmd.phone() != null && !cmd.phone().isBlank()
                && !PHONE_E164_PATTERN.matcher(cmd.phone()).matches()) {
            throw new ValidationException("PHONE_FORMAT_INVALID");
        }

        // 3. RG-AU26/27 : creation workspace PENDING_VERIFICATION (pas ACTIVE)
        String code = codeGenerator.generateUnique();
        // Lot L0 (E13a) : le workspace a creer est le workspace COURANT, pose par
        // l'appelant AVANT la transaction (ContexteWorkspacePublic#poserNouveauWorkspace) :
        // sans lui, l'INSERT est refuse par la politique RLS workspace_self_access.
        UUID nouveauWorkspace = TenantContext.get();
        if (nouveauWorkspace == null) {
            throw new IllegalStateException("Inscription sans workspace courant : appeler "
                    + "ContexteWorkspacePublic#poserNouveauWorkspace avant le cas d'usage");
        }
        Workspace workspace = workspaceRepository.createPending(
                nouveauWorkspace, code, cmd.workspaceName(), cmd.contactEmail(), cmd.subscriptionId());

        TenantContext.set(workspace.id());

        // 4. Verif email unique
        if (userRepository.findByWorkspaceAndEmail(workspace.id(), cmd.email()).isPresent()) {
            throw new ConflictException("EMAIL_ALREADY_REGISTERED");
        }

        // 5. RG-AU05 : MDP temporaire 12 chars (le password fourni dans cmd est ignore)
        String tempPassword = tempPasswordGenerator.generate();
        String pwdHash = passwordHasher.hash(tempPassword);

        // BUG 7 (2026-06-08) — generation de l'identifiant @jurika.ma. Le user
        // a fourni son email perso (cmd.email()) qui devient contact_email
        // (destinataire des notifs). loginEmail = karim.benali@jurika.ma.
        String loginEmail = loginEmailService.generate(
                workspace.id(), cmd.firstName(), cmd.lastName(), cmd.email());
        String contactEmail = cmd.email().toLowerCase();

        // 6. RG-AU29 : creation user avec must_change_password=TRUE
        // (createInvitedUser cf BUG 7 -- distingue login_email vs contact_email).
        User user = userRepository.createInvitedUser(
                workspace.id(), loginEmail, contactEmail, pwdHash,
                cmd.firstName(), cmd.lastName(), cmd.phone(),
                Role.SUPERVISEUR);

        // 7. RG-AU26 : token verification email TTL 24h. La verif token reste
        // associee au contact_email (perso) — c'est lui qui recoit le lien.
        String plainToken = UUID.randomUUID().toString();
        String tokenHash = tokenHasher.hash(plainToken);
        Instant expiresAt = Instant.now().plus(Duration.ofHours(emailTokenTtlHours));
        tokenRepository.create(workspace.id(), user.id(), contactEmail, tokenHash,
                "EMAIL_VERIFICATION", expiresAt, cmd.ipAddress(), cmd.userAgent());

        // 8. RG-AU40 : email avec warning phishing + credentials. BUG 7 : affiche
        // login_email (identifiant) + contact_email (envoi) distincts.
        String verifLink = verificationBaseUrl + "?token=" + plainToken;
        java.util.HashMap<String, Object> emailVars = new java.util.HashMap<>();
        emailVars.put("brand", "JURIKA");
        emailVars.put("firstName", cmd.firstName());
        emailVars.put("workspaceCode", code);
        emailVars.put("email", loginEmail); // legacy var (templates affichent celui-ci comme identifiant)
        emailVars.put("loginEmail", loginEmail);
        emailVars.put("contactEmail", contactEmail);
        emailVars.put("temporaryPassword", tempPassword);
        emailVars.put("verificationUrl", verifLink);
        emailVars.put("ttlHours", String.valueOf(emailTokenTtlHours));
        emailVars.put("phishingWarning",
                "JURIKA ne vous demandera JAMAIS votre mot de passe par email, SMS ou telephone. "
                        + "En cas de message suspect, contactez immediatement support@jurika.ma.");
        emailVars.put("subject", "Activez votre compte JURIKA");
        emailVars.put("planLabel", planLabel(cmd.selectedPlan()));
        boolean emailDelivered;
        if (cmd.deferCredentials()) {
            // BUG 14 — paiement-first flow : on ne envoie PAS le welcome
            // ici. Le MDP temp restera valide cote BDD (BCrypt) mais il
            // sera RE-genere a la validation du paiement (IssueCredentialsUseCase)
            // pour eviter qu'un MDP non-utilise persiste si le paiement
            // n'aboutit jamais.
            log.info("Signup avec deferCredentials=true (workspace={} code={}) — welcome email differe jusqu'a validation paiement",
                    workspace.id(), code);
            emailDelivered = false;
        } else try {
            emailSender.sendTemplated(
                    contactEmail,
                    "Bienvenue chez JURIKA - Activez votre compte",
                    "welcome",
                    emailVars);
            emailDelivered = true;
        } catch (RuntimeException ex) {
            log.error("Envoi email bienvenue echoue pour contact={} (workspace={}, code={}) : {}.",
                    contactEmail, workspace.id(), code, ex.getMessage());
            emailDelivered = false;
        }

        eventPublisher.publishWorkspaceCreated(workspace.id(), code);
        eventPublisher.publishUserRegistered(workspace.id(), user.id(), user.loginEmail());

        auditLogger.log(workspace.id(), user.id(), "WORKSPACE_REGISTER", "workspace",
                workspace.id(), cmd.ipAddress(), cmd.userAgent(),
                Map.of("code", code, "plan", String.valueOf(cmd.subscriptionId()),
                        "status", "PENDING_VERIFICATION",
                        "loginEmail", loginEmail,
                        "contactEmail", contactEmail,
                        "professionalType", String.valueOf(cmd.professionalType())));

        log.info("Workspace {} cree PENDING_VERIFICATION pour user {} loginEmail={} (token TTL {}h)",
                code, user.id(), loginEmail, emailTokenTtlHours);

        // BUG 14 (2026-06-07) — flux paiement-first : on emet des tokens
        // transitoires pour permettre au front d'appeler /api/v1/billing/*
        // immediatement apres le signup. Le JWT a mcp=true mais l'enforcer
        // ChangePasswordEnforcer n'existe QUE dans auth-service — donc les
        // calls vers billing-service /prepare-payment passent. Les tokens
        // seront overwrites par le re-issuance post-paiement.
        if (cmd.deferCredentials()) {
            AuthTokens transient_ = tokenIssuer.issue(user);
            return new Result(workspace.id(), code, user.id(), false,
                    loginEmail, contactEmail,
                    transient_.accessToken(), transient_.refreshToken());
        }

        return new Result(workspace.id(), code, user.id(), emailDelivered,
                loginEmail, contactEmail, null, null);
    }
}
