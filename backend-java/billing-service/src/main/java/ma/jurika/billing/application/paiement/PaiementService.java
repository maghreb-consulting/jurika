package ma.jurika.billing.application.paiement;

import ma.jurika.billing.api.dto.BillingDtos.ChangementForfaitRequest;
import ma.jurika.billing.api.dto.BillingDtos.ChangementForfaitVue;
import ma.jurika.billing.api.dto.BillingDtos.CmiFormulaireVue;
import ma.jurika.billing.api.dto.BillingDtos.EnregistrementPaiementRequest;
import ma.jurika.billing.api.dto.BillingDtos.InstructionVue;
import ma.jurika.billing.api.dto.BillingDtos.PaiementVue;
import ma.jurika.billing.api.dto.BillingDtos.PreparationPaiementRequest;
import ma.jurika.billing.api.dto.BillingDtos.PreparationPaiementVue;
import ma.jurika.billing.api.dto.BillingDtos.ValidationPaiementRequest;
import ma.jurika.billing.application.RefusNommeException;
import ma.jurika.billing.application.abonnement.AbonnementService;
import ma.jurika.billing.application.abonnement.AbonnementService.Changement;
import ma.jurika.billing.application.abonnement.AbonnementService.Echeance;
import ma.jurika.billing.application.abonnement.Tarification;
import ma.jurika.billing.application.cabinet.CabinetsPort;
import ma.jurika.billing.application.facture.FactureService;
import ma.jurika.billing.infrastructure.cmi.CmiPasserelle;
import ma.jurika.billing.infrastructure.cmi.CmiProperties;
import ma.jurika.billing.infrastructure.config.BillingProperties;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.EditeurParametresJpaRepository;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.FactureJpaRepository;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.PaymentJpaRepository;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.SubscriptionJpaRepository;
import ma.jurika.billing.infrastructure.persistence.EditeurParametresEntity;
import ma.jurika.billing.infrastructure.persistence.FactureEntity;
import ma.jurika.billing.infrastructure.persistence.PaymentEntity;
import ma.jurika.billing.infrastructure.persistence.SubscriptionEntity;
import ma.jurika.common.billing.PlanCatalog;
import ma.jurika.common.billing.PlanCatalog.BillingPeriod;
import ma.jurika.common.billing.PlanCatalog.Plan;
import ma.jurika.common.exception.BusinessException;
import ma.jurika.common.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Lot L7 (E5), CDC 4.6 : paiements.
 *
 * <ul>
 *   <li>RG-PAY-01 : carte bancaire ({@code CARD}, page hebergee du CMI), virement
 *       ({@code BANK_TRANSFER}), cheque ({@code CHEQUE}), especes ({@code CASH}).</li>
 *   <li>RG-PAY-02 : virement, cheque et especes sont ENREGISTRES PAR LE SUPER-ADMIN (reception,
 *       reference, date), qui active ou prolonge le workspace. Le cabinet peut declarer son
 *       intention (paiement en attente, instructions), jamais confirmer.</li>
 *   <li>RG-PAY-05 : la carte n'est acquise qu'au retour serveur signe du CMI
 *       ({@link CmiRetourService}) ; le retour du navigateur ne confirme rien. Une meme confirmation
 *       recue deux fois : un seul paiement, une seule facture (verrou de ligne, statut, unicite de la
 *       facture par paiement).</li>
 *   <li>RG-PAY-03 : facture a chaque paiement acquis ({@link FactureService}).</li>
 * </ul>
 */
@Service
public class PaiementService {

    private static final Logger log = LoggerFactory.getLogger(PaiementService.class);

    public static final Set<String> MODES = Set.of("CARD", "BANK_TRANSFER", "CHEQUE", "CASH");
    public static final Set<String> MODES_HORS_LIGNE = Set.of("BANK_TRANSFER", "CHEQUE", "CASH");

    private final PaymentJpaRepository paiements;
    private final SubscriptionJpaRepository abonnements;
    private final FactureJpaRepository factures;
    private final EditeurParametresJpaRepository editeur;
    private final AbonnementService abonnement;
    private final FactureService facturation;
    private final PropagationActivation propagation;
    private final CabinetsPort cabinets;
    private final CmiPasserelle passerelle;
    private final CmiProperties cmi;
    private final BillingProperties props;
    private final org.springframework.beans.factory.ObjectProvider<ma.jurika.common.notification.NotificationPublisher> notifications;

    public PaiementService(PaymentJpaRepository paiements, SubscriptionJpaRepository abonnements,
                           FactureJpaRepository factures, EditeurParametresJpaRepository editeur,
                           AbonnementService abonnement, FactureService facturation,
                           PropagationActivation propagation, CabinetsPort cabinets, CmiPasserelle passerelle,
                           CmiProperties cmi, BillingProperties props,
                           org.springframework.beans.factory.ObjectProvider<ma.jurika.common.notification.NotificationPublisher> notifications) {
        this.paiements = paiements;
        this.abonnements = abonnements;
        this.factures = factures;
        this.editeur = editeur;
        this.abonnement = abonnement;
        this.facturation = facturation;
        this.propagation = propagation;
        this.cabinets = cabinets;
        this.passerelle = passerelle;
        this.cmi = cmi;
        this.props = props;
        this.notifications = notifications;
    }

    // ------------------------------------------------------------------ cabinet

    /** Prepare un paiement du cabinet : souscription (premiere ou reprise) ou echeance. */
    @Transactional
    public PreparationPaiementVue preparer(UUID workspaceId, PreparationPaiementRequest r) {
        if (r == null) throw new IllegalArgumentException("Demande de paiement absente.");
        String mode = mode(r.mode());
        String objet = r.objet() == null ? "" : r.objet().trim().toUpperCase(Locale.ROOT);
        PaymentEntity p;
        switch (objet) {
            case "SOUSCRIPTION" -> {
                if (abonnement.actif(workspaceId).isPresent()) {
                    throw new BusinessException("ABONNEMENT_DEJA_ACTIF",
                            "Votre abonnement est actif : r\u00e9glez l\u2019\u00e9ch\u00e9ance ou changez de forfait.");
                }
                Plan plan = PlanCatalog.findByCode(r.forfait())
                        .orElseThrow(() -> new IllegalArgumentException("Forfait inconnu : " + r.forfait()));
                if (plan.quoteOnly()) {
                    throw new BusinessException("SUR_DEVIS",
                            "Le forfait Sur Devis se souscrit aupr\u00e8s de JURIKA : utilisez la demande de devis.");
                }
                BillingPeriod rythme = BillingPeriod.fromString(r.rythme());
                long ht = Tarification.prixEcheanceHt(plan, rythme, null);
                p = nouveau(workspaceId, "SOUSCRIPTION", plan, null, rythme, ht, mode);
                p.setInscriptionEnLigne(Boolean.TRUE.equals(r.inscription()));
            }
            case "ECHEANCE" -> {
                SubscriptionEntity s = abonnement.actif(workspaceId).orElseThrow(() -> new BusinessException(
                        "ABONNEMENT_ABSENT", "Aucun abonnement actif : souscrivez un forfait."));
                Echeance e = abonnement.prochaineEcheance(s);
                if (e.htCentimes() == null) {
                    throw new RefusNommeException("ECHEANCE_INCALCULABLE", "Le montant de l\u2019\u00e9ch\u00e9ance ne peut pas \u00eatre calcul\u00e9.",
                            List.of(e.motif()));
                }
                p = nouveau(workspaceId, "ECHEANCE", e.plan(), null, e.rythme(), e.htCentimes(), mode);
                p.setPeriodeDebut(e.debut());
                p.setPeriodeFin(e.fin());
            }
            default -> throw new IllegalArgumentException("Objet du paiement attendu : SOUSCRIPTION ou ECHEANCE.");
        }
        paiements.save(p);
        if (MODES_HORS_LIGNE.contains(mode)) {
            signalerAuSuperAdmin(p);
        }
        return vuePreparation(p, r.contactEmail());
    }

    /**
     * RG-PAY-02 : un paiement hors ligne declare par le cabinet attend l'enregistrement du
     * super-admin, qui en est averti dans son workspace (canal du super-admin) apres validation de
     * la transaction. Echec d'envoi : journalise, le paiement reste visible dans l'ecran des
     * paiements a enregistrer.
     */
    private void signalerAuSuperAdmin(PaymentEntity p) {
        ma.jurika.common.notification.NotificationPublisher n = notifications.getIfAvailable();
        if (n == null) return;
        Long id = p.getId();
        UUID ws = p.getWorkspaceId();
        String montant = ma.jurika.billing.application.facture.FacturePdf.dirhams(p.getAmountMadCents());
        String mode = FactureService.libelleMode(p.getMethod()).toLowerCase(Locale.FRANCE);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    String cabinet;
                    try {
                        cabinet = cabinets.identite(ws).denomination();
                    } catch (CabinetsPort.CabinetsIndisponiblesException e) {
                        cabinet = null;
                    }
                    String qui = cabinet == null || cabinet.isBlank() ? "Un cabinet" : "Le cabinet " + cabinet;
                    for (CabinetsPort.SuperAdmin sa : cabinets.superAdmins()) {
                        n.notifyUser(sa.id(), sa.workspaceId(), "PAIEMENT_A_ENREGISTRER", "Paiement \u00e0 enregistrer",
                                qui + " d\u00e9clare un paiement par " + mode + " de " + montant
                                        + " TTC : enregistrez-le \u00e0 sa r\u00e9ception.",
                                "/admin/paiements", java.util.Map.of("paiementId", id, "workspaceId", ws.toString()));
                    }
                } catch (RuntimeException e) {
                    log.warn("Super-admin non averti du paiement {} a enregistrer : {}", id, e.getMessage());
                }
            }
        });
    }

    /** Changement de forfait ou de rythme demande par le cabinet (RG-TAR-04 a RG-TAR-06). */
    @Transactional
    public ChangementForfaitVue changer(UUID workspaceId, UUID auteur, ChangementForfaitRequest r) {
        if (r == null) throw new IllegalArgumentException("Demande de changement absente.");
        Changement c = abonnement.calculerChangement(workspaceId, r.forfait(), r.rythme());
        var apercu = abonnement.apercu(c);
        if ("IDENTIQUE".equals(c.nature())) {
            return new ChangementForfaitVue("IDENTIQUE", apercu.message(), apercu, null);
        }
        if (!c.accepte()) {
            throw new RefusNommeException("CHANGEMENT_REFUSE", apercu.message(), c.depassements());
        }
        if ("APRES_PAIEMENT".equals(c.effet()) && c.prorata().htCentimes() > 0) {
            String mode = mode(r.mode());
            PaymentEntity p = nouveau(workspaceId, "CHANGEMENT_FORFAIT", c.vers(), c.depuis(), c.rythmeActuel(),
                    c.prorata().htCentimes(), mode);
            p.setPeriodeDebut(abonnement.aujourdhui());
            p.setPeriodeFin(c.abonnement().getEngagementFin());
            paiements.save(p);
            if (c.rythmeVise() != c.rythmeActuel()) {
                SubscriptionEntity s = abonnements.verrouillerActif(workspaceId).orElseThrow();
                s.setRythmeAVenir(c.rythmeVise().suffix());
                abonnements.save(s);
            }
            return new ChangementForfaitVue("PAIEMENT_REQUIS", apercu.message(), apercu, vuePreparation(p, null));
        }
        abonnement.appliquerSansPaiement(c, auteur);
        String resultat = "HAUSSE".equals(c.nature()) ? "APPLIQUE" : "PROGRAMME";
        return new ChangementForfaitVue(resultat, apercu.message(), apercu, null);
    }

    @Transactional(readOnly = true)
    public List<PaiementVue> paiementsDuCabinet(UUID workspaceId) {
        return paiements.findAllByWorkspaceIdOrderByCreatedAtDesc(workspaceId).stream().map(this::vue).toList();
    }

    @Transactional(readOnly = true)
    public PaiementVue paiementDuCabinet(UUID workspaceId, Long id) {
        return vue(paiements.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new NotFoundException("Paiement introuvable.")));
    }

    // ------------------------------------------------------------------ super-admin (RG-PAY-02)

    /** Paiement recu (virement, cheque, especes) enregistre directement par le super-admin. */
    @Transactional
    public PaiementVue enregistrer(EnregistrementPaiementRequest r, UUID admin) {
        if (r == null || r.workspaceId() == null) throw new IllegalArgumentException("Cabinet absent.");
        String mode = mode(r.mode());
        if (!MODES_HORS_LIGNE.contains(mode)) {
            throw new IllegalArgumentException("Le super-admin enregistre un virement, un ch\u00e8que ou des esp\u00e8ces ; "
                    + "la carte est confirm\u00e9e par le CMI.");
        }
        if (r.recuLe() == null) throw new IllegalArgumentException("Date de r\u00e9ception du paiement absente.");
        if (r.reference() == null || r.reference().isBlank()) {
            throw new IllegalArgumentException("R\u00e9f\u00e9rence du paiement absente (num\u00e9ro du virement, du ch\u00e8que ou du re\u00e7u).");
        }
        String objet = r.objet() == null ? "" : r.objet().trim().toUpperCase(Locale.ROOT);
        Optional<SubscriptionEntity> actif = abonnement.actif(r.workspaceId());
        PaymentEntity p;
        switch (objet) {
            case "SOUSCRIPTION" -> {
                if (actif.isPresent()) {
                    throw new BusinessException("ABONNEMENT_DEJA_ACTIF",
                            "Ce cabinet a d\u00e9j\u00e0 un abonnement actif : enregistrez une \u00e9ch\u00e9ance.");
                }
                Plan plan = PlanCatalog.findByCode(r.forfait())
                        .orElseThrow(() -> new IllegalArgumentException("Forfait inconnu : " + r.forfait()));
                BillingPeriod rythme = BillingPeriod.fromString(r.rythme());
                long ht = plan.quoteOnly() ? exigerMontantDevis(r.montantHtCentimes()) : Tarification.prixEcheanceHt(plan, rythme, null);
                p = nouveau(r.workspaceId(), "SOUSCRIPTION", plan, null, rythme, ht, mode);
            }
            case "ECHEANCE" -> {
                SubscriptionEntity s = actif.orElseThrow(() -> new BusinessException("ABONNEMENT_ABSENT",
                        "Aucun abonnement actif : enregistrez une souscription (reprise)."));
                Echeance e = abonnement.prochaineEcheance(s);
                long ht = e.plan().quoteOnly() && r.montantHtCentimes() != null ? r.montantHtCentimes() : exigerHt(e);
                p = nouveau(r.workspaceId(), "ECHEANCE", e.plan(), null, e.rythme(), ht, mode);
                p.setPeriodeDebut(e.debut());
                p.setPeriodeFin(e.fin());
            }
            default -> throw new IllegalArgumentException("Objet attendu : SOUSCRIPTION ou ECHEANCE.");
        }
        p.setBankReference(r.reference().trim());
        p.setRecuLe(r.recuLe());
        p.setEnregistrePar(admin);
        p.setNotes(r.notes());
        paiements.save(p);
        return confirmer(p.getId(), "SUPER_ADMIN", r.recuLe(), null, null);
    }

    /** Paiement declare par le cabinet : reception constatee par le super-admin. */
    @Transactional
    public PaiementVue valider(Long id, ValidationPaiementRequest r, UUID admin) {
        PaymentEntity p = paiements.verrouiller(id).orElseThrow(() -> new NotFoundException("Paiement introuvable."));
        if ("COMPLETED".equals(p.getStatus())) return vue(p);
        if (!MODES_HORS_LIGNE.contains(p.getMethod())) {
            throw new BusinessException("PAIEMENT_CARTE",
                    "Un paiement par carte n\u2019est acquis qu\u2019au retour sign\u00e9 du CMI : il ne s\u2019enregistre pas \u00e0 la main.");
        }
        if (r == null || r.recuLe() == null) throw new IllegalArgumentException("Date de r\u00e9ception du paiement absente.");
        if (r.reference() == null || r.reference().isBlank()) {
            throw new IllegalArgumentException("R\u00e9f\u00e9rence du paiement absente (num\u00e9ro du virement, du ch\u00e8que ou du re\u00e7u).");
        }
        p.setRecuLe(r.recuLe());
        p.setBankReference(r.reference().trim());
        p.setEnregistrePar(admin);
        if (r.notes() != null && !r.notes().isBlank()) p.setNotes(r.notes());
        paiements.save(p);
        return confirmer(id, "SUPER_ADMIN", r.recuLe(), null, null);
    }

    @Transactional
    public PaiementVue rejeter(Long id, String motif, UUID admin) {
        PaymentEntity p = paiements.verrouiller(id).orElseThrow(() -> new NotFoundException("Paiement introuvable."));
        if (!"PENDING".equals(p.getStatus())) {
            throw new BusinessException("PAIEMENT_NON_EN_ATTENTE", "Seul un paiement en attente peut \u00eatre rejet\u00e9.");
        }
        if (motif == null || motif.isBlank()) throw new IllegalArgumentException("Motif du rejet absent.");
        p.setStatus("FAILED");
        p.setEnregistrePar(admin);
        p.setNotes("Rejet\u00e9 : " + motif.trim());
        return vue(paiements.save(p));
    }

    @Transactional(readOnly = true)
    public List<PaiementVue> paiementsAEnregistrer() {
        return paiements.findAllByStatusOrderByCreatedAtAsc("PENDING").stream()
                .filter(p -> MODES_HORS_LIGNE.contains(p.getMethod())).map(this::vue).toList();
    }

    @Transactional(readOnly = true)
    public List<PaiementVue> paiementsRecents() {
        return paiements.findTop200ByOrderByCreatedAtDesc().stream().map(this::vue).toList();
    }

    // ------------------------------------------------------------------ confirmation (une seule voie)

    /**
     * Confirme un paiement : statut acquis, abonnement active ou prolonge, facture emise (ou mentions
     * manquantes nommees), conditions particulieres ouvertes. Idempotent sous verrou de ligne : une
     * deuxieme confirmation rend l'etat sans rien refaire. L'activation est propagee a auth-service
     * APRES validation de la transaction (rejouee si elle echoue).
     */
    @Transactional
    public PaiementVue confirmer(Long id, String source, LocalDate debutSouscription, String codeRetour, String transaction) {
        PaymentEntity p = paiements.verrouiller(id).orElseThrow(() -> new NotFoundException("Paiement introuvable."));
        if ("COMPLETED".equals(p.getStatus())) {
            return vue(p);
        }
        if (!"PENDING".equals(p.getStatus())) {
            throw new BusinessException("PAIEMENT_NON_EN_ATTENTE",
                    "Ce paiement n\u2019est plus en attente (statut " + p.getStatus() + ").");
        }
        LocalDate jour = abonnement.aujourdhui();
        p.setStatus("COMPLETED");
        p.setCompletedAt(Instant.now());
        p.setValidatedBy(source);
        if (codeRetour != null) p.setCmiCodeRetour(codeRetour);
        if (transaction != null) p.setCmiTransaction(transaction);
        SubscriptionEntity s = abonnement.appliquer(p, debutSouscription == null ? jour : debutSouscription);
        paiements.save(p);
        FactureService.Emission em = facturation.emettre(p, jour);
        p.setFactureRefus(em.manquants().isEmpty() ? null : String.join(" ; ", em.manquants()));
        paiements.save(p);
        if (!"ECHEANCE".equals(p.getObjet()) || s.getEngagementDebut().equals(p.getPeriodeDebut())) {
            abonnement.ouvrirConditions(s, p.getPeriodeDebut() != null ? p.getPeriodeDebut() : jour, p.getEnregistrePar());
        }
        Long pid = p.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                propagation.propager(pid);
            }
        });
        log.info("Paiement {} acquis workspace={} objet={} mode={} source={}", pid, p.getWorkspaceId(), p.getObjet(),
                p.getMethod(), source);
        return vue(p);
    }

    // ------------------------------------------------------------------ outils

    private PaymentEntity nouveau(UUID ws, String objet, Plan plan, Plan precedent, BillingPeriod rythme, long ht, String mode) {
        Tarification.Montants m = Tarification.depuisHt(ht, abonnement.tauxTva());
        PaymentEntity p = new PaymentEntity();
        p.setWorkspaceId(ws);
        p.setObjet(objet);
        p.setPlanCode(plan.internalCode());
        p.setPlanPrecedent(precedent == null ? null : precedent.internalCode());
        p.setBillingPeriod(rythme.suffix());
        p.setMontantHtCentimes(m.htCentimes());
        p.setTauxTva(m.tauxTva());
        p.setMontantTvaCentimes(m.tvaCentimes());
        p.setAmountMadCents(m.ttcCentimes());
        p.setMethod(mode);
        p.setStatus("PENDING");
        return p;
    }

    private static String mode(String m) {
        String mode = m == null ? "" : m.trim().toUpperCase(Locale.ROOT);
        if (!MODES.contains(mode)) {
            throw new IllegalArgumentException("Mode de paiement attendu : carte (CARD), virement (BANK_TRANSFER), "
                    + "ch\u00e8que (CHEQUE) ou esp\u00e8ces (CASH).");
        }
        return mode;
    }

    private static long exigerMontantDevis(Long montant) {
        if (montant == null || montant <= 0) {
            throw new IllegalArgumentException("Forfait Sur Devis : montant HT de l\u2019\u00e9ch\u00e9ance convenu par devis absent.");
        }
        return montant;
    }

    private static long exigerHt(Echeance e) {
        if (e.htCentimes() == null) {
            throw new RefusNommeException("ECHEANCE_INCALCULABLE", "Le montant de l\u2019\u00e9ch\u00e9ance ne peut pas \u00eatre calcul\u00e9.",
                    List.of(e.motif()));
        }
        return e.htCentimes();
    }

    private PreparationPaiementVue vuePreparation(PaymentEntity p, String contactEmail) {
        if ("CARD".equals(p.getMethod())) {
            if (!cmi.configure()) {
                throw new RefusNommeException("CMI_NON_CONFIGURE",
                        "Le paiement par carte n\u2019est pas encore disponible : la liaison avec le CMI n\u2019est pas configur\u00e9e. "
                                + "Choisissez le virement, le ch\u00e8que ou les esp\u00e8ces.", cmi.manquants());
            }
            String oid = "JUR" + p.getId() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
            p.setCmiOid(oid);
            paiements.save(p);
            String nom = "";
            try {
                CabinetsPort.IdentiteCabinet id = cabinets.identite(p.getWorkspaceId());
                nom = id.denomination() == null ? "" : id.denomination();
                if ((contactEmail == null || contactEmail.isBlank()) && id.email() != null) contactEmail = id.email();
            } catch (CabinetsPort.CabinetsIndisponiblesException e) {
                log.warn("Identite du cabinet indisponible pour le formulaire CMI workspace={} : {}", p.getWorkspaceId(), e.getMessage());
            }
            CmiFormulaireVue form = passerelle.formulaire(oid, p.getAmountMadCents(), nom, contactEmail,
                    props.getFrontUrl() + "/app/billing");
            return new PreparationPaiementVue(vue(p), form, List.of(),
                    "Vous allez \u00eatre redirig\u00e9 vers la page de paiement s\u00e9curis\u00e9e du CMI. Le paiement est acquis "
                            + "\u00e0 la confirmation du CMI, pas au retour sur JURIKA.");
        }
        return new PreparationPaiementVue(vue(p), null, instructions(p),
                switch (p.getMethod()) {
                    case "BANK_TRANSFER" -> "Votre paiement par virement est enregistr\u00e9 en attente. Il sera pris en compte "
                            + "quand JURIKA aura constat\u00e9 la r\u00e9ception du virement.";
                    case "CHEQUE" -> "Votre paiement par ch\u00e8que est enregistr\u00e9 en attente. Il sera pris en compte "
                            + "quand JURIKA aura re\u00e7u le ch\u00e8que.";
                    default -> "Votre paiement en esp\u00e8ces est enregistr\u00e9 en attente. Il sera pris en compte "
                            + "au r\u00e8glement, contre re\u00e7u.";
                });
    }

    /** Instructions de reglement hors ligne : beneficiaire et adresse lus dans les parametres de l'editeur. */
    List<InstructionVue> instructions(PaymentEntity p) {
        EditeurParametresEntity e = editeur.unique().orElse(null);
        String beneficiaire = e == null || e.getRaisonSociale() == null || e.getRaisonSociale().isBlank()
                ? "Non encore renseign\u00e9 par JURIKA (identit\u00e9 de l\u2019\u00e9diteur \u00e0 compl\u00e9ter)" : e.getRaisonSociale();
        String adresse = e == null || e.getSiegeSocial() == null || e.getSiegeSocial().isBlank()
                ? "Non encore renseign\u00e9e par JURIKA" : e.getSiegeSocial() + (e.getVille() == null ? "" : ", " + e.getVille());
        String reference = "JURIKA-" + p.getWorkspaceId().toString().substring(0, 8).toUpperCase(Locale.ROOT) + "-" + p.getId();
        List<InstructionVue> out = new ArrayList<>();
        out.add(new InstructionVue("Montant \u00e0 r\u00e9gler (TTC)", ma.jurika.billing.application.facture.FacturePdf.dirhams(p.getAmountMadCents())));
        out.add(new InstructionVue("R\u00e9f\u00e9rence \u00e0 rappeler", reference));
        switch (p.getMethod()) {
            case "BANK_TRANSFER" -> {
                out.add(new InstructionVue("B\u00e9n\u00e9ficiaire", beneficiaire));
                out.add(new InstructionVue("Banque", vide(props.getVirementBanque())
                        ? "\u00c0 communiquer par JURIKA (coordonn\u00e9es bancaires non encore renseign\u00e9es)" : props.getVirementBanque()));
                out.add(new InstructionVue("RIB", vide(props.getVirementRib())
                        ? "\u00c0 communiquer par JURIKA (coordonn\u00e9es bancaires non encore renseign\u00e9es)" : props.getVirementRib()));
            }
            case "CHEQUE" -> {
                out.add(new InstructionVue("\u00c0 l\u2019ordre de", beneficiaire));
                out.add(new InstructionVue("Adresse d\u2019envoi", adresse));
            }
            case "CASH" -> out.add(new InstructionVue("Lieu du r\u00e8glement", adresse));
            default -> { }
        }
        return out;
    }

    public PaiementVue vue(PaymentEntity p) {
        Optional<FactureEntity> f = p.getId() == null ? Optional.empty() : factures.findByPaymentId(p.getId());
        Plan plan = PlanCatalog.findByCode(p.getPlanCode()).orElse(null);
        return new PaiementVue(p.getId(), p.getWorkspaceId(), p.getObjet(), p.getPlanCode(),
                plan == null ? p.getPlanCode() : plan.displayLabel(), p.getBillingPeriod(), p.getMethod(), p.getStatus(),
                p.getMontantHtCentimes() == null ? null
                        : new ma.jurika.billing.api.dto.BillingDtos.MontantsVue(p.getMontantHtCentimes(), p.getTauxTva(),
                        p.getMontantTvaCentimes(), p.getAmountMadCents()),
                p.getPeriodeDebut(), p.getPeriodeFin(), p.getBankReference(), p.getRecuLe(), p.getCreatedAt(),
                p.getCompletedAt(), f.map(FactureEntity::getId).orElse(null), f.map(FactureEntity::getNumero).orElse(null),
                p.getFactureRefus(), p.isActivationPropagee(), p.getNotes());
    }

    private static boolean vide(String s) {
        return s == null || s.isBlank();
    }
}
