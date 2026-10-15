package ma.jurika.billing.api.contrat;

import ma.jurika.billing.application.contrat.ContratAbonnementPort;
import ma.jurika.billing.application.contrat.ContratService;
import ma.jurika.common.security.AuthenticatedUser;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lot L7 (E6) : contrats d'abonnement, cote super-admin. Etat de chaque contrat (en attente,
 * accepte, resilie), activation d'un abonnement par la saisie des conditions particulieres
 * (RG-CTR-03), resiliation (RG-CTR-08), historique (RG-CTR-09), versions (RG-CTR-07).
 */
@RestController
@RequestMapping("/api/v1/billing/admin/contrats")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ContratAdminController {

    /** Le super-admin voit toutes les versions, retirees comprises (RG-CTR-07). */
    private static final boolean SUPER_ADMIN_PORTEE = true;

    private final ContratService service;

    public ContratAdminController(ContratService service) {
        this.service = service;
    }

    public record ConditionsRequest(String forfait, String rythmePaiement, BigDecimal prixHt, LocalDate dateDebut,
                                    String modePaiement) {}

    public record ResiliationRequest(LocalDate dateEffet, String motif) {}

    public record VersionRequest(String code, String texteModele, LocalDate entreeEnVigueur) {}

    @GetMapping
    public List<ContratService.LigneAdmin> liste() {
        return service.listeAdmin();
    }

    @PostMapping("/{workspaceId}/conditions")
    public Map<String, Object> ouvrir(@PathVariable UUID workspaceId, @RequestBody ConditionsRequest body,
                                      @AuthenticationPrincipal AuthenticatedUser user) {
        UUID id = service.ouvrir(new ContratAbonnementPort.ConditionsParticulieres(workspaceId, body.forfait(),
                body.rythmePaiement(), body.prixHt(), body.dateDebut(), null, user.userId()),
                body.modePaiement(), "SUPER_ADMIN");
        return Map.of("conditionsId", id, "statut", "EN_ATTENTE");
    }

    @PostMapping("/{workspaceId}/resilier")
    public ContratService.ResiliationVue resilier(@PathVariable UUID workspaceId, @RequestBody ResiliationRequest body,
                                                  @AuthenticationPrincipal AuthenticatedUser user) {
        return service.resilierParAdmin(workspaceId, user.userId(), body.dateEffet(), body.motif());
    }

    @GetMapping("/{workspaceId}/historique")
    public ContratService.Historique historique(@PathVariable UUID workspaceId) {
        return service.historique(workspaceId, true);
    }

    @GetMapping("/{workspaceId}/acceptations/{id}/exemplaire")
    public ResponseEntity<byte[]> exemplaire(@PathVariable UUID workspaceId, @PathVariable UUID id) {
        return ContratCabinetController.pdf(service.exemplaire(workspaceId, id, true));
    }

    @GetMapping("/versions")
    public List<ContratService.VersionVue> versions() {
        return service.versions(SUPER_ADMIN_PORTEE);
    }

    @GetMapping("/versions/{code}")
    public ResponseEntity<String> versionTexte(@PathVariable String code) {
        return ResponseEntity.ok().contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .body(service.version(code, SUPER_ADMIN_PORTEE).texteModele());
    }

    @PostMapping("/versions")
    public ContratService.VersionVue publier(@RequestBody VersionRequest body,
                                             @AuthenticationPrincipal AuthenticatedUser user) {
        return service.publierVersion(body.code(), body.texteModele(), body.entreeEnVigueur(), user.userId());
    }
}
