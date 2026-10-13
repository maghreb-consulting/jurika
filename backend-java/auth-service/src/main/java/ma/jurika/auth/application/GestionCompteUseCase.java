package ma.jurika.auth.application;

import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.port.AuditLogger;
import ma.jurika.auth.domain.port.EmailSender;
import ma.jurika.auth.domain.port.EmailVerificationTokenRepository;
import ma.jurika.auth.domain.port.EmailVerificationTokenRepository.StoredToken;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.auth.domain.service.VerificationTokenHasher;
import ma.jurika.common.exception.BusinessException;
import ma.jurika.common.exception.ConflictException;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Lot L5 (E10) : gestion de son compte par chaque utilisateur (CDC 3.6).
 *
 * <ul>
 *   <li>RG-CPT-01 : l'utilisateur modifie ses propres prenom et nom, son adresse
 *       electronique (contact_email), son identifiant de connexion (login_email)
 *       et son mot de passe ({@link ChangePasswordUseCase}, mot de passe actuel exige).</li>
 *   <li>RG-CPT-02 : le changement d'adresse ou d'identifiant ne prend effet qu'a la
 *       confirmation d'un lien ; jusque-la l'ancienne valeur reste active. Lien a
 *       usage unique, duree de vie {@code jurika.auth.email-verification-ttl-hours}
 *       (celle des liens de verification existants), stocke par son empreinte
 *       SHA-256 ({@link VerificationTokenHasher}).</li>
 *   <li>Adresse : le lien part a la NOUVELLE adresse, l'ANCIENNE recoit un avis
 *       (RG-COM-10). Identifiant : il n'est pas une boite aux lettres (format
 *       {@code prenom.nom@jurika.ma}) ; le lien part a l'adresse electronique du
 *       compte, la seule dont la possession est prouvee.</li>
 *   <li>Chaque modification est tracee (audit_log, ancienne et nouvelle valeur ;
 *       jamais le jeton ni le mot de passe).</li>
 * </ul>
 */
@Service
public class GestionCompteUseCase {

    private static final Logger log = LoggerFactory.getLogger(GestionCompteUseCase.class);

    public static final String MOTIF_ADRESSE = "EMAIL_CHANGE";
    public static final String MOTIF_IDENTIFIANT = "LOGIN_CHANGE";

    private static final int LONGUEUR_MAX_NOM = 100;
    private static final int LONGUEUR_MAX_ADRESSE = 150;
    /** Forme d'une adresse : la meme exigence que la connexion (@Email de LoginRequest). */
    private static final Pattern FORME_ADRESSE =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final DateTimeFormatter FORMAT_DATE = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("Africa/Casablanca"));

    private final UserRepository userRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final VerificationTokenHasher tokenHasher;
    private final EmailSender emailSender;
    private final AuditLogger auditLogger;
    private final Clock horloge;
    private final long dureeJetonHeures;
    private final String urlConfirmation;

    public GestionCompteUseCase(UserRepository userRepository,
                                EmailVerificationTokenRepository tokenRepository,
                                VerificationTokenHasher tokenHasher,
                                EmailSender emailSender,
                                AuditLogger auditLogger,
                                Clock horloge,
                                @Value("${jurika.auth.email-verification-ttl-hours}") long dureeJetonHeures,
                                @Value("${jurika.email.account-change-base-url}") String urlConfirmation) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.tokenHasher = tokenHasher;
        this.emailSender = emailSender;
        this.auditLogger = auditLogger;
        this.horloge = horloge;
        this.dureeJetonHeures = dureeJetonHeures;
        this.urlConfirmation = urlConfirmation;
    }

    /** Contexte d'une requete authentifiee : l'utilisateur agit sur SON compte. */
    public record Demandeur(UUID userId, UUID workspaceId, String ipAddress, String userAgent) {}

    public record Attente(String nouvelleValeur, Instant expireLe) {}

    public record EtatCompte(Optional<Attente> adresseEnAttente, Optional<Attente> identifiantEnAttente) {}

    public record Confirmation(String motif, String ancienneValeur, String nouvelleValeur) {}

    // ------------------------------------------------------------------ nom

    @Transactional
    public void modifierNom(Demandeur d, String prenom, String nom) {
        String p = exigerNom(prenom, "PRENOM_OBLIGATOIRE", "Le pr\u00e9nom est obligatoire.");
        String n = exigerNom(nom, "NOM_OBLIGATOIRE", "Le nom est obligatoire.");
        User u = chargerSonCompte(d);
        if (p.equals(u.firstName()) && n.equals(u.lastName())) {
            return;
        }
        userRepository.updateName(u.id(), p, n);
        Map<String, Object> meta = new HashMap<>();
        meta.put("previousFirstName", u.firstName());
        meta.put("previousLastName", u.lastName());
        meta.put("newFirstName", p);
        meta.put("newLastName", n);
        auditLogger.log(d.workspaceId(), d.userId(), "USER_NAME_CHANGED", "user", u.id(),
                d.ipAddress(), d.userAgent(), meta);
    }

    // ------------------------------------------------------------- adresse

    @Transactional
    public Attente demanderChangementAdresse(Demandeur d, String nouvelleAdresse) {
        String nouvelle = exigerAdresse(nouvelleAdresse);
        User u = chargerSonCompte(d);
        if (nouvelle.equalsIgnoreCase(u.contactEmail())) {
            throw new BusinessException("ADRESSE_INCHANGEE",
                    "Cette adresse est d\u00e9j\u00e0 celle du compte.");
        }
        Demande dem = ouvrirDemande(d, u, MOTIF_ADRESSE, nouvelle);

        Map<String, Object> lien = variablesCommunes(u);
        lien.put("nouvelleAdresse", nouvelle);
        lien.put("confirmationUrl", urlConfirmation + "?token=" + dem.jeton());
        lien.put("ttlHours", String.valueOf(dureeJetonHeures));
        envoyer(nouvelle, "JURIKA - confirmez votre nouvelle adresse \u00e9lectronique",
                "email-change-confirm", lien, u);

        Map<String, Object> avis = variablesCommunes(u);
        avis.put("nouvelleAdresse", nouvelle);
        avis.put("requestedAt", FORMAT_DATE.format(horloge.instant()));
        avis.put("ipAddress", d.ipAddress());
        envoyer(u.contactEmail(), "JURIKA - demande de changement de votre adresse \u00e9lectronique",
                "email-change-notice", avis, u);

        Map<String, Object> meta = new HashMap<>();
        meta.put("previousContactEmail", u.contactEmail());
        meta.put("newContactEmail", nouvelle);
        auditLogger.log(d.workspaceId(), d.userId(), "CONTACT_EMAIL_CHANGE_REQUESTED", "user", u.id(),
                d.ipAddress(), d.userAgent(), meta);
        return new Attente(nouvelle, dem.expire());
    }

    // --------------------------------------------------------- identifiant

    @Transactional
    public Attente demanderChangementIdentifiant(Demandeur d, String nouvelIdentifiant) {
        String nouveau = exigerAdresse(nouvelIdentifiant);
        User u = chargerSonCompte(d);
        if (nouveau.equalsIgnoreCase(u.loginEmail())) {
            throw new BusinessException("IDENTIFIANT_INCHANGE",
                    "Cet identifiant est d\u00e9j\u00e0 celui du compte.");
        }
        exigerIdentifiantLibre(u, nouveau);
        Demande dem = ouvrirDemande(d, u, MOTIF_IDENTIFIANT, nouveau);

        Map<String, Object> lien = variablesCommunes(u);
        lien.put("nouvelIdentifiant", nouveau);
        lien.put("confirmationUrl", urlConfirmation + "?token=" + dem.jeton());
        lien.put("ttlHours", String.valueOf(dureeJetonHeures));
        lien.put("requestedAt", FORMAT_DATE.format(horloge.instant()));
        lien.put("ipAddress", d.ipAddress());
        envoyer(u.contactEmail(), "JURIKA - confirmez votre nouvel identifiant de connexion",
                "login-change-confirm", lien, u);

        Map<String, Object> meta = new HashMap<>();
        meta.put("previousLoginEmail", u.loginEmail());
        meta.put("newLoginEmail", nouveau);
        auditLogger.log(d.workspaceId(), d.userId(), "LOGIN_EMAIL_CHANGE_REQUESTED", "user", u.id(),
                d.ipAddress(), d.userAgent(), meta);
        return new Attente(nouveau, dem.expire());
    }

    // ------------------------------------------------------------ annulation

    @Transactional
    public void annulerDemande(Demandeur d, String motif) {
        if (!MOTIF_ADRESSE.equals(motif) && !MOTIF_IDENTIFIANT.equals(motif)) {
            throw new BusinessException("MOTIF_INCONNU", "Demande inconnue.");
        }
        User u = chargerSonCompte(d);
        int retires = tokenRepository.revokePending(u.id(), motif, horloge.instant());
        if (retires > 0) {
            auditLogger.log(d.workspaceId(), d.userId(), "ACCOUNT_CHANGE_CANCELLED", "user", u.id(),
                    d.ipAddress(), d.userAgent(), Map.of("purpose", motif));
        }
    }

    // ---------------------------------------------------------------- etat

    @Transactional(readOnly = true)
    public EtatCompte etat(UUID userId, UUID workspaceId) {
        TenantContext.set(workspaceId);
        Instant maintenant = horloge.instant();
        return new EtatCompte(
                tokenRepository.findPending(userId, MOTIF_ADRESSE, maintenant)
                        .map(t -> new Attente(t.email(), t.expiresAt())),
                tokenRepository.findPending(userId, MOTIF_IDENTIFIANT, maintenant)
                        .map(t -> new Attente(t.email(), t.expiresAt())));
    }

    // ----------------------------------------------------------- confirmation

    /**
     * Route publique : le workspace est pose avant l'appel par
     * {@link ContexteWorkspacePublic#poserParJetonVerification(String)}.
     */
    @Transactional
    public Confirmation confirmer(String jetonBrut, String ipAddress, String userAgent) {
        if (jetonBrut == null || jetonBrut.isBlank()) {
            throw new NotFoundException("Lien de confirmation non valide.");
        }
        StoredToken t = tokenRepository.findByHash(tokenHasher.hash(jetonBrut.trim()))
                .filter(x -> MOTIF_ADRESSE.equals(x.purpose()) || MOTIF_IDENTIFIANT.equals(x.purpose()))
                .orElseThrow(() -> new NotFoundException("Lien de confirmation non valide."));
        if (t.isUsed()) {
            throw new BusinessException("LIEN_DEJA_UTILISE", "Ce lien a d\u00e9j\u00e0 \u00e9t\u00e9 utilis\u00e9 ou remplac\u00e9 par un plus r\u00e9cent.");
        }
        if (horloge.instant().isAfter(t.expiresAt())) {
            throw new BusinessException("LIEN_EXPIRE", "Ce lien a expir\u00e9 : refaites la demande depuis \u00ab Mon compte \u00bb.");
        }
        TenantContext.set(t.workspaceId());
        User u = userRepository.findById(t.userId())
                .filter(x -> x.workspaceId().equals(t.workspaceId()))
                .orElseThrow(() -> new NotFoundException("Lien de confirmation non valide."));
        boolean identifiant = MOTIF_IDENTIFIANT.equals(t.purpose());
        if (identifiant) {
            // L'identifiant a pu etre pris entre la demande et la confirmation.
            exigerIdentifiantLibre(u, t.email());
        }
        // Consommation ATOMIQUE avant tout effet (meme regle que VerifyEmailUseCase).
        if (!tokenRepository.markUsed(t.id(), horloge.instant())) {
            throw new BusinessException("LIEN_DEJA_UTILISE", "Ce lien a d\u00e9j\u00e0 \u00e9t\u00e9 utilis\u00e9 ou remplac\u00e9 par un plus r\u00e9cent.");
        }
        Map<String, Object> meta = new HashMap<>();
        meta.put("tokenId", String.valueOf(t.id()));
        String ancienne;
        if (identifiant) {
            ancienne = u.loginEmail();
            userRepository.updateLoginEmail(u.id(), t.email());
            meta.put("previousLoginEmail", ancienne);
            meta.put("newLoginEmail", t.email());
            auditLogger.log(t.workspaceId(), u.id(), "LOGIN_EMAIL_CHANGED", "user", u.id(),
                    ipAddress, userAgent, meta);
        } else {
            ancienne = u.contactEmail();
            userRepository.updateContactEmail(u.id(), t.email());
            meta.put("previousContactEmail", ancienne);
            meta.put("newContactEmail", t.email());
            auditLogger.log(t.workspaceId(), u.id(), "CONTACT_EMAIL_CHANGED", "user", u.id(),
                    ipAddress, userAgent, meta);
        }
        return new Confirmation(t.purpose(), ancienne, t.email());
    }

    // -------------------------------------------------------------- outils

    /** Jeton en clair (transmis au seul courriel, jamais stocke ni journalise) et echeance. */
    private record Demande(String jeton, Instant expire) {}

    private Demande ouvrirDemande(Demandeur d, User u, String motif, String nouvelleValeur) {
        Instant maintenant = horloge.instant();
        // Une seule demande ouverte par motif : la precedente devient inutilisable.
        tokenRepository.revokePending(u.id(), motif, maintenant);
        String jeton = tokenHasher.generateUuidToken();
        Instant expire = maintenant.plus(Duration.ofHours(dureeJetonHeures));
        tokenRepository.create(u.workspaceId(), u.id(), nouvelleValeur, tokenHasher.hash(jeton),
                motif, expire, d.ipAddress(), tronquer(d.userAgent(), 255));
        return new Demande(jeton, expire);
    }

    private User chargerSonCompte(Demandeur d) {
        TenantContext.set(d.workspaceId());
        User u = userRepository.findById(d.userId())
                .orElseThrow(() -> new NotFoundException("Utilisateur introuvable."));
        if (!u.workspaceId().equals(d.workspaceId())) {
            throw new NotFoundException("Utilisateur introuvable.");
        }
        return u;
    }

    private void exigerIdentifiantLibre(User u, String identifiant) {
        boolean pris = userRepository.findByWorkspaceAndLoginEmail(u.workspaceId(), identifiant)
                .filter(autre -> !autre.id().equals(u.id())).isPresent()
                || userRepository.findByWorkspaceAndEmail(u.workspaceId(), identifiant)
                .filter(autre -> !autre.id().equals(u.id())).isPresent();
        if (pris) {
            throw new ConflictException("Cet identifiant est d\u00e9j\u00e0 utilis\u00e9 par un autre compte du cabinet.");
        }
    }

    private static String exigerNom(String valeur, String code, String message) {
        String v = valeur == null ? "" : valeur.trim();
        if (v.isEmpty()) {
            throw new BusinessException(code, message);
        }
        if (v.length() > LONGUEUR_MAX_NOM) {
            throw new BusinessException(code, "100 caract\u00e8res au plus.");
        }
        return v;
    }

    private static String exigerAdresse(String valeur) {
        String v = valeur == null ? "" : valeur.trim().toLowerCase();
        if (v.isEmpty() || v.length() > LONGUEUR_MAX_ADRESSE || !FORME_ADRESSE.matcher(v).matches()) {
            throw new BusinessException("ADRESSE_INVALIDE", "Adresse \u00e9lectronique non valide.");
        }
        return v;
    }

    private static Map<String, Object> variablesCommunes(User u) {
        Map<String, Object> v = new HashMap<>();
        v.put("firstName", u.firstName() != null ? u.firstName() : "");
        v.put("loginEmail", u.loginEmail());
        v.put("contactEmail", u.contactEmail());
        v.put("unsubscribeUrl", null);
        return v;
    }

    /**
     * Envoi a effet garanti : un echec d'envoi annule la demande (transaction),
     * plutot qu'une demande ouverte dont personne n'a recu le lien ou l'avis.
     */
    private void envoyer(String destinataire, String sujet, String gabarit, Map<String, Object> vars, User u) {
        try {
            emailSender.sendTemplated(destinataire, sujet, gabarit, vars);
        } catch (RuntimeException ex) {
            log.warn("Echec d'envoi du courriel {} pour user={} : {}", gabarit, u.id(), ex.getClass().getSimpleName());
            throw new BusinessException("ENVOI_IMPOSSIBLE",
                    "Le courriel n\u2019a pas pu \u00eatre envoy\u00e9 ; aucune modification n\u2019a \u00e9t\u00e9 enregistr\u00e9e.");
        }
    }

    private static String tronquer(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
