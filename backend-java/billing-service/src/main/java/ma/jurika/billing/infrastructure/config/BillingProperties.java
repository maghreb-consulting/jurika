package ma.jurika.billing.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Proprietes du module de facturation.
 *
 * <p>Lot L7 (E5) : le taux de TVA n'est plus une propriete (il est lu dans les parametres de
 * l'editeur, reglable par le super-admin, billing V6) ; Stripe est retire. Restent : l'adresse du
 * service commercial (Sur Devis), l'expediteur des courriels, l'adresse publique du front (retour
 * du navigateur apres la page du CMI) et les coordonnees de reglement par virement, qui ne sont pas
 * encore connues : vides tant que la direction ne les a pas communiquees (A_DECIDER), et l'ecran le
 * dit au lieu d'afficher des coordonnees inventees.
 */
@ConfigurationProperties(prefix = "jurika.billing")
@Validated
public class BillingProperties {

    // Lot L7 : facultative, aucune adresse par defaut (contact@jurika.ai n'est pas un domaine de JURIKA) ;
    // a defaut, l'adresse d'expedition verifiee (ContactSalesUseCase).
    private String salesEmail = "";

    @NotBlank
    // Lot L6 (E5) : aucun expediteur par defaut (jurika.ma non deploye) : SMTP_FROM obligatoire (@NotBlank).
    private String fromEmail = "";

    /** Adresse publique du front (ex. https://app.jurika.ma), cible du retour du navigateur. */
    @NotBlank
    private String frontUrl = "";

    /** Virement : banque et RIB de l'editeur. A_DECIDER (direction) : vides tant que non communiques. */
    private String virementBanque = "";
    private String virementRib = "";

    public String getSalesEmail() { return salesEmail; }
    public void setSalesEmail(String s) { this.salesEmail = s; }

    /** Lot L7 : adresse commerciale (BILLING_SALES_EMAIL), a defaut l'expediteur verifie ; null si aucune. */
    public String adresseCommerciale() {
        if (salesEmail != null && !salesEmail.isBlank()) return salesEmail.trim();
        return fromEmail == null || fromEmail.isBlank() ? null : fromEmail.trim();
    }
    public String getFromEmail() { return fromEmail; }
    public void setFromEmail(String s) { this.fromEmail = s; }
    public String getFrontUrl() { return frontUrl; }
    public void setFrontUrl(String s) { this.frontUrl = s; }
    public String getVirementBanque() { return virementBanque; }
    public void setVirementBanque(String s) { this.virementBanque = s; }
    public String getVirementRib() { return virementRib; }
    public void setVirementRib(String s) { this.virementRib = s; }
}
