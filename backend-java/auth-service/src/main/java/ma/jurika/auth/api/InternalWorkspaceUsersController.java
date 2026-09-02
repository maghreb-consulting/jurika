package ma.jurika.auth.api;

import ma.jurika.auth.application.ListWorkspaceUsersUseCase;
import ma.jurika.common.exception.NotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 2026-06-30 — endpoint INTERNAL de résolution du rôle d'un membre interne du
 * workspace, pour les services qui ne possèdent pas la table {@code users}.
 *
 * <p>Consommé par {@code ticket-service} (DossierTransferService) pour valider,
 * en défense en profondeur, qu'une cible de transfert de dossier est bien un
 * EMPLOYE (un SUPERVISEUR est oversight-only et ne peut pas recevoir de
 * dossier — cf. G1).
 *
 * <p>Sécurité : path {@code /internal/**} — le gateway rejette les requêtes
 * externes vers ce préfixe (même convention que {@link InternalWorkspaceStatusController}).
 *
 * <p>Portée : ne résout que les membres INTERNES (EMPLOYE + SUPERVISEUR), même
 * source que {@code /auth/users/contacts}. Un userId inconnu de cette projection
 * (CLIENT, SUPER_ADMIN, ou id inexistant) renvoie 404 → le caller traite cela
 * comme « non transférable ».
 */
@RestController
@RequestMapping("/internal/workspaces")
public class InternalWorkspaceUsersController {

    private final ListWorkspaceUsersUseCase listWorkspaceUsersUseCase;

    public InternalWorkspaceUsersController(ListWorkspaceUsersUseCase listWorkspaceUsersUseCase) {
        this.listWorkspaceUsersUseCase = listWorkspaceUsersUseCase;
    }

    /** Rôle + statut d'un membre interne (mirror minimal pour Feign). */
    public record MemberRoleDto(UUID userId, String role, String status) {}

    @GetMapping("/{workspaceId}/users/{userId}/role")
    public ResponseEntity<MemberRoleDto> getUserRole(@PathVariable UUID workspaceId,
                                                     @PathVariable UUID userId) {
        return listWorkspaceUsersUseCase.execute(workspaceId).stream()
                .filter(m -> m.userId().equals(userId))
                .findFirst()
                .map(m -> new MemberRoleDto(m.userId(), m.role().name(), m.status().name()))
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new NotFoundException(
                        "Membre interne introuvable dans ce workspace: " + userId));
    }
}
