package ma.jurika.dataroom.application;

import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.domain.port.ObjectStorage;
import ma.jurika.dataroom.infrastructure.persistence.DocumentEntity;
import ma.jurika.dataroom.infrastructure.persistence.DocumentJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.WopiSessionEntity;
import ma.jurika.dataroom.infrastructure.persistence.WopiSessionJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.WopiVerrouEntity;
import ma.jurika.dataroom.infrastructure.persistence.WopiVerrouJpaRepository;
import ma.jurika.dataroom.infrastructure.wopi.CollaboraDiscovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Édition bureautique fidèle — le protocole WOPI côté serveur.
 *
 * <p>Lot 3 (2026-09-07). Collabora ne reçoit pas le fichier : il vient le
 * chercher et il le repose. Trois points d'entrée suffisent, et c'est ce
 * service qui porte la totalité de leur contrôle d'accès — les routes sont
 * ouvertes au niveau Spring Security parce que Collabora ne présente aucun JWT.
 *
 * <h2>Ce sur quoi repose le contrôle</h2>
 * <ul>
 *   <li><b>Le jeton, pas l'identifiant.</b> Le {@code fileId} de l'URL ne porte
 *       aucune autorité : il est comparé à celui de la séance. Un identifiant
 *       deviné ne donne rien.</li>
 *   <li><b>Le cloisonnement multi-tenant est vérifié à CHAQUE appel</b>, et
 *       explicitement : le document est relu par
 *       {@code findByWorkspaceIdAndId(session.workspaceId, fileId)}. Le filtre
 *       applicatif ne suppose jamais que la RLS le fera — dans ce projet,
 *       {@code jurika_user} a BYPASSRLS.</li>
 *   <li><b>{@code UserCanWrite} vient du RBAC</b>, figé à l'ouverture de la
 *       séance, pas d'un bouton masqué dans l'interface.</li>
 * </ul>
 *
 * <h2>Ce que PutFile écrit</h2>
 * Collabora enregistre automatiquement, toutes les ~30 s dès que le document
 * est modifié. Appliquer littéralement « un PutFile = une nouvelle version »
 * produirait v2, v3, v4… pendant une seule séance et polluerait l'historique
 * juridique de sauvegardes intermédiaires. La règle retenue :
 * <ul>
 *   <li><b>Brouillon</b> (acte généré, pas encore validé) : chaque PutFile met
 *       à jour le brouillon. Il est hors lignage par construction (V26), donc
 *       rien à versionner.</li>
 *   <li><b>Document validé</b> : le PREMIER PutFile de la séance crée une
 *       nouvelle version par le versionnement existant — l'occupant précédent
 *       bascule en historique, il n'est jamais écrasé. Les PutFile suivants de
 *       la MÊME séance mettent à jour cette version. Résultat : une version
 *       juridique par séance d'édition, ce qu'un juriste appelle « j'ai modifié
 *       le document ».</li>
 * </ul>
 */
@Service
public class WopiService {

    private static final Logger log = LoggerFactory.getLogger(WopiService.class);

    private static final SecureRandom ALEA = new SecureRandom();

    private final DocumentJpaRepository documents;
    private final WopiSessionJpaRepository sessions;
    private final WopiVerrouJpaRepository verrous;
    private final DataroomJuridiqueService juridique;
    private final ObjectStorage storage;
    private final DossierArchiveGuard archiveGuard;
    /**
     * L'URL de l'editeur ne se devine pas : son chemin porte une empreinte de
     * version qui change a chaque publication de l'image CODE. Elle est lue
     * dans le document de decouverte.
     */
    private final CollaboraDiscovery discovery;

    /** Durée d'une séance d'édition. Assez longue pour un acte, assez courte pour un jeton d'URL. */
    private final Duration dureeSeance;
    /** Fenêtre pendant laquelle un enregistrement tardif reste accepté après fermeture. */
    private final Duration fenetreDeGrace;
    /** URL du backend telle que COLLABORA la voit — jamais telle que le navigateur la voit. */
    private final String wopiBaseUrl;
    /**
     * Durée de vie d'un verrou d'édition. Collabora le rafraîchit toutes les
     * ~10 min ; 30 min laisse passer un rafraîchissement manqué sans bloquer
     * l'acte une demi-journée si le navigateur du détenteur plante.
     */
    private final Duration dureeVerrou;
    public WopiService(DocumentJpaRepository documents,
                       WopiSessionJpaRepository sessions,
                       WopiVerrouJpaRepository verrous,
                       DataroomJuridiqueService juridique,
                       ObjectStorage storage,
                       DossierArchiveGuard archiveGuard,
                       CollaboraDiscovery discovery,
                       @Value("${jurika.wopi.session-duration:PT2H}") Duration dureeSeance,
                       @Value("${jurika.wopi.grace-window:PT3M}") Duration fenetreDeGrace,
                       @Value("${jurika.wopi.base-url:http://dataroom-service:8084}") String wopiBaseUrl,
                       @Value("${jurika.wopi.lock-duration:PT30M}") Duration dureeVerrou) {
        this.documents = documents;
        this.sessions = sessions;
        this.verrous = verrous;
        this.juridique = juridique;
        this.storage = storage;
        this.archiveGuard = archiveGuard;
        this.discovery = discovery;
        this.dureeSeance = dureeSeance;
        this.fenetreDeGrace = fenetreDeGrace;
        this.wopiBaseUrl = wopiBaseUrl.replaceAll("/+$", "");
        this.dureeVerrou = dureeVerrou;
    }

    // =================================================================
    //  Ouverture / fermeture de séance (appelées par le FRONT, avec JWT)
    // =================================================================

    /**
     * @param wopiSrc        l'URL que Collabora appellera — jamais joignable depuis le navigateur
     * @param accessToken    le jeton en clair, rendu UNE SEULE FOIS
     * @param editeurUrl     l'URL de l'éditeur, à charger dans l'iframe
     * @param canWrite       reflet du RBAC réel, ET du fait qu'un autre employé
     *                       ne tient pas déjà le document ouvert
     * @param editeManuellementAt date de la dernière édition manuelle, ou null
     * @param verrouPar      nom de l'employé qui édite déjà cet acte, ou {@code null}.
     *                       On le dit AVANT d'ouvrir l'éditeur : découvrir en
     *                       fermant que son travail n'a pas été gardé serait la
     *                       pire des façons de l'apprendre.
     * @param verrouDepuis   depuis quand, pour que l'appel téléphonique qui suit
     *                       soit informé
     */
    public record SeanceEdition(UUID sessionId, String wopiSrc, String accessToken,
                                 long accessTokenTtlMs, String editeurUrl, boolean canWrite,
                                 Instant editeManuellementAt,
                                 String verrouPar, Instant verrouDepuis) {}

    /**
     * Lot L0 (E15, inventaire W1) : workspace de la seance WOPI designee par le
     * jeton. Collabora appelle les routes WOPI SANS jeton JWT : le jeton opaque
     * de la seance est leur seule autorite, et la seance porte le workspace. La
     * table des seances n'est pas sous RLS : la recherche fonctionne avant tout
     * workspace courant. Utilise par ContexteWopiConfig pour poser le workspace
     * AVANT la transaction.
     */
    public java.util.Optional<UUID> workspaceDuJeton(String token) {
        if (token == null || token.isBlank()) {
            return java.util.Optional.empty();
        }
        return sessions.findByTokenHash(empreinte(token)).map(WopiSessionEntity::getWorkspaceId);
    }

    @Transactional
    public SeanceEdition ouvrir(UUID documentId, UUID userId, String userDisplayName, Role role) {
        UUID ws = TenantContext.get();
        if (ws == null) throw new NotFoundException("Document inconnu");

        DocumentEntity doc = documents.findByWorkspaceIdAndId(ws, documentId)
                .orElseThrow(() -> new NotFoundException("Document inconnu"));

        // Le droit d'écriture vient du rôle ET de l'état du dossier. Le
        // SUPERVISEUR consulte, il ne produit pas ; un dossier archivé se lit
        // mais ne se modifie plus.
        boolean canWrite = role == Role.EMPLOYE && dossierModifiable(doc);

        Instant now = Instant.now();

        // UN AUTRE EMPLOYÉ TIENT-IL DÉJÀ CET ACTE ?
        //
        // Si oui, cette séance s'ouvre en LECTURE SEULE et le dit. On pourrait
        // laisser Collabora découvrir le conflit au moment de poser son verrou
        // — le protocole le prévoit — mais l'employé aurait alors déjà commencé
        // à taper. Refuser l'écriture d'emblée, en nommant le détenteur, coûte
        // une phrase et évite un travail perdu.
        WopiVerrouEntity verrou = verrouVivant(ws, doc.getId(), now).orElse(null);
        boolean tenuParUnAutre = verrou != null && !verrou.getUserId().equals(userId);
        if (tenuParUnAutre) {
            canWrite = false;
            log.info("Seance WOPI en lecture seule doc={} : verrou tenu par user={} depuis {}",
                    doc.getId(), verrou.getUserId(), verrou.getAcquisAt());
        }

        String token = nouveauJeton();

        WopiSessionEntity s = new WopiSessionEntity();
        s.setTokenHash(empreinte(token));
        s.setWorkspaceId(ws);
        s.setDocumentId(doc.getId());
        s.setUserId(userId);
        s.setUserDisplayName(userDisplayName);
        s.setCanWrite(canWrite);
        s.setCreatedAt(now);
        s.setExpiresAt(now.plus(dureeSeance));
        sessions.save(s);

        // Purge opportuniste : pas de tâche planifiée pour si peu.
        sessions.purgerEchues(now.minus(Duration.ofDays(1)));

        String wopiSrc = wopiBaseUrl + "/api/v1/dataroom/wopi/files/" + doc.getId();
        log.info("Seance WOPI ouverte doc={} user={} canWrite={} expire={}",
                doc.getId(), userId, canWrite, s.getExpiresAt());
        // L'URL rendue est celle de la DECOUVERTE (chemin versionne inclus).
        // Sans elle, le cadre s'ouvrirait sur un 404 silencieux.
        String urlEditeur = discovery.urlEditeurDocx();
        if (urlEditeur == null) {
            throw new IllegalStateException(
                    "Editeur bureautique indisponible : la decouverte Collabora ne repond pas");
        }
        return new SeanceEdition(s.getId(), wopiSrc, token, dureeSeance.toMillis(),
                urlEditeur, canWrite, doc.getEditeManuellementAt(),
                tenuParUnAutre ? nomOuDefaut(verrou.getUserDisplayName()) : null,
                tenuParUnAutre ? verrou.getAcquisAt() : null);
    }

    /**
     * Ferme la séance. La LECTURE est coupée immédiatement ; l'ÉCRITURE reste
     * ouverte le temps de la fenêtre de grâce, parce que Collabora enregistre de
     * façon asynchrone et appelle souvent PutFile APRÈS la fermeture de
     * l'onglet. Révoquer sèchement ferait perdre la dernière sauvegarde sans le
     * dire.
     */
    @Transactional
    public void fermer(UUID sessionId, UUID userId) {
        UUID ws = TenantContext.get();
        sessions.findById(sessionId).ifPresent(s -> {
            if (!s.getWorkspaceId().equals(ws) || !s.getUserId().equals(userId)) {
                // Une séance d'un autre : on ne dit pas qu'elle existe.
                return;
            }
            if (s.getClosedAt() != null) return;
            Instant now = Instant.now();
            s.setClosedAt(now);
            s.setGraceJusqua(now.plus(fenetreDeGrace));
            sessions.save(s);
            // Le verrou meurt avec la séance qui le portait. Sans cela, l'acte
            // resterait bloqué jusqu'à l'expiration pour le collègue suivant,
            // alors que l'éditeur est déjà refermé.
            verrous.findBySessionId(sessionId).ifPresent(v -> {
                verrous.delete(v);
                log.info("Verrou relache doc={} a la fermeture de la seance {}",
                        v.getDocumentId(), sessionId);
            });
            log.info("Seance WOPI fermee session={} grace_jusqua={}", sessionId, s.getGraceJusqua());
        });
    }

    // =================================================================
    //  Points d'entrée WOPI (appelés par COLLABORA, sans JWT)
    // =================================================================

    /** Ce que rend CheckFileInfo. Les noms de champs sont imposés par le protocole. */
    public record CheckFileInfo(String BaseFileName, long Size, String OwnerId, String UserId,
                                 String UserFriendlyName, boolean UserCanWrite,
                                 boolean UserCanNotWriteRelative, boolean SupportsUpdate,
                                 boolean SupportsLocks, boolean SupportsGetLock,
                                 boolean SupportsExtendedLockLength, boolean SupportsRename,
                                 String Version, String LastModifiedTime,
                                 boolean DisablePrint, boolean DisableExport, boolean DisableCopy,
                                 boolean HideUserList, String PostMessageOrigin) {}

    /**
     * Résout la séance ET le document, en vérifiant tout ce qui doit l'être.
     * C'est le point unique par lequel passent les trois entrées WOPI : il n'y a
     * pas de seconde porte.
     */
    private Contexte resoudre(UUID fileIdDeLUrl, String token, boolean pourEcriture) {
        if (token == null || token.isBlank()) {
            throw new WopiAccesRefuse("jeton absent");
        }
        WopiSessionEntity s = sessions.findByTokenHash(empreinte(token))
                .orElseThrow(() -> new WopiAccesRefuse("jeton inconnu"));

        Instant now = Instant.now();
        if (pourEcriture ? !s.ecritureAutorisee(now) : !s.lectureAutorisee(now)) {
            throw new WopiAccesRefuse(pourEcriture
                    ? "seance close ou expiree, hors fenetre de grace"
                    : "seance close ou expiree");
        }
        // Le fileId de l'URL ne porte aucune autorite : il doit correspondre.
        if (!s.getDocumentId().equals(fileIdDeLUrl)) {
            throw new WopiAccesRefuse("le fileId ne correspond pas a la seance");
        }
        // Cloisonnement multi-tenant EXPLICITE, a chaque appel. On ne suppose
        // jamais que la RLS filtrera : jurika_user a BYPASSRLS ici.
        DocumentEntity doc = documents.findByWorkspaceIdAndId(s.getWorkspaceId(), fileIdDeLUrl)
                .orElseThrow(() -> new WopiAccesRefuse("document hors du workspace de la seance"));

        // LE DOCUMENT EFFECTIF DE LA SEANCE.
        //
        // Des que la seance a produit une version (premier PutFile sur un
        // document valide), c'est ELLE qu'il faut lire — pas la ligne d'origine,
        // qui vient de basculer en historique. Sans cela, une reconnexion de
        // Collabora en cours de seance rechargerait le contenu d'AVANT
        // l'edition et ecraserait le travail au PutFile suivant : encore une
        // perte silencieuse.
        //
        // Le controle d'acces, lui, reste adosse au fileId de la seance : c'est
        // la ligne d'origine qui a ete autorisee, la version derivee en herite.
        if (s.getVersionDocumentId() != null && !s.getVersionDocumentId().equals(doc.getId())) {
            doc = documents.findByWorkspaceIdAndId(s.getWorkspaceId(), s.getVersionDocumentId())
                    .orElse(doc);
        }

        return new Contexte(s, doc);
    }

    private record Contexte(WopiSessionEntity session, DocumentEntity document) {}

    /** Refus WOPI — traduit en 401 par le contrôleur, jamais en 404 bavard. */
    public static class WopiAccesRefuse extends RuntimeException {
        public WopiAccesRefuse(String raison) { super(raison); }
    }

    /**
     * Conflit de verrou — traduit en 409 par le contrôleur.
     *
     * <p>À la différence d'un refus d'accès, un conflit RENSEIGNE : il rend
     * l'identifiant du verrou en place, comme le protocole l'exige, et le nom
     * de son détenteur. Ce n'est pas une fuite : l'appelant a déjà prouvé son
     * droit sur ce document par son jeton. C'est ce qui permet à l'éditeur de
     * basculer proprement en lecture seule au lieu de laisser croire que
     * l'enregistrement a eu lieu.
     */
    public static class WopiVerrouConflit extends RuntimeException {
        /** Le verrou EN PLACE, jamais celui qui a été demandé. Vide si aucun. */
        private final String verrouCourant;
        private final String detenteur;

        public WopiVerrouConflit(String verrouCourant, String detenteur, String raison) {
            super(raison);
            this.verrouCourant = verrouCourant == null ? "" : verrouCourant;
            this.detenteur = detenteur;
        }

        public String verrouCourant() { return verrouCourant; }
        public String detenteur() { return detenteur; }
    }

    // =================================================================
    //  Verrous d'édition (lot 4) — appelés par COLLABORA
    // =================================================================

    /**
     * Pose un verrou, ou le rafraîchit s'il est déjà à nous.
     *
     * <p>Trois cas, et un seul refus :
     * <ul>
     *   <li>aucun verrou vivant → on le prend ;</li>
     *   <li>verrou déjà à nous (même {@code lockId}) → on le prolonge, sans
     *       erreur : le protocole demande que reposer son propre verrou
     *       réussisse ;</li>
     *   <li>verrou à un autre → conflit, avec l'identifiant en place.</li>
     * </ul>
     *
     * @param ancienVerrou {@code X-WOPI-OldLock} : « relâche celui-ci et remets
     *                     celui-là ». Présent, il doit correspondre au verrou
     *                     en place, sinon c'est un conflit.
     */
    @Transactional
    public void lock(UUID fileId, String token, String lockId, String ancienVerrou) {
        Contexte c = resoudre(fileId, token, true);
        exigeIdentifiantDeVerrou(lockId);
        WopiSessionEntity s = c.session();
        Instant now = Instant.now();

        WopiVerrouEntity actuel = verrouVivant(s.getWorkspaceId(), s.getDocumentId(), now).orElse(null);

        if (actuel == null) {
            poser(s, lockId, now);
            log.info("Verrou pose doc={} user={} session={}",
                    s.getDocumentId(), s.getUserId(), s.getId());
            return;
        }

        String attendu = (ancienVerrou == null || ancienVerrou.isBlank()) ? lockId : ancienVerrou;
        if (!estLeSien(actuel, s, attendu)) {
            throw conflit(actuel, "verrou detenu par un autre editeur");
        }

        // Le verrou est bien le nôtre : on le prolonge, et on l'échange si un
        // ancien verrou était présenté.
        actuel.setLockId(lockId);
        actuel.setSessionId(s.getId());
        actuel.setUserId(s.getUserId());
        actuel.setUserDisplayName(s.getUserDisplayName());
        actuel.setExpireAt(now.plus(dureeVerrou));
        verrous.save(actuel);
    }

    /**
     * Prolonge un verrou existant. Collabora appelle ceci périodiquement ; sans
     * réponse favorable, il considère avoir perdu le document.
     */
    @Transactional
    public void refreshLock(UUID fileId, String token, String lockId) {
        Contexte c = resoudre(fileId, token, true);
        exigeIdentifiantDeVerrou(lockId);
        WopiSessionEntity s = c.session();
        Instant now = Instant.now();

        WopiVerrouEntity actuel = verrouVivant(s.getWorkspaceId(), s.getDocumentId(), now)
                .orElseThrow(() -> new WopiVerrouConflit("", null, "aucun verrou a rafraichir"));
        if (!estLeSien(actuel, s, lockId)) {
            throw conflit(actuel, "verrou detenu par un autre editeur");
        }
        actuel.setExpireAt(now.plus(dureeVerrou));
        verrous.save(actuel);
    }

    /**
     * Relâche un verrou. Un {@code lockId} qui ne correspond pas est un conflit,
     * jamais un succès silencieux : relâcher le verrou d'un autre rouvrirait la
     * porte à l'écrasement que ce lot ferme.
     */
    @Transactional
    public void unlock(UUID fileId, String token, String lockId) {
        Contexte c = resoudre(fileId, token, true);
        exigeIdentifiantDeVerrou(lockId);
        WopiSessionEntity s = c.session();
        Instant now = Instant.now();

        WopiVerrouEntity actuel = verrouVivant(s.getWorkspaceId(), s.getDocumentId(), now)
                .orElseThrow(() -> new WopiVerrouConflit("", null, "aucun verrou a relacher"));
        if (!estLeSien(actuel, s, lockId)) {
            throw conflit(actuel, "verrou detenu par un autre editeur");
        }
        verrous.delete(actuel);
        log.info("Verrou relache doc={} par user={}", s.getDocumentId(), s.getUserId());
    }

    /** Rend le verrou en place, ou la chaîne vide s'il n'y en a pas. */
    @Transactional(readOnly = true)
    public String getLock(UUID fileId, String token) {
        Contexte c = resoudre(fileId, token, false);
        WopiSessionEntity s = c.session();
        return verrouVivant(s.getWorkspaceId(), s.getDocumentId(), Instant.now())
                .map(WopiVerrouEntity::getLockId)
                .orElse("");
    }

    /**
     * Le verrou vivant d'un document, s'il y en a un.
     *
     * <p>Un verrou échu est traité comme ABSENT — et journalisé au passage : sa
     * reprise signifie qu'une séance a été interrompue sans se refermer, et
     * peut-être du travail non enregistré avec elle.
     */
    private java.util.Optional<WopiVerrouEntity> verrouVivant(UUID ws, UUID documentId, Instant now) {
        return verrous.findByWorkspaceIdAndDocumentId(ws, documentId)
                .filter(v -> {
                    if (v.vivant(now)) return true;
                    log.warn("Verrou echu repris doc={} ancien_detenteur={} echu_depuis={}",
                            documentId, v.getUserId(), Duration.between(v.getExpireAt(), now));
                    return false;
                });
    }

    private void poser(WopiSessionEntity s, String lockId, Instant now) {
        WopiVerrouEntity v = new WopiVerrouEntity();
        v.setDocumentId(s.getDocumentId());
        v.setWorkspaceId(s.getWorkspaceId());
        v.setLockId(lockId);
        v.setSessionId(s.getId());
        v.setUserId(s.getUserId());
        v.setUserDisplayName(s.getUserDisplayName());
        v.setAcquisAt(now);
        v.setExpireAt(now.plus(dureeVerrou));
        verrous.save(v);
    }

    /**
     * Ce verrou appartient-il à cette séance ?
     *
     * <p>Deux conditions, pas une. L'identifiant de verrou doit correspondre, ET
     * le détenteur doit être le même employé.
     *
     * <p>Vérifier le seul identifiant ne suffisait pas, et la vérification dans
     * l'application l'a montré : {@code GetLock} PUBLIE cet identifiant — le
     * protocole l'exige. Un second employé pouvait donc le lire, s'en servir pour
     * relâcher le verrou du premier, puis prendre sa place et écraser son travail.
     * L'identifiant de verrou désigne une session ; il ne prouve rien.
     *
     * <p>La comparaison porte sur l'employé et non sur la séance : le même employé
     * qui rouvre l'acte après une coupure retrouve son verrou, ce qui est le
     * comportement attendu. Ce dont l'acte doit être protégé, c'est d'un AUTRE.
     */
    private static boolean estLeSien(WopiVerrouEntity actuel, WopiSessionEntity s, String lockId) {
        return actuel.getUserId().equals(s.getUserId()) && actuel.detenuPar(lockId);
    }

    private static WopiVerrouConflit conflit(WopiVerrouEntity actuel, String raison) {
        return new WopiVerrouConflit(actuel.getLockId(),
                nomOuDefaut(actuel.getUserDisplayName()), raison);
    }

    private static void exigeIdentifiantDeVerrou(String lockId) {
        if (lockId == null || lockId.isBlank()) {
            throw new WopiAccesRefuse("identifiant de verrou absent");
        }
    }

    private static String nomOuDefaut(String nom) {
        return (nom == null || nom.isBlank()) ? "un autre employe" : nom;
    }

    @Transactional(readOnly = true)
    public CheckFileInfo checkFileInfo(UUID fileId, String token, String postMessageOrigin) {
        Contexte c = resoudre(fileId, token, false);
        DocumentEntity d = c.document();
        WopiSessionEntity s = c.session();

        return new CheckFileInfo(
                nomDeFichier(d),
                d.getSizeBytes(),
                d.getUploadedBy() == null ? "" : d.getUploadedBy().toString(),
                s.getUserId().toString(),
                s.getUserDisplayName() == null ? "Employé" : s.getUserDisplayName(),
                s.isCanWrite(),
                // Pas de « enregistrer sous » : un acte ne se duplique pas depuis l'editeur.
                true,
                s.isCanWrite(),
                // VERROUILLAGE SUPPORTE (lot 4). Tant que ce drapeau etait a
                // `false`, Collabora ne posait aucun verrou : deux employes
                // pouvaient ouvrir le meme acte et le second ecrasait le
                // premier sans qu'aucun des deux ne l'apprenne.
                true,
                // GetLock : l'editeur peut demander qui detient le verrou.
                true,
                // Identifiants de verrou de plus de 256 caracteres — ceux de
                // Collabora en font partie.
                true,
                false,
                versionOpaque(d),
                d.getCreatedAt() == null ? "" : d.getCreatedAt().toString(),
                false, false, false, false,
                postMessageOrigin == null ? "" : postMessageOrigin);
    }

    /** GetFile — le binaire .docx tel qu'il est stocké. */
    @Transactional(readOnly = true)
    public ObjectStorage.DownloadResult getFile(UUID fileId, String token) {
        Contexte c = resoudre(fileId, token, false);
        return storage.download(c.document().getObjectKey());
    }

    /**
     * PutFile — le binaire modifié.
     *
     * @param autosave    en-tête {@code X-COOL-WOPI-IsAutosave} : Collabora
     *                    enregistre tout seul, ce n'est pas un geste de l'employé
     * @param exitSave    en-tête {@code X-COOL-WOPI-IsExitSave} : dernier
     *                    enregistrement, souvent APRÈS la fermeture de l'onglet
     * @param lockId      en-tête {@code X-WOPI-Lock} : le verrou que l'appelant
     *                    croit détenir (lot 4)
     * @return l'identifiant du document effectivement écrit
     */
    @Transactional
    public UUID putFile(UUID fileId, String token, String lockId, byte[] contenu,
                        boolean autosave, boolean exitSave) {
        Contexte c = resoudre(fileId, token, true);

        // LE VERROU, AVANT TOUT LE RESTE (lot 4).
        //
        // Si un autre éditeur tient le document, cette écriture est refusée et
        // AUCUN octet n'est écrit : ni objet déposé, ni version juridique
        // créée. C'est exactement la perte silencieuse que le lot ferme — sans
        // ce contrôle, le dernier à enregistrer effaçait le travail du premier.
        //
        // Un verrou ABSENT laisse passer : c'est le cas de l'enregistrement
        // tardif, qui arrive après la fermeture de la séance et donc après le
        // relâchement de son propre verrou. Refuser là ferait perdre la
        // dernière sauvegarde — la même perte, par l'autre bout.
        WopiVerrouEntity verrou = verrouVivant(
                c.session().getWorkspaceId(), c.session().getDocumentId(), Instant.now()).orElse(null);
        if (verrou != null && !estLeSien(verrou, c.session(), lockId)) {
            log.warn("PutFile refuse doc={} : verrou detenu par user={} (session {})",
                    fileId, verrou.getUserId(), verrou.getSessionId());
            throw conflit(verrou, "verrou detenu par un autre editeur");
        }

        // Le contrôle d'accès passe AVANT toute validation du contenu : sinon un
        // jeton invalide reçoit une réponse différente selon le corps de la
        // requête, ce qui renseigne un attaquant sur ce qui a échoué.
        if (contenu == null || contenu.length == 0) {
            throw new IllegalArgumentException("corps vide : ce n'est pas une sauvegarde");
        }
        WopiSessionEntity s = c.session();
        DocumentEntity d = c.document();
        Instant now = Instant.now();

        // Le TenantContext n'est pas posé par le filtre JWT ici (Collabora
        // n'en présente aucun). Lot L0 (E15) : il est posé AVANT la transaction
        // par ContexteWopiConfig, à partir du jeton ; la ligne ci-dessous, dans le
        // corps de la méthode, arriverait trop tard pour la RLS et ne fait que
        // confirmer la même valeur pour les services appelés.
        TenantContext.set(s.getWorkspaceId());
        try {
            archiveGuard.assertWritable(d.getDossierId(), d.getTicketId());

            UUID ecritSur;
            if (d.isBrouillon()) {
                // Un brouillon est hors lignage (V26) : rien à versionner, on
                // met à jour la copie de travail.
                remplacerContenu(d, contenu, s.getUserId(), now);
                ecritSur = d.getId();
            } else if (s.getVersionDocumentId() != null) {
                // Même séance, enregistrement suivant : on met à jour LA version
                // créée par cette séance, on n'en empile pas une de plus.
                // `resoudre` a déjà substitué cette version au document
                // d'origine — `d` EST la version de la séance.
                remplacerContenu(d, contenu, s.getUserId(), now);
                ecritSur = d.getId();
            } else {
                // Premier enregistrement de la séance sur un document validé :
                // NOUVELLE version juridique. L'ancienne bascule en historique,
                // elle n'est pas écrasée.
                var resume = juridique.replaceAsNewVersion(
                        d.getId(), new ContenuEnMemoire(contenu, nomDeFichier(d), d.getContentType()),
                        "Édition manuelle dans l'éditeur bureautique", s.getUserId());
                DocumentEntity nouvelle = documents
                        .findByWorkspaceIdAndId(s.getWorkspaceId(), resume.id())
                        .orElseThrow(() -> new IllegalStateException("version creee introuvable"));
                nouvelle.setEditeManuellementAt(now);
                nouvelle.setEditePar(s.getUserId());
                documents.save(nouvelle);
                s.setVersionDocumentId(nouvelle.getId());
                ecritSur = nouvelle.getId();
            }

            s.setLastPutAt(now);
            s.setPutCount(s.getPutCount() + 1);
            sessions.save(s);

            log.info("PutFile doc={} ecrit_sur={} autosave={} exitSave={} put_no={} apres_fermeture={}",
                    fileId, ecritSur, autosave, exitSave, s.getPutCount(), s.getClosedAt() != null);
            return ecritSur;
        } finally {
            TenantContext.clear();
        }
    }

    // =================================================================
    //  Utilitaires
    // =================================================================

    /**
     * Remplace le contenu binaire d'une ligne existante : nouvel objet de
     * stockage, ancienne clé purgée, et marquage « modifié manuellement ».
     */
    private void remplacerContenu(DocumentEntity d, byte[] contenu, UUID parQui, Instant now) {
        String ancienneCle = d.getObjectKey();
        String safeName = nomDeFichier(d).replaceAll("[^a-zA-Z0-9._-]", "_");
        String cle = "ws/" + d.getWorkspaceId() + "/dossier/" + d.getDossierId()
                + (d.isBrouillon() ? "/brouillons/" + d.getTicketId() : "/juridique/" + d.getDocumentType())
                + "/edite_" + System.currentTimeMillis() + "_" + safeName;
        try (var in = new java.io.ByteArrayInputStream(contenu)) {
            storage.upload(cle, in, contenu.length, TYPE_DOCX);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Echec ecriture du document edite : " + ex.getMessage(), ex);
        }
        d.setObjectKey(cle);
        d.setSizeBytes(contenu.length);
        d.setContentType(TYPE_DOCX);
        d.setEditeManuellementAt(now);
        d.setEditePar(parQui);
        documents.save(d);

        if (ancienneCle != null && !ancienneCle.equals(cle)) {
            try {
                storage.delete(ancienneCle);
            } catch (Exception ex) {
                log.warn("Objet remplace non purge ({}) : {}", ancienneCle, ex.getMessage());
            }
        }
    }

    private static final String TYPE_DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private boolean dossierModifiable(DocumentEntity doc) {
        try {
            archiveGuard.assertWritable(doc.getDossierId(), doc.getTicketId());
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** Collabora choisit son moteur d'après l'extension : elle doit être là. */
    private static String nomDeFichier(DocumentEntity d) {
        String nom = d.getFilename() == null || d.getFilename().isBlank()
                ? d.getTitle() : d.getFilename();
        if (nom == null || nom.isBlank()) nom = "document";
        return nom.toLowerCase().endsWith(".docx") ? nom : nom + ".docx";
    }

    /**
     * Version opaque : Collabora s'en sert pour détecter qu'un document a changé
     * sous lui. Elle doit bouger à chaque écriture, d'où la clé de stockage.
     */
    private static String versionOpaque(DocumentEntity d) {
        return Integer.toHexString(java.util.Objects.hash(d.getVersion(), d.getObjectKey()));
    }

    private static String nouveauJeton() {
        byte[] b = new byte[32];
        ALEA.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String empreinte(String jeton) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(jeton.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    /** Adapte un tableau d'octets à l'API {@code MultipartFile} du versionnement existant. */
    private record ContenuEnMemoire(byte[] octets, String nom, String type)
            implements org.springframework.web.multipart.MultipartFile {
        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return nom; }
        @Override public String getContentType() { return type == null ? TYPE_DOCX : type; }
        @Override public boolean isEmpty() { return octets.length == 0; }
        @Override public long getSize() { return octets.length; }
        @Override public byte[] getBytes() { return octets; }
        @Override public java.io.InputStream getInputStream() {
            return new java.io.ByteArrayInputStream(octets);
        }
        @Override public void transferTo(java.io.File dest) throws java.io.IOException {
            java.nio.file.Files.write(dest.toPath(), octets);
        }
    }

}
