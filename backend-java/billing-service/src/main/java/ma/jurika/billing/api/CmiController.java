package ma.jurika.billing.api;

import ma.jurika.billing.application.paiement.CmiRetourService;
import ma.jurika.billing.infrastructure.cmi.CmiProperties;
import ma.jurika.billing.infrastructure.cmi.CmiSpecification;
import ma.jurika.billing.infrastructure.config.BillingProperties;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.PaymentJpaRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lot L7 (E5), RG-PAY-04 et RG-PAY-05 : points d'entree PUBLICS du CMI (sans jeton JURIKA ; la
 * securite du retour serveur est la signature verifiee).
 *
 * <ul>
 *   <li>{@code POST /api/v1/billing/cmi/callback} : retour SERVEUR signe, seule confirmation d'un
 *       paiement par carte ; reponse texte attendue par le CMI.</li>
 *   <li>{@code /api/v1/billing/cmi/retour/{ok|echec}} : retour du NAVIGATEUR. Il ne confirme
 *       jamais rien : il redirige vers l'ecran de suivi du paiement, qui attend le retour
 *       serveur.</li>
 *   <li>{@code GET /api/v1/billing/cmi/etat} : liaison configuree ou non (noms des parametres
 *       manquants, jamais leurs valeurs), pour le test de fumee et l'ecran.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/billing/cmi")
public class CmiController {

    private final CmiRetourService retours;
    private final CmiProperties cmi;
    private final BillingProperties props;
    private final PaymentJpaRepository paiements;

    public CmiController(CmiRetourService retours, CmiProperties cmi, BillingProperties props,
                         PaymentJpaRepository paiements) {
        this.retours = retours;
        this.cmi = cmi;
        this.props = props;
        this.paiements = paiements;
    }

    @PostMapping(value = "/callback", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    public String callback(@RequestParam MultiValueMap<String, String> formulaire) {
        return retours.traiter(premieres(formulaire));
    }

    @RequestMapping(value = "/retour/{issue}", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Void> retourNavigateur(@PathVariable String issue,
                                                 @RequestParam MultiValueMap<String, String> formulaire) {
        String oid = formulaire.getFirst(CmiSpecification.OID);
        Long paiement = oid == null ? null : paiements.findByCmiOid(oid).map(p -> p.getId()).orElse(null);
        String cible = UriComponentsBuilder.fromHttpUrl(props.getFrontUrl())
                .path("/app/billing/paiement/retour")
                .queryParam("issue", "ok".equals(issue) ? "ok" : "echec")
                .queryParamIfPresent("paiement", java.util.Optional.ofNullable(paiement))
                .build().toUriString();
        return ResponseEntity.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, cible).build();
    }

    @GetMapping("/etat")
    public Map<String, Object> etat() {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("configure", cmi.configure());
        e.put("manquants", cmi.manquants());
        e.put("simulateur", cmi.isSimulateurActif());
        return e;
    }

    static Map<String, String> premieres(MultiValueMap<String, String> formulaire) {
        Map<String, String> m = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : formulaire.entrySet()) {
            m.put(e.getKey(), e.getValue() == null || e.getValue().isEmpty() ? "" : e.getValue().get(0));
        }
        return m;
    }
}
