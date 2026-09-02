package ma.jurika.dataroom.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Vue read-only sur la table workspaces (creee par auth-service). Sert a la
 * Fiche client pour afficher le nom du cabinet (workspace) dans l'en-tete du
 * PDF, sans cross-call HTTP. Lecture seule : aucune colonne insertable/updatable.
 */
@Entity
@Table(name = "workspaces")
public class WorkspaceViewEntity {
    @Id
    private UUID id;
    @Column(insertable = false, updatable = false)
    private String name;
    @Column(insertable = false, updatable = false)
    private String code;
    /** En-tete PDF V30 — nom affiche personnalise (repli sur name si vide). */
    @Column(name = "nom_affiche_documents", insertable = false, updatable = false)
    private String nomAfficheDocuments;
    // Papier a en-tete V31 — coordonnees + logo, lus pour le socle PDF.
    @Column(name = "logo_bytes", insertable = false, updatable = false)
    private byte[] logoBytes;
    @Column(name = "logo_content_type", insertable = false, updatable = false)
    private String logoContentType;
    @Column(insertable = false, updatable = false)
    private String adresse;
    @Column(insertable = false, updatable = false)
    private String telephone;
    @Column(name = "site_web", insertable = false, updatable = false)
    private String siteWeb;
    @Column(name = "contact_email", insertable = false, updatable = false)
    private String contactEmail;
    @Column(insertable = false, updatable = false)
    private String ice;
    @Column(name = "rc_number", insertable = false, updatable = false)
    private String rcNumber;
    @Column(name = "if_fiscal", insertable = false, updatable = false)
    private String ifFiscal;

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getCode() { return code; }
    public String getNomAfficheDocuments() { return nomAfficheDocuments; }
    public byte[] getLogoBytes() { return logoBytes; }
    public String getLogoContentType() { return logoContentType; }
    public String getAdresse() { return adresse; }
    public String getTelephone() { return telephone; }
    public String getSiteWeb() { return siteWeb; }
    public String getContactEmail() { return contactEmail; }
    public String getIce() { return ice; }
    public String getRcNumber() { return rcNumber; }
    public String getIfFiscal() { return ifFiscal; }
}
