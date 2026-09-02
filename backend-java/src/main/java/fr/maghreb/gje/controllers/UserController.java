package fr.maghreb.gje.controllers;

import fr.maghreb.gje.dto.auth.FcmTokenRequest;
import fr.maghreb.gje.models.User;
import fr.maghreb.gje.repositories.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Utilisateurs", description = "Gestion des profils et tokens (Module 16)")
public class UserController {

    private final UserRepository userRepository;

    @PatchMapping("/fcm-token")
    @Operation(summary = "Enregistrer le token FCM de l'appareil mobile")
    public ResponseEntity<Void> updateFcmToken(
            @RequestBody FcmTokenRequest request,
            @AuthenticationPrincipal User user) {
        
        user.setFcmToken(request.getFcmToken());
        userRepository.save(user);
        
        return ResponseEntity.ok().build();
    }
}
