package ma.jurika.billing.api;

import ma.jurika.billing.api.dto.BillingDtos.AbonnementAdminVue;
import ma.jurika.billing.api.dto.BillingDtos.BilanTacheVue;
import ma.jurika.billing.api.dto.BillingDtos.EnregistrementPaiementRequest;
import ma.jurika.billing.api.dto.BillingDtos.FactureVue;
import ma.jurika.billing.api.dto.BillingDtos.PaiementVue;
import ma.jurika.billing.api.dto.BillingDtos.RejetPaiementRequest;
import ma.jurika.billing.api.dto.BillingDtos.SurDevisVue;
import ma.jurika.billing.api.dto.BillingDtos.ValidationPaiementRequest;
import ma.jurika.billing.application.AdministrationFacturationService;
import ma.jurika.billing.application.facture.FactureQueryService;
import ma.jurika.billing.application.paiement.PaiementService;
import ma.jurika.common.audit.Auditable;
import ma.jurika.common.security.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Lot L7 (E5) : facturation vue du SUPER-ADMIN (tous cabinets). RG-PAY-02 : enregistrement des
 * paiements par virement, cheque et especes (reception, reference, date), qui active ou prolonge le
 * workspace ; RG-PAY-03 : factures de tous les cabinets ; RG-PLN-05 : montant Sur Devis calcule.
 */
@RestController
@RequestMapping("/api/v1/billing/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminBillingController {

    private final PaiementService paiements;
    private final AdministrationFacturationService admin;
    private final FactureQueryService factures;

    public AdminBillingController(PaiementService paiements, AdministrationFacturationService admin,
                                  FactureQueryService factures) {
        this.paiements = paiements;
        this.admin = admin;
        this.factures = factures;
    }

    /** Paiements hors ligne declares par les cabinets, en attente de reception. */
    @GetMapping("/paiements/a-enregistrer")
    public List<PaiementVue> aEnregistrer() {
        return paiements.paiementsAEnregistrer();
    }

    @GetMapping("/paiements")
    public List<PaiementVue> recents() {
        return paiements.paiementsRecents();
    }

    @PostMapping("/paiements")
    @Auditable(action = "BILLING_PAIEMENT_ENREGISTRE", resourceType = "payment")
    public PaiementVue enregistrer(@RequestBody EnregistrementPaiementRequest r,
                                   @AuthenticationPrincipal AuthenticatedUser user) {
        return paiements.enregistrer(r, auteur(user));
    }

    @PostMapping("/paiements/{id}/valider")
    @Auditable(action = "BILLING_PAIEMENT_VALIDE", resourceType = "payment")
    public PaiementVue valider(@PathVariable Long id, @RequestBody ValidationPaiementRequest r,
                               @AuthenticationPrincipal AuthenticatedUser user) {
        return paiements.valider(id, r, auteur(user));
    }

    @PostMapping("/paiements/{id}/rejeter")
    @Auditable(action = "BILLING_PAIEMENT_REJETE", resourceType = "payment")
    public PaiementVue rejeter(@PathVariable Long id, @RequestBody RejetPaiementRequest r,
                               @AuthenticationPrincipal AuthenticatedUser user) {
        return paiements.rejeter(id, r == null ? null : r.motif(), auteur(user));
    }

    @GetMapping("/abonnements")
    public List<AbonnementAdminVue> abonnements() {
        return admin.abonnements();
    }

    @GetMapping("/abonnements/{workspaceId}/sur-devis")
    public SurDevisVue surDevis(@PathVariable UUID workspaceId) {
        return admin.surDevis(workspaceId);
    }

    @GetMapping("/factures")
    public List<FactureVue> factures(@RequestParam(required = false) UUID workspaceId) {
        return factures.toutes(workspaceId);
    }

    @GetMapping("/factures/{id}/pdf")
    public ResponseEntity<byte[]> facturePdf(@PathVariable Long id) {
        return BillingController.pdf(factures.pdf(id));
    }

    @PostMapping("/taches/factures-en-attente")
    public BilanTacheVue facturesEnAttente() {
        return admin.emettreFacturesEnAttente();
    }

    @PostMapping("/taches/activations")
    public BilanTacheVue activations() {
        return admin.propagerActivations();
    }

    @PostMapping("/taches/archivage")
    public BilanTacheVue archivage() {
        return admin.archiver();
    }

    private static UUID auteur(AuthenticatedUser user) {
        if (user == null) throw new IllegalStateException("Super-admin non authentifie");
        return user.userId();
    }
}
