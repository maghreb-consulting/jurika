package ma.jurika.auth.application;

import ma.jurika.auth.domain.port.AlertesQuotaRegistre;
import ma.jurika.common.billing.PlanLimitsService;
import ma.jurika.common.billing.PlanLimitsService.UsageSnapshot;
import ma.jurika.common.notification.NotificationPublisher;
import ma.jurika.common.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Lot L5 (E9, RG-COM-08) : alerte « quota approchant », dans l'application, au
 * superviseur du cabinet.
 *
 * <p>Seuil : RG-PLN-02 du cahier des charges -- « a 80 % d'un quota (dossiers,
 * stockage, utilisateurs), le superviseur et le super-admin sont alertes ».
 * Le seuil est donc une donnee du cahier des charges, pas un choix : constante
 * {@link #SEUIL_ALERTE_POURCENT}. L'usage est celui de {@link PlanLimitsService}
 * (meme source que l'ecran d'usage et que le blocage a 100 %).
 *
 * <p>Une alerte par quota et par MOIS D'ABONNEMENT (registre auth V38, cle = annee-mois du debut du
 * mois d'abonnement, {@link ma.jurika.common.billing.PeriodeAbonnement#cle()}).
 *
 * <p>Lot L7 (E5), RG-PLN-02 : le super-admin est alerte aussi, dans SON workspace (canal du
 * super-admin : super-admins actifs lus par la fonction auth V44). Il gere tous les cabinets ; la
 * notification nomme le cabinet et renvoie a sa fiche. Aucun membre d'un cabinet ne recoit l'alerte
 * d'un autre cabinet (RG-COM-08).
 */
@Service
public class AlertesQuotaService {

    private static final Logger log = LoggerFactory.getLogger(AlertesQuotaService.class);

    /** RG-PLN-02 : seuil d'alerte, en pourcentage du quota. */
    public static final int SEUIL_ALERTE_POURCENT = 80;

    /** Type de notification connu du realtime-service. */
    public static final String TYPE = "QUOTA_APPROCHANT";

    /** Lot L7 (E5) : type de la notification du super-admin (quota d'un cabinet). */
    public static final String TYPE_SUPER_ADMIN = "QUOTA_CABINET_APPROCHANT";

    private static final ZoneId CASABLANCA = ZoneId.of("Africa/Casablanca");
    private static final long OCTETS_PAR_GO = 1024L * 1024L * 1024L;

    private final AlertesQuotaRegistre registre;
    private final ObjectProvider<PlanLimitsService> planLimits;
    private final NotificationPublisher notifier;
    private final TransactionTemplate transaction;
    private final Clock horloge;

    public AlertesQuotaService(AlertesQuotaRegistre registre, ObjectProvider<PlanLimitsService> planLimits,
                               NotificationPublisher notifier, PlatformTransactionManager transactions,
                               Clock horloge) {
        this.registre = registre;
        this.planLimits = planLimits;
        this.notifier = notifier;
        this.transaction = new TransactionTemplate(transactions);
        this.horloge = horloge;
    }

    /** Un quota au-dela du seuil. */
    record Depassement(String quota, String titre, String message, int pourcent) {}

    public record Bilan(int cabinets, int notifications, int erreurs) {}

    public Bilan detecter() {
        PlanLimitsService limites = planLimits.getIfAvailable();
        if (limites == null) {
            log.warn("Alertes de quota : PlanLimitsService inactif (jurika.plan-limits.enabled=false), rien n'est verifie");
            return new Bilan(0, 0, 0);
        }
        String periode = YearMonth.now(horloge.withZone(CASABLANCA)).toString();
        int cabinets = 0;
        int notifications = 0;
        int erreurs = 0;
        List<UUID> actifs = transaction.execute(s -> registre.cabinetsActifs());
        for (UUID ws : actifs == null ? List.<UUID>of() : actifs) {
            cabinets++;
            try {
                TenantContext.set(ws);
                Integer n = transaction.execute(s -> traiter(limites, ws, periode));
                notifications += n == null ? 0 : n;
            } catch (Exception ex) {
                erreurs++;
                log.error("Alertes de quota : cabinet {} non traite : {}", ws, ex.getMessage(), ex);
            } finally {
                TenantContext.clear();
            }
        }
        if (notifications > 0 || erreurs > 0) {
            log.info("Alertes de quota : {} cabinet(s), {} notification(s), {} erreur(s)", cabinets, notifications, erreurs);
        }
        return new Bilan(cabinets, notifications, erreurs);
    }

    private int traiter(PlanLimitsService limites, UUID ws, String periodeCivile) {
        UsageSnapshot usage = limites.snapshot(ws);
        List<Depassement> depassements = depassements(usage);
        if (depassements.isEmpty()) return 0;
        // RG-PLN-01 : le quota s'entend par mois d'abonnement ; la cle du registre est ce mois-la.
        String periode = usage.periodeDebut() == null ? periodeCivile : YearMonth.from(usage.periodeDebut()).toString();
        List<UUID> superviseurs = registre.superviseursActifs(ws);
        if (superviseurs.isEmpty()) {
            log.warn("Alertes de quota : cabinet {} sans superviseur actif ; seul le super-admin est alerte", ws);
        }
        List<AlertesQuotaRegistre.SuperAdmin> superAdmins = registre.superAdminsActifs();
        if (superAdmins.isEmpty()) {
            log.warn("Alertes de quota : aucun super-admin actif, le canal du super-admin n'a pas de destinataire");
        }
        String cabinet = registre.denominationCabinet(ws);
        int emises = 0;
        for (Depassement d : depassements) {
            if (!registre.retenirSiNouvelle(ws, d.quota(), periode)) continue;
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("quota", d.quota());
            meta.put("pourcent", d.pourcent());
            meta.put("periode", periode);
            for (UUID s : superviseurs) {
                notifier.notifyUser(s, ws, TYPE, d.titre(), d.message(), "/app/billing/change-plan", meta);
                emises++;
            }
            Map<String, Object> metaAdmin = new LinkedHashMap<>(meta);
            metaAdmin.put("workspaceId", ws.toString());
            String titre = (cabinet == null ? "Un cabinet" : cabinet) + " : " + d.titre().toLowerCase(Locale.FRANCE);
            for (AlertesQuotaRegistre.SuperAdmin sa : superAdmins) {
                notifier.notifyUser(sa.id(), sa.workspaceId(), TYPE_SUPER_ADMIN, titre,
                        "Cabinet " + (cabinet == null ? ws.toString() : cabinet) + " : " + d.message(),
                        "/admin/workspaces/" + ws, metaAdmin);
                emises++;
            }
        }
        return emises;
    }

    /** Quotas atteints a {@value #SEUIL_ALERTE_POURCENT} % ou plus (illimite : jamais). */
    static List<Depassement> depassements(UsageSnapshot u) {
        List<Depassement> out = new ArrayList<>();
        // Recette R1 : pendant l'essai, le libelle est celui de l'essai (RG-ESS-07), sans "forfait".
        String forfait = u.planLabel() == null ? ""
                : u.essai() ? " (" + u.planLabel() + ")" : " (forfait " + u.planLabel() + ")";
        if (!u.dossiersUnlimited() && atteint(u.dossiers(), u.maxDossiers())) {
            int p = pourcent(u.dossiers(), u.maxDossiers());
            out.add(new Depassement("DOSSIERS", "Quota de dossiers presque atteint",
                    u.dossiers() + " dossiers sur " + u.maxDossiers() + forfait + ", soit " + p
                            + " % du quota. À 100 %, la création d’un nouveau dossier sera bloquée : "
                            + "pensez à changer de forfait.", p));
        }
        if (!u.usersUnlimited() && atteint(u.users(), u.maxUsers())) {
            int p = pourcent(u.users(), u.maxUsers());
            out.add(new Depassement("UTILISATEURS", "Quota d’utilisateurs presque atteint",
                    u.users() + " employés sur " + u.maxUsers() + forfait + ", soit " + p
                            + " % du quota. À 100 %, aucun nouvel employé ne pourra être invité : "
                            + "pensez à changer de forfait.", p));
        }
        long maxOctets = u.maxStorageOctets();
        if (!u.storageUnlimited() && atteint(u.storageBytes(), maxOctets)) {
            int p = pourcent(u.storageBytes(), maxOctets);
            String volume = u.essai()
                    ? String.format(Locale.FRANCE, "%.0f Mo utilisés sur %d Mo", u.storageBytes() / (1024.0 * 1024),
                            maxOctets / (1024L * 1024L))
                    : String.format(Locale.FRANCE, "%.1f Go utilisés sur %d Go", u.storageBytes() / (double) OCTETS_PAR_GO,
                            u.maxStorageGb());
            out.add(new Depassement("STOCKAGE", "Stockage presque plein",
                    volume + forfait + ", soit " + p
                            + " % du quota. À 100 %, l’ajout de documents sera bloqué : pensez à changer de forfait.", p));
        }
        return out;
    }

    static boolean atteint(long usage, long max) {
        return max > 0 && usage * 100 >= (long) SEUIL_ALERTE_POURCENT * max;
    }

    private static int pourcent(long usage, long max) {
        return (int) Math.min(999, usage * 100 / max);
    }
}
