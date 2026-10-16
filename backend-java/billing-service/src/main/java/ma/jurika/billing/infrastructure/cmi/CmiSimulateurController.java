package ma.jurika.billing.infrastructure.cmi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lot L7 (E5) : SIMULATEUR de la page de paiement du CMI, pour tester le parcours carte de bout en
 * bout SANS AUCUN PAIEMENT REEL. Present seulement si {@code jurika.cmi.simulateur-actif=true}
 * ({@code CMI_SIMULATEUR_ACTIF}, faux par defaut) ; refuse en production ({@code BillingConfig}).
 * Pour l'utiliser : {@code CMI_GATEWAY_URL} pointe sur {@code .../api/v1/billing/cmi-simulateur/passerelle}.
 *
 * <p>Comme le CMI : verifie la signature du formulaire recu ; propose d'accepter ou de refuser ;
 * envoie le retour SERVEUR signe au {@code callbackUrl} (par HTTP, comme le CMI) ; puis renvoie le
 * navigateur vers {@code okUrl} ou {@code failUrl}. Le paiement n'est donc acquis que par le retour
 * serveur, jamais par le retour du navigateur.
 */
@RestController
@RequestMapping("/api/v1/billing/cmi-simulateur")
@ConditionalOnProperty(name = "jurika.cmi.simulateur-actif", havingValue = "true")
public class CmiSimulateurController {

    private static final Logger log = LoggerFactory.getLogger(CmiSimulateurController.class);
    private static final SecureRandom RNG = new SecureRandom();

    /** Emetteur du retour serveur (HTTP par defaut ; remplacable dans les tests). */
    public interface EmetteurRetour {
        String emettre(String callbackUrl, Map<String, String> parametres);
    }

    private final CmiProperties props;
    private final EmetteurRetour emetteur;

    public CmiSimulateurController(CmiProperties props,
                                   org.springframework.beans.factory.ObjectProvider<EmetteurRetour> emetteur) {
        this.props = props;
        this.emetteur = emetteur.getIfAvailable(() -> CmiSimulateurController::emettreHttp);
    }

    @PostMapping(value = "/passerelle", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> passerelle(@RequestParam MultiValueMap<String, String> formulaire) {
        Map<String, String> champs = premieres(formulaire);
        if (!CmiSpecification.signatureValide(champs, props.getStoreKey())) {
            return ResponseEntity.badRequest().body(page("Signature invalide",
                    "<p>Le formulaire re\u00e7u n\u2019est pas sign\u00e9 avec la cl\u00e9 du magasin : le CMI le refuserait.</p>"));
        }
        StringBuilder caches = new StringBuilder();
        for (Map.Entry<String, String> e : champs.entrySet()) {
            caches.append(cache(e.getKey(), e.getValue()));
        }
        String corps = "<p class=\"alerte\">Simulateur de test : aucun paiement r\u00e9el n\u2019est effectu\u00e9.</p>"
                + "<dl><dt>Commande</dt><dd>" + esc(champs.get(CmiSpecification.OID)) + "</dd>"
                + "<dt>Montant</dt><dd>" + esc(champs.get(CmiSpecification.AMOUNT)) + " MAD</dd>"
                + "<dt>Payeur</dt><dd>" + esc(champs.get(CmiSpecification.BILL_TO_NAME)) + "</dd></dl>"
                + "<form method=\"post\" action=\"decision\">" + caches + cache("decision", "accepter")
                + "<button type=\"submit\">Accepter le paiement</button></form>"
                + "<form method=\"post\" action=\"decision\">" + caches + cache("decision", "refuser")
                + "<button type=\"submit\" class=\"secondaire\">Refuser le paiement</button></form>";
        return ResponseEntity.ok(page("Paiement par carte (simulateur CMI)", corps));
    }

    @PostMapping(value = "/decision", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> decision(@RequestParam MultiValueMap<String, String> formulaire) {
        Map<String, String> champs = premieres(formulaire);
        boolean accepte = "accepter".equals(champs.remove("decision"));
        if (!CmiSpecification.signatureValide(champs, props.getStoreKey())) {
            return ResponseEntity.badRequest().body(page("Signature invalide", "<p>Formulaire alt\u00e9r\u00e9.</p>"));
        }
        Map<String, String> retour = retourServeur(champs, accepte, props.getStoreKey());
        String reponse;
        try {
            reponse = emetteur.emettre(champs.get(CmiSpecification.CALLBACK_URL), retour);
        } catch (RuntimeException e) {
            reponse = "ERREUR : " + e.getMessage();
        }
        log.info("Simulateur CMI : commande {} {} ; reponse du marchand : {}", champs.get(CmiSpecification.OID),
                accepte ? "acceptee" : "refusee", reponse);
        String cible = accepte ? champs.get(CmiSpecification.OK_URL) : champs.get(CmiSpecification.FAIL_URL);
        // Lot L6b (E16) : le retour automatique passe par un script en ligne ; la passerelle pose
        // script-src 'self', qui l'interdisait (le navigateur restait sur cette page). La page
        // porte sa propre politique : seul SON script (nonce aleatoire) s'execute ; la passerelle
        // ne remplace pas un en-tete deja present. Pas de form-action : le retour poste vers
        // okUrl, qui redirige vers le front (autre origine) ; form-action s'appliquerait aussi a
        // cette redirection (simulateur de test seulement, refuse en production).
        String nonce = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(aleatoireLong());
        StringBuilder caches = new StringBuilder();
        for (Map.Entry<String, String> e : retour.entrySet()) {
            caches.append(cache(e.getKey(), e.getValue()));
        }
        String corps = "<p>Retour serveur envoy\u00e9 ; r\u00e9ponse du marchand : <code>" + esc(reponse) + "</code>.</p>"
                + "<form id=\"retour\" method=\"post\" action=\"" + esc(cible) + "\">" + caches
                + "<button type=\"submit\">Revenir sur le site du marchand</button></form>"
                + "<script nonce=\"" + nonce + "\">document.getElementById('retour').submit();</script>";
        return ResponseEntity.ok()
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; script-src 'nonce-"
                        + nonce + "'; base-uri 'none'; frame-ancestors 'none'")
                .body(page(accepte ? "Paiement accept\u00e9 (simulateur)" : "Paiement refus\u00e9 (simulateur)", corps));
    }

    /** Parametres du retour serveur, signes comme ceux du CMI (voir CmiSpecification). */
    public static Map<String, String> retourServeur(Map<String, String> commande, boolean accepte, String cleMagasin) {
        Map<String, String> r = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : commande.entrySet()) {
            String n = e.getKey().toLowerCase(Locale.ROOT);
            if (!n.equals("hash") && !n.equals("encoding")) r.put(e.getKey(), e.getValue());
        }
        r.put(CmiSpecification.PROC_RETURN_CODE, accepte ? CmiSpecification.CODE_ACCEPTE : "05");
        r.put(CmiSpecification.RESPONSE, accepte ? "Approved" : "Declined");
        r.put(CmiSpecification.TRANS_ID, "SIM" + HexFormat.of().formatHex(aleatoire()).toUpperCase(Locale.ROOT));
        r.put("mdStatus", accepte ? "1" : "0");
        r.put(CmiSpecification.HASH, CmiSpecification.signer(r, cleMagasin));
        return r;
    }

    static String emettreHttp(String callbackUrl, Map<String, String> parametres) {
        MultiValueMap<String, String> f = new LinkedMultiValueMap<>();
        parametres.forEach(f::add);
        return RestClient.create().post().uri(callbackUrl).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(f).retrieve().body(String.class);
    }

    private static Map<String, String> premieres(MultiValueMap<String, String> formulaire) {
        Map<String, String> m = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : formulaire.entrySet()) {
            m.put(e.getKey(), e.getValue() == null || e.getValue().isEmpty() ? "" : e.getValue().get(0));
        }
        return m;
    }

    private static byte[] aleatoireLong() {
        byte[] b = new byte[18];
        RNG.nextBytes(b);
        return b;
    }

    private static byte[] aleatoire() {
        byte[] b = new byte[6];
        RNG.nextBytes(b);
        return b;
    }

    private static String cache(String nom, String valeur) {
        return "<input type=\"hidden\" name=\"" + esc(nom) + "\" value=\"" + esc(valeur) + "\">";
    }

    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }

    private static String page(String titre, String corps) {
        return "<!doctype html><html lang=\"fr\"><head><meta charset=\"utf-8\"><title>" + esc(titre) + "</title>"
                + "<style>body{font-family:sans-serif;max-width:32rem;margin:3rem auto;padding:0 1rem;color:#0c1d3f}"
                + ".alerte{background:#fff4d6;padding:.75rem;border-radius:.5rem}button{margin:.5rem 0;padding:.6rem 1rem}"
                + ".secondaire{background:#eee}dt{font-weight:bold}</style></head><body><h1>" + esc(titre) + "</h1>"
                + corps + "</body></html>";
    }
}
