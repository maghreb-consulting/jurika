package ma.jurika.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Sprint 11 TASK 2 — Payload du wizard signup self-service.
 *
 * Simplification inscription (2026-07-13) : un formulaire unique, sans
 * distinction personne physique / morale. Les champs abandonnes du signup
 * (IF, RC + le double email cabinet) ont ete retires — l'IF/RC ne servaient
 * nulle part ailleurs (colonnes write-only). L'ICE devient OPTIONNEL
 * (completable plus tard dans les parametres du cabinet). Un seul email pro
 * est saisi (email du titulaire) : il devient a la fois le contact_email du
 * workspace ET celui du SUPERVISEUR. La ville est conservee.
 *
 * Valide via Bean Validation au controller (@Valid). Les regex ICE sont aussi
 * enforced cote Postgres (V16 CHECK constraints) en defense en profondeur.
 *
 * RG-SU04 (revise) : ICE 15 digits si renseigne, ville libre.
 */
public class SignupCabinetRequest {

    // Cabinet / structure.
    // Champs par type de profil (2026-07-27) : la denomination n'est plus
    // @NotBlank au niveau DTO. Elle est OPTIONNELLE pour les profils individuels
    // (avocat, notaire, etc.) et OBLIGATOIRE pour les structures (ENTREPRISE /
    // CENTRE_AFFAIRES) — cette regle conditionnelle (qui depend de
    // professionalType) est appliquee dans SignupCabinetUseCase, qui pose aussi
    // le fallback serveur « Prenom Nom ». On borne juste la longueur ici.
    @Size(max = 150, message = "Denomination : 150 caracteres maximum.")
    private String workspaceName;

    // ICE optionnel : vide tolere (completable plus tard). Si renseigne : 15 chiffres.
    @Pattern(regexp = "^(\\d{15})?$", message = "ICE : 15 chiffres requis.")
    private String ice;

    @NotBlank(message = "Ville obligatoire.")
    @Size(min = 2, max = 80, message = "Ville : 2 a 80 caracteres.")
    private String city;

    // Type de profil (onboarding 2026-06-24). Le nouveau front envoie toujours
    // une valeur (defaut UI). On ne met PAS @NotBlank pour ne pas casser les
    // anciens clients qui n'envoient pas ce champ (NULL tolere cote base V29) ;
    // @Pattern accepte null mais rejette toute valeur hors des 8 ProfessionalType.
    @Pattern(regexp = "^(COMPTABLE_AGREE|CONSEILLER_JURIDIQUE|CENTRE_AFFAIRES|EXPERT_COMPTABLE|ENTREPRISE|AVOCAT|NOTAIRE|AUTRE)$",
             message = "Type de profil invalide.")
    private String professionalType;

    // Titulaire (compte SUPERVISEUR)
    @NotBlank(message = "Prenom obligatoire.")
    @Size(min = 1, max = 80)
    private String firstName;

    @NotBlank(message = "Nom obligatoire.")
    @Size(min = 1, max = 80)
    private String lastName;

    // Email professionnel unique : sert d'identifiant de contact (workspace + user).
    @NotBlank(message = "Email professionnel obligatoire.")
    @Email(message = "Email professionnel invalide.")
    @Size(max = 150)
    private String email;

    // Telephone international (2026-07-28) : le front ouvre le selecteur
    // d'indicatif pays et stocke le numero en E.164 (+<indicatif><national>). On
    // accepte donc tout E.164 valide (7 a 15 chiffres). La validation fine par
    // pays (longueur/plage) est faite cote front (libphonenumber-js). La logique
    // 2FA-SMS reste, elle, restreinte aux indicatifs supportes par le fournisseur.
    @NotBlank(message = "Telephone obligatoire.")
    @Pattern(regexp = "^\\+[1-9]\\d{6,14}$",
             message = "Telephone : format international E.164 requis (ex. +212612345678).")
    private String phone;

    // Step 5 - Plan (lu depuis ?plan= querystring landing OU defaut)
    // Spec directeur 2026-06-02 : essentiel | business | entreprise.
    @Pattern(regexp = "^(essentiel|business|entreprise)$",
             message = "Plan : essentiel | business | entreprise.")
    private String selectedPlan;

    // Step 5 - CGU
    private boolean cguAccepted;

    /**
     * BUG 14 (2026-06-07) — si true, le signup ne genere PAS le MDP temporaire
     * cote welcome email immediatement : l'envoi est differe jusqu'a la
     * validation du paiement (CARD via Stripe webhook OU validation manuelle
     * BANK/CHEQUE/CASH OU TEST_BYPASS). Permet le flux signup -> plan -> recap
     * -> paiement -> activation + identifiants.
     */
    private boolean deferCredentials = false;

    public SignupCabinetRequest() {}

    public String getWorkspaceName() { return workspaceName; }
    public void setWorkspaceName(String v) { this.workspaceName = v; }
    public String getIce() { return ice; }
    public void setIce(String v) { this.ice = v; }
    public String getCity() { return city; }
    public void setCity(String v) { this.city = v; }
    public String getProfessionalType() { return professionalType; }
    public void setProfessionalType(String v) { this.professionalType = v; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String v) { this.firstName = v; }
    public String getLastName() { return lastName; }
    public void setLastName(String v) { this.lastName = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getPhone() { return phone; }
    public void setPhone(String v) { this.phone = v; }
    public String getSelectedPlan() { return selectedPlan; }
    public void setSelectedPlan(String v) { this.selectedPlan = v; }
    public boolean isCguAccepted() { return cguAccepted; }
    public void setCguAccepted(boolean v) { this.cguAccepted = v; }
    public boolean isDeferCredentials() { return deferCredentials; }
    public void setDeferCredentials(boolean v) { this.deferCredentials = v; }
}
