package ma.jurika.auth.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Simplification inscription (2026-07-13) — payload de mise a jour des
 * informations du cabinet apres inscription (PATCH /api/v1/workspace/profile).
 *
 * <p>Sert notamment a completer l'ICE laisse vide au signup (il devient
 * obligatoire pour les dossiers). Les deux champs sont optionnels dans la
 * requete : seuls ceux fournis (non-null) sont modifies.
 */
public class UpdateWorkspaceProfileRequest {

    /** ICE : vide (efface) ou 15 chiffres. null = champ non modifie. */
    @Pattern(regexp = "^(\\d{15})?$", message = "ICE : 15 chiffres requis.")
    private String ice;

    @Size(max = 80, message = "Ville : 80 caracteres max.")
    private String city;

    /**
     * En-tete PDF (2026-07-14) — nom affiche en en-tete des documents generes.
     * null = champ non modifie ; "" = effacement (repli sur la denomination).
     */
    @Size(max = 150, message = "Nom affiche : 150 caracteres max.")
    private String nomAfficheDocuments;

    // Papier a en-tete V31 — coordonnees du cabinet (tous optionnels ; "" efface).
    @Size(max = 300, message = "Adresse : 300 caracteres max.")
    private String adresse;
    @Size(max = 30, message = "Telephone : 30 caracteres max.")
    private String telephone;
    @Size(max = 200, message = "Site web : 200 caracteres max.")
    private String siteWeb;
    @Size(max = 20, message = "RC : 20 caracteres max.")
    private String rcNumber;
    @Size(max = 8, message = "Identifiant fiscal : 8 caracteres max.")
    private String ifFiscal;

    public UpdateWorkspaceProfileRequest() {}

    public String getIce() { return ice; }
    public void setIce(String v) { this.ice = v; }
    public String getCity() { return city; }
    public void setCity(String v) { this.city = v; }
    public String getNomAfficheDocuments() { return nomAfficheDocuments; }
    public void setNomAfficheDocuments(String v) { this.nomAfficheDocuments = v; }
    public String getAdresse() { return adresse; }
    public void setAdresse(String v) { this.adresse = v; }
    public String getTelephone() { return telephone; }
    public void setTelephone(String v) { this.telephone = v; }
    public String getSiteWeb() { return siteWeb; }
    public void setSiteWeb(String v) { this.siteWeb = v; }
    public String getRcNumber() { return rcNumber; }
    public void setRcNumber(String v) { this.rcNumber = v; }
    public String getIfFiscal() { return ifFiscal; }
    public void setIfFiscal(String v) { this.ifFiscal = v; }
}
