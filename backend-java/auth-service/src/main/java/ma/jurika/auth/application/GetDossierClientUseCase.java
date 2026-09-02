package ma.jurika.auth.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 2026-07-01 — Resout l'identite du CLIENT lie a un dossier
 * ({@code entreprise_dossiers.client_id -> users}) pour la vue Data Room
 * cote cabinet.
 *
 * <p>Avant, le retrait d'acces ({@link RemoveClientAccessUseCase}) etait une
 * action "aveugle" : aucun endpoint ne renvoyait QUI etait lie. Cet use case
 * comble ce manque afin d'afficher "Client : Prenom Nom (email)" et de rendre
 * la confirmation de retrait nominative.
 *
 * <p>RBAC (cote controller) : EMPLOYE / SUPERVISEUR du workspace courant.
 * Securite : workspace-scoped via {@code WHERE workspace_id = ?} + double
 * verification que le compte client resolu appartient bien au workspace.
 */
@Service
public class GetDossierClientUseCase {

    @PersistenceContext
    private EntityManager em;

    private final UserRepository userRepository;

    public GetDossierClientUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public record Command(UUID workspaceId, UUID dossierId) {}

    /** Identite du client lie. {@code email} = email de contact (destinataire de l'invitation). */
    public record ClientInfo(UUID userId, String firstName, String lastName,
                              String email, String status) {}

    /**
     * @return l'identite du client lie, ou {@code null} si le dossier n'a
     *         aucun client rattache.
     * @throws NotFoundException si le dossier n'existe pas dans ce workspace.
     */
    @Transactional(readOnly = true)
    public ClientInfo execute(Command cmd) {
        TenantContext.set(cmd.workspaceId());

        var rows = em.createNativeQuery(
                "SELECT client_id FROM entreprise_dossiers WHERE id = ?1 AND workspace_id = ?2")
                .setParameter(1, cmd.dossierId())
                .setParameter(2, cmd.workspaceId())
                .getResultList();
        if (rows.isEmpty()) {
            throw new NotFoundException("Dossier introuvable");
        }
        Object clientIdObj = rows.get(0);
        if (clientIdObj == null) {
            // Dossier existant mais sans client lie.
            return null;
        }
        UUID clientId = (UUID) clientIdObj;

        User u = userRepository.findById(clientId).orElse(null);
        if (u == null || !cmd.workspaceId().equals(u.workspaceId())) {
            // Defense-in-depth : compte introuvable ou hors workspace -> pas de fuite.
            return null;
        }
        return new ClientInfo(u.id(), u.firstName(), u.lastName(),
                u.contactEmail(), u.status().name());
    }
}
