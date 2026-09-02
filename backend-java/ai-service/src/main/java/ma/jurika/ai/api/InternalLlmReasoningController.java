package ma.jurika.ai.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import ma.jurika.ai.rag.RagChatClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Lot IA-1 (2026-07-10) — Endpoint INTERNE de « raisonnement » LLM texte libre.
 *
 * <p>Découple le raisonnement (résumé / priorisation à partir d'un état fourni)
 * de la RAG documentaire : ici <b>pas de retrieval</b>, on ne fait que réutiliser
 * {@link RagChatClient#complete(String, String)} (API OpenAI-compatible, provider
 * Groq/Gemini selon la configuration) pour transformer un état sérialisé en texte.
 *
 * <p>Consommé par dashboard-service ({@code AiReasoningClient}) pour l'agent
 * copilote de l'employé (briefing du jour).
 *
 * <p><b>Sécurité</b> : {@code /internal/**} — permitAll dans {@code SecurityConfig}
 * mais <b>non routé par la gateway</b> (seuls {@code /api/v1/ai/**} et
 * {@code /api/v1/chatbot/**} sont exposés). Atteignable uniquement depuis le
 * réseau interne docker, comme les autres endpoints {@code /internal}.
 *
 * <p><b>Dégradation</b> : si le LLM est indisponible (flag off, clé absente,
 * timeout, erreur réseau/HTTP), on renvoie <b>200</b> avec {@code text:null}
 * (jamais 500) — l'appelant retombe alors sur son briefing à base de règles.
 */
@RestController
public class InternalLlmReasoningController {

    private static final Logger log = LoggerFactory.getLogger(InternalLlmReasoningController.class);

    private final RagChatClient chatClient;

    public InternalLlmReasoningController(RagChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Produit un texte à partir d'un prompt système + un prompt utilisateur.
     *
     * @param req {@code systemPrompt} (rôle/ton) + {@code userPrompt} (état + consigne)
     * @return {@code {text}} — {@code text} = null si LLM indisponible (200, jamais 500)
     */
    @PostMapping(value = "/internal/llm/reason",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LlmReasonResponse> reason(@Valid @RequestBody LlmReasonRequest req) {
        try {
            Optional<String> text = chatClient.complete(req.systemPrompt(), req.userPrompt());
            return ResponseEntity.ok(new LlmReasonResponse(text.orElse(null)));
        } catch (RuntimeException ex) {
            // Filet de sécurité : RagChatClient ne jette pas, mais on garantit le
            // contrat « jamais 500 » quoi qu'il arrive.
            log.warn("/internal/llm/reason : erreur inattendue — dégradation text:null : {}", ex.getMessage());
            return ResponseEntity.ok(new LlmReasonResponse(null));
        }
    }
}

/** Corps de requête : prompt système (optionnel) + prompt utilisateur (requis). */
record LlmReasonRequest(String systemPrompt, @NotBlank String userPrompt) {}

/** Réponse : texte généré, ou {@code null} si LLM indisponible. */
record LlmReasonResponse(String text) {}
