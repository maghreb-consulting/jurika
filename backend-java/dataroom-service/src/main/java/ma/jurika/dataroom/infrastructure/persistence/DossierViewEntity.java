package ma.jurika.dataroom.infrastructure.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Vue read-only sur la table entreprise_dossiers (creee par ticket-service).
 * Permet au dataroom-service d'afficher les infos de la societe sans cross-call.
 */
@Entity
@Table(name = "entreprise_dossiers")
public class DossierViewEntity {
    @Id
    private UUID id;
    @Column(name = "workspace_id", insertable = false, updatable = false)
    private UUID workspaceId;
    @Column(name = "raison_sociale", insertable = false, updatable = false)
    private String raisonSociale;
    @Column(name = "forme_juridique", insertable = false, updatable = false)
    private String formeJuridique;
    @Column(insertable = false, updatable = false)
    private String ice;
    @Column(name = "rc_numero", insertable = false, updatable = false)
    private String rcNumero;
    @Column(name = "rc_tribunal", insertable = false, updatable = false)
    private String rcTribunal;
    // Fiche client (2026-07-14) — identifiants post-immatriculation. Presents dans
    // la table entreprise_dossiers (ticket-service) mais absents de cette vue
    // read-only jusqu'ici ; requis par la section 1 de la Fiche client.
    @Column(name = "identifiant_fiscal", insertable = false, updatable = false)
    private String identifiantFiscal;
    @Column(name = "taxe_professionnelle", insertable = false, updatable = false)
    private String taxeProfessionnelle;
    @Column(insertable = false, updatable = false)
    private String cnss;
    @Column(insertable = false, updatable = false)
    private String ville;
    @Column(name = "adresse_siege", insertable = false, updatable = false)
    private String adresseSiege;
    @Column(name = "capital_social_mad", insertable = false, updatable = false)
    private BigDecimal capitalSocialMad;
    @Column(name = "date_constitution", insertable = false, updatable = false)
    private LocalDate dateConstitution;
    /**
     * Lot W2 (2026-07-04) — date d'effet de la dissolution, posee par
     * workflow-service a la cloture du workflow DISSOLUTION. NULL tant que le
     * dossier n'est pas dissous (ou dossier dissous avant l'introduction de la
     * colonne). Sert au calcul du delai 16 j (RG-LI03) cote LIQUIDATION.
     */
    @Column(name = "date_dissolution", insertable = false, updatable = false)
    private LocalDate dateDissolution;
    @Column(insertable = false, updatable = false)
    private String statut;
    /**
     * Lot DIVERS §C (2026-08-13) — {@code MAROCAINE} (defaut) | {@code ETRANGERE}.
     * Une societe mere etrangere est materialisee par un dossier a part entiere
     * (migration ticket-service V16) : c'est ce qui lui donne une Data Room.
     */
    @Column(insertable = false, updatable = false)
    private String origine;
    /** Pays du siege de la societe mere etrangere (NULL pour un dossier marocain). */
    @Column(insertable = false, updatable = false)
    private String pays;
    /**
     * Forme juridique REELLE du pays d'origine (Ltd, GmbH, BV, Inc...). Une societe
     * etrangere n'est PAS une SARL : {@code forme_juridique} vaut alors
     * {@code ETRANGERE} et c'est cette colonne qui porte la forme a afficher et a
     * publier ({@code $SOCIETE_MERE_FORME}).
     */
    @Column(name = "forme_juridique_origine", insertable = false, updatable = false)
    private String formeJuridiqueOrigine;
    @Column(name = "client_id", insertable = false, updatable = false)
    private UUID clientId;
    /**
     * V9 — owner durable du dossier (employe qui gere la societe). Renseigne par
     * ticket-service (createur du dossier ou transfert). Sert au scoping EMPLOYE :
     * un employe ne voit que les dossiers dont il est responsable.
     */
    @Column(name = "responsable_id", insertable = false, updatable = false)
    private UUID responsableId;

    public UUID getId() { return id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public String getRaisonSociale() { return raisonSociale; }
    public String getFormeJuridique() { return formeJuridique; }
    public String getIce() { return ice; }
    public String getRcNumero() { return rcNumero; }
    public String getRcTribunal() { return rcTribunal; }
    public String getIdentifiantFiscal() { return identifiantFiscal; }
    public String getTaxeProfessionnelle() { return taxeProfessionnelle; }
    public String getCnss() { return cnss; }
    public String getVille() { return ville; }
    public String getAdresseSiege() { return adresseSiege; }
    public BigDecimal getCapitalSocialMad() { return capitalSocialMad; }
    public LocalDate getDateConstitution() { return dateConstitution; }
    public LocalDate getDateDissolution() { return dateDissolution; }
    public String getStatut() { return statut; }
    public String getOrigine() { return origine; }
    public String getPays() { return pays; }
    public String getFormeJuridiqueOrigine() { return formeJuridiqueOrigine; }
    public UUID getClientId() { return clientId; }
    public UUID getResponsableId() { return responsableId; }
}
