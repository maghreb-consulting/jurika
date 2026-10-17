package ma.jurika.auth.application;

import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.port.AuditLogger;
import ma.jurika.auth.domain.port.SmsOtpCodeRepository;
import ma.jurika.auth.domain.port.SmsSender;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.auth.domain.service.VerificationTokenHasher;
import ma.jurika.auth.infrastructure.sms.BoiteSmsDeTest;
import ma.jurika.common.exception.BusinessException;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.UnauthorizedException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.security.TenantContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Genere un code OTP 6 chiffres + l'envoie par SMS.
 * <p>
 * Purposes supportes :
 * <ul>
 *     <li>{@code 2FA_SETUP} : activation de la double authentification par SMS, sur le numero
 *     saisi ({@code telephone}, E.164) ou, a defaut, celui du compte. Le numero n'est enregistre
 *     sur le compte qu'apres verification du code (VerifySmsOtpUseCase) ;</li>
 *     <li>{@code PHONE_VERIFICATION} : verification du numero du compte (meme effet) ;</li>
 *     <li>{@code 2FA_LOGIN} : code a la connexion (apres le mot de passe), ou code de la methode
 *     actuelle avant un changement ; seulement au numero verifie d'un compte dont le SMS est la
 *     methode active.</li>
 * </ul>
 * Recette R2 (2026-10-17) :
 * <ul>
 *     <li>refus explicite si le SMS n'est pas disponible (DisponibiliteSms2fa : aucun fournisseur,
 *     ou simulation hors environnement de test) : rien n'est simule, rien n'est ecrit ;</li>
 *     <li>activation sur un compte qui a deja une methode active : code de cette methode exige
 *     ({@code codeActuel}, ControleMethodeActuelle) ;</li>
 *     <li>envoi simule (environnement de test) : le code est ecrit dans le journal d'auth
 *     (SMS-DEV-STUB, LoggerSmsSender) et depose dans BoiteSmsDeTest ; jamais dans la reponse.</li>
 * </ul>
 * Duree de validite (5 min), essais par code (5) : regles existantes
 * ({@code jurika.auth.sms-otp-ttl-minutes}, {@code sms-otp-max-attempts}), les memes que le code
 * SMS de l'essai. Rate limit : 1 envoi par minute par user et par objet (anti-spam).
 */
@Service
public class SendSmsOtpUseCase {

    /** Meme forme que le telephone de l'essai (V40, InscriptionEssaiService). */
    static final Pattern TELEPHONE_E164 = Pattern.compile("^\\+[1-9]\\d{6,14}$");

    private final UserRepository userRepository;
    private final SmsOtpCodeRepository otpRepository;
    private final VerificationTokenHasher hasher;
    private final SmsSender smsSender;
    private final AuditLogger auditLogger;
    private final DisponibiliteSms2fa disponibiliteSms;
    private final ControleMethodeActuelle controleMethodeActuelle;
    private final ObjectProvider<BoiteSmsDeTest> boiteDeTest;

    private final long otpTtlMinutes;
    private final int maxAttempts;
    private final int digits;

    public SendSmsOtpUseCase(UserRepository userRepository,
                              SmsOtpCodeRepository otpRepository,
                              VerificationTokenHasher hasher,
                              SmsSender smsSender,
                              AuditLogger auditLogger,
                              DisponibiliteSms2fa disponibiliteSms,
                              ControleMethodeActuelle controleMethodeActuelle,
                              ObjectProvider<BoiteSmsDeTest> boiteDeTest,
                              @Value("${jurika.auth.sms-otp-ttl-minutes:5}") long otpTtlMinutes,
                              @Value("${jurika.auth.sms-otp-max-attempts:5}") int maxAttempts,
                              @Value("${jurika.auth.sms-otp-digits:6}") int digits) {
        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
        this.hasher = hasher;
        this.smsSender = smsSender;
        this.auditLogger = auditLogger;
        this.disponibiliteSms = disponibiliteSms;
        this.controleMethodeActuelle = controleMethodeActuelle;
        this.boiteDeTest = boiteDeTest;
        this.otpTtlMinutes = otpTtlMinutes;
        this.maxAttempts = maxAttempts;
        this.digits = digits;
    }

    /**
     * @param telephone  numero a verifier (2FA_SETUP), E.164 ; {@code null} : numero du compte
     * @param codeActuel code de la methode active, exige pour activer le SMS si le compte en a une
     */
    public record Command(UUID userId, UUID workspaceId, String purpose,
                           String telephone, String codeActuel,
                           String ipAddress, String userAgent) {

        /** Forme historique (sans numero ni code actuel). */
        public Command(UUID userId, UUID workspaceId, String purpose, String ipAddress, String userAgent) {
            this(userId, workspaceId, purpose, null, null, ipAddress, userAgent);
        }
    }

    public record Result(UUID otpId, String maskedPhone, Instant expiresAt, boolean simule) {}

    // Recette R2 (2026-10-17) : un echec (UnauthorizedException) ne doit pas annuler le compte
    // des echecs ecrit juste avant (sinon la limite des essais, RG-AU37, etait inoperante :
    // compteur remis a l'etat d'avant a chaque refus).
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public Result execute(Command cmd) {
        disponibiliteSms.exiger();
        TenantContext.set(cmd.workspaceId());

        User user = userRepository.findById(cmd.userId())
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));

        String telephone = numeroDestinataire(user, cmd);

        // Rate limit basique : si OTP existant non utilise et cree il y a moins de 60s, refuser
        otpRepository.findLatestActive(cmd.userId(), cmd.purpose()).ifPresent(existing -> {
            Instant minNext = existing.expiresAt().minus(Duration.ofMinutes(otpTtlMinutes - 1));
            if (Instant.now().isBefore(minNext)) {
                throw new BusinessException("SMS_RATE_LIMIT_WAIT_60_SECONDS", "Un code vient d\u2019\u00eatre envoy\u00e9 : attendez une minute avant d\u2019en demander un autre.");
            }
        });

        if (activation(cmd.purpose())) {
            controleMethodeActuelle.exigerCodeActuel(user, cmd.codeActuel(), cmd.ipAddress(), cmd.userAgent());
        }

        String rawCode = hasher.generateNumericCode(digits);
        String codeHash = hasher.hash(rawCode);
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(otpTtlMinutes));

        UUID otpId = otpRepository.create(
                cmd.workspaceId(), cmd.userId(), telephone, codeHash,
                cmd.purpose(), expiresAt, maxAttempts);

        String body = String.format("JURIKA : votre code de verification est %s. Valable %d minutes.",
                rawCode, otpTtlMinutes);
        smsSender.send(telephone, body);
        boolean simule = disponibiliteSms.simule();
        if (simule) {
            boiteDeTest.ifAvailable(b -> b.deposer(telephone, rawCode, expiresAt));
        }

        auditLogger.log(cmd.workspaceId(), cmd.userId(), "SMS_OTP_SENT", "user",
                cmd.userId(), cmd.ipAddress(), cmd.userAgent(),
                Map.of("purpose", cmd.purpose(), "otpId", otpId.toString(), "simule", simule));

        return new Result(otpId, maskPhone(telephone), expiresAt, simule);
    }

    private static boolean activation(String purpose) {
        return "2FA_SETUP".equals(purpose) || "PHONE_VERIFICATION".equals(purpose);
    }

    private static String numeroDestinataire(User user, Command cmd) {
        if ("2FA_LOGIN".equals(cmd.purpose())) {
            // Jamais vers un numero non verifie, ni pour un compte dont le SMS n'est pas la methode.
            if (!"SMS".equals(user.methodeDeuxFacteursActive())) {
                throw new BusinessException("SMS_2FA_NON_ACTIVE",
                        "La double authentification par SMS n\u2019est pas activ\u00e9e pour ce compte.");
            }
            return user.phone();
        }
        if ("2FA_SETUP".equals(cmd.purpose()) && cmd.telephone() != null && !cmd.telephone().isBlank()) {
            String numero = cmd.telephone().replace(" ", "").replace(".", "").replace("-", "");
            if (!TELEPHONE_E164.matcher(numero).matches()) {
                throw new ValidationException("Num\u00e9ro de t\u00e9l\u00e9phone invalide : saisissez-le au format international, "
                        + "par exemple +212612345678.");
            }
            return numero;
        }
        if (user.phone() == null || user.phone().isBlank()) {
            throw new BusinessException("PHONE_NOT_SET", "Aucun num\u00e9ro de t\u00e9l\u00e9phone n\u2019est enregistr\u00e9 pour ce compte : saisissez votre num\u00e9ro, ou choisissez l\u2019application d\u2019authentification.");
        }
        return user.phone();
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 6) return "***";
        return phone.substring(0, phone.length() - 4).replaceAll("\\d", "*") + phone.substring(phone.length() - 4);
    }
}
