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
            tracer(dossierId, acteurId, "PERMISSIONS", avant, apres);
        }
        return saved;
    }

    /** Lot L1 (RG-CLI-01) : une ligne de l'historique de l'acces client d'un dossier. */
    public record HistoriqueAcces(UUID id, String nature, UUID acteurId, String acteurNom,
                                  java.util.Map<String, Object> avant, java.util.Map<String, Object> apres,
                                  Instant createdAt) {}

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * Lot L1 (RG-CLI-01) : ecrit l'historique dans la transaction du changement. Une
     * ecriture refusee fait echouer le changement : rien ne se modifie sans trace.
     */
    private void tracer(UUID dossierId, UUID acteurId, String nature,
                        java.util.Map<String, Object> avant, java.util.Map<String, Object> apres) {
        try {
            em.createNativeQuery("INSERT INTO dataroom_acces_client_historique"
                            + "(workspace_id, dossier_id, acteur_id, nature, avant, apres) "
                            + "VALUES (?1, ?2, ?3, ?4, CAST(?5 AS jsonb), CAST(?6 AS jsonb))")
                    .setParameter(1, TenantContext.get())
                    .setParameter(2, dossierId)
                    .setParameter(3, acteurId)
                    .setParameter(4, nature)
                    .setParameter(5, JSON.writeValueAsString(avant))
                    .setParameter(6, JSON.writeValueAsString(apres))
                    .executeUpdate();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Historique de l'acces client illisible", e);
        }
    }

    /** Lot L1 (RG-CLI-01) : historique de l'acces client du dossier, du plus recent au plus ancien. */
    @Transactional(readOnly = true)
    public List<HistoriqueAcces> historique(UUID dossierId) {
        verifierDossier(dossierId);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT h.id, h.nature, h.acteur_id, "
                                + "NULLIF(trim(concat_ws(' ', u.first_name, u.last_name)), ''), "
                                + "CAST(h.avant AS text), CAST(h.apres AS text), h.created_at "
                                + "FROM dataroom_acces_client_historique h "
                                + "LEFT JOIN users u ON u.id = h.acteur_id "
                                + "WHERE h.workspace_id = ?1 AND h.dossier_id = ?2 "
                                + "ORDER BY h.created_at DESC, h.id")
                .setParameter(1, TenantContext.get())
                .setParameter(2, dossierId)
                .getResultList();
        return rows.stream().map(r -> new HistoriqueAcces((UUID) r[0], (String) r[1], (UUID) r[2], (String) r[3],
                lireJson((String) r[4]), lireJson((String) r[5]), instant(r[6]))).toList();
    }

    /** Le dossier doit exister dans le workspace (404 sinon), sans creer de reglages en lecture. */
    private void verifierDossier(UUID dossierId) {
        UUID ws = TenantContext.get();
        if (ws == null || em.createNativeQuery("SELECT 1 FROM entreprise_dossiers WHERE id = ?1 AND workspace_id = ?2")
                .setParameter(1, dossierId).setParameter(2, ws).getResultList().isEmpty()) {
            throw new NotFoundException("Dossier introuvable");
        }
    }

    private static java.util.Map<String, Object> lireJson(String json) {
        if (json == null) return null;
        try {
            return JSON.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<java.util.LinkedHashMap<String, Object>>() {});
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Historique de l'acces client illisible", e);
        }
    }

    private static Instant instant(Object o) {
        if (o instanceof Instant i) return i;
        if (o instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        if (o instanceof java.sql.Timestamp ts) return ts.toInstant();
        throw new IllegalStateException("Date inattendue : " + o);
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

    /** Lot L1 (RG-CLI-01) : la suspension et la reactivation sont tracees (historique et audit). */
    @Transactional
    public SettingsEntity toggleSuspension(UUID dossierId, UUID acteurId, boolean suspended) {
        SettingsEntity s = getOrCreate(dossierId);
        String avant = s.getAccessStatus();
        s.setAccessStatus(suspended ? "SUSPENDED" : "ACTIVE");
        SettingsEntity saved = repo.save(s);
        if (!saved.getAccessStatus().equals(avant)) {
            java.util.Map<String, Object> av = java.util.Map.of("acces", avant);
            java.util.Map<String, Object> ap = java.util.Map.of("acces", saved.getAccessStatus());
            audit.emit(TenantContext.get(), acteurId,
                    suspended ? "ACCES_CLIENT_SUSPENDU" : "ACCES_CLIENT_REACTIVE", "dossier", dossierId,
                    java.util.Map.of("avant", av, "apres", ap));
            tracer(dossierId, acteurId, suspended ? "SUSPENSION" : "REACTIVATION", av, ap);
        }
        return saved;
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
