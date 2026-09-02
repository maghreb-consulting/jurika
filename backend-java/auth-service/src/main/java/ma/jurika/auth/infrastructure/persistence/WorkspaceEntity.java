package ma.jurika.auth.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspaces")
public class WorkspaceEntity {

    @Id
    private UUID id;
    @Column(nullable = false, unique = true, length = 12)
    private String code;
    @Column(nullable = false, length = 150)
    private String name;
    @Column(name = "contact_email", nullable = false, length = 150)
    private String contactEmail;
    @Column(name = "subscription_id")
    private UUID subscriptionId;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Sprint 11 V16 — Trial 14 jours + ICE/IF/RC/city + flag wizard
    @Column(name = "trial_started_at")
    private Instant trialStartedAt;
    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;
    @Column(name = "trial_status", length = 20)
    private String trialStatus;
    @Column(name = "selected_plan", length = 20)
    private String selectedPlan;
    @Column(length = 15)
    private String ice;
    @Column(name = "if_fiscal", length = 8)
    private String ifFiscal;
    @Column(name = "rc_number", length = 20)
    private String rcNumber;
    @Column(length = 80)
    private String city;
    @Column(name = "created_via_sprint11_wizard", nullable = false)
    private boolean createdViaSprint11Wizard = false;

    // Onboarding 2026-06-24 V29 — type de profil declare au signup (8 valeurs
    // ProfessionalType). NULLABLE : les workspaces pre-evolution restent valides.
    @Column(name = "professional_type", length = 30)
    private String professionalType;

    // En-tete PDF V30 (2026-07-14) — nom affiche en en-tete des documents generes.
    // NULL/vide => repli sur name (denomination). Modifiable par le SUPERVISEUR.
    @Column(name = "nom_affiche_documents", length = 150)
    private String nomAfficheDocuments;

    // Papier a en-tete V31 (2026-07-14) — coordonnees du cabinet. Les octets du
    // logo (logo_bytes) NE SONT PAS mappes ici (evite de charger ~1 Mo sur les
    // chemins chauds type login) : voir WorkspaceLogoEntity. On garde seulement
    // le content-type (leger) pour exposer un flag hasLogo cote profil.
    @Column(length = 300)
    private String adresse;
    @Column(length = 30)
    private String telephone;
    @Column(name = "site_web", length = 200)
    private String siteWeb;
    @Column(name = "logo_content_type", length = 50)
    private String logoContentType;

    // Sprint 12.5 V22 — Theme prefere UI (light defaut velin editorial, dark studio navy)
    @Column(name = "preferred_theme", nullable = false, length = 10)
    private String preferredTheme = "light";

    // Sprint 12 finition V26 (2026-06-04) — subscription Stripe persiste cote auth-service
    // pour que RemoteTrialAccessChecker puisse mapper subscriptionStatus → CONVERTED.
    @Column(name = "subscription_status", length = 20)
    private String subscriptionStatus;
    @Column(name = "subscription_ends_at")
    private Instant subscriptionEndsAt;
    @Column(name = "active_since")
    private Instant activeSince;
    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @PrePersist
    void onPersist() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = "ACTIVE";
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public UUID getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(UUID subscriptionId) { this.subscriptionId = subscriptionId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getTrialStartedAt() { return trialStartedAt; }
    public void setTrialStartedAt(Instant v) { this.trialStartedAt = v; }
    public Instant getTrialEndsAt() { return trialEndsAt; }
    public void setTrialEndsAt(Instant v) { this.trialEndsAt = v; }
    public String getTrialStatus() { return trialStatus; }
    public void setTrialStatus(String v) { this.trialStatus = v; }
    public String getSelectedPlan() { return selectedPlan; }
    public void setSelectedPlan(String v) { this.selectedPlan = v; }
    public String getIce() { return ice; }
    public void setIce(String v) { this.ice = v; }
    public String getIfFiscal() { return ifFiscal; }
    public void setIfFiscal(String v) { this.ifFiscal = v; }
    public String getRcNumber() { return rcNumber; }
    public void setRcNumber(String v) { this.rcNumber = v; }
    public String getCity() { return city; }
    public void setCity(String v) { this.city = v; }
    public boolean isCreatedViaSprint11Wizard() { return createdViaSprint11Wizard; }
    public void setCreatedViaSprint11Wizard(boolean v) { this.createdViaSprint11Wizard = v; }
    public String getProfessionalType() { return professionalType; }
    public void setProfessionalType(String v) { this.professionalType = v; }
    public String getNomAfficheDocuments() { return nomAfficheDocuments; }
    public void setNomAfficheDocuments(String v) { this.nomAfficheDocuments = v; }
    public String getAdresse() { return adresse; }
    public void setAdresse(String v) { this.adresse = v; }
    public String getTelephone() { return telephone; }
    public void setTelephone(String v) { this.telephone = v; }
    public String getSiteWeb() { return siteWeb; }
    public void setSiteWeb(String v) { this.siteWeb = v; }
    public String getLogoContentType() { return logoContentType; }
    public void setLogoContentType(String v) { this.logoContentType = v; }
    public String getPreferredTheme() { return preferredTheme; }
    public void setPreferredTheme(String v) { this.preferredTheme = v; }
    public String getSubscriptionStatus() { return subscriptionStatus; }
    public void setSubscriptionStatus(String v) { this.subscriptionStatus = v; }
    public Instant getSubscriptionEndsAt() { return subscriptionEndsAt; }
    public void setSubscriptionEndsAt(Instant v) { this.subscriptionEndsAt = v; }
    public Instant getActiveSince() { return activeSince; }
    public void setActiveSince(Instant v) { this.activeSince = v; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant v) { this.cancelledAt = v; }
}
