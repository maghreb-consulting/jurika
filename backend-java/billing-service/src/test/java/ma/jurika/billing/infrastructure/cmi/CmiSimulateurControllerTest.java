package ma.jurika.billing.infrastructure.cmi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Lot L6b (E16, parcours du paiement par carte) : apres "Accepter le paiement", la page du
 * simulateur renvoie le navigateur vers JURIKA par un formulaire soumis par un script en ligne.
 * La passerelle pose une politique de securite du contenu (script-src 'self') qui interdit ce
 * script : le navigateur restait sur /cmi-simulateur/decision. La page porte desormais sa
 * propre politique, qui n'autorise que SON script (nonce aleatoire) ; la passerelle la respecte
 * (en-tete deja present, putIfAbsent).
 */
class CmiSimulateurControllerTest {

    private static final String CLE = "cle-de-test-du-magasin";

    @Test
    @SuppressWarnings("unchecked")
    void la_page_de_decision_autorise_son_script_de_retour_par_un_nonce() {
        CmiProperties props = new CmiProperties();
        props.setStoreKey(CLE);
        ObjectProvider<CmiSimulateurController.EmetteurRetour> fournisseur = mock(ObjectProvider.class);
        when(fournisseur.getIfAvailable(org.mockito.ArgumentMatchers.any()))
                .thenReturn((url, parametres) -> "ACTION=POSTAUTH");
        CmiSimulateurController simulateur = new CmiSimulateurController(props, fournisseur);

        Map<String, String> commande = new LinkedHashMap<>();
        commande.put(CmiSpecification.OID, "JUR-TEST-1");
        commande.put(CmiSpecification.AMOUNT, "900.00");
        commande.put(CmiSpecification.OK_URL, "http://192.168.1.251:8080/api/v1/billing/cmi/retour/ok");
        commande.put(CmiSpecification.FAIL_URL, "http://192.168.1.251:8080/api/v1/billing/cmi/retour/echec");
        commande.put(CmiSpecification.CALLBACK_URL, "http://billing-service:8090/api/v1/billing/cmi/callback");
        commande.put(CmiSpecification.HASH, CmiSpecification.signer(commande, CLE));
        MultiValueMap<String, String> formulaire = new LinkedMultiValueMap<>();
        commande.forEach(formulaire::add);
        formulaire.add("decision", "accepter");

        ResponseEntity<String> r = simulateur.decision(formulaire);

        assertThat(r.getStatusCode().is2xxSuccessful()).isTrue();
        Matcher script = Pattern.compile("<script nonce=\"([A-Za-z0-9_-]{16,})\">").matcher(r.getBody());
        assertThat(script.find()).as("script de retour porteur d'un nonce").isTrue();
        String csp = r.getHeaders().getFirst("Content-Security-Policy");
        assertThat(csp).as("politique propre a la page").isNotNull();
        assertThat(csp).contains("script-src 'nonce-" + script.group(1) + "'");
        assertThat(csp).doesNotContain("unsafe-inline' 'unsafe-eval").doesNotContain("script-src *");
    }
}
