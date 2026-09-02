package fr.maghreb.gje.dto.chatbot;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotResponse {
    private String answer;
    private List<String> sources;
}
