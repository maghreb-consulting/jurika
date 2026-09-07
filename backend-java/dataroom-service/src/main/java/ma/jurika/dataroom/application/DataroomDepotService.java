package ma.jurika.dataroom.application;

import ma.jurika.common.audit.Auditable;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.api.dto.DataroomDtos.DepotSummary;
import ma.jurika.dataroom.application.access.ClientAccessLogger;
import ma.jurika.dataroom.domain.port.ObjectStorage;
import ma.jurika.dataroom.infrastructure.persistence.DepotEntity;
import ma.jurika.dataroom.infrastructure.persistence.DepotJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lot V -- Espace « Depots » client (depot libre + consultation employe).
 *
 * <p>Depot libre : ni annee, ni categorie, ni exercice. Le CLIENT depose
 * librement ; l'EMPLOYE responsable (et SUPERVISEUR / SUPER_ADMIN) consulte.
 * Depuis le lot 1 (2026-09-04), c'est aussi la destination des pieces
 * comptables et fiscales, qui ne sont plus classees.
 *
 * <p>Le scoping responsable / client est factorise dans
 * {@link #assertCanAccessDossier(UUID, UUID, Role)} et applique par
 * {@code list} + les acces by-id (download / preview / delete), coherent avec
 * {@link DemandesClientService} (Lot U2) : un EMPLOYE ne voit que les dossiers
 * dont il est responsable ; un CLIENT que SON dossier.
 */
@Service
public class DataroomDepotService {

    private static final Logger log = LoggerFactory.getLogger(DataroomDepotService.class);

    /** Meme regle que le Dossier Comptable : max 10 Mo par fichier. */
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;

    private final DepotJpaRepository repo;
    private final ObjectStorage storage;
    private final DossierViewJpaRepository dossierRepo;
    private final ClientAccessLogger accessLogger;
    /**
     * Dossier dissous / liquide / radie -> lecture seule (pas de depot). Regle
     * factorisee dans {@link DossierArchiveGuard} (Lot DIVERS §A).
     */
    private final DossierArchiveGuard archiveGuard;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager em;

    public DataroomDepotService(DepotJpaRepository repo,
                                ObjectStorage storage,
                                DossierViewJpaRepository dossierRepo,
                                ClientAccessLogger accessLogger,
                                DossierArchiveGuard archiveGuard) {
        this.repo = repo;
        this.storage = storage;
        this.dossierRepo = dossierRepo;
        this.accessLogger = accessLogger;
        this.archiveGuard = archiveGuard;
    }

    // ================================================================
    // Upload
    // ================================================================

    @Transactional
    @Auditable(action = "DEPOT_UPLOADED", resourceType = "depot", resourceIdExpr = "#dossierId")
    public DepotSummary upload(UUID dossierId, String title, MultipartFile file, UUID uploaderId, Role role) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Fichier vide");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ValidationException("FILE_TOO_LARGE : max 10 Mo par fichier");
        }
        // Defense en profondeur : un CLIENT / EMPLOYE ne depose que sur SON dossier
        // (le CLIENT est aussi gate par perm_depot au niveau controller).
        assertCanAccessDossier(dossierId, uploaderId, role);
        // Dossier ferme/liquide -> pas de depot.
        ensureDossierIsActive(dossierId);
        // Quota de stockage : best-effort (warn), ne bloque pas le depot client.
        warnIfQuotaExceeded(file.getSize());

        UUID ws = TenantContext.get();
        String safeName = (file.getOriginalFilename() == null ? "doc" : file.getOriginalFilename())
                .replaceAll("[^a-zA-Z0-9._-]", "_");
        String key = "ws/" + ws + "/dossier/" + dossierId + "/depots/"
                + System.currentTimeMillis() + "_" + safeName;

        try (var in = file.getInputStream()) {
            storage.upload(key, in, file.getSize(), file.getContentType());
        } catch (java.io.IOException ex) {
            throw new RuntimeException("Echec lecture fichier : " + ex.getMessage(), ex);
        }

        DepotEntity e = new DepotEntity();
        e.setWorkspaceId(ws);
        e.setDossierId(dossierId);
        e.setTitle(title != null && !title.isBlank() ? title : safeName);
        e.setObjectKey(key);
        e.setFilename(safeName);
        e.setContentType(file.getContentType());
        e.setSizeBytes(file.getSize());
        e.setUploadedBy(uploaderId);
        e.setCreatedAt(Instant.now());
        repo.save(e);

        // Lot X -- tracer le depot dans l'"Activite client" (best-effort). On ne
        // journalise que le CLIENT : le compteur "Acces client" filtre deja sur
        // user_id = client_id, un depot EMPLOYE serait de toute facon invisible.
        // workspaceId = ws (deja resolu ci-dessus), acteur = uploaderId (= le
        // client, donc = entreprise_dossiers.client_id).
        if (role == Role.CLIENT) {
            accessLogger.log(dossierId, e.getId(), "DEPOT_DOC", ws, uploaderId);
        }
        return summary(e);
    }

    // ================================================================
    // Lecture / acces by-id
    // ================================================================

    /** Liste des depots (non supprimes, tri desc), scopee par role. */
    @Transactional(readOnly = true)
    public List<DepotSummary> list(UUID dossierId, UUID userId, Role role) {
        assertCanAccessDossier(dossierId, userId, role);
        return repo.findByDossierIdAndDeletedAtIsNullOrderByCreatedAtDesc(dossierId)
                .stream().map(this::summary).toList();
    }

    /**
     * Charge un depot pour download / preview en appliquant le meme scoping via
     * le {@code dossier_id} du depot. 404 si absent / supprime.
     */
    @Transactional(readOnly = true)
    public DepotEntity getForStream(UUID id, UUID userId, Role role) {
        DepotEntity e = repo.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Depot inconnu"));
        assertCanAccessDossier(e.getDossierId(), userId, role);
        return e;
    }

    /** Soft-delete apres verification du scoping (via le dossier du depot). */
    @Transactional
    @Auditable(action = "DEPOT_DELETED", resourceType = "depot", resourceIdExpr = "#id")
    public void softDelete(UUID id, UUID userId, Role role) {
        DepotEntity e = repo.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Depot inconnu"));
        assertCanAccessDossier(e.getDossierId(), userId, role);
        // §A — societe archivee : la Data Room est figee, on ne supprime plus.
        ensureDossierIsActive(e.getDossierId());
        int n = repo.softDelete(id, Instant.now());
        if (n == 0) {
            throw new NotFoundException("Depot deja supprime ou inconnu");
        }
    }

    // ================================================================
    // Scoping (Lot U2 : responsable_id / client_id)
    // ================================================================

    /**
     * Scoping par role (miroir de {@link DemandesClientService#listByDossier}) :
     * <ul>
     *   <li>CLIENT : uniquement SON dossier ({@code client_id = user}) -> sinon 403 ;</li>
     *   <li>EMPLOYE : uniquement s'il est responsable ({@code responsable_id = user}) -> sinon 403 ;</li>
     *   <li>SUPERVISEUR / SUPER_ADMIN : non restreints.</li>
     * </ul>
     *
     * <p>Defense-in-depth multi-tenant : {@code findByWorkspaceIdAndId} (la RLS
     * n'est pas fiable car jurika_user a BYPASSRLS sous le conteneur officiel).
     */
    private void assertCanAccessDossier(UUID dossierId, UUID userId, Role role) {
        if (role == Role.SUPERVISEUR || role == Role.SUPER_ADMIN) {
            return; // oversight plateforme
        }
        UUID ws = TenantContext.get();
        if (ws == null) {
            throw new NotFoundException("Dossier inconnu");
        }
        UUID owner = dossierRepo.findByWorkspaceIdAndId(ws, dossierId)
                .map(d -> role == Role.CLIENT ? d.getClientId() : d.getResponsableId())
                .orElse(null);
        if (userId == null || !userId.equals(owner)) {
            throw new AccessDeniedException(role == Role.CLIENT
                    ? "Acces refuse a ce dossier (RG-DR15 : un client n'a acces qu'a SON dossier)."
                    : "Vous n'etes pas responsable de ce dossier : acces aux depots interdit.");
        }
    }

    private void ensureDossierIsActive(UUID dossierId) {
        archiveGuard.assertWritable(dossierId);
    }

    /**
     * Best-effort (RG-DC20 reinterprete) : log un warning si le workspace depasse
     * son quota de stockage. Ne bloque PAS le depot client (contrairement au
     * Dossier Comptable) -- le depot libre reste toujours possible.
     */
    private void warnIfQuotaExceeded(long incomingFileSize) {
        UUID ws = TenantContext.get();
        if (ws == null) return;
        try {
            Number quotaMb = (Number) em.createNativeQuery("""
                    SELECT s.storage_quota_mb
                    FROM workspaces w
                    JOIN subscriptions s ON s.id = w.subscription_id
                    WHERE w.id = ?1
                    """).setParameter(1, ws).getSingleResult();
            Number usedBytes = (Number) em.createNativeQuery("""
                    SELECT COALESCE(
                      (SELECT COALESCE(SUM(size_bytes),0) FROM dataroom_depots
                       WHERE workspace_id = ?1 AND deleted_at IS NULL), 0
                    ) + COALESCE(
                      (SELECT COALESCE(SUM(size_bytes),0) FROM dataroom_documents
                       WHERE workspace_id = ?1), 0)
                    """).setParameter(1, ws).getSingleResult();
            long quotaBytes = quotaMb == null ? Long.MAX_VALUE : quotaMb.longValue() * 1024L * 1024L;
            long used = usedBytes == null ? 0L : usedBytes.longValue();
            if (used + incomingFileSize > quotaBytes) {
                log.warn("Depot : quota stockage depasse pour workspace {} (quota={} Mo, utilise={} Mo) -- depot autorise (best-effort)",
                        ws, quotaBytes / 1024 / 1024, used / 1024 / 1024);
            }
        } catch (Exception ex) {
            log.debug("Depot : calcul quota impossible pour workspace {} : {}", ws, ex.getMessage());
        }
    }

    DepotSummary summary(DepotEntity e) {
        return new DepotSummary(e.getId(), e.getTitle(), e.getFilename(),
                e.getContentType(), e.getSizeBytes(), e.getUploadedBy(), e.getCreatedAt());
    }
}
