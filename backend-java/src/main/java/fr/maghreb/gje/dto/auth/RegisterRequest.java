package fr.maghreb.gje.dto.auth;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank private String workspaceName;
    @Email @NotBlank private String email;
    @NotBlank @Size(min = 8) private String password;
    @NotBlank private String fullName;
    private String forfait = "STARTER";
}
