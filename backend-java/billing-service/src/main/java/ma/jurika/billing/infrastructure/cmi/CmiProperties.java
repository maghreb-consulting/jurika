package ma.jurika.billing.infrastructure.cmi;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Lot L7 (E5), RG-PAY-04 et RG-PAY-06 : parametres du magasin chez le CMI (Centre Monetique
 * Interbancaire), par variables d'environnement SANS valeur par defaut (application.yml) :
 *
 * <ul>
 *   <li>{@code CMI_CLIENT_ID} : identifiant du marchand (clientid) ;</li>
 *   <li>{@code CMI_STORE_KEY} : cle du magasin (secret, signature des echanges) ;</li>
 *   <li>{@code CMI_GATEWAY_URL} : adresse de la page de paiement hebergee du CMI ;</li>
 *   <li>{@code CMI_CALLBACK_URL} : adresse publique HTTPS du retour serveur (le CMI doit pouvoir la
 *       joindre, RG-PAY-06) ;</li>
 *   <li>{@code CMI_OK_URL} / {@code CMI_FAIL_URL} : retour du navigateur (information seulement :
 *       il ne confirme jamais un paiement, RG-PAY-05).</li>
 * </ul>
 *
 * Tant qu'un parametre manque, le paiement par carte est refuse avec un message qui le dit
 * ({@link #manquants()}) ; rien n'est simule en silence. Branchement au vrai CMI : A_DECIDER
 * (contrat de commerce electronique de Maghreb Consulting, CDC 18 action 1).
 *
 * <p>{@code CMI_SIMULATEUR_ACTIF} ({@code jurika.cmi.simulateur-actif}, faux par defaut) active le
 * simulateur de test ({@link CmiSimulateurController}), jamais en production.
 */
@ConfigurationProperties(prefix = "jurika.cmi")
public class CmiProperties {

    private String clientId = "";
    private String storeKey = "";
    private String gatewayUrl = "";
    private String callbackUrl = "";
    private String okUrl = "";
    private String failUrl = "";
    private String lang = "fr";
    private boolean simulateurActif = false;

    /** Parametres manquants (noms des variables d'environnement). Vide = carte utilisable. */
    public java.util.List<String> manquants() {
        java.util.List<String> m = new java.util.ArrayList<>();
        if (blank(clientId)) m.add("CMI_CLIENT_ID");
        if (blank(storeKey)) m.add("CMI_STORE_KEY");
        if (blank(gatewayUrl)) m.add("CMI_GATEWAY_URL");
        if (blank(callbackUrl)) m.add("CMI_CALLBACK_URL");
        if (blank(okUrl)) m.add("CMI_OK_URL");
        if (blank(failUrl)) m.add("CMI_FAIL_URL");
        return m;
    }

    public boolean configure() {
        return manquants().isEmpty();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    public String getClientId() { return clientId; }
    public void setClientId(String v) { this.clientId = v; }
    public String getStoreKey() { return storeKey; }
    public void setStoreKey(String v) { this.storeKey = v; }
    public String getGatewayUrl() { return gatewayUrl; }
    public void setGatewayUrl(String v) { this.gatewayUrl = v; }
    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String v) { this.callbackUrl = v; }
    public String getOkUrl() { return okUrl; }
    public void setOkUrl(String v) { this.okUrl = v; }
    public String getFailUrl() { return failUrl; }
    public void setFailUrl(String v) { this.failUrl = v; }
    public String getLang() { return lang; }
    public void setLang(String v) { this.lang = v; }
    public boolean isSimulateurActif() { return simulateurActif; }
    public void setSimulateurActif(boolean v) { this.simulateurActif = v; }

    /** Jamais la cle de magasin dans un journal. */
    @Override
    public String toString() {
        return "CmiProperties[clientId=" + (blank(clientId) ? "absent" : "present")
                + ", storeKey=" + (blank(storeKey) ? "absente" : "presente")
                + ", gatewayUrl=" + gatewayUrl + ", simulateurActif=" + simulateurActif + "]";
    }
}
