package ma.jurika.billing.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Lot L7 (socle commun) : identite de l'editeur de JURIKA (Maghreb Consulting), une seule ligne
 * (id = 1, migration V6). Alimente les factures (RG-PAY-03) et le contrat d'abonnement (4.8).
 * Creee vide : chaque champ reste nul tant que le super-admin ne l'a pas saisi.
 */
@Entity
@Table(name = "editeur_parametres")
public class EditeurParametresEntity {

    public static final short ID_UNIQUE = 1;

    @Id
    private Short id = ID_UNIQUE;

    @Column(name = "raison_sociale") private String raisonSociale;
    @Column(name = "forme_juridique") private String formeJuridique;
    @Column(name = "capital_social") private String capitalSocial;
    @Column(name = "siege_social") private String siegeSocial;
    @Column(name = "ville") private String ville;
    @Column(name = "rc_numero") private String rcNumero;
    @Column(name = "rc_tribunal") private String rcTribunal;
    @Column(name = "ice") private String ice;
    @Column(name = "identifiant_fiscal") private String identifiantFiscal;
    @Column(name = "taxe_professionnelle") private String taxeProfessionnelle;
    @Column(name = "representant_nom") private String representantNom;
    @Column(name = "representant_qualite") private String representantQualite;
    @Column(name = "taux_tva_pourcent", nullable = false) private BigDecimal tauxTvaPourcent = new BigDecimal("20.00");
    @Column(name = "modifie_le") private Instant modifieLe;
    @Column(name = "modifie_par") private UUID modifiePar;

    public Short getId() { return id; }
    public String getRaisonSociale() { return raisonSociale; }
    public void setRaisonSociale(String v) { this.raisonSociale = v; }
    public String getFormeJuridique() { return formeJuridique; }
    public void setFormeJuridique(String v) { this.formeJuridique = v; }
    public String getCapitalSocial() { return capitalSocial; }
    public void setCapitalSocial(String v) { this.capitalSocial = v; }
    public String getSiegeSocial() { return siegeSocial; }
    public void setSiegeSocial(String v) { this.siegeSocial = v; }
    public String getVille() { return ville; }
    public void setVille(String v) { this.ville = v; }
    public String getRcNumero() { return rcNumero; }
    public void setRcNumero(String v) { this.rcNumero = v; }
    public String getRcTribunal() { return rcTribunal; }
    public void setRcTribunal(String v) { this.rcTribunal = v; }
    public String getIce() { return ice; }
    public void setIce(String v) { this.ice = v; }
    public String getIdentifiantFiscal() { return identifiantFiscal; }
    public void setIdentifiantFiscal(String v) { this.identifiantFiscal = v; }
    public String getTaxeProfessionnelle() { return taxeProfessionnelle; }
    public void setTaxeProfessionnelle(String v) { this.taxeProfessionnelle = v; }
    public String getRepresentantNom() { return representantNom; }
    public void setRepresentantNom(String v) { this.representantNom = v; }
    public String getRepresentantQualite() { return representantQualite; }
    public void setRepresentantQualite(String v) { this.representantQualite = v; }
    public BigDecimal getTauxTvaPourcent() { return tauxTvaPourcent; }
    public void setTauxTvaPourcent(BigDecimal v) { this.tauxTvaPourcent = v; }
    public Instant getModifieLe() { return modifieLe; }
    public void setModifieLe(Instant v) { this.modifieLe = v; }
    public UUID getModifiePar() { return modifiePar; }
    public void setModifiePar(UUID v) { this.modifiePar = v; }
}
