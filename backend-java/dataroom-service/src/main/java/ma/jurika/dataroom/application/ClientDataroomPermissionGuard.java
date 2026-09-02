package ma.jurika.dataroom.application;

import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.Role;
import ma.jurika.dataroom.infrastructure.persistence.SettingsEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Helper commun (defense en profondeur) qui applique les permissions du
 * Data Room au ROLE CLIENT, en plus du RBAC {@code @PreAuthorize}.
 *
 * <p>Decision 2026-06-30 : les flags {@code perm_download} / {@code perm_print} /
 * {@code perm_depot} de {@code dataroom_settings} ne sont plus cosmetiques. Ils
 * sont desormais appliques cote BACKEND sur les endpoints download / depot.
 *
 * <p>Regles :
 * <ul>
 *   <li>La garde ne s'applique QU'AU role CLIENT — employe / superviseur /
 *       super-admin ne sont jamais impactes (no-op).</li>
 *   <li>{@code access_status = SUSPENDED} reste bloquant pour le client
 *       (via {@link DataroomSettingsService#ensureActive}).</li>
 *   <li>DOWNLOAD : CLIENT + {@code !perm_download} -> 403.</li>
 *   <li>DEPOT (upload) : CLIENT + {@code !perm_depot} -> 403.</li>
 * </ul>
 *
 * <p>La lecture des settings reste scopee workspace (RLS / TenantContext via
 * {@link DataroomSettingsService#getOrCreate}).
 */
@Component
public class ClientDataroomPermissionGuard {

    private final DataroomSettingsService settings;

    public ClientDataroomPermissionGuard(DataroomSettingsService settings) {
        this.settings = settings;
    }

    /** DOWNLOAD : interdit au client si {@code perm_download} est faux. */
    public void assertCanDownload(UUID dossierId, AuthenticatedUser user) {
        if (!isClient(user)) return; // garde CLIENT-only
        SettingsEntity s = loadActive(dossierId);
        if (!s.isPermDownload()) {
            throw new AccessDeniedException(
                    "Le telechargement n'est pas autorise pour votre acces (perm_download desactivee).");
        }
    }

    /**
     * PREVIEW / consultation inline : le visionnage est un droit de CONSULTATION
     * (aligne sur le juridique, cf. Lot U3). Pour un CLIENT on bloque UNIQUEMENT
     * si le dossier est SUSPENDED (via {@link #loadActive}) ; on NE gate PAS par
     * {@code perm_download}. Non-CLIENT : no-op.
     */
    public void assertCanPreview(UUID dossierId, AuthenticatedUser user) {
        if (!isClient(user)) return; // garde CLIENT-only
        loadActive(dossierId); // ensureActive -> 400 si SUSPENDED ; AUCUN check perm_download
    }

    /** DEPOT (upload) : interdit au client si {@code perm_depot} est faux. */
    public void assertCanDepot(UUID dossierId, AuthenticatedUser user) {
        if (!isClient(user)) return; // garde CLIENT-only
        SettingsEntity s = loadActive(dossierId);
        if (!s.isPermDepot()) {
            throw new AccessDeniedException(
                    "Le depot de documents n'est pas autorise pour votre acces (perm_depot desactivee).");
        }
    }

    private SettingsEntity loadActive(UUID dossierId) {
        SettingsEntity s = settings.getOrCreate(dossierId);
        // Conserve l'enforcement access_status = SUSPENDED (ValidationException -> 400).
        settings.ensureActive(s);
        return s;
    }

    private static boolean isClient(AuthenticatedUser user) {
        return user != null && user.role() == Role.CLIENT;
    }
}
