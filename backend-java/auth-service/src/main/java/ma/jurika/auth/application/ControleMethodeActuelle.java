package ma.jurika.auth.application;

import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.port.AuditLogger;
import ma.jurika.auth.domain.port.EncryptionService;
import ma.jurika.auth.domain.port.TotpService;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.common.exception.BusinessException;
import ma.jurika.common.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.OptionalLong;

/**
 * Controle du code de la methode de double authentification ACTUELLE.
 *
 * <ul>
 *   <li>{@link #verifierTotp} : code de l'application (anti-rejeu RFC 6238, echecs comptes et
 *       verrouillage RG-AU37) ; regles reprises telles quelles de Verify2faUseCase (connexion),
 *       qui l'appelle ;</li>
 *   <li>{@link #exigerCodeActuel} (recette R2, 2026-10-17) : avant d'activer le SMS sur un compte
 *       qui a deja une methode active, le code de cette methode est exige (sinon un jeton d'acces
 *       derobe suffirait a detourner la double authentification vers un autre numero).</li>
 * </ul>
 */
@Service
public class ControleMethodeActuelle {

    public static final String CODE_REQUIS = "2FA_CODE_ACTUEL_REQUIS";

    private final UserRepository userRepository;
    private final TotpService totpService;
    private final EncryptionService encryptionService;
    private final AuditLogger auditLogger;
    private final VerifySmsOtpUseCase verifySmsOtpUseCase;
    private final int maxFailedAttempts;
    private final Duration lockDuration;

    public ControleMethodeActuelle(UserRepository userRepository,
                                   TotpService totpService,
                                   EncryptionService encryptionService,
                                   AuditLogger auditLogger,
                                   VerifySmsOtpUseCase verifySmsOtpUseCase,
                                   @Value("${jurika.auth.max-failed-attempts:5}") int maxFailedAttempts,
                                   @Value("${jurika.auth.lock-duration-minutes:15}") int lockDurationMinutes) {
        this.userRepository = userRepository;
        this.totpService = totpService;
        this.encryptionService = encryptionService;
        this.auditLogger = auditLogger;
        this.verifySmsOtpUseCase = verifySmsOtpUseCase;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockDuration = Duration.ofMinutes(lockDurationMinutes);
    }

    /**
     * Exige et verifie le code de la methode active, si le compte en a une. Methode SMS : le
     * code est celui d'un SMS "2FA_LOGIN" demande au numero actuel ({@code /2fa/sms/send}).
     */
    public void exigerCodeActuel(User user, String codeActuel, String ipAddress, String userAgent) {
        String methode = user.methodeDeuxFacteursActive();
        if (methode == null) return;
        if (user.isLocked()) {
            throw new UnauthorizedException("Compte verrouill\u00e9 temporairement : r\u00e9essayez plus tard.");
        }
        if (codeActuel == null || codeActuel.isBlank()) {
            throw new BusinessException(CODE_REQUIS, "SMS".equals(methode)
                    ? "Pour changer de m\u00e9thode, saisissez d\u2019abord le code re\u00e7u par SMS sur votre num\u00e9ro actuel."
                    : "Pour changer de m\u00e9thode, saisissez d\u2019abord le code affich\u00e9 par votre application d\u2019authentification.");
        }
        if ("SMS".equals(methode)) {
            verifySmsOtpUseCase.execute(new VerifySmsOtpUseCase.Command(
                    user.id(), user.workspaceId(), "2FA_LOGIN", codeActuel.trim(), ipAddress, userAgent));
        } else {
            verifierTotp(user, codeActuel, ipAddress, userAgent);
        }
    }

    /** Verifie un code TOTP ; leve UnauthorizedException (echec compte, verrouillage). */
    public void verifierTotp(User user, String code, String ipAddress, String userAgent) {
        if (user.totpSecretEncrypted() == null) {
            throw new UnauthorizedException("La double authentification par application n\u2019est pas activ\u00e9e pour ce compte.");
        }
        int totpCode;
        try {
            totpCode = Integer.parseInt(code.replaceAll("\\s+", ""));
        } catch (NumberFormatException e) {
            throw new UnauthorizedException("Code de double authentification incorrect.");
        }
        String secret = encryptionService.decrypt(user.totpSecretEncrypted());
        // Lot L0 (E10d), anti-rejeu (RFC 6238, section 5.2) : le code n'est
        // accepte que si son pas de temps est enregistre ici, de facon
        // atomique, comme strictement posterieur au dernier pas accepte.
        OptionalLong pas = totpService.pasDuCode(secret, totpCode);
        boolean rejeu = pas.isPresent() && !userRepository.consommerPasTotp(user.id(), pas.getAsLong());
        if (pas.isEmpty() || rejeu) {
            // HIGH-5 (audit 2026-06-02) : incrementer failed_login_attempts pour
            // bloquer le brute-force online sur 10^6 codes TOTP. Apres N echecs,
            // lock le compte pour lock-duration-minutes (RG-AU37).
            short newAttempts = (short) (user.failedLoginAttempts() + 1);
            Instant lockUntil = newAttempts >= maxFailedAttempts
                    ? Instant.now().plus(lockDuration)
                    : null;
            userRepository.incrementFailedLogin(user.id(), lockUntil);
            auditLogger.log(user.workspaceId(), user.id(), "LOGIN_2FA_FAILED", "user", user.id(),
                    ipAddress, userAgent,
                    Map.of("method", "TOTP", "attempts", newAttempts,
                            "locked", lockUntil != null, "rejeu", rejeu));
            throw new UnauthorizedException(lockUntil != null
                    ? "Compte verrouill\u00e9 temporairement apr\u00e8s trop d\u2019\u00e9checs de double authentification : r\u00e9essayez plus tard."
                    : "Code de double authentification incorrect.");
        }
        // Succes TOTP : reset le compteur d'echecs (SMS le fait deja dans VerifySmsOtpUseCase).
        userRepository.resetFailedLogin(user.id());
    }
}
