package ma.jurika.billing.api.contrat;

import jakarta.servlet.http.HttpServletRequest;
import ma.jurika.billing.application.contrat.ContratService;
import ma.jurika.common.security.AuthenticatedUser;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Lot L7 (E6) : ecran du contrat d'abonnement, cote cabinet. Toujours accessible, y compris
 * quand un contrat est en attente (la passerelle laisse passer {@code /api/v1/billing/contrat/**})
 * et pendant l'archivage (RG-CTR-04 : exemplaire telechargeable a tout moment).
 *
 * <p>Le cabinet est celui du jeton (TenantContext, pose par JwtAuthFilter) : filtrage explicite
 * par workspace dans chaque requete.
 */
@RestController
@RequestMapping("/api/v1/billing/contrat")
@PreAuthorize("hasRole('SUPERVISEUR')")
public class ContratCabinetController {

    private final ContratService service;

    public ContratCabinetController(ContratService service) {
        this.service = service;
    }

    /** Etat reduit, pour l'employe : il sait seulement qu'il doit attendre le superviseur. */
    public record EtatEmploye(String statut, boolean acceptationRequise) {}

    public record AcceptationRequest(String civilite, String qualite, String formeCabinet, String rcVilleCabinet,
                                     String empreinteLue, boolean caseCochee, boolean confirmation) {
        ContratService.CommandeAcceptation commande() {
            return new ContratService.CommandeAcceptation(civilite, qualite, formeCabinet, rcVilleCabinet,
                    empreinteLue, caseCochee, confirmation);
        }
    }

    public record ResiliationRequest(String motif) {}

    @GetMapping("/etat")
    @PreAuthorize("hasAnyRole('SUPERVISEUR','EMPLOYE')")
    public Object etat(@AuthenticationPrincipal AuthenticatedUser user) {
        ContratService.Etat e = service.etat(user.workspaceId());
        if ("EMPLOYE".equals(user.role().name())) {
            return new EtatEmploye(e.statut(), "CONDITIONS_PARTICULIERES".equals(e.objetAAccepter()));
        }
        return e;
    }

    @PostMapping("/apercu")
    public ContratService.Apercu apercu(@AuthenticationPrincipal AuthenticatedUser user,
                                        @RequestBody AcceptationRequest body, HttpServletRequest req) {
        return service.apercu(user.workspaceId(), requerant(user, req), body.commande());
    }

    @PostMapping("/accepter")
    public ContratService.AcceptationVue accepter(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @RequestBody AcceptationRequest body, HttpServletRequest req) {
        return service.accepter(user.workspaceId(), requerant(user, req), body.commande());
    }

    @GetMapping("/historique")
    public ContratService.Historique historique(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.historique(user.workspaceId(), false);
    }

    @GetMapping("/acceptations/{id}/exemplaire")
    public ResponseEntity<byte[]> exemplaire(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return pdf(service.exemplaire(user.workspaceId(), id, false));
    }

    @PostMapping("/acceptations/{id}/copie")
    public ResponseEntity<Void> renvoyerCopie(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        service.renvoyerCopie(user.workspaceId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resilier")
    public ContratService.ResiliationVue resilier(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @RequestBody ResiliationRequest body) {
        return service.resilierParCabinet(user.workspaceId(), user.userId(), body.motif());
    }

    @GetMapping("/versions")
    public List<ContratService.VersionVue> versions() {
        return service.versions();
    }

    @GetMapping("/versions/{code}")
    public ResponseEntity<String> versionTexte(@PathVariable String code) {
        return ResponseEntity.ok().contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .body(service.version(code).texteModele());
    }

    static ResponseEntity<byte[]> pdf(ContratService.Exemplaire ex) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(ex.nomFichier(), StandardCharsets.UTF_8).build().toString())
                .body(ex.pdf());
    }

    static ContratService.Requerant requerant(AuthenticatedUser user, HttpServletRequest req) {
        String chaine = req.getHeader("X-Forwarded-For");
        String ip = req.getRemoteAddr();
        if (chaine != null && !chaine.isBlank()) {
            String premiere = chaine.split(",")[0].trim();
            if (!premiere.isEmpty()) ip = premiere;
        }
        String trace = (chaine == null || chaine.isBlank()) ? req.getRemoteAddr() : chaine + " ; " + req.getRemoteAddr();
        if (trace != null && trace.length() > 500) trace = trace.substring(0, 500);
        if (ip != null && ip.length() > 64) ip = ip.substring(0, 64);
        return new ContratService.Requerant(user.userId(), user.role().name(), req.getHeader(HttpHeaders.AUTHORIZATION),
                ip, trace);
    }
}
