package ma.jurika.billing.application;

import ma.jurika.billing.api.dto.BillingDtos.AbonnementAdminVue;
import ma.jurika.billing.api.dto.BillingDtos.BilanTacheVue;
import ma.jurika.billing.api.dto.BillingDtos.SurDevisVue;
import ma.jurika.billing.application.abonnement.AbonnementService;
import ma.jurika.billing.application.abonnement.ImpayeJournal;
import ma.jurika.billing.application.abonnement.ImpayeService;
import ma.jurika.billing.application.cabinet.CabinetsPort;
import ma.jurika.billing.application.facture.FactureService;
import ma.jurika.billing.application.paiement.PropagationActivation;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.PaymentJpaRepository;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.SubscriptionJpaRepository;
import ma.jurika.billing.infrastructure.persistence.PaymentEntity;
import ma.jurika.billing.infrastructure.persistence.SubscriptionEntity;
import ma.jurika.common.billing.PlanCatalog;
import ma.jurika.common.billing.PlanCatalog.BillingPeriod;
import ma.jurika.common.billing.PlanCatalog.Plan;
import ma.jurika.common.billing.SurDevisCalcul;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lot L7 (E5) : vues et actions du super-admin sur la facturation (cabinets et forfaits, Sur
 * Devis RG-PLN-05, factures en attente d'une mention, activations a rejouer, archivage des
 * echeances depassees).
 */
@Service
public class AdministrationFacturationService {

    private final SubscriptionJpaRepository abonnements;
    private final PaymentJpaRepository paiements;
    private final AbonnementService abonnement;
    private final FactureService factures;
    private final PropagationActivation propagation;
    private final CabinetsPort cabinets;
    private final ImpayeService impayes;
    private final ImpayeJournal journal;
    private final TransactionTemplate tx;

    public AdministrationFacturationService(SubscriptionJpaRepository abonnements, PaymentJpaRepository paiements,
                                            AbonnementService abonnement, FactureService factures,
                                            PropagationActivation propagation, CabinetsPort cabinets,
                                            ImpayeService impayes, ImpayeJournal journal,
                                            PlatformTransactionManager tm) {
        this.impayes = impayes;
        this.journal = journal;
        this.abonnements = abonnements;
        this.paiements = paiements;
        this.abonnement = abonnement;
        this.factures = factures;
        this.propagation = propagation;
        this.cabinets = cabinets;
        this.tx = new TransactionTemplate(tm);
    }

    /** Dernier abonnement de chaque cabinet (actif s'il existe), avec l'estimation Sur Devis. */
    @Transactional(readOnly = true)
    public List<AbonnementAdminVue> abonnements() {
        Map<UUID, SubscriptionEntity> parCabinet = new LinkedHashMap<>();
        for (SubscriptionEntity s : abonnements.findAllByOrderByCreatedAtDesc()) {
            SubscriptionEntity deja = parCabinet.get(s.getWorkspaceId());
            if (deja == null || (!AbonnementService.ACTIF.equals(deja.getStatus()) && AbonnementService.ACTIF.equals(s.getStatus()))) {
                parCabinet.put(s.getWorkspaceId(), s);
            }
        }
        List<AbonnementAdminVue> out = new ArrayList<>();
        for (SubscriptionEntity s : parCabinet.values()) {
            Plan plan = PlanCatalog.findByCode(s.getPlanCode()).orElse(null);
            boolean actif = AbonnementService.ACTIF.equals(s.getStatus());
            out.add(new AbonnementAdminVue(s.getWorkspaceId(), s.getId(), actif ? AbonnementService.statutActif(s) : "ARCHIVE",
                    s.getPlanCode(), plan == null ? s.getPlanCode() : plan.displayLabel(), s.getRythme(), s.getEngagementDebut(),
                    s.getEngagementFin(), s.getPeriodeDebut(), s.getPeriodeFin(),
                    actif ? echeance(s) : null, actif ? changement(s) : null, s.getArchiveMotif(),
                    actif && plan != null && plan.quoteOnly() ? surDevis(s.getWorkspaceId()) : null,
                    actif ? AbonnementService.impayeVue(s, abonnement.aujourdhui()) : null,
                    journalImpaye(s.getWorkspaceId())));
        }
        return out;
    }

    private ma.jurika.billing.api.dto.BillingDtos.EcheanceVue echeance(SubscriptionEntity s) {
        AbonnementService.Echeance e = abonnement.prochaineEcheance(s);
        var m = e.htCentimes() == null ? null
                : new ma.jurika.billing.api.dto.BillingDtos.MontantsVue(e.htCentimes(), abonnement.tauxTva(),
                ma.jurika.billing.application.abonnement.Tarification.depuisHt(e.htCentimes(), abonnement.tauxTva()).tvaCentimes(),
                ma.jurika.billing.application.abonnement.Tarification.depuisHt(e.htCentimes(), abonnement.tauxTva()).ttcCentimes());
        return new ma.jurika.billing.api.dto.BillingDtos.EcheanceVue(e.debut(), e.fin(), e.plan().internalCode(),
                e.plan().displayLabel(), e.rythme() == null ? null : e.rythme().suffix(), m, e.motif());
    }

    private static ma.jurika.billing.api.dto.BillingDtos.ChangementAVenirVue changement(SubscriptionEntity s) {
        if (s.getPlanAVenir() == null && s.getRythmeAVenir() == null) return null;
        Plan p = PlanCatalog.exiger(s.getPlanAVenir() != null ? s.getPlanAVenir() : s.getPlanCode());
        return new ma.jurika.billing.api.dto.BillingDtos.ChangementAVenirVue(p.internalCode(), p.displayLabel(),
                s.getRythmeAVenir() != null ? s.getRythmeAVenir() : s.getRythme(), s.getPeriodeFin());
    }

    /** RG-PLN-05 : montant Sur Devis calcule sur l'usage reel du mois d'abonnement. */
    public SurDevisVue surDevis(UUID workspaceId) {
        var repMensuel = SurDevisCalcul.repereMaxParDossier(BillingPeriod.MONTHLY).orElse(null);
        var repAnnuel = SurDevisCalcul.repereMaxParDossier(BillingPeriod.YEARLY).orElse(null);
        try {
            CabinetsPort.UsageCabinet u = cabinets.usage(workspaceId);
            SurDevisCalcul.Resultat r = SurDevisCalcul.calculer(u.dossiers());
            return new SurDevisVue(u.dossiers(), u.periodeDebut(), u.periodeFin(), r.prixParDossierHt(), r.montantHt(),
                    r.motifAbsence(), repMensuel, repAnnuel);
        } catch (CabinetsPort.CabinetsIndisponiblesException e) {
            return new SurDevisVue(0, null, null, SurDevisCalcul.PRIX_PAR_DOSSIER_HT_A_DECIDER, null,
                    "Usage du cabinet indisponible : " + e.getMessage(), repMensuel, repAnnuel);
        }
    }

    /** Factures non emises faute d'une mention : nouvelle tentative, une transaction par paiement. */
    public BilanTacheVue emettreFacturesEnAttente() {
        List<PaymentEntity> attente = paiements.findAllByStatusAndFactureRefusIsNotNull("COMPLETED");
        int ok = 0;
        List<String> details = new ArrayList<>();
        for (PaymentEntity a : attente) {
            Boolean emise = tx.execute(st -> {
                PaymentEntity p = paiements.verrouiller(a.getId()).orElseThrow();
                FactureService.Emission em = factures.emettre(p, abonnement.aujourdhui());
                p.setFactureRefus(em.manquants().isEmpty() ? null : String.join(" ; ", em.manquants()));
                paiements.save(p);
                return em.facture().isPresent();
            });
            if (Boolean.TRUE.equals(emise)) ok++;
            else details.add("Paiement " + a.getId() + " : " + paiements.findById(a.getId()).map(PaymentEntity::getFactureRefus).orElse(""));
        }
        return new BilanTacheVue(attente.size(), ok, attente.size() - ok, details);
    }

    /** Activations non propagees a auth-service : nouvelle tentative. */
    public BilanTacheVue propagerActivations() {
        List<PaymentEntity> attente = paiements.findAllByStatusAndActivationPropageeFalse("COMPLETED");
        int ok = 0;
        List<String> details = new ArrayList<>();
        for (PaymentEntity p : attente) {
            if (propagation.propager(p.getId())) ok++;
            else details.add("Paiement " + p.getId() + " (cabinet " + p.getWorkspaceId() + ")");
        }
        return new BilanTacheVue(attente.size(), ok, attente.size() - ok, details);
    }

    /** Lot L7 (E8) : derniers faits du journal des impayes d'un cabinet (lecture transverse). */
    private List<ma.jurika.billing.api.dto.BillingDtos.ImpayeEvenementVue> journalImpaye(UUID workspaceId) {
        List<ImpayeJournal.Evenement> l = tx.execute(st -> {
            journal.porteeGlobale();
            return journal.duCabinet(workspaceId, 10);
        });
        return l == null ? List.of() : l.stream().map(e -> new ma.jurika.billing.api.dto.BillingDtos.ImpayeEvenementVue(
                e.id(), e.echeance(), e.evenement(), e.destinataire(), e.detail(), e.creeLe())).toList();
    }

    /**
     * RG-TAR-03 et article 4.4 du contrat (lot L7, E8) : echeances depassees : rappel, suspension a
     * J+15 apres rappel, archivage a l'arret du forfait ({@link ImpayeService}).
     */
    public BilanTacheVue archiver() {
        ImpayeService.Bilan b = impayes.traiterEcheances();
        return new BilanTacheVue(b.examines(), b.reussis(), b.echecs().size(), b.echecs());
    }
}
