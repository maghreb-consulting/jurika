package fr.maghreb.gje.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FcmTokenRequest {
    @NotBlank
    private String fcmToken;
}
