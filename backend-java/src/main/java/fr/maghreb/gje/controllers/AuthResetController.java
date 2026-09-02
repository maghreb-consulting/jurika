package fr.maghreb.gje.controllers;

import fr.maghreb.gje.models.User;
import fr.maghreb.gje.services.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthResetController {

    private final AuthService authService;

    @PostMapping("/reset-2fa/{targetUserId}")
    @PreAuthorize("hasAnyRole('SUPERVISEUR', 'SUPER_ADMIN')")
    public ResponseEntity<?> reset2FA(
            @PathVariable UUID targetUserId,
            @AuthenticationPrincipal User requestingUser,
            HttpServletRequest request) {

        String ipAddress = request.getRemoteAddr();
        
        try {
            Map<String, Object> result = authService.reset2FA(targetUserId, requestingUser, ipAddress);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("FORBIDDEN:")) {
                return ResponseEntity.status(403).body(Map.of("error", e.getMessage().replace("FORBIDDEN:", "")));
            }
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }
}
