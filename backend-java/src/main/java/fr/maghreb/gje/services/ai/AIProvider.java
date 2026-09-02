package fr.maghreb.gje.services.ai;

import fr.maghreb.gje.dto.document.AIExtractionResponse;

public interface AIProvider {
    /**
     * Extracts information from a document image base64.
     * @param base64Content the image payload.
     * @return structured extraction response.
     */
    AIExtractionResponse extract(String base64Content);
    
    /**
     * Indicates if this provider is configured and available.
     */
    boolean isAvailable();
}
