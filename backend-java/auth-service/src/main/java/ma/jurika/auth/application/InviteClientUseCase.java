package ma.jurika.auth.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.port.AuditLogger;
import ma.jurika.auth.domain.port.EmailSender;
import ma.jurika.auth.domain.port.PasswordHasher;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.auth.domain.service.LoginEmailService;
import ma.jurika.auth.domain.service.TemporaryPasswordGenerator;
import ma.jurika.common.billing.PlanLimitsService;
import ma.jurika.common.exception.ConflictException;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Cree un compte CLIENT et l'attache a un dossier (mode A securise).
 *
 * <p>Flow :
 * <ol>
 *   <li>Verifie que le dossier appartient au workspace courant</li>
 *   <li>Si l'email est deja un user dans le workspace : associe-le au dossier sans recreer</li>
 *   <li>Sinon : cree un user role=CLIENT avec MDP temporaire (12 chars), email pre-verifie</li>
 *   <li>Associe le dossier au client (entreprise_dossiers.client_id)</li>
 *   <li>Envoie un email avec : code workspace + email + MDP temporaire + lien pre-rempli</li>
 * </ol>
 *
 * <p>Securite : le client peut se connecter normalement (workspace + email + MDP), JWT + RLS
 * isolent ses donnees. Aucun accès anonyme via lien public — toujours authentifié.
 */
@Service
public class InviteClientUseCase {

    private static final Logger log = LoggerFactory.getLogger(InviteClientUseCase.class);

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final TemporaryPasswordGenerator tempPasswordGenerator;
    private final EmailSender emailSender;
    private final AuditLogger auditLogger;
    private final String loginBaseUrl;
    /** Sprint Beta (pricing-deploy) — TASK 3. Optionnel : si la conf
     * {@code jurika.plan-limits.enabled=false}, le bean n'existe pas et
     * on continue sans enforcer (pour les tests d'integration Sprint 11). */
    private final PlanLimitsService planLimitsService;
    private final LoginEmailService loginEmailService;

    @PersistenceContext
    private EntityManager em;

    public InviteClientUseCase(UserRepository userRepository,
                                PasswordHasher passwordHasher,
                                TemporaryPasswordGenerator tempPasswordGenerator,
                                EmailSender emailSender,
                                AuditLogger auditLogger,
                                @Value("${jurika.frontend.login-url:http://localhost:5173/login}") String loginBaseUrl,
                                @Autowired(required = false) PlanLimitsService planLimitsService,
                                LoginEmailService loginEmailService) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.tempPasswordGenerator = tempPasswordGenerator;
        this.emailSender = emailSender;
        this.auditLogger = auditLogger;
        this.loginBaseUrl = loginBaseUrl;
        this.planLimitsService = planLimitsService;
        this.loginEmailService = loginEmailService;
    }

    public record Command(UUID workspaceId, String workspaceCode, UUID dossierId,
                           String email, String firstName, String lastName, String phone,
                           UUID invitedBy, String ipAddress, String userAgent) {}

    /** BUG 7 (2026-06-08) — expose loginEmail (identifiant @jurika.ma) en plus
     *  du tempPassword pour transmission par autre canal en cas d'email rejete. */
    public record Result(UUID userId, boolean userCreated, String tempPassword,
                          String loginEmail, String contactEmail) {}

    @Transactional
    public Result execute(Command cmd) {
        if (cmd.email() == null || !cmd.email().contains("@")) {
            throw new ValidationException("Email invalide");
        }
        TenantContext.set(cmd.workspaceId());

        // Verifie dossier existe + appartient au workspace
        @SuppressWarnings("unchecked")
        var dossierRows = (java.util.List<Object[]>) em.createNativeQuery("""
                SELECT id, raison_sociale, client_id FROM entreprise_dossiers
                 WHERE id = ?1 AND workspace_id = ?2
                """)
                .setParameter(1, cmd.dossierId())
                .setParameter(2, cmd.workspaceId())
                .getResultList();
        if (dossierRows.isEmpty()) {
            throw new NotFoundException("Dossier introuvable");
        }
        Object[] dossier = dossierRows.get(0);
        String raisonSociale = String.valueOf(dossier[1]);
        // 2026-06-04 (fix P1) : 1 dossier <-> 1 client max. Si le dossier est
        // deja lie a un autre client, on refuse plutot que d'ecraser. Permet
        // la reinvitation du MEME client (idempotence) en revanche.
        Object existingClientIdObj = dossier[2];
        if (existingClientIdObj != null) {
            UUID existingClientId = (UUID) existingClientIdObj;
            // On verifiera plus tard si c'est le meme user (apres resolution userId).
            // Pour cela on stocke la valeur et on check apres le bloc users existant.
            // Si user n'existe pas, alors par def ce sera un nouveau user != existingClientId.
            Optional<User> maybeExisting = userRepository.findByWorkspaceAndEmail(cmd.workspaceId(), cmd.email());
            UUID targetUserId = maybeExisting.map(User::id).orElse(null);
            if (targetUserId == null || !targetUserId.equals(existingClientId)) {
                throw new ConflictException(
                        "Le dossier '" + raisonSociale + "' est deja lie a un autre client. "
                                + "Un dossier ne peut avoir qu'UN seul client a la fois. "
                                + "Detacher l'ancien client avant d'en inviter un nouveau.");
            }
        }

        // User existe deja ? Le matching se fait sur l'email legacy (= contact_email
        // pour les comptes V28+, = identifiant historique pour les anciens), pour
        // identifier "1 personne = 1 compte CLIENT par cabinet".
        Optional<User> existing = userRepository.findByWorkspaceAndEmail(cmd.workspaceId(), cmd.email());
        String tempPassword = null;
        UUID userId;
        String loginEmail;
        String contactEmail = cmd.email().toLowerCase();
        boolean created = false;
        if (existing.isPresent()) {
            User u = existing.get();
            if (u.role() != Role.CLIENT) {
                throw new ConflictException(
                        "Cet email est deja utilise par un compte " + u.role() + " (non-client)");
            }
            userId = u.id();
            loginEmail = u.loginEmail();
            contactEmail = u.contactEmail();
        } else {
            // 2026-07-04 (decision metier) — un CLIENT ne consomme PAS de siege :
            // le quota du forfait ne porte que sur les EMPLOYE (cf.
            // PlanLimitsService.countUsers, role='EMPLOYE'). Le nombre de clients
            // est ILLIMITE sur tous les plans (1 dataroom <-> 1 client). On ne
            // passe donc PLUS par enforceUserLimit ici, sinon un cabinet ayant
            // atteint son quota d'employes ne pourrait plus inviter de clients.
            // NB : enforceUserLimit reste appele a l'invitation d'un EMPLOYE
            // (InviteEmployeUseCase), la ou le siege est reellement consomme.
            // BUG 7 (2026-06-08) — generation de l'identifiant @jurika.ma (flag-aware).
            loginEmail = loginEmailService.generate(
                    cmd.workspaceId(), cmd.firstName(), cmd.lastName(), cmd.email());
            tempPassword = tempPasswordGenerator.generate();
            String hash = passwordHasher.hash(tempPassword);
            // RG-CA04 : createInvitedUser positionne must_change_password=TRUE
            // + status=PENDING (BUG 6) + login_email/contact_email distincts.
            User u = userRepository.createInvitedUser(
                    cmd.workspaceId(), loginEmail, contactEmail, hash,
                    cmd.firstName(), cmd.lastName(), cmd.phone(),
                    Role.CLIENT);
            // L'email du client est verifie immediatement (l'employe l'a fourni en personne).
            userRepository.markEmailVerified(u.id(), Instant.now());
            userId = u.id();
            created = true;
        }

        // Lier dossier au client. Defense-in-depth : scope workspace_id explicite
        // (l'existence a deja ete verifiee scoped plus haut, mais on ne laisse
        // aucun UPDATE cross-tenant possible sur entreprise_dossiers).
        em.createNativeQuery(
                "UPDATE entreprise_dossiers SET client_id = ?1 WHERE id = ?2 AND workspace_id = ?3")
                .setParameter(1, userId)
                .setParameter(2, cmd.dossierId())
                .setParameter(3, cmd.workspaceId())
                .executeUpdate();

        // Email d'invitation (best-effort)
        // BUG 7 — pre-remplissage avec login_email (identifiant) pas email perso.
        String loginUrl = loginBaseUrl + "?workspace=" + cmd.workspaceCode()
                + "&email=" + java.net.URLEncoder.encode(loginEmail, java.nio.charset.StandardCharsets.UTF_8);
        try {
            // BUG 7 — destinataire = contact_email (perso) ; contenu affiche login_email + contact.
            java.util.Map<String, Object> vars = new java.util.HashMap<>();
            vars.put("brand", "JURIKA");
            vars.put("firstName", cmd.firstName() == null ? "" : cmd.firstName());
            vars.put("workspaceCode", cmd.workspaceCode());
            vars.put("email", loginEmail);
            vars.put("loginEmail", loginEmail);
            vars.put("contactEmail", contactEmail);
            vars.put("temporaryPassword", tempPassword == null ? "(votre mot de passe existant)" : tempPassword);
            vars.put("loginUrl", loginUrl);
            vars.put("raisonSociale", raisonSociale);
            vars.put("subject", "Acces Data Room JURIKA");
            emailSender.sendTemplated(contactEmail,
                    "JURIKA - Acces a votre Data Room",
                    "client-invite", vars);
        } catch (Exception ex) {
            log.warn("Email invite client echec contact={}: {} -- DEV ONLY tempPassword={}",
                    contactEmail, ex.getMessage(), tempPassword);
        }

        auditLogger.log(cmd.workspaceId(), cmd.invitedBy(), "CLIENT_INVITED", "dossier",
                cmd.dossierId(), cmd.ipAddress(), cmd.userAgent(),
                Map.of("client_contact_email", contactEmail,
                        "generated_login_email", loginEmail,
                        "user_id", userId.toString(),
                        "created_new_user", String.valueOf(created)));

        return new Result(userId, created, tempPassword, loginEmail, contactEmail);
    }
}
