package fr.maghreb.gje.services;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import fr.maghreb.gje.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FCMService {

    private final UserRepository userRepository;
    private boolean firebaseEnabled = false;

    @PostConstruct
    public void initialize() {
        if (!FirebaseApp.getApps().isEmpty()) {
            firebaseEnabled = true;
            return;
        }

        // 1) Try loading from a local file first
        Path serviceAccountPath = Paths.get("firebase-service-account.json");
        if (Files.exists(serviceAccountPath)) {
            try (InputStream is = new FileInputStream(serviceAccountPath.toFile())) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(is))
                        .build();
                FirebaseApp.initializeApp(options);
                firebaseEnabled = true;
                log.info("Firebase initialisé depuis firebase-service-account.json ✓");
                return;
            } catch (IOException e) {
                log.warn("Fichier firebase-service-account.json trouvé mais illisible: {}", e.getMessage());
            }
        }

        // 2) Fallback: try Application Default Credentials (GCP env)
        try {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .build();
            FirebaseApp.initializeApp(options);
            firebaseEnabled = true;
            log.info("Firebase initialisé via Application Default Credentials ✓");
        } catch (IOException e) {
            firebaseEnabled = false;
            log.warn("Firebase NON configuré — notifications push désactivées. " +
                    "Pour activer FCM, placez firebase-service-account.json à la racine du projet.");
        }
    }

    public boolean isEnabled() {
        return firebaseEnabled;
    }

    public void sendPushNotification(UUID userId, String title, String body, Map<String, String> data) {
        if (!firebaseEnabled) {
            log.debug("FCM désactivé — notification push ignorée pour userId={}", userId);
            return;
        }
        userRepository.findById(userId).ifPresent(user -> {
            String token = user.getFcmToken();
            if (token != null && !token.isEmpty()) {
                sendToToken(token, title, body, data);
            } else {
                log.debug("Aucun token FCM pour l'utilisateur {}", userId);
            }
        });
    }

    private void sendToToken(String token, String title, String body, Map<String, String> data) {
        try {
            Message message = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putAllData(data)
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("Push envoyée ✓ : {}", response);
        } catch (FirebaseMessagingException e) {
            log.error("Erreur envoi push: {}", e.getMessage());
        }
    }
}
