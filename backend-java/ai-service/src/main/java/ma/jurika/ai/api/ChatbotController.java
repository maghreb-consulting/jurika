package ma.jurika.ai.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ma.jurika.ai.rag.DonneePersonnelleDetecteeException;
import ma.jurika.ai.rag.FiltreDonneesPersonnelles;
import ma.jurika.ai.rag.RagService;
import ma.jurika.ai.rag.SourceIngestionService;
import ma.jurika.common.audit.Auditable;
import ma.jurika.common.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/chatbot")
public class ChatbotController {

    private static final Logger log = LoggerFactory.getLogger(ChatbotController.class);

    /**
     * Lot L5 E8 (RG-COM-01, CDC 3.2 et 3.3) : le chatbot est une aide pour les utilisateurs du
     * cabinet (employe, superviseur) ; le super-admin l'interroge pour eprouver les sources qu'il
     * depose. Le client n'y a pas acces.
     */
    private static final String INTERROGER =
            "hasAnyAuthority('ROLE_EMPLOYE','ROLE_SUPERVISEUR','ROLE_SUPER_ADMIN')";

    /**
     * Lot L0 (E24, CDC 3.1, RG-ADM-03) : AJOUT et RETRAIT des sources reserves au super-admin
     * (JURIKA "depose et gere les sources fiables du chatbot"). Lot L5 E8 : ces sources forment
     * le corpus commun, lu par tous les cabinets ; leur liste n'est servie qu'au super-admin.
     */
    private static final String ADMIN_SOURCES = "hasAuthority('ROLE_SUPER_ADMIN')";

    private final RagService rag;
    private final SourceIngestionService ingestion;

    public ChatbotController(RagService rag, SourceIngestionService ingestion) {
        this.rag = rag;
        this.ingestion = ingestion;
    }

    /**
     * RG-COM-03 / RG-COM-04 : la question est analysee AVANT tout traitement. Si elle contient
     * une donnee personnelle reconnaissable, refus 422 avec la nature de la donnee ; la question
     * n'est ni transmise au modele ni journalisee.
     */
    @PostMapping("/ask")
    @PreAuthorize(INTERROGER)
    public Map<String, Object> ask(@Valid @RequestBody AskRequest req) {
        List<FiltreDonneesPersonnelles.Nature> natures = FiltreDonneesPersonnelles.detecter(req.question());
        if (!natures.isEmpty()) {
            throw new DonneePersonnelleDetecteeException(natures);
        }
        return rag.ask(req.question());
    }

    /** Refus RG-COM-04 : seule la nature des donnees detectees est rendue et journalisee. */
    @ExceptionHandler(DonneePersonnelleDetecteeException.class)
    public ResponseEntity<Map<String, Object>> donneePersonnelle(DonneePersonnelleDetecteeException ex) {
        log.info("Question au chatbot refusee (RG-COM-04) : {}", ex.natures());
        return ResponseEntity.unprocessableEntity().body(corpsRefus(ex.natures()));
    }

    static Map<String, Object> corpsRefus(List<FiltreDonneesPersonnelles.Nature> natures) {
        List<Map<String, String>> detail = natures.stream()
                .map(n -> Map.of("nature", n.name(), "libelle", n.libelle()))
                .toList();
        String liste = String.join(", ", natures.stream().map(FiltreDonneesPersonnelles.Nature::libelle).toList());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 422);
        body.put("code", "DONNEE_PERSONNELLE");
        body.put("message", "Votre question contient une donnée personnelle (" + liste + ") : elle n'a pas "
                + "été envoyée. Reformulez-la sans cette donnée : le chatbot n'a besoin d'aucune donnée "
                + "de client pour répondre.");
        body.put("donnees", detail);
        return body;
    }

    /**
     * Ingere une source commune : soit un fichier (pdf/docx/txt) via {@code file}, soit
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

        UUID uploadedBy = utilisateur(user);

        SourceIngestionService.IngestResult result;
        try {
            if (file != null && !file.isEmpty()) {
                result = ingestion.ingestFile(uploadedBy,
                        file.getBytes(), file.getOriginalFilename(), file.getContentType());
            } else if (text != null && !text.isBlank()) {
                result = ingestion.ingestText(uploadedBy, title, text);
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

    /** Liste les sources communes (1 entree par document). */
    @GetMapping("/sources")
    @PreAuthorize(ADMIN_SOURCES)
    public List<Map<String, Object>> listSources() {
        return ingestion.listSources();
    }

    /** Retire une source commune (tous ses passages). */
    @DeleteMapping("/sources/{docId}")
    @PreAuthorize(ADMIN_SOURCES)
    @Auditable(action = "CHATBOT_SOURCE_DELETED", resourceType = "RAG_SOURCE")
    public Map<String, Object> deleteSource(@PathVariable UUID docId) {
        int deleted = ingestion.deleteSource(docId);
        if (deleted == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Source introuvable.");
        }
        return Map.of("deleted", deleted);
    }

    private UUID utilisateur(AuthenticatedUser user) {
        if (user == null || user.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur introuvable.");
        }
        return user.userId();
    }

    public record AskRequest(@NotBlank @Size(max = 1000) String question) {}
}
