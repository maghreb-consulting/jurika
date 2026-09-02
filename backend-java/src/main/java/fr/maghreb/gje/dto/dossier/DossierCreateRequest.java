package fr.maghreb.gje.dto.dossier;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
@Data
public class DossierCreateRequest {
    @NotBlank private String denomination;
    @NotBlank private String formeJuridique;
    private String ice;
    private String rcNumber;
    private BigDecimal capitalSocial;
    private String siegeSocial;
    private String gerant;
    private String objetSocial;
    private LocalDate dateEcheance;
    private String source;
}
