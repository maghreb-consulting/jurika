package ma.jurika.billing.application.abonnement;

import ma.jurika.billing.api.dto.BillingDtos.AbonnementVue;
import ma.jurika.billing.api.dto.BillingDtos.ApercuChangementVue;
import ma.jurika.billing.api.dto.BillingDtos.ChangementAVenirVue;
import ma.jurika.billing.api.dto.BillingDtos.EcheanceVue;
import ma.jurika.billing.api.dto.BillingDtos.FonctionVue;
import ma.jurika.billing.api.dto.BillingDtos.MontantsVue;
import ma.jurika.billing.application.RefusNommeException;
import ma.jurika.billing.application.cabinet.CabinetsPort;
import ma.jurika.billing.application.cabinet.CabinetsPort.UsageCabinet;
import ma.jurika.billing.application.contrat.ContratAbonnementPort;
import ma.jurika.billing.application.contrat.ContratAbonnementPort.ConditionsParticulieres;
import ma.jurika.billing.infrastructure.cmi.CmiProperties;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.EditeurParametresJpaRepository;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.PaymentJpaRepository;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.SubscriptionJpaRepository;
import ma.jurika.billing.infrastructure.persistence.EditeurParametresEntity;
import ma.jurika.billing.infrastructure.persistence.PaymentEntity;
import ma.jurika.billing.infrastructure.persistence.SubscriptionEntity;
import ma.jurika.common.billing.FonctionForfait;
import ma.jurika.common.billing.PeriodeAbonnement;
import ma.jurika.common.billing.PlanCatalog;
import ma.jurika.common.billing.PlanCatalog.BillingPeriod;
import ma.jurika.common.billing.PlanCatalog.Plan;
import ma.jurika.common.billing.PlanLimitsService;
import ma.jurika.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Lot L7 (E5) : abonnement d'un cabinet, CDC 4.3 et 4.7.
 *
 * <ul>
 *   <li>RG-TAR-01, RG-TAR-02 : souscrit pour un an ; echeance mensuelle (dixieme du prix annuel) ou
 *       annuelle.</li>
 *   <li>RG-TAR-04 : hausse effective tout de suite ; en annuel, difference au prorata des mois
 *       restants, les nouveaux quotas s'appliquant a la confirmation du paiement ; en mensuel, la
 *       mensualite suivante est celle du nouveau forfait (hausse appliquee sans paiement
 *       intermediaire : Decision a revoir du lot).</li>
 *   <li>RG-TAR-05 : baisse a la prochaine echeance, sans remboursement, refusee si l'usage depasse le
 *       forfait vise (ce qui depasse est nomme).</li>
 *   <li>RG-TAR-06 : rythme change a la prochaine echeance.</li>
 *   <li>RG-TAR-03, RG-TAR-07, RG-PLN-07 : echeance impayee ou annee non renouvelee : archivage,
 *       jamais de suppression ; RG-TAR-08 : nouvelle souscription = reactivation dans l'etat exact.</li>
 * </ul>
 * Chaque activation et chaque changement ouvre des conditions particulieres en attente
 * d'acceptation ({@link ContratAbonnementPort}, RG-CTR-03, RG-CTR-05) quand la partie contrat est
 * presente.
 */
@Service
public class AbonnementService {

    private static final Logger log = LoggerFactory.getLogger(AbonnementService.class);

    /**
     * A_DECIDER (direction) : delai de grace apres une echeance impayee avant l'archivage. Le CDC
     * n'en prevoit pas (RG-TAR-03) : 0 jour, rien n'est invente.
     */
    public static final int DELAI_GRACE_JOURS_A_DECIDER = 0;

    public static final String ACTIF = "active";

    private final SubscriptionJpaRepository abonnements;
    private final PaymentJpaRepository paiements;
    private final EditeurParametresJpaRepository editeur;
    private final CabinetsPort cabinets;
    private final ObjectProvider<ContratAbonnementPort> contrat;
    private final CmiProperties cmi;
    private final Clock horloge;

    @Autowired
    public AbonnementService(SubscriptionJpaRepository abonnements, PaymentJpaRepository paiements,
                             EditeurParametresJpaRepository editeur, CabinetsPort cabinets,
                             ObjectProvider<ContratAbonnementPort> contrat, CmiProperties cmi) {
        this(abonnements, paiements, editeur, cabinets, contrat, cmi, Clock.systemUTC());
    }

    AbonnementService(SubscriptionJpaRepository abonnements, PaymentJpaRepository paiements,
                      EditeurParametresJpaRepository editeur, CabinetsPort cabinets,
                      ObjectProvider<ContratAbonnementPort> contrat, CmiProperties cmi, Clock horloge) {
        this.abonnements = abonnements;
        this.paiements = paiements;
        this.editeur = editeur;
        this.cabinets = cabinets;
        this.contrat = contrat;
        this.cmi = cmi;
        this.horloge = horloge;
    }

    /** Aujourd'hui a Casablanca. */
    public LocalDate aujourdhui() {
        return LocalDate.ofInstant(horloge.instant(), PeriodeAbonnement.CASABLANCA);
    }

    /** Taux de TVA des parametres de l'editeur (reglable par le super-admin). */
    public BigDecimal tauxTva() {
        return editeur.unique().map(EditeurParametresEntity::getTauxTvaPourcent)
                .orElseThrow(() -> new IllegalStateException("Parametres de l'editeur absents (billing V6)"));
    }

    public Optional<SubscriptionEntity> actif(UUID workspaceId) {
        return abonnements.findFirstByWorkspaceIdAndStatusOrderByCreatedAtDesc(workspaceId, ACTIF);
    }

    // ------------------------------------------------------------------ lecture

    @Transactional(readOnly = true)
    public AbonnementVue etat(UUID workspaceId) {
        Optional<SubscriptionEntity> actif = actif(workspaceId);
        int enAttente = (int) paiements.findAllByWorkspaceIdAndStatus(workspaceId, "PENDING").size();
        String carteMotif = cmi.configure() ? null
                : "Paiement par carte non encore disponible : la liaison avec le CMI n\u2019est pas configur\u00e9e. "
                + "Choisissez le virement, le ch\u00e8que ou les esp\u00e8ces.";
        if (actif.isEmpty()) {
            Optional<SubscriptionEntity> dernier = abonnements.findFirstByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
            String statut = dernier.isPresent() ? "ARCHIVE" : "AUCUN";
            Plan plan = dernier.flatMap(s -> PlanCatalog.findByCode(s.getPlanCode())).orElse(null);
            return new AbonnementVue(workspaceId, statut,
                    plan == null ? null : plan.internalCode(), plan == null ? null : plan.displayLabel(),
                    dernier.map(SubscriptionEntity::getRythme).orElse(null),
                    dernier.map(SubscriptionEntity::getEngagementDebut).orElse(null),
                    dernier.map(SubscriptionEntity::getEngagementFin).orElse(null),
                    dernier.map(SubscriptionEntity::getPeriodeDebut).orElse(null),
                    dernier.map(SubscriptionEntity::getPeriodeFin).orElse(null),
                    null, null, dernier.map(SubscriptionEntity::getArchiveMotif).orElse(null),
                    fonctions(plan), cmi.configure(), carteMotif, enAttente);
        }
        SubscriptionEntity s = actif.get();
        Plan plan = PlanCatalog.exiger(s.getPlanCode());
        return new AbonnementVue(workspaceId, "ACTIF", plan.internalCode(), plan.displayLabel(), s.getRythme(),
                s.getEngagementDebut(), s.getEngagementFin(), s.getPeriodeDebut(), s.getPeriodeFin(),
                echeanceVue(s), changementAVenir(s), null, fonctions(plan), cmi.configure(), carteMotif, enAttente);
    }

    static List<FonctionVue> fonctions(Plan plan) {
        return Arrays.stream(FonctionForfait.values())
                .map(f -> new FonctionVue(f.name(), f.libelle(), plan != null && plan.inclut(f),
                        PlanLimitsService.premierForfaitAvec(f)))
                .toList();
    }

    /** Prochaine echeance d'un abonnement actif : forfait et rythme a venir, periode, montants. */
    public record Echeance(Plan plan, BillingPeriod rythme, LocalDate debut, LocalDate fin, Long htCentimes, String motif) {}

    public Echeance prochaineEcheance(SubscriptionEntity s) {
        Plan plan = PlanCatalog.exiger(s.getPlanAVenir() != null ? s.getPlanAVenir() : s.getPlanCode());
        String r = s.getRythmeAVenir() != null ? s.getRythmeAVenir() : s.getRythme();
        if (r == null) {
            return new Echeance(plan, null, s.getPeriodeFin(), null, null,
                    "Rythme de paiement inconnu (abonnement ant\u00e9rieur au lot) : \u00e0 fixer par JURIKA.");
        }
        BillingPeriod rythme = BillingPeriod.fromString(r);
        LocalDate debut = s.getPeriodeFin();
        LocalDate ancre = s.getEngagementDebut() != null ? s.getEngagementDebut() : debut;
        LocalDate fin = Tarification.finPeriode(ancre, debut, rythme);
        try {
            long ht = Tarification.prixEcheanceHt(plan, rythme, s.getPrixEcheanceHtCentimes());
            return new Echeance(plan, rythme, debut, fin, ht, null);
        } catch (IllegalStateException e) {
            return new Echeance(plan, rythme, debut, fin, null, e.getMessage());
        }
    }

    EcheanceVue echeanceVue(SubscriptionEntity s) {
        Echeance e = prochaineEcheance(s);
        MontantsVue m = e.htCentimes() == null ? null : montants(Tarification.depuisHt(e.htCentimes(), tauxTva()));
        return new EcheanceVue(e.debut(), e.fin(), e.plan().internalCode(), e.plan().displayLabel(),
                e.rythme() == null ? null : e.rythme().suffix(), m, e.motif());
    }

    static ChangementAVenirVue changementAVenir(SubscriptionEntity s) {
        if (s.getPlanAVenir() == null && s.getRythmeAVenir() == null) return null;
        Plan p = PlanCatalog.exiger(s.getPlanAVenir() != null ? s.getPlanAVenir() : s.getPlanCode());
        return new ChangementAVenirVue(p.internalCode(), p.displayLabel(),
                s.getRythmeAVenir() != null ? s.getRythmeAVenir() : s.getRythme(), s.getPeriodeFin());
    }

    static MontantsVue montants(Tarification.Montants m) {
        return new MontantsVue(m.htCentimes(), m.tauxTva(), m.tvaCentimes(), m.ttcCentimes());
    }

    // ------------------------------------------------------------------ changement de forfait

    /** Calcul d'un changement demande, sans rien ecrire. */
    public record Changement(SubscriptionEntity abonnement, Plan depuis, Plan vers, BillingPeriod rythmeActuel,
                             BillingPeriod rythmeVise, String nature, String effet, LocalDate effectifLe,
                             Tarification.Prorata prorata, List<String> depassements) {
        public boolean accepte() { return depassements.isEmpty() && !"IDENTIQUE".equals(nature); }
    }

    public Changement calculerChangement(UUID workspaceId, String forfaitVise, String rythmeVise) {
        SubscriptionEntity s = actif(workspaceId).orElseThrow(() -> new BusinessException("ABONNEMENT_ABSENT",
                "Aucun abonnement actif : souscrivez d\u2019abord un forfait."));
        Plan depuis = PlanCatalog.exiger(s.getPlanCode());
        Plan vers = PlanCatalog.findByCode(forfaitVise)
                .orElseThrow(() -> new IllegalArgumentException("Forfait inconnu : " + forfaitVise));
        if (vers.quoteOnly() && !depuis.quoteOnly()) {
            throw new BusinessException("SUR_DEVIS",
                    "Le forfait Sur Devis se souscrit aupr\u00e8s de JURIKA : utilisez la demande de devis.");
        }
        if (s.getRythme() == null) {
            throw new BusinessException("RYTHME_INCONNU",
                    "Le rythme de paiement de cet abonnement n\u2019est pas connu : JURIKA doit le fixer avant tout changement.");
        }
        BillingPeriod actuel = BillingPeriod.fromString(s.getRythme());
        BillingPeriod vise = rythmeVise == null || rythmeVise.isBlank() ? actuel : BillingPeriod.fromString(rythmeVise);
        List<String> depassements = new ArrayList<>();
        String nature;
        String effet;
        LocalDate effectifLe;
        Tarification.Prorata prorata = null;
        if (PlanCatalog.estHausse(depuis, vers)) {
            nature = "HAUSSE";
            if (actuel == BillingPeriod.YEARLY) {
                effet = "APRES_PAIEMENT";
                prorata = Tarification.prorataHausseAnnuelle(depuis, vers, s.getEngagementDebut(), s.getEngagementFin(), aujourdhui());
            } else {
                effet = "IMMEDIAT";
            }
            effectifLe = aujourdhui();
        } else if (PlanCatalog.estBaisse(depuis, vers)) {
            nature = "BAISSE";
            effet = "PROCHAINE_ECHEANCE";
            effectifLe = s.getPeriodeFin();
            depassements.addAll(depassements(workspaceId, vers));
        } else if (vise != actuel) {
            nature = "RYTHME";
            effet = "PROCHAINE_ECHEANCE";
            effectifLe = s.getPeriodeFin();
        } else {
            nature = "IDENTIQUE";
            effet = null;
            effectifLe = null;
        }
        return new Changement(s, depuis, vers, actuel, vise, nature, effet, effectifLe, prorata, depassements);
    }

    public ApercuChangementVue apercu(Changement c) {
        MontantsVue m = c.prorata() == null ? null : montants(Tarification.depuisHt(c.prorata().htCentimes(), tauxTva()));
        String message = switch (c.nature()) {
            case "HAUSSE" -> "APRES_PAIEMENT".equals(c.effet())
                    ? "Passage au forfait " + c.vers().displayLabel() + " d\u00e8s la confirmation du paiement de la diff\u00e9rence, "
                    + "au prorata des " + c.prorata().moisRestants() + " mois restants de l\u2019ann\u00e9e d\u2019abonnement."
                    : "Passage imm\u00e9diat au forfait " + c.vers().displayLabel()
                    + " ; la prochaine mensualit\u00e9 sera celle du nouveau forfait.";
            case "BAISSE" -> c.depassements().isEmpty()
                    ? "Passage au forfait " + c.vers().displayLabel() + " \u00e0 la prochaine \u00e9ch\u00e9ance, le "
                    + format(c.effectifLe()) + ", sans remboursement de la p\u00e9riode en cours."
                    : "Passage au forfait " + c.vers().displayLabel() + " impossible : l\u2019usage du cabinet d\u00e9passe ce forfait.";
            case "RYTHME" -> "Paiement " + (c.rythmeVise() == BillingPeriod.YEARLY ? "annuel" : "mensuel")
                    + " \u00e0 partir de la prochaine \u00e9ch\u00e9ance, le " + format(c.effectifLe()) + ".";
            default -> "Ce forfait et ce rythme sont d\u00e9j\u00e0 les v\u00f4tres.";
        };
        return new ApercuChangementVue(c.depuis().internalCode(), c.vers().internalCode(), c.vers().displayLabel(),
                c.rythmeActuel().suffix(), c.rythmeVise().suffix(), c.nature(), c.effet(), c.effectifLe(),
                c.prorata() == null ? null : c.prorata().moisRestants(), m, c.accepte(), c.depassements(), message);
    }

    /** RG-TAR-05 : ce qui depasse le forfait vise (dossiers du mois, acces employes, stockage). */
    List<String> depassements(UUID workspaceId, Plan vers) {
        UsageCabinet u;
        try {
            u = cabinets.usage(workspaceId);
        } catch (CabinetsPort.CabinetsIndisponiblesException e) {
            throw new BusinessException("USAGE_INDISPONIBLE",
                    "L\u2019usage du cabinet n\u2019a pas pu \u00eatre v\u00e9rifi\u00e9 : la baisse de forfait n\u2019est pas accept\u00e9e sans ce contr\u00f4le. R\u00e9essayez.");
        }
        List<String> out = new ArrayList<>();
        PlanCatalog.Quotas q = vers.quotas();
        if (!q.isUsersUnlimited() && u.users() > q.maxUsers()) {
            out.add(u.users() + " acc\u00e8s employ\u00e9s actifs pour " + q.maxUsers() + " dans le forfait "
                    + vers.displayLabel() + " : " + (u.users() - q.maxUsers()) + " de trop.");
        }
        if (!q.isDossiersUnlimited() && u.dossiers() > q.maxDossiers()) {
            out.add(u.dossiers() + " dossiers ouverts ce mois-ci pour " + q.maxDossiers() + " dans le forfait "
                    + vers.displayLabel() + " : " + (u.dossiers() - q.maxDossiers()) + " de trop.");
        }
        long maxOctets = (long) q.maxStorageGb() * 1024L * 1024L * 1024L;
        if (!q.isStorageUnlimited()) {
            if (!u.storageMesure()) {
                out.add("Stockage non mesur\u00e9 : la baisse ne peut pas \u00eatre v\u00e9rifi\u00e9e.");
            } else if (u.storageBytes() > maxOctets) {
                out.add(String.format(Locale.FRANCE, "%.2f Go de stockage utilis\u00e9s pour %d Go dans le forfait %s.",
                        u.storageBytes() / (1024.0 * 1024 * 1024), q.maxStorageGb(), vers.displayLabel()));
            }
        }
        return out;
    }

    /**
     * Applique un changement sans paiement : hausse en mensuel (immediate), baisse et rythme
     * (programmes a la prochaine echeance). La hausse en annuel passe par un paiement
     * (PaiementService) puis {@link #appliquer}.
     */
    @Transactional
    public void appliquerSansPaiement(Changement c, UUID auteur) {
        if (!c.accepte()) {
            throw new RefusNommeException("CHANGEMENT_REFUSE", apercu(c).message(), c.depassements());
        }
        SubscriptionEntity s = abonnements.verrouillerActif(c.abonnement().getWorkspaceId()).orElseThrow();
        switch (c.nature()) {
            case "HAUSSE" -> {
                if ("APRES_PAIEMENT".equals(c.effet())) {
                    throw new IllegalStateException("Hausse en annuel : paiement de la difference requis");
                }
                s.setPlanCode(c.vers().internalCode());
                if (s.getPlanAVenir() != null && PlanCatalog.exiger(s.getPlanAVenir()).rang() <= c.vers().rang()) {
                    s.setPlanAVenir(null);
                }
            }
            case "BAISSE" -> s.setPlanAVenir(c.vers().internalCode());
            case "RYTHME" -> s.setRythmeAVenir(c.rythmeVise().suffix());
            default -> { return; }
        }
        if (c.rythmeVise() != c.rythmeActuel()) {
            s.setRythmeAVenir(c.rythmeVise().suffix());
        }
        abonnements.save(s);
        ouvrirConditions(s, "HAUSSE".equals(c.nature()) ? aujourdhui() : s.getPeriodeFin(), auteur);
        if ("HAUSSE".equals(c.nature())) {
            propagerApresCommit(s);
        }
        log.info("Changement de forfait {} applique workspace={} {} -> {} rythme {} -> {}", c.nature(),
                s.getWorkspaceId(), c.depuis().internalCode(), c.vers().internalCode(), c.rythmeActuel(), c.rythmeVise());
    }

    /** Annule un changement programme (baisse ou rythme) tant que l'echeance n'est pas passee. */
    @Transactional
    public void annulerChangementAVenir(UUID workspaceId) {
        SubscriptionEntity s = abonnements.verrouillerActif(workspaceId).orElseThrow(() ->
                new BusinessException("ABONNEMENT_ABSENT", "Aucun abonnement actif."));
        s.setPlanAVenir(null);
        s.setRythmeAVenir(null);
        abonnements.save(s);
    }

    // ------------------------------------------------------------------ application d'un paiement

    /**
     * Applique un paiement acquis a l'abonnement (appele dans la transaction de confirmation).
     * SOUSCRIPTION (ou echeance d'un abonnement deja archive) : nouvel abonnement actif, du
     * {@code debut} pour un an (RG-TAR-08 : reprise dans l'etat exact, seul le statut du workspace
     * change). ECHEANCE : periode suivante, forfait et rythme a venir appliques. CHANGEMENT_FORFAIT :
     * nouveau forfait des maintenant (RG-TAR-04).
     *
     * @return l'abonnement actif apres application
     */
    public SubscriptionEntity appliquer(PaymentEntity p, LocalDate debutSouscription) {
        Plan plan = PlanCatalog.exiger(p.getPlanCode());
        BillingPeriod rythme = BillingPeriod.fromString(p.getBillingPeriod());
        Optional<SubscriptionEntity> actif = abonnements.verrouillerActif(p.getWorkspaceId());
        String objet = p.getObjet();
        if ("ECHEANCE".equals(objet) && actif.isEmpty()) {
            objet = "SOUSCRIPTION"; // l'abonnement a ete archive entre-temps : reprise
        }
        switch (objet) {
            case "SOUSCRIPTION" -> {
                if (actif.isPresent()) {
                    throw new BusinessException("ABONNEMENT_DEJA_ACTIF",
                            "Ce cabinet a d\u00e9j\u00e0 un abonnement actif : payez l\u2019\u00e9ch\u00e9ance ou changez de forfait.");
                }
                SubscriptionEntity s = new SubscriptionEntity();
                s.setWorkspaceId(p.getWorkspaceId());
                s.setPlanCode(plan.internalCode());
                s.setStatus(ACTIF);
                s.setRythme(rythme.suffix());
                s.setEngagementDebut(debutSouscription);
                s.setEngagementFin(PeriodeAbonnement.echeance(debutSouscription, 12));
                LocalDate fin = Tarification.finPeriode(debutSouscription, debutSouscription, rythme);
                s.periode(debutSouscription, fin);
                if (plan.quoteOnly()) s.setPrixEcheanceHtCentimes(p.getMontantHtCentimes());
                p.setPeriodeDebut(debutSouscription);
                p.setPeriodeFin(fin);
                return abonnements.save(s);
            }
            case "ECHEANCE" -> {
                SubscriptionEntity s = actif.get();
                LocalDate debut = s.getPeriodeFin();
                boolean rythmeChange = s.getRythme() != null && !rythme.suffix().equals(s.getRythme());
                if (rythmeChange || !debut.isBefore(s.getEngagementFin())) {
                    // Nouvelle annee d'abonnement (renouvellement, RG-CTR-06) ou nouveau rythme
                    // (RG-TAR-06) : l'engagement repart de cette echeance pour un an.
                    s.setEngagementDebut(debut);
                    s.setEngagementFin(PeriodeAbonnement.echeance(debut, 12));
                }
                LocalDate fin = Tarification.finPeriode(s.getEngagementDebut(), debut, rythme);
                s.setPlanCode(plan.internalCode());
                s.setRythme(rythme.suffix());
                s.setPlanAVenir(null);
                s.setRythmeAVenir(null);
                if (plan.quoteOnly() && p.getMontantHtCentimes() != null) s.setPrixEcheanceHtCentimes(p.getMontantHtCentimes());
                s.periode(debut, fin);
                p.setPeriodeDebut(debut);
                p.setPeriodeFin(fin);
                return abonnements.save(s);
            }
            case "CHANGEMENT_FORFAIT" -> {
                SubscriptionEntity s = actif.orElseThrow(() -> new BusinessException("ABONNEMENT_ABSENT",
                        "Abonnement archiv\u00e9 : la diff\u00e9rence de forfait ne peut plus s\u2019appliquer ; souscrivez de nouveau."));
                s.setPlanCode(plan.internalCode());
                if (s.getPlanAVenir() != null && PlanCatalog.exiger(s.getPlanAVenir()).rang() <= plan.rang()) {
                    s.setPlanAVenir(null);
                }
                return abonnements.save(s);
            }
            default -> throw new IllegalStateException("Objet de paiement inconnu : " + p.getObjet());
        }
    }

    /** RG-CTR-03, RG-CTR-05 : conditions particulieres en attente d'acceptation (partie contrat). */
    public void ouvrirConditions(SubscriptionEntity s, LocalDate dateDebut, UUID auteur) {
        ContratAbonnementPort port = contrat.getIfAvailable();
        if (port == null) {
            log.info("Contrat d'abonnement non branche : conditions particulieres non ouvertes workspace={}", s.getWorkspaceId());
            return;
        }
        Echeance e = prochaineEcheance(s);
        Plan plan = e.plan();
        BillingPeriod rythme = e.rythme() != null ? e.rythme() : BillingPeriod.fromString(s.getRythme());
        Long ht = e.htCentimes();
        port.ouvrirConditionsParticulieres(new ConditionsParticulieres(s.getWorkspaceId(), plan.codeContrat(),
                rythme.codeContrat(), ht == null ? null : BigDecimal.valueOf(ht).movePointLeft(2),
                dateDebut, s.getEngagementFin(), auteur));
    }

    // ------------------------------------------------------------------ propagation et archivage

    /** Apres validation de la transaction : forfait et periode propages a auth-service (rejoue si echec). */
    void propagerApresCommit(SubscriptionEntity s) {
        UUID ws = s.getWorkspaceId();
        String plan = s.getPlanCode();
        LocalDate debut = s.getEngagementDebut();
        String rythme = s.getRythme();
        LocalDate fin = s.getPeriodeFin();
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            cabinets.activer(ws, plan, debut, rythme, fin);
                        } catch (RuntimeException e) {
                            log.error("Changement de forfait non propage a auth-service workspace={} : {}", ws, e.getMessage());
                        }
                    }
                });
    }

    /** Resultat de l'archivage planifie. */
    public record BilanArchivage(int examines, int archives, List<String> echecs) {}

    /**
     * RG-TAR-03, RG-TAR-07 : abonnements actifs dont la periode payee est terminee depuis plus du
     * delai de grace : archives (impaye en cours d'annee, fin d'abonnement a l'echeance annuelle).
     * Chaque cabinet dans sa transaction ; auth-service injoignable : l'archivage est annule et
     * rejoue au passage suivant.
     */
    public BilanArchivage archiverEcheancesDepassees(org.springframework.transaction.support.TransactionTemplate tx) {
        LocalDate limite = aujourdhui().minusDays(DELAI_GRACE_JOURS_A_DECIDER);
        List<SubscriptionEntity> echues = abonnements.findAllByStatusAndPeriodeFinLessThanEqual(ACTIF, limite);
        int archives = 0;
        List<String> echecs = new ArrayList<>();
        for (SubscriptionEntity e : echues) {
            try {
                tx.executeWithoutResult(st -> {
                    SubscriptionEntity s = abonnements.verrouillerActif(e.getWorkspaceId()).orElse(null);
                    if (s == null || s.getPeriodeFin().isAfter(limite)) return;
                    boolean finAnnee = !s.getPeriodeFin().isBefore(s.getEngagementFin() == null ? s.getPeriodeFin() : s.getEngagementFin());
                    String motif = finAnnee ? "FIN_ABONNEMENT" : "IMPAYE";
                    s.setStatus("IMPAYE".equals(motif) ? "unpaid" : "cancelled");
                    s.setArchiveMotif(motif);
                    s.setCancelledAt(Instant.now(horloge));
                    abonnements.save(s);
                    cabinets.archiver(s.getWorkspaceId(), motif);
                });
                archives++;
            } catch (RuntimeException ex) {
                echecs.add(e.getWorkspaceId() + " : " + ex.getMessage());
                log.error("Archivage non fait workspace={} : {}", e.getWorkspaceId(), ex.getMessage());
            }
        }
        return new BilanArchivage(echues.size(), archives, echecs);
    }

    static String format(LocalDate d) {
        return d == null ? "" : String.format(Locale.FRANCE, "%02d/%02d/%d", d.getDayOfMonth(), d.getMonthValue(), d.getYear());
    }
}
