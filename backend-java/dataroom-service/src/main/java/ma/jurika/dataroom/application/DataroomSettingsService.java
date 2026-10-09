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

    private final ma.jurika.common.audit.AuditEventEmitter audit;

    public DataroomSettingsService(SettingsJpaRepository repo, ma.jurika.common.audit.AuditEventEmitter audit) {
        this.repo = repo;
        this.audit = audit;
    }

    @Transactional
    public SettingsEntity getOrCreate(UUID dossierId) {
        // Lot L0 (E20, P9) : filtre workspace explicite, en plus de la RLS.
        UUID ws = TenantContext.get();
        if (ws == null) {
            throw new NotFoundException("Dossier introuvable");
        }
        return repo.findByDossierIdAndWorkspaceId(dossierId, ws).orElseGet(() -> {
            // Fix 2026-06-07 (BUG 3 finition) — Ne JAMAIS recreer les settings
            // d'un dossier RADIE (deja supprime). Sinon le user qui clique sur
            // un dossier-fantome relance un dataroom_settings ACTIVE et a
            // l'impression que la suppression a juste "reactive" le dataroom.
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

    /**
     * Lot L1 (RG-CLI-01) : reglage des permissions du client. Une valeur null laisse la
     * permission inchangee. Chaque modification est tracee (acteur, avant, apres).
     */
    @Transactional
    public SettingsEntity updatePermissions(UUID dossierId, UUID acteurId, Boolean permDownload,
                                            Boolean permPrint, Boolean permDepot,
                                            Boolean permConsultation, Boolean permDemandes) {
        SettingsEntity s = getOrCreate(dossierId);
        java.util.Map<String, Object> avant = permissions(s);
        if (permDownload != null) s.setPermDownload(permDownload);
        if (permPrint != null) s.setPermPrint(permPrint);
        if (permDepot != null) s.setPermDepot(permDepot);
        if (permConsultation != null) s.setPermConsultation(permConsultation);
        if (permDemandes != null) s.setPermDemandes(permDemandes);
        SettingsEntity saved = repo.save(s);
        java.util.Map<String, Object> apres = permissions(saved);
        if (!avant.equals(apres)) {
            java.util.Map<String, Object> meta = new java.util.LinkedHashMap<>();
            meta.put("avant", avant);
            meta.put("apres", apres);
            audit.emit(TenantContext.get(), acteurId, "PERMISSIONS_CLIENT_MODIFIEES", "dossier", dossierId, meta);
        }
        return saved;
    }

    private static java.util.Map<String, Object> permissions(SettingsEntity s) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("consultation", s.isPermConsultation());
        m.put("telechargement", s.isPermDownload());
        m.put("impression", s.isPermPrint());
        m.put("depot", s.isPermDepot());
        m.put("demandes", s.isPermDemandes());
        return m;
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
