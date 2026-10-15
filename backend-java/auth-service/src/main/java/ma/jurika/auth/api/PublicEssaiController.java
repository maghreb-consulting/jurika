package ma.jurika.auth.api;

import jakarta.servlet.http.HttpServletRequest;
import ma.jurika.auth.application.ContexteWorkspacePublic;
import ma.jurika.auth.application.essai.ConditionsEssai;
import ma.jurika.auth.application.essai.InscriptionEssaiService;
import ma.jurika.auth.application.essai.InscriptionEssaiService.Etat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lot L7 (E4) : inscription PUBLIQUE a l'essai gratuit (CDC 4.2, RG-CTR-10).
 *
 * <ul>
 *   <li>{@code GET  /api/v1/public/essai/conditions} : texte, version, empreinte ;</li>
 *   <li>{@code POST /api/v1/public/essai/demandes} : cinq informations + acceptation ;</li>
 *   <li>{@code POST /api/v1/public/essai/demandes/{id}/telephone} : code SMS ;</li>
 *   <li>{@code POST /api/v1/public/essai/demandes/{id}/code} : nouveau code SMS ;</li>
 *   <li>{@code GET  /api/v1/public/essai/demandes/{id}} : etat ;</li>
 *   <li>{@code POST /api/v1/public/essai/confirmer-adresse} : lien recu par courriel.</li>
 * </ul>
 * L'essai est cree des que l'adresse ET le telephone sont verifies, quelle que
 * soit la verification faite en dernier.
 */
@RestController
@RequestMapping("/api/v1/public/essai")
public class PublicEssaiController {

    private final InscriptionEssaiService inscription;
    private final ConditionsEssai conditions;
    private final ContexteWorkspacePublic contextePublic;

    public PublicEssaiController(InscriptionEssaiService inscription, ConditionsEssai conditions,
                                 ContexteWorkspacePublic contextePublic) {
        this.inscription = inscription;
        this.conditions = conditions;
        this.contextePublic = contextePublic;
    }

    public record DemandeBody(String nom, String prenom, String organisation, String telephone, String email,
                              Boolean conditionsAcceptees, String conditionsVersion) {}

    public record CodeBody(String code) {}

    public record JetonBody(String jeton) {}

    @GetMapping("/conditions")
    public Map<String, Object> conditions() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("version", conditions.version());
        r.put("empreinte", conditions.empreinte());
        r.put("texte", conditions.texte());
        return r;
    }

    @PostMapping("/demandes")
    public ResponseEntity<InscriptionEssaiService.DemandeCreee> demander(
            @RequestBody DemandeBody body,
            @RequestHeader(value = "X-Jurika-Appareil", required = false) String appareil,
            HttpServletRequest http) {
        InscriptionEssaiService.DemandeCreee creee = inscription.demander(new InscriptionEssaiService.Demander(
                body.nom(), body.prenom(), body.organisation(), body.telephone(), body.email(),
                Boolean.TRUE.equals(body.conditionsAcceptees()), body.conditionsVersion(), appareil,
                adresseIp(http), http.getHeader("User-Agent")));
        return ResponseEntity.status(HttpStatus.CREATED).body(creee);
    }

    @GetMapping("/demandes/{demandeId}")
    public Etat etat(@PathVariable UUID demandeId) {
        return inscription.etat(demandeId);
    }

    @PostMapping("/demandes/{demandeId}/code")
    public Map<String, Object> renvoyerCode(@PathVariable UUID demandeId) {
        return Map.of("smsEnvoye", inscription.renvoyerCode(demandeId));
    }

    @PostMapping("/demandes/{demandeId}/telephone")
    public Etat verifierTelephone(@PathVariable UUID demandeId, @RequestBody CodeBody body) {
        return creerSiPret(inscription.verifierTelephone(demandeId, body == null ? null : body.code()));
    }

    @PostMapping("/confirmer-adresse")
    public Etat confirmerAdresse(@RequestBody JetonBody body) {
        return creerSiPret(inscription.confirmerAdresse(body == null ? null : body.jeton()));
    }

    /** Les deux verifications faites : le nouveau workspace est pose avant la transaction (RLS). */
    private Etat creerSiPret(Etat etat) {
        if (etat.emailVerifie() && etat.telephoneVerifie() && !etat.essaiCree()) {
            contextePublic.poserNouveauWorkspace();
            return inscription.creerEssai(etat.demandeId());
        }
        return etat;
    }

    static String adresseIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) return xRealIp.trim();
        return request.getRemoteAddr();
    }
}
