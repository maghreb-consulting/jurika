package fr.maghreb.gje.dto.fiche;

import fr.maghreb.gje.models.FicheJuridique.FormeJuridique;
import fr.maghreb.gje.models.FicheJuridique.StatutJuridique;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class UpdateFicheJuridiqueRequest {
    // Section 1
    private String ice;
    private String numeroRC;
    private LocalDate dateCreation;
    private String abreviation;
    private BigDecimal capitalSocial;
    private FormeJuridique formeJuridique;
    private StatutJuridique statutJuridique;

    // Section 2
    private String activitePrincipale;
    private Boolean activiteReglementee;
    private String autorisationReglementee;
    private String codeNaf;

    // Section 3
    private String adresseSiege;
    private String activiteAuSiege;
    private String enseigne;
    private List<EtablissementSecondaireDTO> etablissementsSecondaires;

    // Section 4
    private List<RepresentantDTO> representants;
}
