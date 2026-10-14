package ma.jurika.auth.domain.port;

import java.util.List;
import java.util.UUID;

/**
 * Lot L5 (E9, RG-COM-08 et RG-PLN-02) : registre des alertes de quota emises
 * (table {@code alertes_quota_notifiees}, auth V38) et destinataires.
 */
public interface AlertesQuotaRegistre {

    /** Cabinets actifs (seul chemin hors workspace : detection planifiee). */
    List<UUID> cabinetsActifs();

    /**
     * Retient l'alerte d'un quota pour une periode ({@code AAAA-MM}). {@code true}
     * la premiere fois seulement. Le workspace courant doit etre {@code workspaceId}.
     */
    boolean retenirSiNouvelle(UUID workspaceId, String quota, String periode);

    /** Superviseurs actifs du cabinet. */
    List<UUID> superviseursActifs(UUID workspaceId);

    /** Lot L7 (E5), RG-PLN-02 : un super-admin actif et son workspace (canal d'alerte du super-admin). */
    record SuperAdmin(UUID id, UUID workspaceId) {}

    /** Super-admins actifs (fonction auth V44, hors workspace). */
    List<SuperAdmin> superAdminsActifs();

    /** Denomination du cabinet (message d'alerte du super-admin). Le workspace courant doit etre {@code workspaceId}. */
    String denominationCabinet(UUID workspaceId);
}
