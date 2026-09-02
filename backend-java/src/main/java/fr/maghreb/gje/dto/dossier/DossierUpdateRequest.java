package fr.maghreb.gje.dto.dossier;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
@Data
public class DossierUpdateRequest {
    private String denomination;
    private String ice;
    private String rcNumber;
    private BigDecimal capitalSocial;
    private String siegeSocial;
    private String gerant;
    private String objetSocial;
    private LocalDate dateEcheance;
    private String statut;
}
