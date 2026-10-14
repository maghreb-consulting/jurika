package ma.jurika.auth.application;

import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.model.UserStatus;
import ma.jurika.auth.domain.port.AuditLogger;
import ma.jurika.auth.domain.port.EmailSender;
import ma.jurika.auth.domain.port.PasswordHasher;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.auth.domain.port.WorkspaceRepository;
import ma.jurika.auth.domain.service.LoginEmailService;
import ma.jurika.auth.domain.service.TemporaryPasswordGenerator;
import ma.jurika.common.billing.PlanLimitsService;
import ma.jurika.common.exception.ConflictException;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * HIGH-13 (audit 2026-06-02) — Invite collaborateur interne EMPLOYE ou SUPERVISEUR
 * dans un workspace (cabinet juridique). Bloqueur V1 sans equivalent : seul
 * InviteClient existait, et un cabinet a plusieurs personnes ne pouvait pas
 * onboarder ses propres employes.
 *
 * <p>Differences avec InviteClientUseCase :
 *  - role EMPLOYE ou SUPERVISEUR (jamais CLIENT)
 *  - reserve aux SUPERVISEUR du workspace (un EMPLOYE ne peut pas inviter)
 *  - email immediatement marque verifie (l'inviteur l'a fourni en personne)
 *  - MDP temp 12 chars + must_change_password=TRUE
 *  - lien direct vers /login avec le workspace pre-rempli
 *
 * <p>Securite : l'inviteur doit etre SUPERVISEUR du MEME workspace (RBAC).
 * Audit log {@code EMPLOYE_INVITED} avec invitedBy + role.
 */
@Service
public class InviteEmployeUseCase {

    private static final Logger log = LoggerFactory.getLogger(InviteEmployeUseCase.class);
    private static final Set<Role> ALLOWED_ROLES = Set.of(Role.EMPLOYE, Role.SUPERVISEUR);

    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final TemporaryPasswordGenerator tempPasswordGenerator;
    private final PasswordHasher passwordHasher;
    private final EmailSender emailSender;
    private final AuditLogger auditLogger;
    private final PlanLimitsService planLimitsService;
    private final LoginEmailService loginEmailService;
    private final String loginBaseUrl;

    public InviteEmployeUseCase(WorkspaceRepository workspaceRepository,
                                UserRepository userRepository,
                                TemporaryPasswordGenerator tempPasswordGenerator,
                                PasswordHasher passwordHasher,
                                EmailSender emailSender,
                                AuditLogger auditLogger,
                                // 2026-06-04 (fix P1) : enforce quota EMPLOYE selon le plan
                                // (autoSupplied @Nullable pour rester compat tests qui ne
                                // bootent pas jurika-common.PlanLimitsService).
                                @org.springframework.beans.factory.annotation.Autowired(required = false) PlanLimitsService planLimitsService,
                                LoginEmailService loginEmailService,
                                @Value("${jurika.email.login-base-url:${FRONTEND_URL:http://localhost:5173}/login}") String loginBaseUrl) {
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
        this.tempPasswordGenerator = tempPasswordGenerator;
        this.passwordHasher = passwordHasher;
        this.emailSender = emailSender;
        this.auditLogger = auditLogger;
        this.planLimitsService = planLimitsService;
        this.loginEmailService = loginEmailService;
        this.loginBaseUrl = loginBaseUrl;
    }

    public record Command(UUID workspaceId, String email, String firstName, String lastName,
                           String phone, Role role, UUID invitedBy,
                           String ipAddress, String userAgent) {}

    /** BUG 7 (2026-06-08) — Result expose {@code loginEmail} (identifiant
     *  genere) en plus du tempPassword pour que l'inviteur puisse le
     *  transmettre au membre par un autre canal si l'email est rejete. */
    public record Result(UUID userId, boolean created, String tempPassword,
                          String loginEmail, String contactEmail, boolean emailDelivered) {}

    @Transactional
    public Result execute(Command cmd) {
        if (!ALLOWED_ROLES.contains(cmd.role())) {
            throw new ValidationException(
                    "Seuls un employ\u00e9 ou un superviseur peuvent \u00eatre invit\u00e9s ici. "
                            + "Un client s\u2019invite depuis son dossier.");
        }

        var workspace = workspaceRepository.findById(cmd.workspaceId())
                .orElseThrow(() -> new NotFoundException("Workspace introuvable."));
        TenantContext.set(workspace.id());

        // BUG 7 — anti-duplicate sur contact_email : 1 personne (= 1 email perso)
        // = 1 compte par cabinet. On conserve aussi le check sur email column
        // historique pour eviter qu'un workspace ait deux entrees pour la meme
        // personne (cas mixte transition).
        Optional<User> existing = userRepository.findByWorkspaceAndEmail(workspace.id(), cmd.email());
        if (existing.isPresent()) {
            throw new ConflictException(
                    "Un compte existe d\u00e9j\u00e0 dans ce workspace avec l\u2019adresse " + cmd.email());
        }

        // 2026-06-04 (fix P1) : enforce quota plan pour les invitations EMPLOYE
        // (SUPERVISEUR : pas de quota plan car il y en a au maximum UN par
        // workspace — voir garde-fou immediatement apres).
        // Throw PlanLimitException 402 -> handler global -> frontend affiche upsell.
        if (cmd.role() == Role.EMPLOYE) {
            verifierEmployeEssai(workspace.id());
        }
        if (cmd.role() == Role.EMPLOYE && planLimitsService != null) {
            planLimitsService.enforceUserLimit(workspace.id());
        }

        // 2026-06-08 (BUG 12+ session 5) : un cabinet = UN SEUL SUPERVISEUR.
        // Regle metier explicite (« 1 admin/workspace par contrat »). On compte
        // les SUPERVISEUR du workspace qui ne sont PAS desactives (PENDING +
        // ACTIVE consomment le siege ; INACTIVE le libere puisqu'on peut
        // promouvoir un remplacant apres un depart). 409 CONFLICT si pris.
        if (cmd.role() == Role.SUPERVISEUR) {
            long existingSupervisors = userRepository
                    .findByWorkspaceAndRoles(workspace.id(), Set.of(Role.SUPERVISEUR)).stream()
                    .filter(u -> u.status() != UserStatus.INACTIVE)
                    .count();
            if (existingSupervisors >= 1) {
                throw new ConflictException(
                        "Un cabinet n\u2019a qu\u2019un seul superviseur. "
                                + "D\u00e9sactivez le superviseur actuel avant d\u2019en inviter un nouveau, "
                                + "ou invitez ce membre en tant qu\u2019employ\u00e9.");
            }
        }

        // BUG 7 (2026-06-08) — generation de l'identifiant @jurika.ma. Si le
        // flag est off, loginEmailService renvoie cmd.email() (legacy).
        String loginEmail = loginEmailService.generate(
                workspace.id(), cmd.firstName(), cmd.lastName(), cmd.email());
        String contactEmail = cmd.email().toLowerCase();

        String tempPassword = tempPasswordGenerator.generate();
        String hash = passwordHasher.hash(tempPassword);
        User u = userRepository.createInvitedUser(
                workspace.id(), loginEmail, contactEmail, hash,
                cmd.firstName(), cmd.lastName(), cmd.phone(),
                cmd.role());
        // Email valide d'entree (inviteur en personne) -> le user peut setup 2FA immediatement
        userRepository.markEmailVerified(u.id(), Instant.now());

        // BUG 7 — le pre-remplissage du formulaire de login utilise l'identifiant
        // (login_email), pas l'email perso (contact).
        String loginUrl = loginBaseUrl + "?workspace=" + workspace.code()
                + "&email=" + java.net.URLEncoder.encode(loginEmail, java.nio.charset.StandardCharsets.UTF_8);

        Map<String, Object> vars = new HashMap<>();
        vars.put("brand", "JURIKA");
        vars.put("firstName", cmd.firstName() == null ? "" : cmd.firstName());
        vars.put("workspaceCode", workspace.code());
        vars.put("workspaceName", workspace.name());
        // BUG 7 — variables template enrichies : email = loginEmail (compat
        // template existant), loginEmail explicite, contactEmail.
        vars.put("email", loginEmail);
        vars.put("loginEmail", loginEmail);
        vars.put("contactEmail", contactEmail);
        vars.put("temporaryPassword", tempPassword);
        vars.put("roleLabel", cmd.role() == Role.SUPERVISEUR ? "Superviseur" : "Employe");
        vars.put("loginUrl", loginUrl);
        vars.put("phishingWarning",
                "JURIKA ne vous demandera jamais votre mot de passe, ni par courriel, ni par SMS, ni par t\u00e9l\u00e9phone. "
                        + "En cas de message suspect, \u00e9crivez imm\u00e9diatement \u00e0 support@jurika.ma.");
        vars.put("subject", "Invitation \u00e0 rejoindre " + workspace.name() + " sur JURIKA");

        boolean delivered;
        try {
            // BUG 7 — destinataire = contact_email (email perso de la personne).
            emailSender.sendTemplated(contactEmail,
                    "Invitation \u00e0 rejoindre " + workspace.name() + " sur JURIKA",
                    "employe-invite", vars);
            delivered = true;
        } catch (RuntimeException ex) {
            log.warn("Email invite employe echec workspace={} target={} : {}",
                    workspace.code(), contactEmail, ex.getMessage());
            delivered = false;
        }

        auditLogger.log(workspace.id(), cmd.invitedBy(), "EMPLOYE_INVITED", "user",
                u.id(), cmd.ipAddress(), cmd.userAgent(),
                Map.of("invited_contact_email", contactEmail,
                        "generated_login_email", loginEmail,
                        "role", cmd.role().name(),
                        "emailDelivered", String.valueOf(delivered)));

        return new Result(u.id(), true, tempPassword, loginEmail, contactEmail, delivered);
    }
    /** Lot L7 (E4), RG-ESS-04 : pendant l'essai gratuit, 1 superviseur et 1 employe. */
    private org.springframework.beans.factory.ObjectProvider<ma.jurika.common.trial.EssaiQuotas> essaiQuotas;

    @org.springframework.beans.factory.annotation.Autowired
    public void setEssaiQuotas(org.springframework.beans.factory.ObjectProvider<ma.jurika.common.trial.EssaiQuotas> q) {
        this.essaiQuotas = q;
    }

    private void verifierEmployeEssai(java.util.UUID workspaceId) {
        ma.jurika.common.trial.EssaiQuotas q = essaiQuotas == null ? null : essaiQuotas.getIfAvailable();
        if (q != null) q.verifierNouvelEmploye(workspaceId);
    }

}
