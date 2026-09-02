package fr.maghreb.gje.controllers;

import fr.maghreb.gje.dto.chatbot.ChatbotRequest;
import fr.maghreb.gje.dto.chatbot.ChatbotResponse;
import fr.maghreb.gje.models.WorkspaceFaq;
import fr.maghreb.gje.models.User;
import fr.maghreb.gje.services.ChatbotService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chatbot")
@RequiredArgsConstructor
@Tag(name = "Chatbot RAG", description = "Module 14 - Assistance IA et FAQ")
public class ChatbotController {

    private final ChatbotService chatbotService;

    @PostMapping("/question")
    @Operation(summary = "Poser une question au chatbot RAG")
    public ResponseEntity<ChatbotResponse> askQuestion(
            @RequestBody ChatbotRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(chatbotService.askQuestion(request, user.getId(), user.getWorkspaceId()));
    }

    @GetMapping("/historique")
    @Operation(summary = "Récupérer l'historique des 20 dernières conversations")
    public ResponseEntity<List<Object>> getHistory(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(chatbotService.getHistory(user.getWorkspaceId()));
    }

    @PostMapping("/faq/{questionId}/publier")
    @Operation(summary = "Publier une réponse du chatbot dans la FAQ du workspace")
    @PreAuthorize("hasRole('SUPERVISEUR')")
    public ResponseEntity<Void> publishToFaq(
            @PathVariable UUID questionId,
            @AuthenticationPrincipal User user) {
        chatbotService.publishToFaq(questionId, user.getId(), user.getWorkspaceId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/faq")
    @Operation(summary = "Récupérer la FAQ publique du workspace")
    public ResponseEntity<List<WorkspaceFaq>> getFaq(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(chatbotService.getFaq(user.getWorkspaceId()));
    }
}
