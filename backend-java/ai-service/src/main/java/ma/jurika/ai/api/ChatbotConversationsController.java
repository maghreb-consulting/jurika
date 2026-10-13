package ma.jurika.ai.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ma.jurika.ai.rag.ConversationsChatbotService;
import ma.jurika.ai.rag.ConversationsChatbotService.Proprietaire;
import ma.jurika.ai.rag.DonneePersonnelleDetecteeException;
import ma.jurika.common.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lot L5b E2 (RG-COM-12) : conversations du chatbot de l'utilisateur connecte.
 *
 * <p>Memes ayants droit que {@code /chatbot/ask} (employe, superviseur, super-admin ; pas le
 * client). Chaque route ne voit que les conversations de l'utilisateur connecte : celle d'un
 * autre utilisateur, meme du meme cabinet, repond 404. Une question portant une donnee
 * personnelle est refusee en 422 (RG-COM-04) et n'est jamais enregistree.
 */
@RestController
@RequestMapping("/api/v1/chatbot/conversations")
public class ChatbotConversationsController {

    private static final Logger log = LoggerFactory.getLogger(ChatbotConversationsController.class);

    static final String INTERROGER = "hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPERVISEUR','ROLE_SUPER_ADMIN')";

    private final ConversationsChatbotService conversations;

    public ChatbotConversationsController(ConversationsChatbotService conversations) {
        this.conversations = conversations;
    }

    /** Mes conversations, de la plus recemment active a la plus ancienne. */
    @GetMapping
    @PreAuthorize(INTERROGER)
    public List<Map<String, Object>> lister(@AuthenticationPrincipal AuthenticatedUser user,
                                            @RequestParam(value = "archivees", defaultValue = "false") boolean archivees) {
        return conversations.lister(proprietaire(user), archivees);
    }

    /** Ouvre une conversation par sa premiere question (le titre en est tire). */
    @PostMapping
    @PreAuthorize(INTERROGER)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> creer(@AuthenticationPrincipal AuthenticatedUser user,
                                     @Valid @RequestBody QuestionRequest req) {
        return conversations.creer(proprietaire(user), req.question());
    }

    /** Une de mes conversations : questions, reponses et sources citees. */
    @GetMapping("/{id}")
    @PreAuthorize(INTERROGER)
    public Map<String, Object> lire(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return conversations.lire(proprietaire(user), id);
    }

    /** Pose une question dans une de mes conversations (409 si elle est archivee). */
    @PostMapping("/{id}/questions")
    @PreAuthorize(INTERROGER)
    public Map<String, Object> poser(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                     @Valid @RequestBody QuestionRequest req) {
        return conversations.poser(proprietaire(user), id, req.question());
    }

    /** Renomme une de mes conversations. */
    @PatchMapping("/{id}")
    @PreAuthorize(INTERROGER)
    public Map<String, Object> renommer(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                        @Valid @RequestBody TitreRequest req) {
        return conversations.renommer(proprietaire(user), id, req.titre());
    }

    @PostMapping("/{id}/archiver")
    @PreAuthorize(INTERROGER)
    public Map<String, Object> archiver(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return conversations.archiver(proprietaire(user), id, true);
    }

    @PostMapping("/{id}/desarchiver")
    @PreAuthorize(INTERROGER)
    public Map<String, Object> desarchiver(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return conversations.archiver(proprietaire(user), id, false);
    }

    /** Refus RG-COM-04 : seule la nature des donnees detectees est rendue et journalisee. */
    @ExceptionHandler(DonneePersonnelleDetecteeException.class)
    public ResponseEntity<Map<String, Object>> donneePersonnelle(DonneePersonnelleDetecteeException ex) {
        log.info("Question ou titre de conversation refuse (RG-COM-04) : {}", ex.natures());
        return ResponseEntity.unprocessableEntity().body(ChatbotController.corpsRefus(ex.natures()));
    }

    private static Proprietaire proprietaire(AuthenticatedUser user) {
        if (user == null || user.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable.");
        }
        return new Proprietaire(user.workspaceId(), user.userId());
    }

    public record QuestionRequest(@NotBlank @Size(max = 1000) String question) {}

    public record TitreRequest(@NotBlank @Size(max = ConversationsChatbotService.TITRE_MAX) String titre) {}
}
