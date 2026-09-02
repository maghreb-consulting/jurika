package fr.maghreb.gje.dto.document;

import lombok.*;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIExtractionResponse {
    
    private String denomination;
    private String formeJuridique;
    private Double capitalSocial;
    private String siegeSocial;
    private String activitePrincipale;
    private String ice;
    private String numeroRC;
    private List<GerantDTO> gerants;
    private List<AssocieDTO> associes;
    private String dateConstitution;
    
    private Map<String, Double> confidenceScores;
    private Map<String, ExtractionStatus> statuses;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GerantDTO {
        private String nomPrenom;
        private String cin;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssocieDTO {
        private String nomPrenom;
        private Double parts;
    }

    public enum ExtractionStatus {
        CONFIRMED, // Green (score >= 0.85)
        TO_VERIFY, // Orange (0.50 - 0.84)
        TO_FILL    // Red (< 0.50)
    }
}
