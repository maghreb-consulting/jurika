package fr.maghreb.gje.dto.auth;
import lombok.*;

@Data @Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tempToken;
    private Boolean requires2fa;
    private Boolean requires2faSetup;
    private String message;
    private String userId;
    private String workspaceId;
    private String email;
    private String fullName;
    private String role;
    private String qrCode;
    private Long expiresIn;
}
