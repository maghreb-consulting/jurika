package fr.maghreb.gje.dto.admin;
import jakarta.validation.constraints.*;
import lombok.Data;
@Data
public class CreateWorkspaceRequest {
    @NotBlank private String name;
    @Email @NotBlank private String emailSuperviseur;
    @NotBlank private String fullNameSuperviseur;
    private String forfait = "STARTER";
    private Integer dureeMois = 12;
    private Integer maxUsers;
    private java.math.BigDecimal storageLimitGb;
}
