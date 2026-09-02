package fr.maghreb.gje.controllers;

import fr.maghreb.gje.dto.document.AIExtractionRequest;
import fr.maghreb.gje.dto.document.AIExtractionResponse;
import fr.maghreb.gje.services.AIExtractionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/documents/import")
@RequiredArgsConstructor
public class AIExtractionController {

    private final AIExtractionService aiExtractionService;

    @PostMapping("/extract")
    public ResponseEntity<AIExtractionResponse> extract(
            @Valid @RequestBody AIExtractionRequest request) {
        
        return ResponseEntity.ok(aiExtractionService.extract(request.getFileContent()));
    }
}
