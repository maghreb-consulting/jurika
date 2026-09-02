package ma.jurika.dataroom.application;

import ma.jurika.common.audit.Auditable;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.api.dto.DataroomDtos.CategoryCount;
import ma.jurika.dataroom.api.dto.DataroomDtos.ComptableDocumentSummary;
import ma.jurika.dataroom.api.dto.DataroomDtos.DossierComptableView;
import ma.jurika.dataroom.domain.port.ObjectStorage;
import ma.jurika.dataroom.infrastructure.persistence.ComptableDocumentEntity;
import ma.jurika.dataroom.infrastructure.persistence.ComptableJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class DataroomComptableService {

    /**
     * RG-DC09 (Sprint 8) : 'PAIE' a ete renomme 'LA_PAIE' (V15) pour aligner sur le modele directeur.
     * Prompt G (2026-06-23) : 'AUTRE' ajoute comme fourre-tout pour les imports
     * (anciens dossiers sans rangement strict). Cf migration V18.
     */
    private static final Set<String> CATEGORIES = Set.of(
            "ACHATS", "VENTES", "BANQUE", "CAISSE", "NDF", "LA_PAIE", "AUTRE");

    /** RG-DC14 : formats acceptes pour le Dossier Comptable. */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "jpg", "jpeg", "png", "xls", "xlsx", "doc", "docx");

    /** RG-DC15 : taille maximum 10 Mo par fichier. */
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;

    private final ComptableJpaRepository repo;
    private final ObjectStorage storage;
    private final ma.jurika.dataroom.infrastructure.persistence.SettingsJpaRepository settingsRepo;
    private final ma.jurika.dataroom.infrastructure.persistence.DossierViewJpaRepository dossierRepo;
    private final ma.jurika.dataroom.domain.port.AccountantNotifier notifier;
    private final ma.jurika.dataroom.infrastructure.persistence.ExerciceFiscalJpaRepository exercices;
    /**
     * RG-DC25 — la regle « dossier archive = lecture seule » vit desormais dans
     * {@link DossierArchiveGuard} (source unique, cf. Lot DIVERS §A) : elle etait
     * dupliquee a l'identique ici, dans le fiscal et dans les depots.
     */
    private final DossierArchiveGuard archiveGuard;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager em;

    public DataroomComptableService(ComptableJpaRepository repo,
                                     ObjectStorage storage,
                                     ma.jurika.dataroom.infrastructure.persistence.SettingsJpaRepository settingsRepo,
                                     ma.jurika.dataroom.infrastructure.persistence.DossierViewJpaRepository dossierRepo,
                                     ma.jurika.dataroom.domain.port.AccountantNotifier notifier,
                                     ma.jurika.dataroom.infrastructure.persistence.ExerciceFiscalJpaRepository exercices,
                                     DossierArchiveGuard archiveGuard) {
        this.repo = repo;
        this.storage = storage;
        this.settingsRepo = settingsRepo;
        this.dossierRepo = dossierRepo;
        this.notifier = notifier;
        this.exercices = exercices;
        this.archiveGuard = archiveGuard;
    }

    @Transactional(readOnly = true)
    public DossierComptableView view(UUID dossierId) {
        List<Short> years = repo.findDistinctYears(dossierId);
        short courante = (short) LocalDate.now().getYear();
        // Make sure current year is in the dropdown even if empty
        if (!years.contains(courante)) {
            Set<Short> set = new HashSet<>(years);
            set.add(courante);
            years = set.stream().sorted((a, b) -> Short.compare(b, a)).toList();
        }

        List<Object[]> raw = repo.countByYearGroupedByCategory(dossierId, courante);
        List<CategoryCount> totaux = raw.stream()
                .map(r -> new CategoryCount((String) r[0], ((Number) r[1]).longValue()))
                .toList();

        return new DossierComptableView(dossierId, years, courante, totaux);
    }

    @Transactional(readOnly = true)
    public List<ComptableDocumentSummary> list(UUID dossierId, short annee, String categorie) {
        validateCategory(categorie);
        return repo.findByYearCategory(dossierId, annee, categorie)
                .stream().map(this::summary).toList();
    }

    /**
     * Upload batch : plusieurs fichiers pour une meme (annee, categorie).
     * Le titre passe en parametre est utilise comme prefixe ; sinon le nom de fichier.
     */
    @Transactional
    @Auditable(action = "COMPTABLE_UPLOADED", resourceType = "comptable_document",
            resourceIdExpr = "#dossierId")
    public List<ComptableDocumentSummary> uploadBatch(UUID dossierId, short annee, String categorie,
                                                       String title, List<MultipartFile> files, UUID uploaderId) {
        if (files == null || files.isEmpty()) {
            throw new ValidationException("Aucun fichier fourni");
        }
        List<ComptableDocumentSummary> results = new java.util.ArrayList<>(files.size());
        for (MultipartFile f : files) {
            results.add(upload(dossierId, annee, categorie, title, f, uploaderId));
        }
        return results;
    }

    @Transactional
    @Auditable(action = "COMPTABLE_UPLOADED", resourceType = "comptable_document",
            resourceIdExpr = "#dossierId")
    public ComptableDocumentSummary upload(UUID dossierId, short annee, String categorie,
                                            String title, MultipartFile file, UUID uploaderId) {
        validateCategory(categorie);
        if (annee < 2000 || annee > 2100) {
            throw new ValidationException("Annee invalide");
        }
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Fichier vide");
        }
        // RG-DC15 : max 10 Mo par fichier
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ValidationException("FILE_TOO_LARGE : max 10 Mo par fichier");
        }
        // RG-DC25 : dossier ferme/liquide -> lecture seule (pas d'upload pendant 10 ans)
        ensureDossierIsActive(dossierId);
        // RG-DC20 : quota par plan (Essentiel 5 Go / Business 20 Go / Entreprise sur mesure)
        ensureWorkspaceQuotaNotExceeded(file.getSize());
        UUID ws = TenantContext.get();
        // Sprint 8 : resoudre/creer l'exercice fiscal correspondant a l'annee + bloquer si VERROUILLE
        var exercice = resolveOrCreateExercice(dossierId, annee);
        if ("VERROUILLE".equals(exercice.getStatut())) {
            throw new ma.jurika.common.exception.BusinessException("EXERCICE_LOCKED",
                    "Exercice verrouille (controle fiscal en cours)");
        }
        String safeName = (file.getOriginalFilename() == null ? "doc" : file.getOriginalFilename())
                .replaceAll("[^a-zA-Z0-9._-]", "_");
        // RG-DC14 : extensions autorisees uniquement
        validateExtension(safeName);
        String key = "ws/" + ws + "/dossier/" + dossierId + "/comptable/"
                + annee + "/" + categorie + "/" + System.currentTimeMillis() + "_" + safeName;

        try (var in = file.getInputStream()) {
            storage.upload(key, in, file.getSize(), file.getContentType());
        } catch (java.io.IOException ex) {
            throw new RuntimeException("Echec lecture fichier : " + ex.getMessage(), ex);
        }

        ComptableDocumentEntity e = new ComptableDocumentEntity();
        e.setWorkspaceId(ws);
        e.setDossierId(dossierId);
        e.setAnnee(annee);
        e.setExerciceFiscalId(exercice.getId());
        e.setCategorie(categorie);
        e.setTitle(title != null && !title.isBlank() ? title : safeName);
        e.setObjectKey(key);
        e.setFilename(safeName);
        e.setContentType(file.getContentType());
        e.setSizeBytes(file.getSize());
        e.setUploadedBy(uploaderId);
        e.setCreatedAt(Instant.now());
        repo.save(e);

        // RG-DC27 : notification email comptable (best-effort, async)
        try {
            var settings = settingsRepo.findById(dossierId).orElse(null);
            if (settings != null
                    && settings.isNotifyAccountantOnUpload()
                    && settings.getAccountantEmail() != null
                    && !settings.getAccountantEmail().isBlank()) {
                String raisonSociale = dossierRepo.findById(dossierId)
                        .map(d -> d.getRaisonSociale())
                        .orElse("dossier " + dossierId);
                notifier.notifyUpload(settings.getAccountantEmail(), dossierId, raisonSociale,
                        annee, categorie, safeName, uploaderId);
            }
        } catch (Exception ex) {
            org.slf4j.LoggerFactory.getLogger(DataroomComptableService.class)
                    .warn("RG-DC27 notif comptable echec : {}", ex.getMessage());
        }
        return summary(e);
    }

    @Transactional
    public void softDelete(UUID documentId) {
        // RG-DC25 : refus de suppression sur un dossier en archive legale
        ComptableDocumentEntity existing = repo.findById(documentId)
                .orElseThrow(() -> new NotFoundException("Document inconnu"));
        ensureDossierIsActive(existing.getDossierId());
        int n = repo.softDelete(documentId, Instant.now());
        if (n == 0) {
            throw new NotFoundException("Document deja supprime ou inconnu");
        }
    }

    /** RG-DC25 : refuse l'ecriture si le dossier est dissous/liquide/radie (archivage legal 10 ans). */
    private void ensureDossierIsActive(UUID dossierId) {
        archiveGuard.assertWritable(dossierId);
    }

    /**
     * RG-DC20 : verifie que le workspace n'a pas depasse son quota de stockage.
     * Quota selon plan : Essentiel 5 Go, Business 20 Go, Entreprise sur mesure (storage_quota_mb sur subscriptions).
     */
    private void ensureWorkspaceQuotaNotExceeded(long incomingFileSize) {
        UUID ws = ma.jurika.common.security.TenantContext.get();
        if (ws == null) return;
        Number quotaMb;
        Number usedBytes;
        try {
            quotaMb = (Number) em.createNativeQuery("""
                    SELECT s.storage_quota_mb
                    FROM workspaces w
                    JOIN subscriptions s ON s.id = w.subscription_id
                    WHERE w.id = ?1
                    """).setParameter(1, ws).getSingleResult();
            usedBytes = (Number) em.createNativeQuery("""
                    SELECT COALESCE(
                      (SELECT COALESCE(SUM(size_bytes),0) FROM dataroom_comptable_documents
                       WHERE workspace_id = ?1 AND deleted_at IS NULL), 0
                    ) + COALESCE(
                      (SELECT COALESCE(SUM(size_bytes),0) FROM dataroom_documents
                       WHERE workspace_id = ?1), 0)
                    """).setParameter(1, ws).getSingleResult();
        } catch (Exception ex) {
            // Pas de subscription liee : on autorise (cas dev/seed) mais log
            org.slf4j.LoggerFactory.getLogger(DataroomComptableService.class)
                    .debug("RG-DC20 : impossible de calculer le quota workspace {} : {}", ws, ex.getMessage());
            return;
        }
        long quotaBytes = quotaMb == null ? Long.MAX_VALUE : quotaMb.longValue() * 1024L * 1024L;
        long used = usedBytes == null ? 0L : usedBytes.longValue();
        if (used + incomingFileSize > quotaBytes) {
            throw new ValidationException("STORAGE_QUOTA_EXCEEDED : quota = "
                    + (quotaBytes / 1024 / 1024) + " Mo, utilise = "
                    + (used / 1024 / 1024) + " Mo. Upgradez votre plan.");
        }
    }

    /**
     * RG-DC26 : exporte tous les documents d'une annee en un seul ZIP organise par categorie.
     * Structure : {categorie}/{filename}
     */
    @Transactional(readOnly = true)
    public byte[] exportYearAsZip(UUID dossierId, short annee) {
        if (annee < 2000 || annee > 2100) {
            throw new ValidationException("Annee invalide");
        }
        List<ComptableDocumentEntity> docs = repo.findAllByYear(dossierId, annee);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            for (ComptableDocumentEntity doc : docs) {
                String entryName = doc.getCategorie() + "/" + doc.getFilename();
                zos.putNextEntry(new java.util.zip.ZipEntry(entryName));
                try (var in = storage.download(doc.getObjectKey()).stream()) {
                    in.transferTo(zos);
                } catch (Exception ex) {
                    org.slf4j.LoggerFactory.getLogger(DataroomComptableService.class)
                            .warn("ZIP {} : echec lecture {} : {}", entryName, doc.getId(), ex.getMessage());
                }
                zos.closeEntry();
            }
            zos.putNextEntry(new java.util.zip.ZipEntry("MANIFEST.txt"));
            String manifest = "Export Dossier Comptable JURIKA\n"
                    + "Dossier : " + dossierId + "\n"
                    + "Annee   : " + annee + "\n"
                    + "Total   : " + docs.size() + " document(s)\n"
                    + "Exporte : " + Instant.now() + "\n";
            zos.write(manifest.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
        } catch (java.io.IOException ex) {
            throw new RuntimeException("Echec generation ZIP : " + ex.getMessage(), ex);
        }
        return baos.toByteArray();
    }

    @Transactional(readOnly = true)
    public ComptableDocumentEntity loadForDownload(UUID documentId) {
        ComptableDocumentEntity e = repo.findById(documentId)
                .orElseThrow(() -> new NotFoundException("Document inconnu"));
        if (e.getDeletedAt() != null) {
            throw new NotFoundException("Document supprime");
        }
        return e;
    }

    private void validateCategory(String categorie) {
        if (categorie == null || !CATEGORIES.contains(categorie)) {
            throw new ValidationException("Categorie invalide. Valeurs : " + CATEGORIES);
        }
    }

    /**
     * Sprint 8 : retourne l'exercice fiscal (workspace+dossier+annee) ou le cree
     * automatiquement (annee civile, OUVERT). RG-DF03 + RG-DC10 reinterprete.
     *
     * <p>2026-06-24 — Le COMPTABLE est la timeline maitresse : cette ligne d'exercice
     * est l'ancre a laquelle l'ouverture FISCALE ({@link ExerciceFiscalService#open})
     * s'aligne (memes dates). Le comptable garde sa liberte (toute annee 2000-2100) ;
     * c'est le fiscal qui doit etre conforme au comptable, jamais l'inverse.
     */
    @Transactional
    public ma.jurika.dataroom.infrastructure.persistence.ExerciceFiscalEntity resolveOrCreateExercice(
            UUID dossierId, short annee) {
        var existing = exercices.findByDossierIdAndAnnee(dossierId, annee);
        if (existing.isPresent()) return existing.get();
        var e = new ma.jurika.dataroom.infrastructure.persistence.ExerciceFiscalEntity();
        e.setWorkspaceId(TenantContext.get());
        e.setDossierId(dossierId);
        e.setAnnee(annee);
        e.setDateDebut(java.time.LocalDate.of(annee, 1, 1));
        e.setDateFin(java.time.LocalDate.of(annee, 12, 31));
        e.setStatut("OUVERT");
        return exercices.save(e);
    }

    /** RG-DC14 : extensions autorisees PDF/JPG/PNG/XLS/XLSX/DOC/DOCX. */
    private void validateExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            throw new ValidationException("FILE_EXTENSION_MISSING");
        }
        String ext = filename.substring(dot + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new ValidationException("FILE_TYPE_NOT_ALLOWED : autorises = " + ALLOWED_EXTENSIONS);
        }
    }

    ComptableDocumentSummary summary(ComptableDocumentEntity e) {
        return new ComptableDocumentSummary(e.getId(), e.getAnnee(), e.getCategorie(),
                e.getTitle(), e.getFilename(), e.getContentType(), e.getSizeBytes(), e.getCreatedAt());
    }
}
