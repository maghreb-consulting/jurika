package ma.jurika.billing.api;

import ma.jurika.billing.api.dto.BillingDtos;
import ma.jurika.billing.api.dto.BillingDtos.AbonnementVue;
import ma.jurika.billing.api.dto.BillingDtos.ApercuChangementVue;
import ma.jurika.billing.api.dto.BillingDtos.ChangementForfaitRequest;
import ma.jurika.billing.api.dto.BillingDtos.ChangementForfaitVue;
import ma.jurika.billing.api.dto.BillingDtos.FactureVue;
import ma.jurika.billing.api.dto.BillingDtos.PaiementVue;
import ma.jurika.billing.api.dto.BillingDtos.PreparationPaiementRequest;
import ma.jurika.billing.api.dto.BillingDtos.PreparationPaiementVue;
import ma.jurika.billing.application.ContactSalesUseCase;
import ma.jurika.billing.application.abonnement.AbonnementService;
import ma.jurika.billing.application.facture.FactureQueryService;
import ma.jurika.billing.application.paiement.PaiementService;
import ma.jurika.billing.infrastructure.persistence.FactureEntity;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.TenantContext;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Lot L7 (E5) : facturation vue du CABINET (son workspace, filtre explicite).
 *
 * <ul>
 *   <li>Lecture de l'abonnement : superviseur et employe (l'ecran masque les fonctions hors forfait
 *       et explique pourquoi).</li>
 *   <li>Changement de forfait, paiement, factures : SUPERVISEUR seul (operations financieres ; le
 *       super-admin passe par la hierarchie des roles).</li>
 * </ul>
 * L'annotation de classe est le garde-fou par defaut : un nouveau point d'entree non annote est
 * reserve au superviseur.
 */
@RestController
@RequestMapping("/api/v1/billing")
@PreAuthorize("hasRole('SUPERVISEUR')")
public class BillingController {

    private final AbonnementService abonnement;
    private final PaiementService paiements;
    private final FactureQueryService factures;
    private final ContactSalesUseCase contactSales;

    public BillingController(AbonnementService abonnement, PaiementService paiements,
                             FactureQueryService factures, ContactSalesUseCase contactSales) {
        this.abonnement = abonnement;
        this.paiements = paiements;
        this.factures = factures;
        this.contactSales = contactSales;
    }

    @GetMapping("/abonnement")
    @PreAuthorize("hasAnyRole('SUPERVISEUR','EMPLOYE')")
    public AbonnementVue abonnement() {
        return abonnement.etat(cabinet());
    }

    @PostMapping("/changement-forfait/apercu")
    public ApercuChangementVue apercuChangement(@RequestBody ChangementForfaitRequest r) {
        if (r == null) throw new IllegalArgumentException("Demande de changement absente.");
        return abonnement.apercu(abonnement.calculerChangement(cabinet(), r.forfait(), r.rythme()));
    }

    @PostMapping("/changement-forfait")
    public ChangementForfaitVue changer(@RequestBody ChangementForfaitRequest r,
                                        @AuthenticationPrincipal AuthenticatedUser user) {
        return paiements.changer(cabinet(), user == null ? null : user.userId(), r);
    }

    @DeleteMapping("/changement-forfait")
    public ResponseEntity<Void> annulerChangement() {
        abonnement.annulerChangementAVenir(cabinet());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/paiements")
    public PreparationPaiementVue preparer(@RequestBody PreparationPaiementRequest r) {
        return paiements.preparer(cabinet(), r);
    }

    @GetMapping("/paiements")
    public List<PaiementVue> paiements() {
        return paiements.paiementsDuCabinet(cabinet());
    }

    @GetMapping("/paiements/{id}")
    public PaiementVue paiement(@PathVariable Long id) {
        return paiements.paiementDuCabinet(cabinet(), id);
    }

    @GetMapping("/factures")
    public List<FactureVue> factures() {
        return factures.duCabinet(cabinet());
    }

    @GetMapping("/factures/{id}/pdf")
    public ResponseEntity<byte[]> facturePdf(@PathVariable Long id) {
        return pdf(factures.pdfDuCabinet(cabinet(), id));
    }

    /** Sur Devis : demande de contact au service commercial (RG-SOU-01). */
    @PostMapping("/contact-sales")
    @PreAuthorize("hasAnyRole('SUPERVISEUR','EMPLOYE')")
    public BillingDtos.ContactSalesResponse contactSales(@RequestBody BillingDtos.ContactSalesRequest req) {
        return contactSales.send(cabinet(), req);
    }

    static ResponseEntity<byte[]> pdf(FactureEntity f) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("Facture-" + f.getNumero() + ".pdf").build().toString())
                .body(f.getPdf());
    }

    private static UUID cabinet() {
        UUID id = TenantContext.get();
        if (id == null) {
            throw new IllegalStateException("TenantContext.workspaceId absent : JwtAuthFilter doit etre actif");
        }
        return id;
    }
}
