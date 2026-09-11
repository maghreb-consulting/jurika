package ma.jurika.ticket.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Referentiel des demarches, charge par migration Flyway depuis le guide du
 * cabinet (V20). Lecture seule cote application : le referentiel se corrige par
 * migration, jamais par l'API.
 */
@Entity
@Table(name = "demarches_referentiel")
public class DemarcheReferentielEntity {

    @Id
    private UUID id;
    @Column(name = "workflow_type", insertable = false, updatable = false)
    private String workflowType;
    @Column(insertable = false, updatable = false)
    private Short ordre;
    @Column(name = "phase_code", insertable = false, updatable = false)
    private String phaseCode;
    @Column(name = "phase_libelle", insertable = false, updatable = false)
    private String phaseLibelle;
    @Column(insertable = false, updatable = false)
    private String libelle;
    @Column(name = "statut_ticket", insertable = false, updatable = false)
    private String statutTicket;
    @Column(insertable = false, updatable = false)
    private String acteur;
    @Column(insertable = false, updatable = false)
    private String organisme;
    @Column(insertable = false, updatable = false)
    private String obligatoire;
    @Column(name = "condition_application", insertable = false, updatable = false)
    private String conditionApplication;
    @Column(name = "pieces_entrantes", insertable = false, updatable = false)
    private String piecesEntrantes;
    @Column(name = "document_produit", insertable = false, updatable = false)
    private String documentProduit;
    @Column(name = "justificatifs_texte", insertable = false, updatable = false)
    private String justificatifsTexte;
    @Column(name = "modele_jurika", insertable = false, updatable = false)
    private String modeleJurika;
    @Column(insertable = false, updatable = false)
    private String delai;
    @Column(name = "cout_indicatif", insertable = false, updatable = false)
    private String coutIndicatif;
    @Column(name = "variables_alimentees", insertable = false, updatable = false)
    private String variablesAlimentees;
    @Column(name = "delai_valeur", insertable = false, updatable = false)
    private Short delaiValeur;
    @Column(name = "delai_unite", insertable = false, updatable = false)
    private String delaiUnite;
    @Column(name = "delai_reference_ordre", insertable = false, updatable = false)
    private Short delaiReferenceOrdre;
    /**
     * Lot B (V26) — nom de la donnee du dossier qui fait courir le delai, quand
     * il ne part d'aucun cochage. Exclusive de {@code delai_reference_ordre},
     * une contrainte de la base le garantit.
     */
    @Column(name = "delai_reference_donnee", insertable = false, updatable = false)
    private String delaiReferenceDonnee;
    /**
     * Lot B — les deux lignes d'une meme formalite (depot puis retrait) portent
     * le meme code. C'est ce qui permet a la ligne « retrait » de savoir depuis
     * quand elle attend : la date de cochage du depot.
     */
    @Column(name = "formalite_code", insertable = false, updatable = false)
    private String formaliteCode;
    @Column(name = "formalite_volet", insertable = false, updatable = false)
    private String formaliteVolet;
    /**
     * Lot B — une ligne retiree du parcours (migration V24) reste en base tant
     * qu'un cochage la reference : son etat, son horodatage et ses justificatifs
     * ne se jettent pas. Elle ne s'affiche pas pour autant.
     */
    @Column(insertable = false, updatable = false)
    private Boolean actif;

    public UUID getId() { return id; }
    public String getWorkflowType() { return workflowType; }
    public Short getOrdre() { return ordre; }
    public String getPhaseCode() { return phaseCode; }
    public String getPhaseLibelle() { return phaseLibelle; }
    public String getLibelle() { return libelle; }
    public String getStatutTicket() { return statutTicket; }
    public String getActeur() { return acteur; }
    public String getOrganisme() { return organisme; }
    public String getObligatoire() { return obligatoire; }
    public String getConditionApplication() { return conditionApplication; }
    public String getPiecesEntrantes() { return piecesEntrantes; }
    public String getDocumentProduit() { return documentProduit; }
    public String getJustificatifsTexte() { return justificatifsTexte; }
    public String getModeleJurika() { return modeleJurika; }
    public String getDelai() { return delai; }
    public String getCoutIndicatif() { return coutIndicatif; }
    public String getVariablesAlimentees() { return variablesAlimentees; }
    public Short getDelaiValeur() { return delaiValeur; }
    public String getDelaiUnite() { return delaiUnite; }
    public Short getDelaiReferenceOrdre() { return delaiReferenceOrdre; }
    public String getDelaiReferenceDonnee() { return delaiReferenceDonnee; }
    public String getFormaliteCode() { return formaliteCode; }
    public String getFormaliteVolet() { return formaliteVolet; }
    public Boolean getActif() { return actif; }
}
