package ma.jurika.auth.application;

import ma.jurika.auth.domain.model.ProfessionalType;
import ma.jurika.auth.domain.port.IpRateLimiter;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.auth.infrastructure.persistence.WorkspaceEntity;
import ma.jurika.auth.infrastructure.persistence.WorkspaceJpaRepository;
import ma.jurika.common.audit.Auditable;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.TooManyRequestsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Sprint 11 TASK 2 — Signup self-service "cabinet" depuis la landing publique
 * (wizard 5 etapes : Cabinet / Admin / Securite / 2FA / Recap).
 *
 * <p>Composition : reutilise {@link RegisterWorkspaceUseCase} pour toute la
 * logique eprouvee (DNS MX, phone validation, MDP temp, audit, events, email
 * welcome). Puis enrichit le workspace nouvellement cree avec :
 *  - plan preselectionne lu depuis le wizard (selected_plan)
 *  - identifiants legaux Maroc (ICE 15 ch, IF 8 ch, RC, ville) — RG-SU04
 *  - flag created_via_sprint11_wizard = TRUE
 *
 * <p>UX-3 (2026-06-02) : le concept de trial 14 jours est SUPPRIME du produit.
 * Les colonnes trial_started_at / trial_ends_at / trial_status existent encore
 * en base (V16) mais ne sont plus posees (cleanup migration V20+ pendant).
 */
@Service
public class SignupCabinetUseCase {

    private static final Logger log = LoggerFactory.getLogger(SignupCabinetUseCase.class);

    // Spec directeur 2026-06-02 — codes plan canoniques.
    private static final Set<String> ALLOWED_PLANS = Set.of("essentiel", "business", "entreprise");

    private final RegisterWorkspaceUseCase registerWorkspaceUseCase;
    private final WorkspaceJpaRepository workspaceJpaRepository;
    private final UserRepository userRepository;
    private final IpRateLimiter rateLimiter;
    /**
     * Fix 2026-06-07 : flag dev pour auto-verifier l'email a la creation du
     * workspace via le wizard self-service. Par defaut FALSE (= prod : on
     * exige le click sur le lien d'activation, RG-AU26). En dev, mettre
     * JURIKA_SIGNUP_AUTO_VERIFY_EMAIL=true dans .env.local pour que le
     * user puisse passer directement au /setup-2fa sans cliquer dans le mail.
     *
     * <p>Justification : sur un wizard self-service ou le user fournit lui-meme
     * son email + passe un paiement, la double-verif n'apporte pas de securite
     * supplementaire (cf. patterns Stripe/Vercel/Linear). Le flag permet de
     * desactiver le step "click email" sans toucher au code RG-AU26 pour les
     * autres chemins (POST /register legacy, invite-employee, invite-client).
     */
    private final boolean autoVerifyEmail;

    public SignupCabinetUseCase(RegisterWorkspaceUseCase registerWorkspaceUseCase,
                                WorkspaceJpaRepository workspaceJpaRepository,
                                UserRepository userRepository,
                                IpRateLimiter rateLimiter,
                                @Value("${jurika.signup.auto-verify-email:false}") boolean autoVerifyEmail) {
        this.registerWorkspaceUseCase = registerWorkspaceUseCase;
        this.workspaceJpaRepository = workspaceJpaRepository;
        this.userRepository = userRepository;
        this.rateLimiter = rateLimiter;
        this.autoVerifyEmail = autoVerifyEmail;
        if (autoVerifyEmail) {
            log.warn("Signup auto-verify-email ACTIVE -- l'email est marque verifie au signup, sans click sur le lien. Ne pas activer en prod.");
        }
    }

    /**
     * Simplification inscription (2026-07-13) : un seul jeu de champs, sans
     * IF/RC ni double email. {@code email} est l'unique email professionnel
     * saisi — il alimente a la fois le contact_email du workspace ET celui du
     * SUPERVISEUR. {@code ice} est optionnel (completable ensuite via les
     * parametres du cabinet). La ville reste conservee.
     */
    public record Command(
            String workspaceName,
            String firstName,
            String lastName,
            String phone,
            String email,
            String ice,
            String city,
            String selectedPlan,
            // Type de profil declare au signup (onboarding 2026-06-24). NULL
            // tolere pour les chemins legacy ; le wizard envoie toujours une valeur.
            String professionalType,
            String ipAddress,
            String userAgent,
            // BUG 14 (2026-06-07) — defere l'envoi du MDP temp jusqu'a la
            // validation du paiement. Pour les chemins legacy (tests),
            // utiliser le constructeur sans ce flag (defaut false).
            boolean deferCredentials
    ) {
        public Command(String workspaceName,
                       String firstName, String lastName, String phone, String email,
                       String ice, String city,
                       String selectedPlan, String professionalType,
                       String ipAddress, String userAgent) {
            this(workspaceName, firstName, lastName, phone, email,
                 ice, city, selectedPlan, professionalType,
                 ipAddress, userAgent, false);
        }
    }

    /**
     * BUG 7 (2026-06-08) — Result expose loginEmail + contactEmail genere a
     * l'inscription pour que le wizard signup affiche "voici votre identifiant
     * de connexion karim.benali@jurika.ma" sur l'ecran de succes.
     *
     * BUG 14 (2026-06-07) — Result expose accessToken/refreshToken transitoires
     * quand deferCredentials=true pour permettre l'appel direct au billing-service
     * depuis le wizard Step Paiement.
     */
    public record Result(UUID workspaceId, String workspaceCode, UUID userId, boolean emailDelivered,
                          String loginEmail, String contactEmail,
                          String accessToken, String refreshToken) {
        public Result(UUID workspaceId, String workspaceCode, UUID userId, boolean emailDelivered) {
            this(workspaceId, workspaceCode, userId, emailDelivered, null, null, null, null);
        }
    }

    @Transactional
    @Auditable(action = "CABINET_SIGNUP", resourceType = "workspace")
    public Result execute(Command cmd) {
        // 1) Rate limit (RG-SU03)
        String key = cmd.ipAddress() == null || cmd.ipAddress().isBlank() ? "unknown" : cmd.ipAddress();
        if (!rateLimiter.tryAcquire("signup-cabinet", key)) {
            throw new TooManyRequestsException("RATE_LIMITED", "Trop de tentatives de creation. Reessayez dans 1 heure.");
        }

        // 2) Validation plan
        String plan = cmd.selectedPlan() == null ? "essentiel" : cmd.selectedPlan().toLowerCase();
        if (!ALLOWED_PLANS.contains(plan)) {
            throw new IllegalArgumentException("Plan inconnu : " + cmd.selectedPlan());
        }

        // 2 bis) Validation type de profil (onboarding 2026-06-24). NULL/vide
        // tolere (workspace sans type). Toute valeur non-null doit appartenir
        // aux 8 ProfessionalType — sinon rejet (defense en profondeur en plus
        // du @Pattern du DTO, car la Command est aussi atteignable hors HTTP).
        String professionalType = null;
        if (cmd.professionalType() != null && !cmd.professionalType().isBlank()) {
            professionalType = ProfessionalType.fromString(cmd.professionalType())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Type de profil inconnu : " + cmd.professionalType()))
                    .name();
        }

        // 2 ter) Denomination par type de profil (2026-07-27). Obligatoire pour
        // les structures (ENTREPRISE / CENTRE_AFFAIRES), optionnelle pour les
        // profils individuels : si laissee vide, fallback serveur « Prenom Nom »
        // — on ne persiste JAMAIS une denomination vide en base.
        String workspaceName = cmd.workspaceName() == null ? "" : cmd.workspaceName().trim();
        boolean structureType = "ENTREPRISE".equals(professionalType)
                || "CENTRE_AFFAIRES".equals(professionalType);
        if (workspaceName.isEmpty()) {
            if (structureType) {
                throw new IllegalArgumentException(
                        "Raison sociale obligatoire pour ce type de profil.");
            }
            workspaceName = (safe(cmd.firstName()) + " " + safe(cmd.lastName())).trim();
        }

        // 3) Delegation au use case existant (DNS MX, phone, MDP temp, audit, events, email).
        // Simplification 2026-07-13 : un seul email saisi (cmd.email()) devient le
        // contact_email du workspace ET du SUPERVISEUR (plus de double saisie).
        RegisterWorkspaceUseCase.Result base = registerWorkspaceUseCase.execute(
                new RegisterWorkspaceUseCase.Command(
                        workspaceName,
                        cmd.email(),
                        null,
                        cmd.firstName(),
                        cmd.lastName(),
                        cmd.phone(),
                        cmd.email(),
                        null,
                        cmd.ipAddress(),
                        cmd.userAgent(),
                        plan,
                        professionalType,
                        cmd.deferCredentials()
                )
        );

        // 4) Enrichissement workspace : plan + ICE (optionnel) + ville + activation immediate.
        // IF/RC retires du signup (2026-07-13) — n'etaient lus nulle part.
        WorkspaceEntity workspace = workspaceJpaRepository.findById(base.workspaceId())
                .orElseThrow(() -> new NotFoundException("Workspace introuvable apres creation : " + base.workspaceId()));

        String ice = (cmd.ice() == null || cmd.ice().isBlank()) ? null : cmd.ice().trim();
        workspace.setSelectedPlan(plan);
        workspace.setIce(ice);
        workspace.setCity(cmd.city());
        workspace.setProfessionalType(professionalType); // null tolere (workspace sans type)
        workspace.setCreatedViaSprint11Wizard(true);
        // RegisterWorkspaceUseCase cree le workspace en PENDING_VERIFICATION (RG-AU26/27).
        // Le wizard self-service livre un acces immediat : on transitionne ACTIVE pour
        // que workspace-check ne renvoie pas 401 "suspendu ou desactive".
        workspace.setStatus("ACTIVE");
        workspaceJpaRepository.save(workspace);

        // Fix 2026-06-07 : si JURIKA_SIGNUP_AUTO_VERIFY_EMAIL=true on marque
        // email_verified_at=NOW() pour que /setup-2fa ne renvoie pas
        // 400 EMAIL_VERIFICATION_REQUIRED. Le user passe directement au choix
        // de la methode 2FA sans cliquer sur le lien d'activation.
        if (autoVerifyEmail) {
            userRepository.markEmailVerified(base.userId(), Instant.now());
            log.info("Cabinet signup: email auto-verifie pour user={} (flag dev jurika.signup.auto-verify-email=true)",
                    base.userId());
        }

        log.info("Cabinet signup completed: workspace={} code={} plan={}",
                workspace.getId(), base.workspaceCode(), plan);

        return new Result(base.workspaceId(), base.workspaceCode(), base.userId(),
                base.emailDelivered(),
                base.loginEmail(), base.contactEmail(),
                base.accessToken(), base.refreshToken());
    }

    /** Null-safe : evite un NPE dans le fallback « Prenom Nom » (hors chemin HTTP @NotBlank). */
    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
