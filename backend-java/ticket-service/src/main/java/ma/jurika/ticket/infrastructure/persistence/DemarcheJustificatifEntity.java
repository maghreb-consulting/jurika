package ma.jurika.ticket.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** Justificatif attendu par une demarche du referentiel (V20). Lecture seule. */
@Entity
@Table(name = "demarches_justificatifs")
public class DemarcheJustificatifEntity {

    @Id
    private UUID id;
    @Column(name = "demarche_id", insertable = false, updatable = false)
    private UUID demarcheId;
    @Column(name = "alternative_groupe", insertable = false, updatable = false)
    private Short alternativeGroupe;
    @Column(name = "document_type", insertable = false, updatable = false)
    private String documentType;
    @Column(insertable = false, updatable = false)
    private String libelle;

    public UUID getId() { return id; }
    public UUID getDemarcheId() { return demarcheId; }
    public Short getAlternativeGroupe() { return alternativeGroupe; }
    public String getDocumentType() { return documentType; }
    public String getLibelle() { return libelle; }
}
