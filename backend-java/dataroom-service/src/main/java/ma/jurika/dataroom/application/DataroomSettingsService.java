package ma.jurika.dataroom.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ma.jurika.common.audit.Auditable;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.infrastructure.persistence.SettingsEntity;
import ma.jurika.dataroom.infrastructure.persistence.SettingsJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DataroomSettingsService {

    private final SettingsJpaRepository repo;

    @PersistenceContext
    private EntityManager em;

    public DataroomSettingsService(SettingsJpaRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public SettingsEntity getOrCreate(UUID dossierId) {
        return repo.findById(dossierId).orElseGet(() -> {
            // Fix 2026-06-07 (BUG 3 finition) — Ne JAMAIS recreer les settings
            // d'un dossier RADIE (deja supprime). Sinon le user qui clique sur
            // un dossier-fantome relance un dataroom_settings ACTIVE et a
            // l'impression que la suppression a juste "reactive" le dataroom.
            UUID ws = TenantContext.get();
            @SuppressWarnings("unchecked")
            List<Object> rows = em.createNativeQuery(
                    "SELECT statut FROM entreprise_dossiers WHERE id = ?1 AND workspace_id = ?2")
                    .setParameter(1, dossierId)
                    .setParameter(2, ws)
                    .getResultList();
            if (rows.isEmpty()) {
                throw new NotFoundException("Dossier introuvable");
            }
            String statut = rows.get(0) == null ? null : rows.get(0).toString();
            if ("RADIE".equals(statut)) {
                throw new NotFoundException(
                        "Ce dataroom a ete supprime (dossier RADIE).");
            }
            SettingsEntity s = new SettingsEntity();
            s.setDossierId(dossierId);
            s.setWorkspaceId(ws);
            s.setAccessStatus("ACTIVE");
            s.setPermDownload(true);
            s.setPermPrint(false);
            // V19 : par defaut le client est en consultation seule (pas de depot).
            s.setPermDepot(false);
            s.setClientLinkToken(UUID.randomUUID());
            s.setAccessCount(0);
            return repo.save(s);
        });
    }

    @Transactional
    @Auditable(action = "PERMISSIONS_CHANGED", resourceType = "dossier", resourceIdExpr = "#dossierId")
    public SettingsEntity updatePermissions(UUID dossierId, boolean permDownload, boolean permPrint,
                                            boolean permDepot) {
        SettingsEntity s = getOrCreate(dossierId);
        s.setPermDownload(permDownload);
        s.setPermPrint(permPrint);
        s.setPermDepot(permDepot);
        return repo.save(s);
    }

    @Transactional
    public SettingsEntity toggleSuspension(UUID dossierId, boolean suspended) {
        SettingsEntity s = getOrCreate(dossierId);
        s.setAccessStatus(suspended ? "SUSPENDED" : "ACTIVE");
        return repo.save(s);
    }

    @Transactional
    public SettingsEntity regenerateLinkToken(UUID dossierId) {
        SettingsEntity s = getOrCreate(dossierId);
        s.setClientLinkToken(UUID.randomUUID());
        return repo.save(s);
    }

    /**
     * RG-DC27 : configure l'email du comptable et active/desactive la notif a chaque upload.
     */
    @Transactional
    public SettingsEntity updateAccountantNotification(UUID dossierId, String accountantEmail, boolean enabled) {
        if (enabled && (accountantEmail == null || accountantEmail.isBlank() || !accountantEmail.contains("@"))) {
            throw new ValidationException("Email comptable invalide");
        }
        SettingsEntity s = getOrCreate(dossierId);
        s.setAccountantEmail(accountantEmail == null || accountantEmail.isBlank() ? null : accountantEmail.trim());
        s.setNotifyAccountantOnUpload(enabled);
        return repo.save(s);
    }

    @Transactional
    public void incrementAccess(UUID dossierId) {
        repo.incrementAccess(dossierId, Instant.now());
    }

    @Transactional(readOnly = true)
    public SettingsEntity findByToken(UUID token) {
        return repo.findByClientLinkToken(token)
                .orElseThrow(() -> new NotFoundException("Lien client invalide"));
    }

    public void ensureActive(SettingsEntity s) {
        if ("SUSPENDED".equals(s.getAccessStatus())) {
            throw new ValidationException("Data Room suspendu");
        }
    }
}
