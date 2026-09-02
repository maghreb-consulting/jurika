package ma.jurika.auth.api;

import ma.jurika.auth.application.WorkspaceLogoService;
import ma.jurika.common.security.AuthenticatedUser;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;

/**
 * Papier à en-tête V31 (2026-07-14) — logo du cabinet (workspace courant).
 *
 * <p>POST / DELETE : SUPERVISEUR uniquement (403 pour EMPLOYE / CLIENT), audité.
 * GET : tout membre authentifié (aperçu dans les paramètres). Isolation : le
 * workspace est toujours celui du token (jamais fourni par le client).
 */
@RestController
@RequestMapping("/api/v1/workspace/profile/logo")
public class WorkspaceLogoController {

    private final WorkspaceLogoService service;

    public WorkspaceLogoController(WorkspaceLogoService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_SUPERVISEUR')")
    public ResponseEntity<Void> upload(@AuthenticationPrincipal AuthenticatedUser user,
                                       @RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        service.upload(user.workspaceId(), file.getBytes(), file.getContentType(),
                file.getOriginalFilename());
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> get(@AuthenticationPrincipal AuthenticatedUser user) {
        Optional<WorkspaceLogoService.Logo> logo = service.get(user.workspaceId());
        if (logo.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        WorkspaceLogoService.Logo l = logo.get();
        String ct = (l.contentType() == null || l.contentType().isBlank())
                ? MediaType.IMAGE_PNG_VALUE : l.contentType();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(ct))
                .body(l.bytes());
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('ROLE_SUPERVISEUR')")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user) {
        service.delete(user.workspaceId());
        return ResponseEntity.noContent().build();
    }
}
