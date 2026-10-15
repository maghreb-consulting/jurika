package ma.jurika.billing.application.abonnement;

import ma.jurika.billing.application.cabinet.CabinetsPort;
import ma.jurika.billing.application.email.BillingEmailService;
import ma.jurika.billing.infrastructure.config.BillingProperties;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.SubscriptionJpaRepository;
import ma.jurika.billing.infrastructure.persistence.SubscriptionEntity;
import ma.jurika.common.notification.NotificationPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lot L7 (E8) : echeance impayee, article 4.4 du contrat d'abonnement valide : "en l'absence de
 * paiement 15 jours apres l'echeance et apres un rappel reste sans effet, l'acces au workspace est
 * suspendu. Aucune donnee n'est supprimee ; l'acces est retabli des le paiement constate."
 *
 * <p>Tache quotidienne ({@link #traiterEcheances}), idempotente, chaque cabinet a part :
 * <ol>
 *   <li><b>Arret du forfait</b> (articles 11 et 12, RG-TAR-07) : une resiliation enregistree
 *       (par le cabinet, ou par l'editeur pour defaut de paiement persistant, article 12.2) archive
 *       le cabinet a sa date d'effet. C'est le seul archivage automatique : jamais une
 *       suppression.</li>
 *   <li><b>Rappel</b> des l'echeance passee (J0) : courriel au cabinet (adresse du contrat,
 *       article 17), notification a l'ecran de ses superviseurs, avis au super-admin. Un seul
 *       rappel envoye par echeance ; un echec est journalise, signale au super-admin et retente au
 *       passage suivant.</li>
 *   <li><b>Delai</b> : l'acces continue jusqu'a J+{@value #DELAI_SUSPENSION_JOURS} exclu (bandeau
 *       a l'ecran : date de suspension et moyens de payer).</li>
 *   <li><b>Suspension</b> a J+{@value #DELAI_SUSPENSION_JOURS} sans paiement, SI le rappel a ete
 *       envoye avant ce jour ; sinon rien n'est suspendu, le fait est journalise et signale au
 *       super-admin. La suspension ne supprime rien ; le superviseur se connecte encore pour
 *       payer (auth-service, chemins de {@code AccesArchive}).</li>
 *   <li><b>Retablissement</b> des le paiement constate (carte confirmee par le CMI ou paiement
 *       enregistre par le super-admin), dans l'etat exact : {@link AbonnementService#appliquer}
 *       puis la propagation de l'activation.</li>
 * </ol>
 * Chaque fait est ecrit dans {@link ImpayeJournal} (billing V15).
 */
@Service
public class ImpayeService {

    private static final Logger log = LoggerFactory.getLogger(ImpayeService.class);

    /** Article 4.4 du contrat d'abonnement valide : 15 jours apres l'echeance (decision du 2026-10-14). */
    public static final int DELAI_SUSPENSION_JOURS = 15;

    private final SubscriptionJpaRepository abonnements;
    private final AbonnementService abonnement;
    private final ImpayeJournal journal;
    private final CabinetsPort cabinets;
    private final BillingEmailService courriel;
    private final BillingProperties props;
    private final ObjectProvider<NotificationPublisher> notifications;
    private final FacturationHorloge horloge;
    private final TransactionTemplate tx;

    public ImpayeService(SubscriptionJpaRepository abonnements, AbonnementService abonnement, ImpayeJournal journal,
                         CabinetsPort cabinets, BillingEmailService courriel, BillingProperties props,
                         ObjectProvider<NotificationPublisher> notifications, FacturationHorloge horloge,
                         PlatformTransactionManager tm) {
        this.abonnements = abonnements;
        this.abonnement = abonnement;
        this.journal = journal;
        this.cabinets = cabinets;
        this.courriel = courriel;
        this.props = props;
        this.notifications = notifications;
        this.horloge = horloge;
        this.tx = new TransactionTemplate(tm);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Date de suspension d'une echeance impayee (article 4.4). */
    public static LocalDate dateSuspension(LocalDate echeance) {
        return echeance.plusDays(DELAI_SUSPENSION_JOURS);
    }

    /**
     * Date d'arret du forfait d'un abonnement resilie (date d'effet de la resiliation, sinon fin de
     * la periode payee), ou null s'il n'est pas resilie.
     */
    public static LocalDate dateArret(SubscriptionEntity s) {
        if (!s.isCancelAtPeriodEnd()) return null;
        return s.getResiliationEffet() != null ? s.getResiliationEffet() : s.getPeriodeFin();
    }

    /**
     * L'echeance (fin de la periode payee) est-elle impayee au jour donne ? Non si le forfait
     * s'arrete au plus tard a cette echeance (resiliation : rien n'est du apres).
     */
    public static boolean echeanceImpayee(SubscriptionEntity s, LocalDate jour) {
        if (s.getPeriodeFin() == null || s.getPeriodeFin().isAfter(jour)) return false;
        LocalDate arret = dateArret(s);
        return arret == null || arret.isAfter(s.getPeriodeFin());
    }

    /** Bilan d'un passage de la tache quotidienne. */
    public record Bilan(int examines, int rappels, int suspensions, int archives, List<String> echecs) {
        public int reussis() { return rappels + suspensions + archives; }
    }

    private enum Fait { RIEN, RAPPEL, SUSPENSION, ARCHIVE }

    /** Tache quotidienne : arret du forfait, rappel, suspension (idempotente). */
    public Bilan traiterEcheances() {
        LocalDate jour = horloge.aujourdhui();
        List<SubscriptionEntity> echues = abonnements.findAllByStatusAndPeriodeFinLessThanEqual(AbonnementService.ACTIF, jour);
        int rappels = 0;
        int suspensions = 0;
        int archives = 0;
        List<String> echecs = new ArrayList<>();
        for (SubscriptionEntity e : echues) {
            try {
                for (Fait f : traiter(e.getWorkspaceId(), jour, echecs)) {
                    switch (f) {
                        case RAPPEL -> rappels++;
                        case SUSPENSION -> suspensions++;
                        case ARCHIVE -> archives++;
                        default -> { }
                    }
                }
            } catch (RuntimeException ex) {
                echecs.add(e.getWorkspaceId() + " : " + ex.getMessage());
                log.error("Echeance impayee non traitee workspace={} : {}", e.getWorkspaceId(), ex.getMessage());
            }
        }
        return new Bilan(echues.size(), rappels, suspensions, archives, echecs);
    }

    private List<Fait> traiter(UUID ws, LocalDate jour, List<String> echecs) {
        List<Fait> faits = new ArrayList<>();
        // 1. Arret du forfait (resiliation a sa date d'effet) : archivage, jamais une suppression.
        Boolean archive = tx.execute(st -> {
            SubscriptionEntity s = abonnements.verrouillerActif(ws).orElse(null);
            if (s == null) return false;
            LocalDate arret = dateArret(s);
            if (arret == null || arret.isAfter(jour)) return false;
            s.setStatus("cancelled");
            s.setArchiveMotif("RESILIATION");
            s.setCancelledAt(horloge.maintenant());
            abonnements.save(s);
            cabinets.archiver(ws, "RESILIATION");
            log.info("Forfait arrete (resiliation au {}) : cabinet archive workspace={}", arret, ws);
            return true;
        });
        if (Boolean.TRUE.equals(archive)) {
            faits.add(Fait.ARCHIVE);
            return faits;
        }
        SubscriptionEntity s = abonnements.findFirstByWorkspaceIdAndStatusOrderByCreatedAtDesc(ws, AbonnementService.ACTIF)
                .orElse(null);
        if (s == null || !echeanceImpayee(s, jour)) return faits;
        LocalDate echeance = s.getPeriodeFin();
        Long id = s.getId();

        // 2. Rappel (une seule fois par echeance, retente tant qu'il n'est pas parti).
        Instant rappelLe = s.rappelEnvoyePourEcheanceEnCours() ? s.getRappelImpayeEnvoyeLe() : null;
        if (rappelLe == null) {
            rappelLe = envoyerRappel(s, echecs);
            if (rappelLe != null) faits.add(Fait.RAPPEL);
        }

        // 3. Suspension a J+15, seulement apres un rappel envoye avant ce jour.
        if (s.getSuspenduLe() != null || jour.isBefore(dateSuspension(echeance))) return faits;
        if (rappelLe == null) {
            signalerSuspensionSansRappel(ws, id, echeance);
            return faits;
        }
        if (!FacturationHorloge.jour(rappelLe).isBefore(jour)) {
            log.info("Suspension differee au lendemain du rappel envoye ce jour workspace={} echeance={}", ws, echeance);
            return faits;
        }
        final LocalDate jourRappel = FacturationHorloge.jour(rappelLe);
        try {
            Boolean fait = tx.execute(st -> {
                SubscriptionEntity v = abonnements.verrouillerActif(ws).orElse(null);
                if (v == null || !id.equals(v.getId()) || !echeance.equals(v.getPeriodeFin()) || v.getSuspenduLe() != null) {
                    return false; // paye ou traite entre-temps
                }
                v.setSuspenduLe(horloge.maintenant());
                abonnements.save(v);
                journal.porteeGlobale();
                journal.ecrire(ws, id, echeance, ImpayeJournal.SUSPENSION, null,
                        "Acc\u00e8s suspendu (article 4.4) : \u00e9ch\u00e9ance du " + AbonnementService.format(echeance)
                                + " impay\u00e9e, rappel envoy\u00e9 le " + AbonnementService.format(jourRappel) + ".");
                cabinets.suspendreImpaye(ws);
                return true;
            });
            if (Boolean.TRUE.equals(fait)) {
                faits.add(Fait.SUSPENSION);
                log.info("Acces suspendu pour impaye workspace={} echeance={}", ws, echeance);
                avertir(ws, "ABONNEMENT_SUSPENDU", "Acc\u00e8s suspendu",
                        "L\u2019acc\u00e8s au cabinet est suspendu : l\u2019\u00e9ch\u00e9ance du " + AbonnementService.format(echeance)
                                + " n\u2019est pas r\u00e9gl\u00e9e. Payez-la pour le r\u00e9tablir ; rien n\u2019a \u00e9t\u00e9 supprim\u00e9.",
                        "a \u00e9t\u00e9 suspendu pour l\u2019\u00e9ch\u00e9ance impay\u00e9e du " + AbonnementService.format(echeance) + ".");
            }
        } catch (RuntimeException ex) {
            String motif = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            echecs.add(ws + " : suspension non faite (" + motif + ")");
            log.error("Suspension pour impaye non faite workspace={} (rejouee au passage suivant) : {}", ws, motif);
            ecrireSeul(ws, id, echeance, ImpayeJournal.SUSPENSION_ECHEC, null, motif);
        }
        return faits;
    }

    /** Envoie le rappel ; rend la date d'envoi, ou null si l'envoi a echoue (journalise). */
    private Instant envoyerRappel(SubscriptionEntity s, List<String> echecs) {
        UUID ws = s.getWorkspaceId();
        LocalDate echeance = s.getPeriodeFin();
        LocalDate suspension = dateSuspension(echeance);
        String destinataire = null;
        String cabinet = null;
        String erreur;
        try {
            CabinetsPort.IdentiteCabinet id = cabinets.identite(ws);
            destinataire = id == null ? null : id.email();
            cabinet = id == null ? null : id.denomination();
            Map<String, Object> vars = new HashMap<>();
            vars.put("cabinet", cabinet == null || cabinet.isBlank() ? "votre cabinet" : cabinet);
            AbonnementService.Echeance e = abonnement.prochaineEcheance(s);
            vars.put("forfait", e.plan().displayLabel());
            vars.put("echeance", AbonnementService.format(echeance));
            vars.put("montant", e.htCentimes() == null ? null : ma.jurika.billing.application.facture.FacturePdf.dirhams(
                    Tarification.depuisHt(e.htCentimes(), abonnement.tauxTva()).ttcCentimes()));
            vars.put("dernierJour", AbonnementService.format(suspension.minusDays(1)));
            vars.put("suspensionLe", AbonnementService.format(suspension));
            vars.put("lienPaiement", props.getFrontUrl() + "/app/billing");
            erreur = courriel.envoyerRappelImpaye(destinataire, vars);
        } catch (RuntimeException ex) {
            erreur = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        }
        Instant le = horloge.maintenant();
        String dest = destinataire;
        String err = erreur;
        if (erreur == null) {
            try {
                tx.executeWithoutResult(st -> {
                    SubscriptionEntity v = abonnements.verrouillerActif(ws).orElseThrow();
                    v.rappelEnvoye(echeance, le);
                    abonnements.save(v);
                    journal.porteeGlobale();
                    journal.ecrire(ws, s.getId(), echeance, ImpayeJournal.RAPPEL_ENVOYE, dest,
                            "Rappel envoy\u00e9 ; suspension pr\u00e9vue le " + AbonnementService.format(suspension) + " sans paiement.");
                });
            } catch (RuntimeException ex) {
                // Le courriel est parti mais rien n'est ecrit : on le dit (un second rappel suivra).
                log.error("Rappel envoye mais non enregistre workspace={} : {}", ws, ex.getMessage());
                echecs.add(ws + " : rappel envoy\u00e9 mais non enregistr\u00e9 (" + ex.getMessage() + ")");
                return null;
            }
            log.info("Rappel d'echeance impayee envoye workspace={} echeance={} destinataire={}", ws, echeance, dest);
            avertir(ws, "ECHEANCE_IMPAYEE", "\u00c9ch\u00e9ance non r\u00e9gl\u00e9e",
                    "L\u2019\u00e9ch\u00e9ance du " + AbonnementService.format(echeance) + " n\u2019est pas r\u00e9gl\u00e9e. Sans paiement, "
                            + "l\u2019acc\u00e8s au cabinet sera suspendu le " + AbonnementService.format(suspension) + ".",
                    "a re\u00e7u le rappel de l\u2019\u00e9ch\u00e9ance impay\u00e9e du " + AbonnementService.format(echeance)
                            + " ; suspension pr\u00e9vue le " + AbonnementService.format(suspension) + " sans paiement.");
            return le;
        }
        echecs.add(ws + " : rappel non envoy\u00e9 (" + err + ")");
        log.warn("Rappel d'echeance impayee NON envoye workspace={} echeance={} : {}", ws, echeance, err);
        ecrireSeul(ws, s.getId(), echeance, ImpayeJournal.RAPPEL_ECHEC, dest, err);
        avertirSuperAdmin(ws, "RAPPEL_IMPAYE_ECHEC", "Rappel d\u2019\u00e9ch\u00e9ance non envoy\u00e9",
                "n\u2019a pas re\u00e7u le rappel de l\u2019\u00e9ch\u00e9ance impay\u00e9e du " + AbonnementService.format(echeance)
                        + " (" + err + "). Sans rappel envoy\u00e9, l\u2019acc\u00e8s ne sera pas suspendu.");
        return null;
    }

    /** J+15 sans rappel envoye : pas de suspension ; journalise et signale une fois. */
    private void signalerSuspensionSansRappel(UUID ws, Long id, LocalDate echeance) {
        Boolean deja = tx.execute(st -> {
            journal.porteeGlobale();
            return journal.existe(ws, id, echeance, ImpayeJournal.SUSPENSION_SANS_RAPPEL);
        });
        log.warn("Acces NON suspendu faute de rappel envoye workspace={} echeance={}", ws, echeance);
        if (Boolean.TRUE.equals(deja)) return;
        ecrireSeul(ws, id, echeance, ImpayeJournal.SUSPENSION_SANS_RAPPEL, null,
                "D\u00e9lai de " + DELAI_SUSPENSION_JOURS + " jours \u00e9coul\u00e9 sans rappel envoy\u00e9 : acc\u00e8s non suspendu (article 4.4).");
        avertirSuperAdmin(ws, "SUSPENSION_SANS_RAPPEL", "Suspension non faite",
                "n\u2019a pas r\u00e9gl\u00e9 l\u2019\u00e9ch\u00e9ance du " + AbonnementService.format(echeance)
                        + ", mais aucun rappel n\u2019a pu lui \u00eatre envoy\u00e9 : son acc\u00e8s n\u2019est pas suspendu. "
                        + "V\u00e9rifiez son adresse \u00e9lectronique et l\u2019envoi des courriels.");
    }

    private void ecrireSeul(UUID ws, Long id, LocalDate echeance, String evenement, String destinataire, String detail) {
        try {
            tx.executeWithoutResult(st -> {
                journal.porteeGlobale();
                journal.ecrire(ws, id, echeance, evenement, destinataire, detail);
            });
        } catch (RuntimeException ex) {
            log.error("Journal des impayes non ecrit workspace={} evenement={} : {}", ws, evenement, ex.getMessage());
        }
    }

    /** Notification a l'ecran des superviseurs du cabinet et avis au super-admin. */
    private void avertir(UUID ws, String type, String titre, String messageCabinet, String messageSuperAdmin) {
        NotificationPublisher n = notifications.getIfAvailable();
        if (n == null) {
            log.warn("Notifications indisponibles : superviseurs non avertis a l'ecran workspace={} type={}", ws, type);
        } else {
            try {
                for (UUID sup : cabinets.superviseurs(ws)) {
                    n.notifyUser(sup, ws, type, titre, messageCabinet, "/app/billing", Map.of("workspaceId", ws.toString()));
                }
            } catch (RuntimeException e) {
                log.warn("Superviseurs non avertis a l'ecran workspace={} type={} : {}", ws, type, e.getMessage());
            }
        }
        avertirSuperAdmin(ws, type, titre, messageSuperAdmin);
    }

    private void avertirSuperAdmin(UUID ws, String type, String titre, String suite) {
        NotificationPublisher n = notifications.getIfAvailable();
        if (n == null) {
            log.warn("Notifications indisponibles : super-admin non averti workspace={} type={}", ws, type);
            return;
        }
        try {
            String cabinet;
            try {
                cabinet = cabinets.identite(ws).denomination();
            } catch (RuntimeException e) {
                cabinet = null;
            }
            String qui = cabinet == null || cabinet.isBlank() ? "Un cabinet" : "Le cabinet " + cabinet;
            for (CabinetsPort.SuperAdmin sa : cabinets.superAdmins()) {
                n.notifyUser(sa.id(), sa.workspaceId(), type, titre, qui + " " + suite, "/admin/abonnements",
                        Map.of("workspaceId", ws.toString()));
            }
        } catch (RuntimeException e) {
            log.warn("Super-admin non averti workspace={} type={} : {}", ws, type, e.getMessage());
        }
    }
}
