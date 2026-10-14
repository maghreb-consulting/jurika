package ma.jurika.auth.application.essai;

import ma.jurika.auth.domain.port.AlertesQuotaRegistre;
import ma.jurika.auth.domain.port.AuditLogger;
import ma.jurika.auth.domain.port.EmailSender;
import ma.jurika.auth.domain.port.PurgeObjetsEssai;
import ma.jurika.auth.infrastructure.essai.EssaiJdbc;
import ma.jurika.auth.infrastructure.persistence.WorkspaceEntity;
import ma.jurika.auth.infrastructure.persistence.WorkspaceJpaRepository;
import ma.jurika.common.exception.BusinessException;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.notification.NotificationPublisher;
import ma.jurika.common.trial.EssaiRegles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Lot L7 (E4) : cycle de l'essai gratuit (CDC 4.2).
 *
 * <ul>
 *   <li>RG-ESS-10 : au terme des 7 jours (ou de la fin decidee par le
 *       super-admin), lecture seule pendant 30 jours ; l'avertissement de
 *       suppression (courriel et application) part au passage en lecture
 *       seule et porte la date de suppression ; la suppression definitive
 *       n'a lieu qu'apres cet avertissement, au terme des 30 jours ;</li>
 *   <li>RG-ESS-09 : un cabinet qui souscrit sort de l'essai (CONVERTED, pose
 *       par la transition ACTIVATED de billing) : rien n'est supprime ;</li>
 *   <li>RG-ESS-11 : le super-admin prolonge (une fois) ou met fin.</li>
 * </ul>
 * Chaque operation porte sur UN workspace, pose comme workspace courant par
 * l'appelant avant la transaction (RLS).
 */
@Service
public class CycleEssaiService {

    private static final Logger log = LoggerFactory.getLogger(CycleEssaiService.class);
    private static final ZoneId CASABLANCA = ZoneId.of("Africa/Casablanca");
    private static final DateTimeFormatter DATE_FR =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH);
    /** A_DECIDER (directeur) : duree maximale d'une prolongation ; borne de securite retenue. */
    static final int PROLONGATION_MAX_JOURS = 30;

    private final WorkspaceJpaRepository workspaces;
    private final EssaiJdbc essais;
    private final EmailSender emailSender;
    private final AuditLogger audit;
    private final PurgeObjetsEssai purgeObjets;
    private final JdbcTemplate jdbc;
    private final AlertesQuotaRegistre superviseurs;
    private final NotificationPublisher notifications;
    private final Clock horloge;

    public CycleEssaiService(WorkspaceJpaRepository workspaces, EssaiJdbc essais, EmailSender emailSender,
                             AuditLogger audit, PurgeObjetsEssai purgeObjets, JdbcTemplate jdbc,
                             AlertesQuotaRegistre superviseurs, NotificationPublisher notifications,
                             Clock horloge) {
        this.superviseurs = superviseurs;
        this.notifications = notifications;
        this.workspaces = workspaces;
        this.essais = essais;
        this.emailSender = emailSender;
        this.audit = audit;
        this.purgeObjets = purgeObjets;
        this.jdbc = jdbc;
        this.horloge = horloge;
    }

    /** Resultat d'un passage du cycle sur un workspace. */
    public enum Issue { AUCUNE, LECTURE_SEULE, AVERTI, A_SUPPRIMER }

    @Transactional(readOnly = true)
    public EtatEssai etat(UUID workspaceId) {
        return EtatEssai.de(charger(workspaceId), horloge.instant());
    }

    /**
     * Passage planifie : bascule en lecture seule, avertissement. Renvoie
     * {@link Issue#A_SUPPRIMER} quand les 30 jours sont ecoules apres
     * avertissement ; la suppression elle-meme est {@link #supprimer(UUID)}.
     */
    @Transactional
    public Issue traiter(UUID workspaceId) {
        WorkspaceEntity ws = charger(workspaceId);
        Instant maintenant = horloge.instant();
        Issue issue = Issue.AUCUNE;
        if ("TRIAL_ACTIVE".equals(ws.getTrialStatus()) && ws.getTrialEndsAt() != null
                && !maintenant.isBefore(ws.getTrialEndsAt())) {
            passerEnLectureSeule(ws, ws.getTrialEndsAt(), null);
            issue = Issue.LECTURE_SEULE;
        }
        if ("TRIAL_EXPIRED".equals(ws.getTrialStatus()) && ws.getEssaiExpireLe() == null) {
            // Essai expire avant ce lot (ancienne logique) : la lecture seule commence maintenant.
            ws.setEssaiExpireLe(maintenant);
        }
        if ("TRIAL_EXPIRED".equals(ws.getTrialStatus()) && ws.getEssaiAvertissementLe() == null) {
            if (avertir(ws)) {
                ws.setEssaiAvertissementLe(maintenant);
                issue = Issue.AVERTI;
            }
        }
        workspaces.save(ws);
        if ("TRIAL_EXPIRED".equals(ws.getTrialStatus()) && ws.getEssaiAvertissementLe() != null
                && ws.getSubscriptionStatus() == null
                && !maintenant.isBefore(ws.getEssaiExpireLe().plus(Duration.ofDays(EssaiRegles.LECTURE_SEULE_JOURS)))) {
            return Issue.A_SUPPRIMER;
        }
        return issue;
    }

    /**
     * RG-ESS-10 : suppression definitive. Objets MinIO d'abord (dataroom), puis
     * toutes les lignes du cabinet (fonction essai_supprimer_definitivement,
     * qui reverifie les garde-fous). Un echec MinIO arrete tout.
     */
    @Transactional
    public Map<String, Long> supprimer(UUID workspaceId) {
        WorkspaceEntity ws = charger(workspaceId);
        Instant maintenant = horloge.instant();
        if (!"TRIAL_EXPIRED".equals(ws.getTrialStatus()) || ws.getSubscriptionStatus() != null
                || ws.getEssaiAvertissementLe() == null || ws.getEssaiExpireLe() == null
                || maintenant.isBefore(ws.getEssaiExpireLe().plus(Duration.ofDays(EssaiRegles.LECTURE_SEULE_JOURS)))) {
            throw new BusinessException("ESSAI_SUPPRESSION_REFUSEE",
                    "Suppression refus\u00e9e : seul un essai non souscrit, averti et en lecture seule depuis "
                            + EssaiRegles.LECTURE_SEULE_JOURS + " jours peut \u00eatre supprim\u00e9.");
        }
        String code = ws.getCode();
        int objets = purgeObjets.purger(workspaceId);
        Map<String, Long> lignes = new LinkedHashMap<>();
        jdbc.query("SELECT table_nom, lignes FROM essai_supprimer_definitivement(?)",
                rs -> { lignes.put(rs.getString(1), rs.getLong(2)); }, workspaceId);
        long total = lignes.values().stream().mapToLong(Long::longValue).sum();
        // La trace est ecrite hors du workspace supprime (workspace NULL), sans donnee personnelle.
        Map<String, Object> meta = new HashMap<>();
        meta.put("workspaceCode", code);
        meta.put("objetsMinio", objets);
        meta.put("lignes", total);
        audit.log(null, null, "ESSAI_SUPPRIME_DEFINITIVEMENT", "workspace", workspaceId, null, null, meta);
        log.warn("Essai {} supprime definitivement : {} objets, {} lignes", code, objets, total);
        return lignes;
    }

    // ------------------------------------------------------- super-admin

    @Transactional
    public EtatEssai prolonger(UUID workspaceId, Integer jours, UUID admin) {
        WorkspaceEntity ws = charger(workspaceId);
        exigerEssai(ws);
        if (ws.getEssaiProlongeLe() != null) {
            throw new BusinessException("ESSAI_DEJA_PROLONGE",
                    "Cet essai a d\u00e9j\u00e0 \u00e9t\u00e9 prolong\u00e9 : un essai ne se prolonge qu\u2019une fois.");
        }
        if (jours == null || jours < 1 || jours > PROLONGATION_MAX_JOURS) {
            throw new ValidationException("Indiquez la dur\u00e9e de la prolongation, de 1 \u00e0 "
                    + PROLONGATION_MAX_JOURS + " jours.");
        }
        Instant maintenant = horloge.instant();
        Instant base = "TRIAL_ACTIVE".equals(ws.getTrialStatus()) && ws.getTrialEndsAt() != null
                && ws.getTrialEndsAt().isAfter(maintenant) ? ws.getTrialEndsAt() : maintenant;
        ws.setTrialEndsAt(base.plus(Duration.ofDays(jours)));
        ws.setTrialStatus("TRIAL_ACTIVE");
        ws.setEssaiExpireLe(null);
        ws.setEssaiAvertissementLe(null);
        ws.setEssaiFinMotif(null);
        ws.setEssaiProlongeLe(maintenant);
        ws.setEssaiProlongationJours(jours);
        workspaces.save(ws);
        Map<String, Object> meta = new HashMap<>();
        meta.put("jours", jours);
        meta.put("nouvelleFin", ws.getTrialEndsAt().toString());
        audit.log(workspaceId, admin, "ESSAI_PROLONGE", "workspace", workspaceId, null, null, meta);
        return EtatEssai.de(ws, maintenant);
    }

    @Transactional
    public EtatEssai mettreFin(UUID workspaceId, String motif, UUID admin) {
        WorkspaceEntity ws = charger(workspaceId);
        if (!"TRIAL_ACTIVE".equals(ws.getTrialStatus())) {
            throw new BusinessException("ESSAI_NON_EN_COURS", "Cet essai n\u2019est pas en cours.");
        }
        if (motif == null || motif.isBlank()) {
            throw new ValidationException("Indiquez le motif de la fin de l\u2019essai.");
        }
        String m = motif.trim().length() > 300 ? motif.trim().substring(0, 300) : motif.trim();
        Instant maintenant = horloge.instant();
        passerEnLectureSeule(ws, maintenant, m);
        if (avertir(ws)) {
            ws.setEssaiAvertissementLe(maintenant);
        }
        workspaces.save(ws);
        Map<String, Object> meta = new HashMap<>();
        meta.put("motif", m);
        audit.log(workspaceId, admin, "ESSAI_TERMINE_PAR_JURIKA", "workspace", workspaceId, null, null, meta);
        return EtatEssai.de(ws, maintenant);
    }

    @Transactional
    public void signalementExamine(UUID workspaceId, UUID admin) {
        if (essais.signalementExamine(workspaceId, horloge.instant()) == 0) {
            throw new NotFoundException("Aucun signalement pour ce cabinet.");
        }
        audit.log(workspaceId, admin, "ESSAI_SIGNALEMENT_EXAMINE", "workspace", workspaceId, null, null, Map.of());
    }

    // ------------------------------------------------------------- outils

    private void passerEnLectureSeule(WorkspaceEntity ws, Instant depuis, String motif) {
        ws.setTrialStatus("TRIAL_EXPIRED");
        ws.setTrialEndsAt(depuis);
        ws.setEssaiExpireLe(depuis);
        ws.setEssaiFinMotif(motif);
        audit.log(ws.getId(), null, "ESSAI_LECTURE_SEULE", "workspace", ws.getId(), null, null,
                Map.of("depuis", depuis.toString()));
    }

    /** Courriel d'avertissement ; {@code false} s'il n'est pas parti (nouvel essai au passage suivant). */
    private boolean avertir(WorkspaceEntity ws) {
        Instant suppression = ws.getEssaiExpireLe().plus(Duration.ofDays(EssaiRegles.LECTURE_SEULE_JOURS));
        Map<String, Object> vars = new HashMap<>();
        vars.put("brand", "JURIKA");
        vars.put("cabinet", ws.getName());
        vars.put("workspaceCode", ws.getCode());
        vars.put("dateSuppression", DATE_FR.format(suppression.atZone(CASABLANCA)));
        vars.put("subject", "Votre essai gratuit JURIKA est termin\u00e9");
        // Avertissement dans l'application (cloche) ; le bandeau de l'essai le repete a chaque page.
        String message = "Le cabinet est en lecture seule. Sans souscription d\u2019un forfait, ses donn\u00e9es "
                + "seront supprim\u00e9es d\u00e9finitivement le " + vars.get("dateSuppression") + ".";
        for (UUID s : superviseurs.superviseursActifs(ws.getId())) {
            notifications.notifyUser(s, ws.getId(), "WORKSPACE_EVENT", "Votre essai gratuit est termin\u00e9",
                    message, "/app/billing", Map.of("essai", "AVERTISSEMENT_SUPPRESSION"));
        }
        try {
            emailSender.sendTemplated(ws.getContactEmail(), "Votre essai gratuit JURIKA est termin\u00e9",
                    "essai-avertissement-suppression", vars);
            return true;
        } catch (RuntimeException ex) {
            log.error("Avertissement de suppression de l'essai {} non envoye : {}", ws.getCode(), ex.getMessage());
            return false;
        }
    }

    private static void exigerEssai(WorkspaceEntity ws) {
        if (!"TRIAL_ACTIVE".equals(ws.getTrialStatus()) && !"TRIAL_EXPIRED".equals(ws.getTrialStatus())) {
            throw new BusinessException("ESSAI_INCONNU", "Ce cabinet n\u2019est pas en essai gratuit.");
        }
    }

    private WorkspaceEntity charger(UUID workspaceId) {
        return workspaces.findById(workspaceId)
                .orElseThrow(() -> new NotFoundException("Cabinet introuvable."));
    }
}
