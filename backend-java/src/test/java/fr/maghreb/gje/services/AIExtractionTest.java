package fr.maghreb.gje.services;

import fr.maghreb.gje.dto.document.AIExtractionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AIExtractionTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private AIExtractionService aiExtractionService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(aiExtractionService, "apiKey", "test-key");
        ReflectionTestUtils.setField(aiExtractionService, "restTemplate", restTemplate);
    }

    @Test
    void testExtract_SuccessMapping() {
        // Given
        String mockJsonResponse = "{\"choices\":[{\"message\":{\"content\":\"{" +
            "\\\"denomination\\\": \\\"Maghreb Tech\\\", \\\"formeJuridique\\\": \\\"SARL\\\", " +
            "\\\"confidence_scores\\\": { \\\"denomination\\\": 0.95, \\\"formeJuridique\\\": 0.70, \\\"ice\\\": 0.40 }" +
            "}\"}}]}";

        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
            .thenReturn(new ResponseEntity<>(mockJsonResponse, HttpStatus.OK));

        // When
        AIExtractionResponse result = aiExtractionService.extract("base64data");

        // Then
        assertEquals("Maghreb Tech", result.getDenomination());
        assertEquals("SARL", result.getFormeJuridique());
        
        // Check Statuses
        assertEquals(AIExtractionResponse.ExtractionStatus.CONFIRMED, result.getStatuses().get("denomination")); // 0.95
        assertEquals(AIExtractionResponse.ExtractionStatus.TO_VERIFY, result.getStatuses().get("formeJuridique")); // 0.70
        assertEquals(AIExtractionResponse.ExtractionStatus.TO_FILL, result.getStatuses().get("ice")); // 0.40
    }

    @Test
    void testExtract_FallbackOnTimeoutOrError() {
        // Given
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
            .thenThrow(new RuntimeException("Timeout"));

        // When
        AIExtractionResponse result = aiExtractionService.extract("base64data");

        // Then
        assertNotNull(result);
        assertEquals(AIExtractionResponse.ExtractionStatus.TO_FILL, result.getStatuses().get("denomination"));
        assertNull(result.getDenomination());
    }
}
