package ma.jurika.auth.application;

import ma.jurika.auth.domain.model.AuthTokens;
import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.model.UserStatus;
import ma.jurika.auth.domain.port.AuditLogger;
import ma.jurika.auth.domain.port.EmailSender;
import ma.jurika.auth.domain.port.EncryptionService;
import ma.jurika.auth.domain.port.EventPublisher;
import ma.jurika.auth.domain.port.RefreshTokenRepository;
import ma.jurika.auth.domain.port.TokenIssuer;
import ma.jurika.auth.domain.port.TotpService;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

@Service
public class Setup2faUseCase {

    private static final Logger log = LoggerFactory.getLogger(Setup2faUseCase.class);
    private static final DateTimeFormatter EVENT_AT_FMT = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm 'GMT'")
            .withZone(ZoneId.of("Africa/Casablanca"));

    private final UserRepository userRepository;
    private final TotpService totpService;
    private final EncryptionService encryptionService;
    private final EventPublisher eventPublisher;
    private final AuditLogger auditLogger;
    private final EmailSender emailSender;
    private final TokenIssuer tokenIssuer;
    private final RefreshTokenRepository refreshTokenRepository;
    private final String issuer;

    public Setup2faUseCase(UserRepository userRepository,
                           TotpService totpService,
                           EncryptionService encryptionService,
                           EventPublisher eventPublisher,
                           AuditLogger auditLogger,
                           EmailSender emailSender,
                           TokenIssuer tokenIssuer,
                           RefreshTokenRepository refreshTokenRepository,
                           @Value("${jurika.auth.totp-issuer:JURIKA}") String issuer) {
        this.userRepository = userRepository;
        this.totpService = totpService;
        this.encryptionService = encryptionService;
        this.eventPublisher = eventPublisher;
        this.auditLogger = auditLogger;
        this.emailSender = emailSender;
        this.tokenIssuer = tokenIssuer;
        this.refreshTokenRepository = refreshTokenRepository;
        this.issuer = issuer;
    }

    public record InitResult(String secret, String otpAuthUri, String qrCodePngBase64) {}

    public record SetupOptions(boolean totpAvailable, boolean smsAvailable, String maskedPhone) {}

    /**
     * Renvoie au frontend les methodes 2FA disponibles pour cet utilisateur.
     * TOTP est toujours dispo ; SMS exige un numero de telephone enregistre
     * en base (saisi pendant l'inscription).
     *
     * <p>Utilise par {@code Choose2faMethodPage} pour masquer le bouton SMS
     * quand le compte n'a pas de telephone — evite un appel 400 frustrant et
     * une mauvaise UX (RG-AU30 : le choix doit etre explicite et coherent).
     */
    @Transactional(readOnly = true)
    public SetupOptions getSetupOptions(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Utilisateur inconnu"));
        boolean hasPhone = user.phone() != null && !user.phone().isBlank();
        return new SetupOptions(true, hasPhone, hasPhone ? maskPhone(user.phone()) : null);
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "***";
        String last4 = phone.substring(phone.length() - 4);
        return phone.substring(0, phone.length() - 4).replaceAll("\\d", "*") + last4;
    }

    @Transactional
    public InitResult initiate(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Utilisateur inconnu"));

        // HIGH-7 (audit 2026-06-02) : refuser si TOTP deja active (anti-takeover).
        // Avant ce fix, n'importe quel detenteur d'un access token (legitime ou XSS)
        // pouvait appeler /2fa/setup-totp pour ecraser le secret du vrai user et
        // detourner son 2FA. Pour changer de methode, il faut passer par un endpoint
        // dedie change-2fa-method exigeant re-auth (cf. backlog).
        if (user.totpEnabled() && "TOTP".equals(user.twofaMethod())) {
            throw new ValidationException(
                    "2FA_ALREADY_CONFIGURED : le 2FA TOTP est deja configure pour ce compte. "
                            + "Pour changer, passez par /auth/2fa/change-method (re-auth requise).");
        }

        // HIGH-10 (audit 2026-06-02) : refuser si email non verifie. Le wizard Sprint 11
        // force le workspace en ACTIVE sans verification email -> sans ce garde-fou,
        // un attaquant qui devine le code workspace + email du cabinet pourrait
        // configurer son propre 2FA sur le compte.
        if (!user.isEmailVerified()) {
            throw new ValidationException(
                    "EMAIL_VERIFICATION_REQUIRED : verifiez votre adresse email "
                            + "(clic sur le lien recu) avant de configurer le 2FA.");
        }

        String secret = totpService.generateSecret();
        // BUG 7 (audit 2026-06-08) — label TOTP base sur loginEmail (identifiant
        // stable) plutot que user.email() pour ne pas suggerer aux users qu'ils
        // peuvent changer leur QR code en changeant leur contact_email.
        String label = issuer + ":" + user.loginEmail();
        String otpUri = totpService.buildOtpAuthUri(issuer, label, secret);
        byte[] png = totpService.renderQrCodePng(otpUri, 240);

        String encrypted = encryptionService.encrypt(secret);
        userRepository.updateTotpSecret(user.id(), encrypted, false);

        auditLogger.log(user.workspaceId(), user.id(), "2FA_SETUP_INITIATED", "user", user.id(),
                null, null, Map.of());

        return new InitResult(secret, otpUri, Base64.getEncoder().encodeToString(png));
    }

    /**
     * Confirme l'activation TOTP et renvoie un NOUVEAU couple access+refresh
     * token avec {@code requires2faSetup=false} dans le claim.
     *
     * <p><b>Sprint hotfix 2026-06-04</b> : avant ce fix, ce use case renvoyait
     * {@code void} et le caller continuait avec son ancien token (claim
     * {@code r2s=true}). {@link ma.jurika.auth.infrastructure.security.Setup2faRequiredEnforcer}
     * lit ce flag depuis le JWT (pas la DB) et bloquait toutes les requetes
     * suivantes a 403 SETUP_2FA_REQUIRED — meme si la DB avait deja
     * {@code twofa_method='TOTP'} + {@code totp_enabled=TRUE}. Le frontend
     * tombait dans une boucle "configurer 2FA → succes → bloque a nouveau".
     *
     * @param ipAddress IP du client (pour stocker la session refresh, peut etre null)
     * @param userAgent User-Agent (pour stocker la session refresh, peut etre null)
     */
    @Transactional
    public AuthTokens confirm(UUID userId, int totpCode, String ipAddress, String userAgent) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Utilisateur inconnu"));
        if (user.totpSecretEncrypted() == null) {
            throw new ValidationException("Aucun setup 2FA en cours, appelez d'abord /setup-2fa");
        }
        String secret = encryptionService.decrypt(user.totpSecretEncrypted());
        // Lot L0 (E10d), anti-rejeu : meme regle que la connexion, le pas du code
        // de confirmation est consomme de facon atomique.
        OptionalLong pas = totpService.pasDuCode(secret, totpCode);
        if (pas.isEmpty() || !userRepository.consommerPasTotp(user.id(), pas.getAsLong())) {
            throw new ValidationException("Code 2FA invalide");
        }
        userRepository.updateTotpSecret(user.id(), user.totpSecretEncrypted(), true);
        // Active TOTP comme methode 2FA preferentielle
        userRepository.updateTwofaMethod(user.id(), "TOTP");
        eventPublisher.publishUser2faEnabled(user.workspaceId(), user.id());
        auditLogger.log(user.workspaceId(), user.id(), "2FA_ENABLED", "user", user.id(),
                null, null, Map.of("method", "TOTP"));

        // Sprint 3 / TASK 2 — notification 2fa-enabled (best-effort)
        try {
            Map<String, Object> vars = new HashMap<>();
            vars.put("firstName", user.firstName() != null ? user.firstName() : user.email());
            vars.put("method", "Google Authenticator (TOTP)");
            vars.put("enabledAt", EVENT_AT_FMT.format(Instant.now()));
            vars.put("unsubscribeUrl", null);
            // BUG 7 (chore 2026-06-08) — fragment identityFooter de _base.html
            vars.put("loginEmail", user.loginEmail());
            vars.put("contactEmail", user.contactEmail());
            // BUG 7 (2026-06-08) — notif vers contact_email.
            emailSender.sendTemplated(user.contactEmail(),
                    "JURIKA - double authentification activee",
                    "2fa-enabled",
                    vars);
        } catch (RuntimeException ex) {
            log.warn("Echec envoi email 2fa-enabled pour user={} : {}",
                    user.id(), ex.getMessage());
        }

        // Hotfix 2026-06-04 — re-emettre les tokens avec r2s=false.
        // Re-load pour que User.requires2faSetup() lise twofaMethod="TOTP" +
        // totpEnabled=true (donc renvoie false) et que tokenIssuer.issue()
        // mette le claim r2s=false.
        User refreshed = userRepository.findById(user.id())
                .orElseThrow(() -> new NotFoundException("Utilisateur disparu apres update"));

        // BUG 6 (decision 2026-06-08 chore) — Fin d'onboarding atteinte : si le
        // user est encore PENDING ET a fini son change-MDP (mcp=false) ET vient
        // de confirmer la 2FA (r2s=false), on bascule ACTIVE. C'est ICI que la
        // transition se fait, plus dans LoginUseCase. Garantit que PENDING dure
        // tant qu'au moins une etape d'onboarding reste.
        if (refreshed.status() == UserStatus.PENDING
                && !refreshed.mustChangePassword()
                && !refreshed.requires2faSetup()) {
            userRepository.setStatus(refreshed.id(), UserStatus.ACTIVE);
            auditLogger.log(refreshed.workspaceId(), refreshed.id(),
                    "USER_ACTIVATED_BY_ONBOARDING", "user", refreshed.id(),
                    ipAddress, userAgent,
                    Map.of("previousStatus", "PENDING", "onboardingStep", "2fa_confirm_totp"));
            refreshed = userRepository.findById(refreshed.id())
                    .orElseThrow(() -> new NotFoundException("Utilisateur disparu apres setStatus"));
        }

        AuthTokens tokens = tokenIssuer.issue(refreshed);
        TokenIssuer.ParsedRefreshToken parsed = tokenIssuer.parseRefreshToken(tokens.refreshToken());
        refreshTokenRepository.store(refreshed.id(), refreshed.workspaceId(),
                parsed.hash(), tokens.refreshExpiresAt(), userAgent, ipAddress);
        return tokens;
    }

    /**
     * Renvoie uniquement le PNG (binaire) du QR code de l'utilisateur, pour usage
     * direct dans une balise &lt;img&gt;. Genere un nouveau secret s'il n'y en a pas
     * encore ou si le 2FA n'est pas confirme.
     */
    @Transactional
    public byte[] getQrCodePng(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Utilisateur inconnu"));

        String secret;
        if (user.totpSecretEncrypted() == null) {
            secret = totpService.generateSecret();
            String encrypted = encryptionService.encrypt(secret);
            userRepository.updateTotpSecret(user.id(), encrypted, false);
        } else {
            secret = encryptionService.decrypt(user.totpSecretEncrypted());
        }

        // BUG 7 (audit 2026-06-08) — label TOTP base sur loginEmail (identifiant
        // stable) plutot que user.email() pour ne pas suggerer aux users qu'ils
        // peuvent changer leur QR code en changeant leur contact_email.
        String label = issuer + ":" + user.loginEmail();
        String otpUri = totpService.buildOtpAuthUri(issuer, label, secret);
        return totpService.renderQrCodePng(otpUri, 240);
    }
}
