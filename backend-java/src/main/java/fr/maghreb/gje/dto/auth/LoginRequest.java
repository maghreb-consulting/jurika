package fr.maghreb.gje.dto.auth;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class LoginRequest {
    private String workspaceId;
    private String workspaceCode;
    @Email @NotBlank private String email;
    @NotBlank private String password;
}
