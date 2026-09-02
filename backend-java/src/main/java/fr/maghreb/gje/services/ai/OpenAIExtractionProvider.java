package fr.maghreb.gje.services.ai;

import fr.maghreb.gje.dto.document.AIExtractionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component("openAiProvider")
@RequiredArgsConstructor
@Slf4j
public class OpenAIExtractionProvider implements AIProvider {

    @Value("${openai.api.key:}")
    private String apiKey;

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty() && !apiKey.contains("votre_cle_openai_ici");
    }

    @Override
    public AIExtractionResponse extract(String base64Content) {
        if (!isAvailable()) {
            log.warn("Clé API OpenAI absente ou invalide.");
            return buildFallbackResponse();
        }

        log.info("Extraction via OpenAI GPT-4 (Placeholder - Not fully implemented yet)");
        
        // This is a placeholder structure for when OpenAI is officially re-integrated.
        // It demonstrates how OCP allows adding multiple strategies without modifying contexts.
        return buildFallbackResponse();
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
