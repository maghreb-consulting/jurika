package ma.jurika.billing.infrastructure.cmi;

import ma.jurika.billing.api.dto.BillingDtos.CmiFormulaireVue;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lot L7 (E5), RG-PAY-04 : formulaire de la page de paiement hebergee par le CMI. Le navigateur
 * le soumet en POST (formulaire auto-soumis) : la carte est saisie chez le CMI, la plateforme ne la
 * voit ni ne la stocke jamais. Champs et signature : {@link CmiSpecification}.
 */
@Component
public class CmiPasserelle {

    private static final SecureRandom RNG = new SecureRandom();

    private final CmiProperties props;

    public CmiPasserelle(CmiProperties props) {
        this.props = props;
    }

    /**
     * @param oid      numero de commande unique (paiement)
     * @param centimes montant TTC a regler
     * @param nom      nom du payeur affiche par le CMI (cabinet)
     * @param email    adresse du payeur (recu du CMI)
     * @param shopUrl  page de retour au magasin (bouton "annuler" du CMI)
     */
    public CmiFormulaireVue formulaire(String oid, long centimes, String nom, String email, String shopUrl) {
        if (!props.configure()) {
            throw new IllegalStateException("CMI non configure : " + props.manquants());
        }
        Map<String, String> champs = new LinkedHashMap<>();
        champs.put(CmiSpecification.CLIENT_ID, props.getClientId().trim());
        champs.put(CmiSpecification.AMOUNT, CmiSpecification.montant(centimes));
        champs.put(CmiSpecification.CURRENCY, CmiSpecification.DEVISE_MAD);
        champs.put(CmiSpecification.OID, oid);
        champs.put(CmiSpecification.OK_URL, props.getOkUrl().trim());
        champs.put(CmiSpecification.FAIL_URL, props.getFailUrl().trim());
        champs.put(CmiSpecification.CALLBACK_URL, props.getCallbackUrl().trim());
        champs.put(CmiSpecification.SHOP_URL, shopUrl == null ? "" : shopUrl);
        champs.put(CmiSpecification.TRAN_TYPE_FIELD, CmiSpecification.TRAN_TYPE);
        champs.put(CmiSpecification.STORE_TYPE_FIELD, CmiSpecification.STORE_TYPE);
        champs.put(CmiSpecification.HASH_ALGO_FIELD, CmiSpecification.HASH_ALGORITHM);
        champs.put(CmiSpecification.LANG, props.getLang() == null || props.getLang().isBlank() ? "fr" : props.getLang());
        champs.put(CmiSpecification.REFRESH_TIME, "5");
        champs.put(CmiSpecification.CALLBACK_RESPONSE, "true");
        champs.put(CmiSpecification.BILL_TO_NAME, nom == null ? "" : nom);
        champs.put(CmiSpecification.EMAIL, email == null ? "" : email);
        champs.put(CmiSpecification.RND, HexFormat.of().formatHex(aleatoire()));
        champs.put(CmiSpecification.ENCODING_FIELD, CmiSpecification.ENCODING);
        champs.put(CmiSpecification.HASH, CmiSpecification.signer(champs, props.getStoreKey()));
        return new CmiFormulaireVue(props.getGatewayUrl().trim(), champs);
    }

    private static byte[] aleatoire() {
        byte[] b = new byte[10];
        RNG.nextBytes(b);
        return b;
    }
}
