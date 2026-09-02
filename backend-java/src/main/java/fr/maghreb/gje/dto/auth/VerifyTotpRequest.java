package fr.maghreb.gje.dto.auth;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class VerifyTotpRequest {
    private String tempToken;
    @NotBlank @Size(min=6, max=6) private String otpCode;
}
