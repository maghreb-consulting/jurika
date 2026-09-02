package fr.maghreb.gje.controllers;

import fr.maghreb.gje.dto.auth.*;
import fr.maghreb.gje.security.JwtService;
import fr.maghreb.gje.services.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    @GetMapping("/workspaces/verify")
    public ResponseEntity<Map<String, Object>> verifyWorkspace(
            @RequestParam String code) {
        Map<String, Object> result = authService.verifyWorkspace(code);
        boolean valid = (boolean) result.get("valid");
        return ResponseEntity
            .status(valid ? HttpStatus.OK : HttpStatus.NOT_FOUND)
            .body(result);
    }

    @PostMapping("/auth/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(authService.register(request));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            String message = e.getMessage();
            if (message.startsWith("LOCKED:")) {
                return ResponseEntity.status(423)
                    .body(Map.of(
                        "error", "Compte verrouillé 15 minutes",
                        "locked_until", message.replace("LOCKED:", "")
                    ));
            }
            if (message.startsWith("INVALID_PASSWORD:")) {
                int remaining = Integer.parseInt(
                    message.replace("INVALID_PASSWORD:", ""));
                return ResponseEntity.status(401)
                    .body(Map.of(
                        "error", "Identifiants incorrects",
                        "attempts_remaining", remaining
                    ));
            }
            return ResponseEntity.status(401)
                .body(Map.of("error", message));
        }
    }

    @PostMapping("/auth/setup-2fa")
    public ResponseEntity<Map<String, String>> setup2FA(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String token = authHeader.substring(7);
        String userId = jwtService.extractUserId(token);
        String workspaceId = jwtService.extractWorkspaceId(token);
        
        return ResponseEntity.ok(
            authService.setup2FA(userId, workspaceId));
    }

    @PostMapping("/auth/verify-2fa")
    public ResponseEntity<?> verify2FA(
            @Valid @RequestBody VerifyTotpRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        if (request.getTempToken() == null && authHeader != null && authHeader.startsWith("Bearer ")) {
            request.setTempToken(authHeader.substring(7));
        }
        
        try {
            return ResponseEntity.ok(authService.verify2FA(request));
        } catch (RuntimeException e) {
            String message = e.getMessage();
            if (message != null && message.startsWith("MAX_OTP_ATTEMPTS:")) {
                return ResponseEntity.status(429)
                    .body(Map.of(
                        "error", "otp_max_attempts",
                        "message", message.replace("MAX_OTP_ATTEMPTS:", ""),
                        "requires_login", true
                    ));
            }
            return ResponseEntity.status(401)
                .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<?> refreshToken(
            @RequestHeader("Authorization") String authHeader) {
        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return ResponseEntity.status(401).body(Map.of("error", "Token manquant"));
            }
            String refreshToken = authHeader.substring(7);
            return ResponseEntity.ok(authService.refreshToken(refreshToken));
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String token = authHeader.substring(7);
                String userId = jwtService.extractUserId(token);
                authService.logout(userId);
            } catch (Exception e) {
                // Ignore token extraction failures on logout
            }
        }
        return ResponseEntity.noContent().build();
    }
}
