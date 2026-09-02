package fr.maghreb.gje.services;

import fr.maghreb.gje.dto.document.AIExtractionResponse;
import fr.maghreb.gje.services.ai.AIProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class AIExtractionService {

    private final AIProvider aiProvider;

    @Autowired
    public AIExtractionService(@Qualifier("geminiProvider") AIProvider aiProvider) {
        this.aiProvider = aiProvider;
    }

    public AIExtractionResponse extract(String base64Content) {
        log.info("Lancement de l'extraction IA via le provider configuré (Strategy Pattern)");
        
        if (aiProvider == null || !aiProvider.isAvailable()) {
            log.warn("Aucun AIProvider disponible. Utilisation du fallback statique (OCP Respecté).");
            return buildFallbackResponse();
        }
        
        return aiProvider.extract(base64Content);
    }

    private AIExtractionResponse buildFallbackResponse() {
        return AIExtractionResponse.builder()
            .statuses(new HashMap<>(Map.of(
                "denomination", AIExtractionResponse.ExtractionStatus.TO_FILL,
                "formeJuridique", AIExtractionResponse.ExtractionStatus.TO_FILL,
                "capitalSocial", AIExtractionResponse.ExtractionStatus.TO_FILL,
                "ice", AIExtractionResponse.ExtractionStatus.TO_FILL
            )))
            .confidenceScores(new HashMap<>())
            .build();
    }
}

