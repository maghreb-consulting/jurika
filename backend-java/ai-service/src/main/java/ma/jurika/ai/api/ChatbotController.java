package ma.jurika.ai.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ma.jurika.ai.rag.RagService;
import ma.jurika.ai.rag.SourceIngestionService;
import ma.jurika.common.audit.Auditable;
import ma.jurika.common.security.AuthenticatedUser;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chatbot")
public class ChatbotController {

    private final RagService rag;
    private final SourceIngestionService ingestion;

    public ChatbotController(RagService rag, SourceIngestionService ingestion) {
        this.rag = rag;
        this.ingestion = ingestion;
    }

    @PostMapping("/ask")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> ask(@AuthenticationPrincipal AuthenticatedUser user,
                                   @Valid @RequestBody AskRequest req) {
        return rag.ask(req.question(), workspaceOf(user));
    }

    // ----------------------------------------------------------------------
    // Gestion des sources fiables alimentant le RAG. Reservee au cabinet
    // (EMPLOYE / SUPERVISEUR / SUPER_ADMIN) : le CLIENT ne doit PAS gerer le
    // corpus RAG (il garde seulement /ask pour interroger). Le scoping
    // multi-tenant est assure par le workspace du principal a chaque appel.
    // Ces @PreAuthorize sont effectifs depuis l'activation de
    // @EnableMethodSecurity dans SecurityConfig (2026-07-25).
    // ----------------------------------------------------------------------
    private static final String MANAGE_SOURCES =
            "hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPERVISEUR','ROLE_SUPER_ADMIN')";

    /**
     * Lot L0 (E24, CDC § 3.1, RG-ADM-03) : AJOUT et RETRAIT des sources reserves
     * au super-admin (JURIKA « depose et gere les sources fiables du chatbot »).
     * La liste reste lisible par le cabinet.
     */
    private static final String ADMIN_SOURCES = "hasAuthority('ROLE_SUPER_ADMIN')";

    /**
     * Ingere une source : soit un fichier (pdf/docx/txt) via {@code file}, soit
     * du texte colle via {@code text} (+ {@code title} optionnel).
     */
    @PostMapping(value = "/sources", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(ADMIN_SOURCES)
    @Auditable(action = "CHATBOT_SOURCE_ADDED", resourceType = "RAG_SOURCE")
    public Map<String, Object> addSource(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "text", required = false) String text,
            @RequestParam(value = "title", required = false) String title) {

        UUID workspaceId = workspaceOf(user);
        UUID uploadedBy = user.userId();

        SourceIngestionService.IngestResult result;
        try {
            if (file != null && !file.isEmpty()) {
                result = ingestion.ingestFile(workspaceId, uploadedBy,
                        file.getBytes(), file.getOriginalFilename(), file.getContentType());
            } else if (text != null && !text.isBlank()) {
                result = ingestion.ingestText(workspaceId, uploadedBy, title, text);
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Fournir un fichier (file) ou du texte (text).");
            }
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Lecture du fichier impossible : " + ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        }

        return Map.of(
                "docId", result.docId().toString(),
                "source", result.source(),
                "chunks", result.chunks());
    }

    /** Liste les sources du workspace courant (1 entree par document). */
    @GetMapping("/sources")
    @PreAuthorize(MANAGE_SOURCES)
    public List<Map<String, Object>> listSources(@AuthenticationPrincipal AuthenticatedUser user) {
        return ingestion.listSources(workspaceOf(user));
    }

    /** Supprime un document source (tous ses chunks) du workspace courant. */
    @DeleteMapping("/sources/{docId}")
    @PreAuthorize(ADMIN_SOURCES)
    @Auditable(action = "CHATBOT_SOURCE_DELETED", resourceType = "RAG_SOURCE")
    public Map<String, Object> deleteSource(@AuthenticationPrincipal AuthenticatedUser user,
                                            @PathVariable UUID docId) {
        int deleted = ingestion.deleteSource(workspaceOf(user), docId);
        if (deleted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Source introuvable.");
        }
        return Map.of("deleted", deleted);
    }

    private UUID workspaceOf(AuthenticatedUser user) {
        if (user == null || user.workspaceId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Workspace introuvable.");
        }
        return user.workspaceId();
    }

    public record AskRequest(@NotBlank @Size(max = 1000) String question) {}
}
