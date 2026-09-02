package fr.maghreb.gje.services;

import fr.maghreb.gje.dto.chatbot.ChatbotRequest;
import fr.maghreb.gje.dto.chatbot.ChatbotResponse;
import fr.maghreb.gje.models.*;
import fr.maghreb.gje.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatbotService {

    private final WorkspaceFaqRepository faqRepository;
    // Assume ChatbotHistoryRepository exists based on CDC mentioning the table
    // private final ChatbotHistoryRepository historyRepository;

    public ChatbotResponse askQuestion(ChatbotRequest request, UUID userId, UUID workspaceId) {
        log.info("Question chatbot reçue du workspace {}: {}", workspaceId, request.getQuestion());
        
        // TODO: Appel service RAG (Node.js ou Python) pour obtenir réponse + sources
        
        return ChatbotResponse.builder()
                .answer("Réponse simulée du chatbot GJE basée sur le CDC.")
                .sources(List.of("CDC Page 14", "Statuts SARL"))
                .build();
    }

    public List<Object> getHistory(UUID workspaceId) {
        log.info("Récupération de l'historique chatbot pour le workspace {}", workspaceId);
        // TODO: historyRepository.findTop20ByWorkspaceIdOrderByTimestampDesc(workspaceId);
        return Collections.emptyList();
    }

    @Transactional
    public void publishToFaq(UUID questionId, UUID userId, UUID workspaceId) {
        log.info("Publication d'une question dans la FAQ par l'utilisateur {}", userId);
        // TODO: Récupérer depuis historique, créer WorkspaceFaq
    }

    public List<WorkspaceFaq> getFaq(UUID workspaceId) {
        return faqRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
    }
}
