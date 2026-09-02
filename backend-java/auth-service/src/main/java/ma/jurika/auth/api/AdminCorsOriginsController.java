package ma.jurika.auth.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import ma.jurika.auth.domain.port.AllowedOriginRepository;
import ma.jurika.auth.domain.port.AllowedOriginRepository.AllowedOrigin;
import ma.jurika.common.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Admin endpoints for managing the CORS allowlist (RG-SAAS-01/02).
 *
 * <p>{@code GET /api/v1/admin/cors-origins} returns the flat de-duplicated list and is
 * polled by the gateway every 5 minutes (cf. {@code DynamicCorsConfigurationSource}).
 * Restricted to {@code SUPER_ADMIN} only.
 */
@RestController
public class AdminCorsOriginsController {

    private final AllowedOriginRepository repository;

    public AdminCorsOriginsController(AllowedOriginRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/api/v1/admin/cors-origins")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<String> listAll() {
        return repository.findAllDistinct();
    }

    @GetMapping("/api/v1/admin/workspaces/{workspaceId}/allowed-origins")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<AllowedOriginResponse> listForWorkspace(@PathVariable UUID workspaceId) {
        return repository.findByWorkspace(workspaceId).stream()
                .map(AllowedOriginResponse::from)
                .toList();
    }

    @PostMapping("/api/v1/admin/workspaces/{workspaceId}/allowed-origins")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<AllowedOriginResponse> add(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody AddOriginRequest body,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        AllowedOrigin saved = repository.add(workspaceId, body.origin(),
                principal == null ? null : principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(AllowedOriginResponse.from(saved));
    }

    @DeleteMapping("/api/v1/admin/workspaces/{workspaceId}/allowed-origins/{originId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID workspaceId, @PathVariable UUID originId) {
        boolean deleted = repository.delete(workspaceId, originId);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    public record AddOriginRequest(
            @NotBlank
            @Pattern(regexp = "^https?://[A-Za-z0-9.-]+(:\\d{1,5})?$",
                     message = "Origine invalide : format attendu http(s)://host[:port]")
            String origin) {}

    public record AllowedOriginResponse(UUID id, UUID workspaceId, String origin) {
        static AllowedOriginResponse from(AllowedOrigin o) {
            return new AllowedOriginResponse(o.id(), o.workspaceId(), o.origin());
        }
    }
}
