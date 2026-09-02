package ma.jurika.dataroom.application;

import ma.jurika.common.audit.Auditable;
import ma.jurika.common.exception.BusinessException;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.api.dto.DataroomDtos.DossierFiscalDetailedView;
import ma.jurika.dataroom.api.dto.DataroomDtos.DossierFiscalView;
import ma.jurika.dataroom.api.dto.DataroomDtos.ExerciceFiscalSummary;
import ma.jurika.dataroom.api.dto.DataroomDtos.FiscalCategoryCount;
import ma.jurika.dataroom.api.dto.DataroomDtos.FiscalDocumentSummary;
import ma.jurika.dataroom.api.dto.DataroomDtos.SubClassificationDef;
import ma.jurika.dataroom.application.fiscal.FiscalSubClassifications;
import ma.jurika.dataroom.domain.port.AccountantNotifier;
import ma.jurika.dataroom.domain.port.DataroomEventPublisher;
import ma.jurika.dataroom.domain.port.ObjectStorage;
import ma.jurika.dataroom.infrastructure.persistence.ComptableDocumentEntity;
import ma.jurika.dataroom.infrastructure.persistence.ComptableJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.ExerciceFiscalEntity;
import ma.jurika.dataroom.infrastructure.persistence.ExerciceFiscalJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.FiscalDocumentEntity;
import ma.jurika.dataroom.infrastructure.persistence.FiscalDocumentJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.SettingsJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Sprint 8 -- DataroomFiscalService : CRUD complet du Dossier Fiscal V2.1.
 *
 * Implemente RG-DF01..28 (cf. docs/v2/Regles_de_Gestion_V2.md) et la
 * conformite CGI Maroc (Art. 211 retention 10 ans, Art. 95-125 TVA,
 * Art. 1-22 IS, Art. 22-86 IR, Art. 220-242 contentieux).
 */
@Service
public class DataroomFiscalService {

    private static final Logger log = LoggerFactory.getLogger(DataroomFiscalService.class);

    /**
     * Prompt G (2026-06-23) : 'AUTRE' ajoute comme fourre-tout pour les imports
     * (anciens dossiers sans rangement strict). Cf migration V18.
     */
    public static final List<String> CATEGORIES_CGI = List.of(
            "TVA", "IS", "IR", "TP_TSC", "RAS", "ATTESTATIONS", "CONTENTIEUX", "AUTRE");

    /** RG-DF07 : extensions autorisees. */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "jpg", "jpeg", "png", "xls", "xlsx", "doc", "docx");

    /** RG-DF08 : taille max 15 Mo par fichier. */
    private static final long MAX_FILE_SIZE_BYTES = 15L * 1024 * 1024;

    private final FiscalDocumentJpaRepository repo;
    private final ExerciceFiscalJpaRepository exercices;
    private final ExerciceFiscalService exerciceService;
    private final ComptableJpaRepository comptableRepo;
    private final ObjectStorage storage;
    private final SettingsJpaRepository settingsRepo;
    private final DossierViewJpaRepository dossierRepo;
    private final AccountantNotifier notifier;
    private final DataroomEventPublisher eventPublisher;
    /** RG-DC25 / RG-DF18 : dossier en archive legale -> lecture seule 10 ans (cf. Lot DIVERS §A). */
    private final DossierArchiveGuard archiveGuard;

    @Autowired
    public DataroomFiscalService(FiscalDocumentJpaRepository repo,
                                  ExerciceFiscalJpaRepository exercices,
                                  ExerciceFiscalService exerciceService,
                                  ComptableJpaRepository comptableRepo,
                                  ObjectStorage storage,
                                  SettingsJpaRepository settingsRepo,
                                  DossierViewJpaRepository dossierRepo,
                                  AccountantNotifier notifier,
                                  DataroomEventPublisher eventPublisher,
                                  DossierArchiveGuard archiveGuard) {
        this.repo = repo;
        this.exercices = exercices;
        this.exerciceService = exerciceService;
        this.comptableRepo = comptableRepo;
        this.storage = storage;
        this.settingsRepo = settingsRepo;
        this.dossierRepo = dossierRepo;
        this.notifier = notifier;
        this.eventPublisher = eventPublisher;
        this.archiveGuard = archiveGuard;
    }

    // ================================================================
    // Vues
    // ================================================================

    @Transactional(readOnly = true)
    public DossierFiscalView view(UUID dossierId, UUID exerciceId) {
        List<ExerciceFiscalEntity> rows = exercices.findAllByDossierIdOrderByAnneeDesc(dossierId);
        List<ExerciceFiscalSummary> summaries = rows.stream().map(this::toSummary).toList();
        UUID currentId = resolveCurrent(rows, exerciceId);
        return new DossierFiscalView(dossierId, currentId, summaries, CATEGORIES_CGI,
                "Module Dossier Fiscal -- 7 categories CGI, alertes echeances DGI.");
    }

    @Transactional(readOnly = true)
    public DossierFiscalDetailedView detailedView(UUID dossierId, UUID exerciceId) {
        List<ExerciceFiscalEntity> rows = exercices.findAllByDossierIdOrderByAnneeDesc(dossierId);
        List<ExerciceFiscalSummary> summaries = rows.stream().map(this::toSummary).toList();
        UUID currentId = resolveCurrent(rows, exerciceId);

        List<FiscalCategoryCount> counts = new ArrayList<>();
        if (currentId != null) {
            Map<String, Long> map = new HashMap<>();
            for (Object[] row : repo.countByCategorie(dossierId, currentId)) {
                map.put((String) row[0], ((Number) row[1]).longValue());
            }
            for (String cat : CATEGORIES_CGI) {
                counts.add(new FiscalCategoryCount(cat, map.getOrDefault(cat, 0L)));
            }
        }

        return new DossierFiscalDetailedView(dossierId, currentId, summaries, counts, CATEGORIES_CGI);
    }

    @Transactional(readOnly = true)
    public List<FiscalDocumentSummary> list(UUID dossierId, UUID exerciceId, String categorie) {
        List<FiscalDocumentEntity> docs = (categorie == null || categorie.isBlank())
                ? repo.findByDossierAndExercice(dossierId, exerciceId)
                : repo.findByDossierExerciceAndCategorie(dossierId, exerciceId, categorie);
        return docs.stream().map(this::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<SubClassificationDef> getSubClassifications(String categorie) {
        if (categorie != null && !categorie.isBlank()) {
            List<String> vals = FiscalSubClassifications.ALLOWED.get(categorie);
            if (vals == null) {
                throw new ValidationException("Categorie invalide : " + categorie);
            }
            return List.of(new SubClassificationDef(categorie, vals));
        }
        List<SubClassificationDef> out = new ArrayList<>();
        FiscalSubClassifications.ALLOWED.forEach((k, v) -> out.add(new SubClassificationDef(k, v)));
        return out;
    }

    // ================================================================
    // Upload
    // ================================================================

    /**
     * Prompt H (2026-06-23) — variante "upload par année brute" pour les imports
     * de dossiers anciens. Résout (ou crée à la volée) l'exercice fiscal de
     * l'année donnée, puis délègue à {@link #upload(UUID, UUID, String, String,
     * String, String, String, String, String, UUID, MultipartFile, UUID, boolean)}.
     * Idempotent — un appel répété pour la même {@code annee} réutilise l'exercice.
     */
    @Transactional
    public FiscalDocumentSummary uploadByAnnee(UUID dossierId,
                                                short annee,
                                                String categorie,
                                                String sousClassification,
                                                String title,
                                                String commentaire,
                                                String tifMetadata,
                                                String numeroDeclaration,
                                                String periodeDeclaree,
                                                UUID comptableDocSource,
                                                MultipartFile file,
                                                UUID uploaderId,
                                                boolean isClientRole) {
        ExerciceFiscalEntity ex = exerciceService.findOrCreateByAnnee(dossierId, annee, uploaderId);
        return upload(dossierId, ex.getId(), categorie, sousClassification, title,
                commentaire, tifMetadata, numeroDeclaration, periodeDeclaree,
                comptableDocSource, file, uploaderId, isClientRole);
    }

    @Transactional
    @Auditable(action = "FISCAL_UPLOADED", resourceType = "fiscal_document",
            resourceIdExpr = "#exerciceFiscalId")
    public FiscalDocumentSummary upload(UUID dossierId,
                                         UUID exerciceFiscalId,
                                         String categorie,
                                         String sousClassification,
                                         String title,
                                         String commentaire,
                                         String tifMetadata,
                                         String numeroDeclaration,
                                         String periodeDeclaree,
                                         UUID comptableDocSource,
                                         MultipartFile file,
                                         UUID uploaderId,
                                         boolean isClientRole) {

        validateCategorie(categorie);
        validateSousClassification(categorie, sousClassification);

        // RG-DF04 : le CLIENT ne peut pas uploader de CONTENTIEUX
        if (isClientRole && "CONTENTIEUX".equals(categorie)) {
            throw new AccessDeniedException(
                    "RG-DF04 : seul un employe du cabinet peut uploader un document CONTENTIEUX.");
        }

        // RG-DF23 : commentaire >= 20 chars sur CONTENTIEUX
        if ("CONTENTIEUX".equals(categorie)
                && (commentaire == null || commentaire.trim().length() < 20)) {
            throw new ValidationException(
                    "RG-DF23 : un commentaire de 20 caracteres minimum est obligatoire pour CONTENTIEUX.");
        }

        if (file == null || file.isEmpty()) {
            throw new ValidationException("Fichier vide");
        }
        // RG-DF08 : 15 Mo max
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ValidationException("FILE_TOO_LARGE : max 15 Mo par fichier");
        }

        // RG-DC25 / RG-DF18 : dossier archive => lecture seule
        ensureDossierIsActive(dossierId);

        // Verif exercice
        ExerciceFiscalEntity ex = exercices.findById(exerciceFiscalId)
                .orElseThrow(() -> new NotFoundException("Exercice fiscal inconnu : " + exerciceFiscalId));
        if (!dossierId.equals(ex.getDossierId())) {
            throw new ValidationException("Exercice ne correspond pas au dossier");
        }
        if ("VERROUILLE".equals(ex.getStatut())) {
            throw new BusinessException("EXERCICE_LOCKED",
                    "RG-DF25 : exercice verrouille (controle fiscal DGI), upload interdit.");
        }
        // CLOTURE : upload accepte pour CONTENTIEUX et ATTESTATIONS uniquement (documents recus apres cloture)
        if ("CLOTURE".equals(ex.getStatut())
                && !Set.of("CONTENTIEUX", "ATTESTATIONS").contains(categorie)) {
            throw new BusinessException("EXERCICE_CLOSED",
                    "RG-DF25 : exercice cloture, seuls CONTENTIEUX et ATTESTATIONS peuvent etre ajoutes.");
        }

        // RG-DF27 : verifier lien Comptable source (workspace + dossier match)
        UUID ws = TenantContext.get();
        if (comptableDocSource != null) {
            ComptableDocumentEntity src = comptableRepo.findById(comptableDocSource)
                    .orElseThrow(() -> new ValidationException("Document comptable source inconnu"));
            if (!ws.equals(src.getWorkspaceId()) || !dossierId.equals(src.getDossierId())) {
                throw new ValidationException(
                        "RG-DF27 : le document comptable source doit appartenir au meme dossier.");
            }
        }

        String safeName = (file.getOriginalFilename() == null ? "doc" : file.getOriginalFilename())
                .replaceAll("[^a-zA-Z0-9._-]", "_");
        validateExtension(safeName);

        // RG-DF09 / RG-DF10 : cle MinIO ws/{ws}/dossier/{d}/fiscal/{ex}/{cat}/{uuid}_{filename}
        UUID newId = UUID.randomUUID();
        String key = "ws/" + ws + "/dossier/" + dossierId + "/fiscal/"
                + exerciceFiscalId + "/" + categorie + "/" + newId + "_" + safeName;

        try (var in = file.getInputStream()) {
            storage.upload(key, in, file.getSize(), file.getContentType());
        } catch (IOException ex2) {
            throw new RuntimeException("Echec lecture fichier : " + ex2.getMessage(), ex2);
        }

        FiscalDocumentEntity e = new FiscalDocumentEntity();
        e.setId(newId);
        e.setWorkspaceId(ws);
        e.setDossierId(dossierId);
        e.setExerciceFiscalId(exerciceFiscalId);
        e.setCategorie(categorie);
        e.setSousClassification(sousClassification);
        e.setTitle(title != null && !title.isBlank() ? title : safeName);
        e.setCommentaire(commentaire);
        e.setObjectKey(key);
        e.setFilename(safeName);
        e.setContentType(file.getContentType());
        e.setSizeBytes(file.getSize());
        e.setTifMetadata(tifMetadata);
        e.setNumeroDeclaration(numeroDeclaration);
        e.setPeriodeDeclaree(periodeDeclaree);
        e.setComptableDocSource(comptableDocSource);
        e.setUploadedBy(uploaderId);
        repo.save(e);

        // RG-DF21 : notif comptable + gerant (best-effort)
        notifyFiscalUploadBestEffort(dossierId, ex.getAnnee(), categorie, sousClassification,
                safeName, uploaderId);

        // RG-DF24 : notification immediate CONTENTIEUX via Observer / WebSocket
        if ("CONTENTIEUX".equals(categorie) && eventPublisher != null) {
            try {
                eventPublisher.publish(new DataroomEventPublisher.DataroomDocumentEvent(
                        DataroomEventPublisher.Kind.UPLOADED,
                        ws, dossierId, e.getId(),
                        "FISCAL_CONTENTIEUX",
                        e.getTitle(),
                        uploaderId, Instant.now()));
            } catch (Exception pubEx) {
                log.warn("RG-DF24 : publish CONTENTIEUX event echec : {}", pubEx.getMessage());
            }
        }
        return toSummary(e);
    }

    // ================================================================
    // Download / Delete
    // ================================================================

    @Transactional(readOnly = true)
    public FiscalDocumentEntity loadForDownload(UUID documentId) {
        FiscalDocumentEntity e = repo.findById(documentId)
                .orElseThrow(() -> new NotFoundException("Document fiscal inconnu"));
        if (e.isDeleted()) {
            throw new NotFoundException("Document supprime");
        }
        return e;
    }

    @Transactional
    @Auditable(action = "FISCAL_DELETED", resourceType = "fiscal_document",
            resourceIdExpr = "#documentId")
    public void softDelete(UUID documentId, UUID deletedBy) {
        FiscalDocumentEntity e = repo.findById(documentId)
                .orElseThrow(() -> new NotFoundException("Document fiscal inconnu"));
        if (e.isDeleted()) {
            throw new NotFoundException("Document deja supprime");
        }

        // RG-DF12 : CONTENTIEUX non supprimable tant que exercice non CLOTURE
        if ("CONTENTIEUX".equals(e.getCategorie())) {
            ExerciceFiscalEntity ex = exercices.findById(e.getExerciceFiscalId())
                    .orElseThrow(() -> new NotFoundException("Exercice inconnu"));
            if (!"CLOTURE".equals(ex.getStatut()) && !"VERROUILLE".equals(ex.getStatut())) {
                throw new BusinessException("CONTENTIEUX_NOT_DELETABLE",
                        "RG-DF12 : un document CONTENTIEUX ne peut etre supprime que lorsque l'exercice est CLOTURE.");
            }
        }

        // RG-DC25 / RG-DF18
        ensureDossierIsActive(e.getDossierId());

        // CGI Art. 211 : rétention 10 ans -> soft delete uniquement (purge planifiee)
        int n = repo.softDelete(documentId, Instant.now(), deletedBy);
        if (n == 0) {
            throw new NotFoundException("Document deja supprime ou inconnu");
        }
    }

    /**
     * Job planifie (Sprint 14) : supprime physiquement les documents
     * soft-deleted dont la creation date de plus de 10 ans (CGI Art. 211).
     */
    @Transactional
    public int purgeExpiredDocuments() {
        Instant cutoff = Instant.now().minus(10L * 365 + 3, ChronoUnit.DAYS);
        List<FiscalDocumentEntity> expired = repo.findExpiredSoftDeleted(cutoff);
        int n = 0;
        for (FiscalDocumentEntity d : expired) {
            try {
                storage.delete(d.getObjectKey());
            } catch (Exception ex) {
                log.warn("Purge MinIO {} echouee : {}", d.getId(), ex.getMessage());
            }
            repo.delete(d);
            n++;
        }
        if (n > 0) log.info("Purge CGI Art. 211 : {} documents purges (> 10 ans).", n);
        return n;
    }

    // ================================================================
    // Export ZIP exercice complet (RG-DF19)
    // ================================================================

    @Transactional(readOnly = true)
    @Auditable(action = "FISCAL_EXPORTED_ZIP", resourceType = "fiscal_exercice",
            resourceIdExpr = "#exerciceId")
    public byte[] exportExerciceAsZip(UUID dossierId, UUID exerciceId) {
        List<FiscalDocumentEntity> docs = repo.findByDossierAndExercice(dossierId, exerciceId);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (FiscalDocumentEntity doc : docs) {
                String entryName = doc.getCategorie() + "/" + doc.getSousClassification()
                        + "/" + doc.getFilename();
                zos.putNextEntry(new ZipEntry(entryName));
                try (var in = storage.download(doc.getObjectKey()).stream()) {
                    in.transferTo(zos);
                } catch (Exception ex) {
                    log.warn("ZIP {} : echec lecture {} : {}", entryName, doc.getId(), ex.getMessage());
                }
                zos.closeEntry();
            }
            zos.putNextEntry(new ZipEntry("MANIFEST.txt"));
            String manifest = "Export Dossier Fiscal JURIKA (Sprint 8)\n"
                    + "Dossier  : " + dossierId + "\n"
                    + "Exercice : " + exerciceId + "\n"
                    + "Total    : " + docs.size() + " document(s)\n"
                    + "Exporte  : " + Instant.now() + "\n";
            zos.write(manifest.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
        } catch (IOException ex) {
            throw new RuntimeException("Echec generation ZIP fiscal : " + ex.getMessage(), ex);
        }
        return baos.toByteArray();
    }

    // ================================================================
    // Helpers
    // ================================================================

    private void notifyFiscalUploadBestEffort(UUID dossierId, short annee, String categorie,
                                               String sousClassification, String filename, UUID uploaderId) {
        try {
            var settings = settingsRepo.findById(dossierId).orElse(null);
            if (settings != null
                    && settings.isNotifyAccountantOnUpload()
                    && settings.getAccountantEmail() != null
                    && !settings.getAccountantEmail().isBlank()) {
                String raisonSociale = dossierRepo.findById(dossierId)
                        .map(d -> d.getRaisonSociale())
                        .orElse("dossier " + dossierId);
                notifier.notifyFiscalUpload(settings.getAccountantEmail(), dossierId, raisonSociale,
                        annee, categorie, sousClassification, filename, uploaderId);
            }
        } catch (Exception ex) {
            log.warn("RG-DF21 notif comptable echec : {}", ex.getMessage());
        }
    }

    private void ensureDossierIsActive(UUID dossierId) {
        archiveGuard.assertWritable(dossierId);
    }

    private void validateCategorie(String categorie) {
        if (categorie == null || !CATEGORIES_CGI.contains(categorie)) {
            throw new ValidationException("Categorie fiscale invalide. Valeurs : " + CATEGORIES_CGI);
        }
    }

    private void validateSousClassification(String categorie, String sousClassification) {
        if (sousClassification == null || sousClassification.isBlank()) {
            throw new ValidationException("RG-DF16 : sous-classification obligatoire.");
        }
        if (!FiscalSubClassifications.isValid(categorie, sousClassification)) {
            throw new ValidationException(
                    "RG-DF16 : sous-classification '" + sousClassification
                            + "' invalide pour categorie '" + categorie + "'. Valeurs : "
                            + FiscalSubClassifications.ALLOWED.get(categorie));
        }
    }

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

    private UUID resolveCurrent(List<ExerciceFiscalEntity> rows, UUID requested) {
        if (requested != null) {
            for (ExerciceFiscalEntity r : rows) {
                if (requested.equals(r.getId())) return r.getId();
            }
        }
        for (ExerciceFiscalEntity r : rows) {
            if ("OUVERT".equals(r.getStatut())) return r.getId();
        }
        return rows.isEmpty() ? null : rows.get(0).getId();
    }

    private ExerciceFiscalSummary toSummary(ExerciceFiscalEntity e) {
        return new ExerciceFiscalSummary(
                e.getId(), e.getAnnee(),
                e.getDateDebut() != null ? e.getDateDebut().toString() : null,
                e.getDateFin() != null ? e.getDateFin().toString() : null,
                e.getStatut(), e.getDateOuverture(), e.getDateCloture());
    }

    private FiscalDocumentSummary toSummary(FiscalDocumentEntity e) {
        String comptableTitle = null;
        if (e.getComptableDocSource() != null) {
            comptableTitle = comptableRepo.findById(e.getComptableDocSource())
                    .map(ComptableDocumentEntity::getTitle).orElse(null);
        }
        return new FiscalDocumentSummary(
                e.getId(), e.getDossierId(), e.getExerciceFiscalId(),
                e.getCategorie(), e.getSousClassification(),
                e.getTitle(), e.getCommentaire(),
                e.getFilename(), e.getContentType(), e.getSizeBytes(),
                e.getTifMetadata(), e.getNumeroDeclaration(), e.getPeriodeDeclaree(),
                e.getComptableDocSource(), comptableTitle,
                e.getCreatedAt(), e.getDeletedAt());
    }
}
