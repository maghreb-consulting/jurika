package ma.jurika.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ma.jurika.auth.application.ContexteWorkspacePublic;
import ma.jurika.auth.application.GestionCompteUseCase;
import ma.jurika.auth.application.GestionCompteUseCase.Attente;
import ma.jurika.auth.application.GestionCompteUseCase.Demandeur;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Lot L5 (E10) : "Mon compte" (CDC 3.6, RG-CPT-01 et RG-CPT-02).
 *
 * <p>Chaque route agit sur le compte de l'appelant (identifiant tire du jeton,
 * jamais du corps). Nom, adresse et identifiant : employe, superviseur et
 * super-admin. Le client n'y a pas acces : RG-CPT-01 nomme l'employe et le
 * superviseur, et RG-COM-09 avertit le client a l'adresse enregistree lors de
 * son invitation (decision a revoir, rapport du lot). Le mot de passe reste
 * modifiable par tous ({@code POST /api/v1/auth/change-password}, mot de passe
 * actuel exige).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class CompteController {

    private static final String ROLES_COMPTE =
            "hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPERVISEUR','ROLE_SUPER_ADMIN')";

    private final GestionCompteUseCase gestionCompte;
    private final ContexteWorkspacePublic contextePublic;

    public CompteController(GestionCompteUseCase gestionCompte, ContexteWorkspacePublic contextePublic) {
        this.gestionCompte = gestionCompte;
        this.contextePublic = contextePublic;
    }

    public record NomRequest(@NotBlank @Size(max = 100) String firstName,
                             @NotBlank @Size(max = 100) String lastName) {}

    public record AdresseRequest(@NotBlank @Size(max = 150) String nouvelleAdresse) {}

    public record IdentifiantRequest(@NotBlank @Size(max = 150) String nouvelIdentifiant) {}

    public record ConfirmationRequest(@NotBlank @Size(max = 100) String token) {}

    @PatchMapping("/me/nom")
    @PreAuthorize(ROLES_COMPTE)
    public ResponseEntity<Void> modifierNom(@AuthenticationPrincipal AuthenticatedUser user,
                                            @jakarta.validation.Valid @RequestBody NomRequest req,
                                            HttpServletRequest http) {
        gestionCompte.modifierNom(demandeur(user, http), req.firstName(), req.lastName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/adresse")
    @PreAuthorize(ROLES_COMPTE)
    public ResponseEntity<Map<String, Object>> demanderAdresse(@AuthenticationPrincipal AuthenticatedUser user,
                                                               @jakarta.validation.Valid @RequestBody AdresseRequest req,
                                                               HttpServletRequest http) {
        Attente a = gestionCompte.demanderChangementAdresse(demandeur(user, http), req.nouvelleAdresse());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(versCorps(a));
    }

    @PostMapping("/me/identifiant")
    @PreAuthorize(ROLES_COMPTE)
    public ResponseEntity<Map<String, Object>> demanderIdentifiant(@AuthenticationPrincipal AuthenticatedUser user,
                                                                   @jakarta.validation.Valid @RequestBody IdentifiantRequest req,
                                                                   HttpServletRequest http) {
        Attente a = gestionCompte.demanderChangementIdentifiant(demandeur(user, http), req.nouvelIdentifiant());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(versCorps(a));
    }

    /** {@code motif} : {@code adresse} ou {@code identifiant}. */
    @DeleteMapping("/me/demandes/{motif}")
    @PreAuthorize(ROLES_COMPTE)
    public ResponseEntity<Void> annuler(@AuthenticationPrincipal AuthenticatedUser user,
                                        @PathVariable String motif, HttpServletRequest http) {
        gestionCompte.annulerDemande(demandeur(user, http), motifInterne(motif));
        return ResponseEntity.noContent().build();
    }

    /** Demandes en attente de confirmation (etat affiche par l'ecran "Mon compte"). */
    @GetMapping("/me/demandes")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> demandes(@AuthenticationPrincipal AuthenticatedUser user) {
        var etat = gestionCompte.etat(user.userId(), user.workspaceId());
        Map<String, Object> corps = new HashMap<>();
        corps.put("adresse", etat.adresseEnAttente().map(CompteController::versCorps).orElse(null));
        corps.put("identifiant", etat.identifiantEnAttente().map(CompteController::versCorps).orElse(null));
        return corps;
    }

    /** Route PUBLIQUE : le lien recu par courriel est la preuve. */
    @PostMapping("/compte/confirmer")
    public Map<String, Object> confirmer(@jakarta.validation.Valid @RequestBody ConfirmationRequest req,
                                         HttpServletRequest http) {
        contextePublic.poserParJetonVerification(req.token());
        var c = gestionCompte.confirmer(req.token(), http.getRemoteAddr(), http.getHeader("User-Agent"));
        return Map.of(
                "motif", GestionCompteUseCase.MOTIF_IDENTIFIANT.equals(c.motif()) ? "identifiant" : "adresse",
                "nouvelleValeur", c.nouvelleValeur());
    }

    private static Demandeur demandeur(AuthenticatedUser user, HttpServletRequest http) {
        return new Demandeur(user.userId(), user.workspaceId(), http.getRemoteAddr(), http.getHeader("User-Agent"));
    }

    private static Map<String, Object> versCorps(Attente a) {
        return Map.of("nouvelleValeur", a.nouvelleValeur(), "expireLe", a.expireLe().toString());
    }

    private static String motifInterne(String motif) {
        return Optional.ofNullable(motif)
                .map(m -> switch (m) {
                    case "adresse" -> GestionCompteUseCase.MOTIF_ADRESSE;
                    case "identifiant" -> GestionCompteUseCase.MOTIF_IDENTIFIANT;
                    default -> null;
                })
                .orElseThrow(() -> new NotFoundException("Demande inconnue"));
    }
}
