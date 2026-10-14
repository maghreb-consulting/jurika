package ma.jurika.auth.application.essai;

import ma.jurika.auth.application.RegisterWorkspaceUseCase;
import ma.jurika.auth.domain.port.AuditLogger;
import ma.jurika.auth.domain.port.EmailSender;
import ma.jurika.auth.domain.port.IpRateLimiter;
import ma.jurika.auth.domain.port.SmsSender;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.auth.domain.service.VerificationTokenHasher;
import ma.jurika.auth.infrastructure.essai.EssaiJdbc;
import ma.jurika.auth.infrastructure.essai.EssaiJdbc.Demande;
import ma.jurika.auth.infrastructure.persistence.WorkspaceEntity;
import ma.jurika.auth.infrastructure.persistence.WorkspaceJpaRepository;
import ma.jurika.common.exception.BusinessException;
import ma.jurika.common.exception.ConflictException;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.TooManyRequestsException;
import ma.jurika.common.exception.UnauthorizedException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.security.TenantContext;
import ma.jurika.common.trial.EssaiRegles;
import ma.jurika.common.validation.DnsMxValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Lot L7 (E4) : inscription a l'essai gratuit (CDC 4.2).
 *
 * <ol>
 *   <li>RG-ESS-01 : cinq informations, et seulement celles-ci (nom, prenom,
 *       organisation, telephone, adresse electronique) ; aucun ICE ;</li>
 *   <li>RG-CTR-10 : conditions d'utilisation de l'essai acceptees (case non
 *       pre-cochee cote ecran ; refus serveur si non cochee) et tracees ;</li>
 *   <li>RG-ESS-02 : refus si l'adresse ou le telephone a deja servi a un essai
 *       ou a un compte ; adresses jetables refusees ; adresse verifiee par un
 *       lien, telephone par un code SMS ; l'essai n'est cree qu'une fois les
 *       deux verifies ;</li>
 *   <li>RG-ESS-03 : origine commune (meme reseau, meme appareil) signalee au
 *       super-admin, jamais bloquee.</li>
 * </ol>
 */
@Service
public class InscriptionEssaiService {

    private static final Logger log = LoggerFactory.getLogger(InscriptionEssaiService.class);

    static final Pattern TELEPHONE_E164 = Pattern.compile("^\\+[1-9]\\d{6,14}$");
    private static final Pattern APPAREIL = Pattern.compile("^[A-Za-z0-9-]{8,64}$");
    static final Duration VALIDITE_DEMANDE = Duration.ofHours(24);
    private static final Duration DELAI_RENVOI_SMS = Duration.ofSeconds(60);
    private static final int CHIFFRES_CODE = 6;
    private static final int TENTATIVES_MAX = 5;

    static final String MESSAGE_DEJA_UTILISEE =
            "Cette adresse \u00e9lectronique a d\u00e9j\u00e0 servi \u00e0 un essai ou \u00e0 un compte JURIKA : "
                    + "connectez-vous, ou contactez JURIKA.";
    static final String MESSAGE_TELEPHONE_DEJA_UTILISE =
            "Ce num\u00e9ro de t\u00e9l\u00e9phone a d\u00e9j\u00e0 servi \u00e0 un essai ou \u00e0 un compte JURIKA : "
                    + "connectez-vous, ou contactez JURIKA.";

    private final EssaiJdbc essais;
    private final ConditionsEssai conditions;
    private final DomainesJetables jetables;
    private final DnsMxValidator emailValidator;
    private final VerificationTokenHasher hasher;
    private final SmsSender smsSender;
    private final EmailSender emailSender;
    private final IpRateLimiter rateLimiter;
    private final RegisterWorkspaceUseCase registerWorkspace;
    private final WorkspaceJpaRepository workspaces;
    private final UserRepository users;
    private final AuditLogger audit;
    private final Clock horloge;
    private final String urlConfirmation;
    private final long validiteCodeMinutes;

    public InscriptionEssaiService(EssaiJdbc essais, ConditionsEssai conditions, DomainesJetables jetables,
                                   DnsMxValidator emailValidator, VerificationTokenHasher hasher,
                                   SmsSender smsSender, EmailSender emailSender, IpRateLimiter rateLimiter,
                                   RegisterWorkspaceUseCase registerWorkspace, WorkspaceJpaRepository workspaces,
                                   UserRepository users, AuditLogger audit, Clock horloge,
                                   @Value("${jurika.essai.confirmation-url:http://localhost:5173/essai/confirmer}")
                                   String urlConfirmation,
                                   @Value("${jurika.auth.sms-otp-ttl-minutes:5}") long validiteCodeMinutes) {
        this.essais = essais;
        this.conditions = conditions;
        this.jetables = jetables;
        this.emailValidator = emailValidator;
        this.hasher = hasher;
        this.smsSender = smsSender;
        this.emailSender = emailSender;
        this.rateLimiter = rateLimiter;
        this.registerWorkspace = registerWorkspace;
        this.workspaces = workspaces;
        this.users = users;
        this.audit = audit;
        this.horloge = horloge;
        this.urlConfirmation = urlConfirmation;
        this.validiteCodeMinutes = validiteCodeMinutes;
    }

    public record Demander(String nom, String prenom, String organisation, String telephone, String email,
                           boolean conditionsAcceptees, String conditionsVersion, String appareil,
                           String adresseIp, String userAgent) {}

    public record DemandeCreee(UUID demandeId, String telephoneMasque, String email, boolean smsEnvoye,
                               boolean emailEnvoye, Instant expireLe) {}

    /** Etat d'une demande ; {@code workspaceCode} et {@code loginEmail} une fois l'essai cree. */
    public record Etat(UUID demandeId, boolean emailVerifie, boolean telephoneVerifie, boolean essaiCree,
                       String workspaceCode, String loginEmail, boolean identifiantsEnvoyes) {}

    // ------------------------------------------------------------ demande

    @Transactional
    public DemandeCreee demander(Demander cmd) {
        String ip = cmd.adresseIp() == null || cmd.adresseIp().isBlank() ? "inconnue" : cmd.adresseIp();
        if (!rateLimiter.tryAcquire("signup-cabinet", ip)) {
            throw new TooManyRequestsException("RATE_LIMITED",
                    "Trop de demandes d\u2019essai depuis ce r\u00e9seau : r\u00e9essayez dans une heure.");
        }
        String nom = obligatoire(cmd.nom(), "le nom", 80);
        String prenom = obligatoire(cmd.prenom(), "le pr\u00e9nom", 80);
        String organisation = obligatoire(cmd.organisation(), "l\u2019organisation (nom du cabinet)", 150);
        String telephone = obligatoire(cmd.telephone(), "le t\u00e9l\u00e9phone", 20).replace(" ", "");
        String email = obligatoire(cmd.email(), "l\u2019adresse \u00e9lectronique", 150).toLowerCase(Locale.ROOT);

        if (!cmd.conditionsAcceptees()) {
            throw new ValidationException("Cochez la case d\u2019acceptation des conditions d\u2019utilisation "
                    + "de l\u2019essai pour continuer.");
        }
        if (!conditions.version().equals(cmd.conditionsVersion())) {
            throw new ConflictException("Les conditions d\u2019utilisation de l\u2019essai ont chang\u00e9 : "
                    + "relisez-les puis acceptez-les de nouveau.");
        }
        if (!TELEPHONE_E164.matcher(telephone).matches()) {
            throw new ValidationException("T\u00e9l\u00e9phone : format international attendu "
                    + "(par exemple +212612345678).");
        }
        if (jetables.estJetable(email)) {
            throw new ValidationException("Les adresses \u00e9lectroniques jetables ne sont pas accept\u00e9es : "
                    + "indiquez l\u2019adresse de votre cabinet.");
        }
        DnsMxValidator.Result verif = emailValidator.validate(email);
        if (!verif.valid()) {
            throw new ValidationException(verif.reason());
        }
        controlerUnicite(email, telephone);

        Instant maintenant = horloge.instant();
        String jeton = UUID.randomUUID().toString();
        String code = hasher.generateNumericCode(CHIFFRES_CODE);
        String appareil = cmd.appareil() != null && APPAREIL.matcher(cmd.appareil()).matches() ? cmd.appareil() : null;
        Instant expireLe = maintenant.plus(VALIDITE_DEMANDE);
        UUID id = essais.creerDemande(new EssaiJdbc.NouvelleDemande(nom, prenom, organisation, telephone, email,
                hasher.hash(jeton), hasher.hash(code), maintenant,
                maintenant.plus(Duration.ofMinutes(validiteCodeMinutes)),
                conditions.version(), conditions.empreinte(), maintenant,
                cmd.adresseIp(), appareil, tronquer(cmd.userAgent(), 500), expireLe));

        boolean smsEnvoye = envoyerCode(telephone, code);
        boolean emailEnvoye = envoyerLien(email, prenom, jeton);
        log.info("Demande d'essai {} enregistree (sms={}, courriel={})", id, smsEnvoye, emailEnvoye);
        return new DemandeCreee(id, masquer(telephone), email, smsEnvoye, emailEnvoye, expireLe);
    }

    @Transactional
    public boolean renvoyerCode(UUID demandeId) {
        Demande d = demandeOuverte(demandeId);
        if (d.telephoneVerifieLe() != null) {
            throw new BusinessException("ESSAI_TELEPHONE_DEJA_VERIFIE", "Votre t\u00e9l\u00e9phone est d\u00e9j\u00e0 v\u00e9rifi\u00e9.");
        }
        Instant maintenant = horloge.instant();
        if (d.codeSmsEnvoyeLe() != null && maintenant.isBefore(d.codeSmsEnvoyeLe().plus(DELAI_RENVOI_SMS))) {
            throw new BusinessException("SMS_RATE_LIMIT_WAIT_60_SECONDS",
                    "Un code vient d\u2019\u00eatre envoy\u00e9 : attendez une minute avant d\u2019en demander un autre.");
        }
        String code = hasher.generateNumericCode(CHIFFRES_CODE);
        essais.nouveauCodeSms(d.id(), hasher.hash(code), maintenant,
                maintenant.plus(Duration.ofMinutes(validiteCodeMinutes)));
        return envoyerCode(d.telephone(), code);
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public Etat verifierTelephone(UUID demandeId, String code) {
        Demande d = demandeOuverte(demandeId);
        if (d.telephoneVerifieLe() == null) {
            Instant maintenant = horloge.instant();
            if (d.codeSmsEmpreinte() == null || d.codeSmsExpireLe() == null
                    || maintenant.isAfter(d.codeSmsExpireLe())) {
                throw new BusinessException("SMS_OTP_EXPIRED", "Ce code a expir\u00e9 : demandez-en un nouveau.");
            }
            if (d.codeSmsTentatives() >= TENTATIVES_MAX) {
                throw new BusinessException("SMS_OTP_TOO_MANY_ATTEMPTS",
                        "Trop d\u2019essais pour ce code : demandez-en un nouveau.");
            }
            if (code == null || !hasher.hash(code.trim()).equals(d.codeSmsEmpreinte())) {
                essais.tentativeSmsEchouee(d.id());
                throw new UnauthorizedException("Code re\u00e7u par SMS incorrect.");
            }
            essais.telephoneVerifie(d.id(), maintenant);
        }
        return etat(d.id());
    }

    @Transactional
    public Etat confirmerAdresse(String jeton) {
        if (jeton == null || jeton.isBlank()) {
            throw new NotFoundException("Lien de confirmation invalide.");
        }
        Demande d = essais.demandeParJeton(hasher.hash(jeton.trim()))
                .orElseThrow(() -> new NotFoundException("Lien de confirmation invalide ou d\u00e9j\u00e0 utilis\u00e9."));
        if (d.workspaceId() == null) {
            if (d.estExpiree(horloge.instant())) {
                throw new BusinessException("ESSAI_DEMANDE_EXPIREE",
                        "Ce lien a expir\u00e9 : recommencez l\u2019inscription \u00e0 l\u2019essai.");
            }
            essais.emailVerifie(d.id(), horloge.instant());
        }
        return etat(d.id());
    }

    @Transactional(readOnly = true)
    public Etat etat(UUID demandeId) {
        Demande d = essais.demande(demandeId)
                .orElseThrow(() -> new NotFoundException("Demande d\u2019essai introuvable."));
        return new Etat(d.id(), d.emailVerifieLe() != null, d.telephoneVerifieLe() != null,
                d.workspaceId() != null, null, null, false);
    }

    // ----------------------------------------------------------- creation

    /**
     * Cree l'essai d'une demande verifiee. L'appelant a pose le NOUVEAU workspace
     * comme workspace courant AVANT l'appel (ContexteWorkspacePublic), pour que
     * les INSERT passent la RLS.
     */
    @Transactional
    public Etat creerEssai(UUID demandeId) {
        UUID nouveauWorkspace = TenantContext.get();
        if (nouveauWorkspace == null) {
            throw new IllegalStateException("Creation d'essai sans workspace courant");
        }
        Demande d = demandeOuverte(demandeId);
        if (!d.estVerifiee()) {
            throw new BusinessException("ESSAI_VERIFICATION_INCOMPLETE",
                    "Confirmez votre adresse \u00e9lectronique (lien re\u00e7u) et votre t\u00e9l\u00e9phone "
                            + "(code re\u00e7u par SMS) pour ouvrir l\u2019essai.");
        }
        if (!conditions.version().equals(d.conditionsVersion())
                || !conditions.empreinte().equals(d.conditionsEmpreinte())) {
            throw new ConflictException("Les conditions d\u2019utilisation de l\u2019essai ont chang\u00e9 depuis "
                    + "votre demande : recommencez l\u2019inscription.");
        }
        if (!essais.reserverCreation(d.id(), nouveauWorkspace)) {
            throw new ConflictException("Cet essai est d\u00e9j\u00e0 ouvert : connectez-vous avec les "
                    + "identifiants re\u00e7us par courriel.");
        }
        // Une demande concurrente a pu ouvrir un essai ou un compte entre-temps.
        controlerUnicite(d.email(), d.telephone());

        Instant maintenant = horloge.instant();
        EssaiJdbc.OriginesCommunes origines = essais.originesCommunes(d.adresseIp(), d.appareil());

        RegisterWorkspaceUseCase.Result base = registerWorkspace.execute(new RegisterWorkspaceUseCase.Command(
                d.organisation(), d.email(), null, d.prenom(), d.nom(), d.telephone(), d.email(),
                null, d.adresseIp(), d.userAgent(), null, null, false));

        WorkspaceEntity ws = workspaces.findById(base.workspaceId())
                .orElseThrow(() -> new NotFoundException("Cabinet introuvable apr\u00e8s sa cr\u00e9ation."));
        ws.setTrialStatus("TRIAL_ACTIVE");
        ws.setTrialStartedAt(maintenant);
        ws.setTrialEndsAt(maintenant.plus(Duration.ofDays(EssaiRegles.DUREE_JOURS)));
        ws.setStatus("ACTIVE");
        ws.setCreatedViaSprint11Wizard(true);
        ws.setTelephone(d.telephone());
        workspaces.save(ws);

        // Adresse et telephone viennent d'etre verifies (lien, code SMS).
        users.markEmailVerified(base.userId(), maintenant);
        users.markPhoneVerified(base.userId(), maintenant);

        String signalement = signalement(origines);
        essais.inscrireAuRegistre(ws.getId(), base.workspaceCode(), empreinte(d.email()), empreinte(d.telephone()),
                d.adresseIp(), d.appareil(), signalement, maintenant);
        essais.enregistrerAcceptation(ws.getId(), base.userId(), d.conditionsVersion(), conditions.texte(),
                d.conditionsEmpreinte(), d.conditionsAccepteesLe(), d.adresseIp(), d.userAgent());

        Map<String, Object> meta = new HashMap<>();
        meta.put("demande", d.id().toString());
        meta.put("conditionsVersion", d.conditionsVersion());
        meta.put("conditionsEmpreinte", d.conditionsEmpreinte());
        meta.put("finEssai", ws.getTrialEndsAt().toString());
        if (signalement != null) meta.put("signalement", signalement);
        audit.log(ws.getId(), base.userId(), "ESSAI_CREE", "workspace", ws.getId(),
                d.adresseIp(), d.userAgent(), meta);
        if (signalement != null) {
            log.warn("Essai {} signale au super-admin : {}", base.workspaceCode(), signalement);
        }
        return new Etat(d.id(), true, true, true, base.workspaceCode(), base.loginEmail(), base.emailDelivered());
    }

    // ------------------------------------------------------------- outils

    private void controlerUnicite(String email, String telephone) {
        EssaiJdbc.IdentiteUtilisee deja = essais.identiteDejaUtilisee(email, telephone,
                empreinte(email), empreinte(telephone));
        if (deja.email()) throw new ConflictException(MESSAGE_DEJA_UTILISEE);
        if (deja.telephone()) throw new ConflictException(MESSAGE_TELEPHONE_DEJA_UTILISE);
    }

    private Demande demandeOuverte(UUID id) {
        Demande d = essais.demande(id)
                .orElseThrow(() -> new NotFoundException("Demande d\u2019essai introuvable."));
        if (d.workspaceId() != null) {
            throw new ConflictException("Cet essai est d\u00e9j\u00e0 ouvert : connectez-vous avec les "
                    + "identifiants re\u00e7us par courriel.");
        }
        if (d.estExpiree(horloge.instant())) {
            throw new BusinessException("ESSAI_DEMANDE_EXPIREE",
                    "Cette demande a expir\u00e9 : recommencez l\u2019inscription \u00e0 l\u2019essai.");
        }
        return d;
    }

    /** RG-ESS-03 : motif lisible du signalement, ou {@code null}. */
    static String signalement(EssaiJdbc.OriginesCommunes o) {
        List<String> motifs = new ArrayList<>();
        if (o.memeReseau() > 0) {
            motifs.add("m\u00eame r\u00e9seau (adresse IP) que " + o.memeReseau() + " essai"
                    + (o.memeReseau() > 1 ? "s" : "") + " d\u00e9j\u00e0 ouvert" + (o.memeReseau() > 1 ? "s" : ""));
        }
        if (o.memeAppareil() > 0) {
            motifs.add("m\u00eame appareil que " + o.memeAppareil() + " essai"
                    + (o.memeAppareil() > 1 ? "s" : "") + " d\u00e9j\u00e0 ouvert" + (o.memeAppareil() > 1 ? "s" : ""));
        }
        if (motifs.isEmpty()) return null;
        String texte = String.join(" ; ", motifs);
        return Character.toUpperCase(texte.charAt(0)) + texte.substring(1) + ".";
    }

    /** Empreinte SHA-256 de l'identite normalisee (minuscules, sans espaces). */
    public static String empreinte(String valeur) {
        return ConditionsEssai.sha256(valeur.trim().replace(" ", "").toLowerCase(Locale.ROOT));
    }

    private boolean envoyerCode(String telephone, String code) {
        try {
            smsSender.send(telephone, String.format(
                    "JURIKA : votre code de verification pour l'essai gratuit est %s. Valable %d minutes.",
                    code, validiteCodeMinutes));
            return true;
        } catch (RuntimeException ex) {
            log.error("Envoi du code SMS de l'essai echoue : {}", ex.getMessage());
            return false;
        }
    }

    private boolean envoyerLien(String email, String prenom, String jeton) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("brand", "JURIKA");
        vars.put("firstName", prenom);
        vars.put("verificationUrl", urlConfirmation + "?jeton=" + jeton);
        vars.put("subject", "Confirmez votre adresse pour ouvrir votre essai JURIKA");
        try {
            emailSender.sendTemplated(email, "Confirmez votre adresse pour ouvrir votre essai JURIKA",
                    "essai-confirmation", vars);
            return true;
        } catch (RuntimeException ex) {
            log.error("Envoi du lien de confirmation de l'essai echoue : {}", ex.getMessage());
            return false;
        }
    }

    private static String obligatoire(String v, String libelle, int max) {
        if (v == null || v.isBlank()) {
            throw new ValidationException("Indiquez " + libelle + ".");
        }
        String t = v.trim();
        if (t.length() > max) {
            throw new ValidationException("Trop long : " + libelle + " (" + max + " caract\u00e8res au plus).");
        }
        return t;
    }

    private static String tronquer(String v, int max) {
        return v == null ? null : (v.length() > max ? v.substring(0, max) : v);
    }

    static String masquer(String telephone) {
        if (telephone.length() < 6) return "***";
        return telephone.substring(0, telephone.length() - 4).replaceAll("\\d", "*")
                + telephone.substring(telephone.length() - 4);
    }
}
