package ma.jurika.dataroom.application;

import ma.jurika.common.audit.Auditable;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.observability.BusinessMetrics;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.api.dto.DataroomDtos.DocumentSummary;
import ma.jurika.dataroom.api.dto.DataroomDtos.DossierJuridiqueView;
import ma.jurika.dataroom.api.dto.DataroomDtos.TicketHistoryEntry;
import ma.jurika.dataroom.domain.port.ObjectStorage;
import ma.jurika.dataroom.infrastructure.persistence.DocumentEntity;
import ma.jurika.dataroom.infrastructure.persistence.DocumentJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewEntity;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.SnapshotJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.TicketSnapshotEntity;
import ma.jurika.dataroom.infrastructure.persistence.TicketViewEntity;
import ma.jurika.dataroom.infrastructure.persistence.TicketViewJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.spec.TicketSpecifications;
import ma.jurika.dataroom.domain.port.DataroomEventPublisher;
import ma.jurika.dataroom.domain.port.DataroomEventPublisher.DataroomDocumentEvent;
import ma.jurika.dataroom.domain.port.DataroomEventPublisher.Kind;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DataroomJuridiqueService {

    private final DocumentJpaRepository documents;
    private final DossierViewJpaRepository dossiers;
    private final TicketViewJpaRepository tickets;
    private final SnapshotJpaRepository snapshots;
    private final ObjectStorage storage;
    private final DataroomEventPublisher events;
    private final BusinessMetrics businessMetrics;
    /**
     * Lot DIVERS §A (2026-08-13) — societe dissoute / liquidee / radiee : le
     * Dossier Juridique passe en LECTURE SEULE. Ce chemin etait le seul des
     * quatre (juridique / comptable / fiscal / depots) a n'avoir aucun garde.
     */
    private final DossierArchiveGuard archiveGuard;

    public DataroomJuridiqueService(DocumentJpaRepository documents,
                                     DossierViewJpaRepository dossiers,
                                     TicketViewJpaRepository tickets,
                                     SnapshotJpaRepository snapshots,
                                     ObjectStorage storage,
                                     DataroomEventPublisher events,
                                     BusinessMetrics businessMetrics,
                                     DossierArchiveGuard archiveGuard) {
        this.documents = documents;
        this.dossiers = dossiers;
        this.tickets = tickets;
        this.snapshots = snapshots;
        this.storage = storage;
        this.events = events;
        this.businessMetrics = businessMetrics;
        this.archiveGuard = archiveGuard;
    }

    @Transactional(readOnly = true)
    public DossierJuridiqueView view(UUID dossierId) {
        return view(dossierId, null, null, null);
    }

    /**
     * 2026-06-04 (fix P1) — RG-DR15 : un CLIENT ne peut acceder qu'au dossier
     * dont {@code client_id} pointe sur son user ID. Avant ce fix le filtrage
     * etait fait pour la liste (listDossiersForClient) mais PAS pour la vue
     * detail -- un CLIENT pouvait deviner un UUID dossier d'un autre client
     * (meme workspace) et lire ses documents juridiques (fuite de donnees
     * inter-clients dans un meme cabinet).
     *
     * @throws ma.jurika.common.exception.NotFoundException si dossier inconnu
     *         (cache un eventuel cross-tenant deja bloque par RLS workspace)
     * @throws org.springframework.security.access.AccessDeniedException si le
     *         dossier appartient a un AUTRE client du meme workspace
     */
    @Transactional(readOnly = true)
    public void assertClientAccess(UUID dossierId, UUID clientUserId) {
        // Defense-in-depth multi-tenant : findByWorkspaceIdAndId au lieu de
        // findById brut. La RLS n'est pas fiable (jurika_user a BYPASSRLS sous
        // le conteneur officiel) -- sans ce filtre un user pouvait verifier
        // l'existence d'un UUID dossier cross-tenant via l'exception 404 vs OK.
        UUID ws = TenantContext.get();
        if (ws == null) {
            throw new NotFoundException("Dossier inconnu");
        }
        DossierViewEntity d = dossiers.findByWorkspaceIdAndId(ws, dossierId)
                .orElseThrow(() -> new NotFoundException("Dossier inconnu"));
        UUID assignedClient = d.getClientId();
        if (assignedClient == null || !assignedClient.equals(clientUserId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Acces refuse a ce dossier (RG-DR15 : un client n'a acces qu'a SON dossier).");
        }
    }

    /**
     * Sprint 7 / TASK 2 -- view enrichie avec filtres optionnels sur l'historique
     * des operations (timeline). Si tous les filtres sont null, comportement
     * identique a la version sans parametres (retrocompatibilite).
     *
     * @param types filtre liste de TicketViewEntity.type (CREATION, MODIFICATION, etc.)
     * @param from  borne inferieure cloture_at (inclusive)
     * @param to    borne superieure cloture_at (inclusive)
     */
    @Transactional(readOnly = true)
    public DossierJuridiqueView view(UUID dossierId,
                                      List<String> types,
                                      java.time.Instant from,
                                      java.time.Instant to) {
        // Defense-in-depth multi-tenant : tous les lookups passent par
        // workspace_id explicite (jurika_user a BYPASSRLS sous le conteneur
        // officiel, la RLS est court-circuitee). Sans ce filtre, un user A
        // pouvait lire la fiche d'un dossier B en devinant son UUID.
        UUID ws = TenantContext.get();
        if (ws == null) {
            throw new NotFoundException("Dossier inconnu");
        }
        DossierViewEntity dossier = dossiers.findByWorkspaceIdAndId(ws, dossierId)
                .orElseThrow(() -> new NotFoundException("Dossier inconnu"));

        List<DocumentEntity> enVigueur =
                documents.findAllByWorkspaceIdAndDossierIdAndCurrentTrueOrderByCreatedAtDesc(ws, dossierId);

        Specification<TicketViewEntity> spec = Specification
                .where(TicketSpecifications.byDossier(dossierId))
                .and(TicketSpecifications.ofStatut("CLOTURE"));
        if (types != null && !types.isEmpty()) {
            spec = spec.and(TicketSpecifications.ofTypes(types));
        }
        if (from != null || to != null) {
            spec = spec.and(TicketSpecifications.closedBetween(from, to));
        }
        List<TicketViewEntity> closedTickets = tickets.findAll(
                spec, Sort.by(Sort.Direction.DESC, "clotureAt"));

        List<TicketHistoryEntry> historique = closedTickets.stream()
                .map(this::buildHistoryEntry)
                .toList();

        return new DossierJuridiqueView(
                dossier.getId(),
                dossier.getRaisonSociale(),
                dossier.getFormeJuridique(),
                dossier.getIce(),
                dossier.getRcNumero(),
                dossier.getRcTribunal(),
                dossier.getIdentifiantFiscal(),
                dossier.getTaxeProfessionnelle(),
                dossier.getCnss(),
                dossier.getAdresseSiege(),
                dossier.getVille(),
                dossier.getCapitalSocialMad(),
                dossier.getDateConstitution(),
                dossier.getStatut(),
                enVigueur.stream().map(this::summary).toList(),
                historique);
    }

    private TicketHistoryEntry buildHistoryEntry(TicketViewEntity t) {
        // Defense-in-depth multi-tenant : workspace_id explicite partout (la RLS
        // est court-circuitee par BYPASSRLS sous postgres officiel).
        UUID ws = TenantContext.get();
        if (ws == null) {
            return new TicketHistoryEntry(t.getId(), t.getReference(), t.getTitre(), t.getType(),
                    t.getClotureAt(), t.getDescription(), List.of(), List.of());
        }
        List<TicketSnapshotEntity> snaps = snapshots.findAllByWorkspaceIdAndTicketId(ws, t.getId());
        Map<UUID, String> kindByDoc = new HashMap<>();
        for (TicketSnapshotEntity s : snaps) kindByDoc.put(s.getDocumentId(), s.getSnapshotKind());

        List<DocumentEntity> ticketDocs = documents.findAllByWorkspaceIdAndTicketIdOrderByCreatedAtAsc(ws, t.getId());
        // generated docs = those linked to this ticket
        List<DocumentSummary> generated = ticketDocs.stream().map(this::summary).toList();

        // replaced docs = explicit snapshots marked REPLACED.
        // Filtre additionnel WHERE workspace_id sur le lookup par id (ws final
        // pour usage dans la lambda — Java exige effectively-final).
        final UUID wsFinal = ws;
        List<DocumentSummary> replaced = snaps.stream()
                .filter(s -> "REPLACED".equals(s.getSnapshotKind()))
                .map(s -> documents.findByWorkspaceIdAndId(wsFinal, s.getDocumentId()))
                .flatMap(Optional::stream)
                .map(this::summary)
                .toList();

        return new TicketHistoryEntry(
                t.getId(), t.getReference(), t.getTitre(), t.getType(),
                t.getClotureAt(), t.getDescription(),
                replaced, generated);
    }

    /**
     * Batch : N fichiers (creent N versions successives ou N docs distincts).
     * Titre derive du nom de fichier pour chacun, evite les collisions de cle MinIO.
     */
    @Transactional
    public List<DocumentSummary> uploadVersionBatch(UUID dossierId, String documentType, String title,
                                                     UUID ticketId, List<MultipartFile> files, UUID uploaderId) {
        if (files == null || files.isEmpty()) {
            throw new ma.jurika.common.exception.ValidationException("Aucun fichier fourni");
        }
        org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DataroomJuridiqueService.class);
        log.info("uploadVersionBatch dossier={} type={} files={}",
                dossierId, documentType, files.size());

        List<DocumentSummary> out = new ArrayList<>(files.size());
        int idx = 0;
        for (MultipartFile f : files) {
            idx++;
            String fileTitle = (title == null || title.isBlank())
                    ? defaultTitleFromFilename(f)
                    : title + " (" + idx + "/" + files.size() + ")";
            try {
                out.add(uploadVersion(dossierId, documentType, fileTitle, ticketId, f, uploaderId));
            } catch (Exception ex) {
                log.error("uploadVersionBatch echec fichier {}/{} ({}): {}",
                        idx, files.size(), f.getOriginalFilename(), ex.getMessage(), ex);
                throw new RuntimeException("Upload batch echec sur fichier " + idx
                        + "/" + files.size() + " (" + f.getOriginalFilename() + ") : "
                        + ex.getMessage(), ex);
            }
        }
        return out;
    }

    private static String defaultTitleFromFilename(MultipartFile f) {
        String name = f.getOriginalFilename();
        if (name == null || name.isBlank()) return "Document";
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /**
     * Upload simple : par defaut chaque upload est un DOCUMENT DISTINCT (replacePrevious=false).
     * Pour remplacer une version existante (workflow IA Modification), utiliser
     * {@link #uploadVersion(UUID, String, String, UUID, MultipartFile, UUID, boolean)}.
     */
    @Transactional
    @Auditable(action = "DOCUMENT_UPLOADED", resourceType = "document", resourceIdExpr = "#dossierId")
    public DocumentSummary uploadVersion(UUID dossierId, String documentType, String title,
                                          UUID ticketId, MultipartFile file, UUID uploaderId) {
        return uploadVersion(dossierId, documentType, title, ticketId, file, uploaderId, false);
    }

    /**
     * @param replacePrevious si true, marque l'ancien doc current du meme type comme REPLACED
     *                        et incremente la version (utilise par les workflows IA).
     *                        Si false (defaut), cree un document distinct version=1 :
     *                        les uploads manuels multi-fichiers ne sont pas versionnes.
     */
    @Transactional
    public DocumentSummary uploadVersion(UUID dossierId, String documentType, String title,
                                          UUID ticketId, MultipartFile file, UUID uploaderId,
                                          boolean replacePrevious) {
        // §A — societe dissoute : plus aucun depot, SAUF les actes de cloture
        // portes par le ticket de LIQUIDATION (derogation verifiee cote serveur).
        archiveGuard.assertWritable(dossierId, ticketId);
        UUID workspaceId = TenantContext.get();
        short nextVersion = 1;
        Instant now = Instant.now();

        // Fix DR1 (2026-08-16) — UN SEUL document « en vigueur » par slot logique.
        //
        // `replacePrevious=false` créait systématiquement un document DISTINCT
        // (version=1, is_current=true). Comme l'upload manuel passait toujours par ce
        // chemin, re-déposer un « Statuts » produisait DEUX statuts en vigueur côte à
        // côte au lieu d'un courant + une version historique repliée. Le chemin de
        // remplacement existait mais n'était câblé sur AUCUN endpoint REST.
        //
        // On route donc vers le versioning dès que le slot (dossier, type, titre) est
        // déjà occupé — quelle que soit l'origine du dépôt (workflow ou manuel). Le
        // slot inclut le TITRE : deux CIN de personnes différentes restent deux
        // documents courants distincts (cf. A5), ce qui serait faux avec (dossier, type).
        boolean slotOccupe = !replacePrevious
                && documents.findCurrentBySlot(dossierId, documentType, title).isPresent();

        if (replacePrevious || slotOccupe) {
            Optional<DocumentEntity> previous = replacePrevious
                    ? documents.findCurrentByType(dossierId, documentType)
                    : documents.findCurrentBySlot(dossierId, documentType, title);
            if (previous.isPresent()) {
                DocumentEntity p = previous.get();
                documents.markReplaced(p.getId(), now);
                nextVersion = (short) (p.getVersion() + 1);
                if (ticketId != null) {
                    TicketSnapshotEntity snap = new TicketSnapshotEntity();
                    snap.setWorkspaceId(workspaceId);
                    snap.setTicketId(ticketId);
                    snap.setDocumentId(p.getId());
                    snap.setSnapshotKind("REPLACED");
                    snapshots.save(snap);
                }
            }
        }

        // 2. Upload to MinIO -- timestamp prefix evite la collision quand plusieurs
        // docs version=1 sont uploades (mode replacePrevious=false)
        String safeName = (file.getOriginalFilename() == null ? "document" : file.getOriginalFilename())
                .replaceAll("[^a-zA-Z0-9._-]", "_");
        String key = "ws/" + workspaceId + "/dossier/" + dossierId
                + "/juridique/" + documentType + "/v" + nextVersion
                + "_" + System.currentTimeMillis() + "_" + safeName;
        try (var in = file.getInputStream()) {
            storage.upload(key, in, file.getSize(), file.getContentType());
        } catch (java.io.IOException ex) {
            throw new RuntimeException("Echec lecture fichier : " + ex.getMessage(), ex);
        }

        // 3. Persist
        DocumentEntity e = new DocumentEntity();
        e.setWorkspaceId(workspaceId);
        e.setDossierId(dossierId);
        e.setTicketId(ticketId);
        e.setDocumentType(documentType);
        e.setTitle(title);
        e.setVersion(nextVersion);
        e.setCurrent(true);
        e.setObjectKey(key);
        e.setFilename(safeName);
        e.setContentType(file.getContentType());
        e.setSizeBytes(file.getSize());
        e.setUploadedBy(uploaderId);
        e.setCreatedAt(now);
        documents.save(e);

        if (ticketId != null) {
            TicketSnapshotEntity gen = new TicketSnapshotEntity();
            gen.setWorkspaceId(workspaceId);
            gen.setTicketId(ticketId);
            gen.setDocumentId(e.getId());
            gen.setSnapshotKind("GENERATED");
            snapshots.save(gen);
        }

        // Sprint 7 / TASK 5.2 -- Observer pattern : notifie le realtime-service
        // qui push WebSocket vers le client connecte au dossier.
        events.publish(new DataroomDocumentEvent(
                replacePrevious ? Kind.REPLACED : Kind.UPLOADED,
                workspaceId, dossierId, e.getId(),
                documentType, title, uploaderId, now));

        // Sprint 14 bis / D-S2-05 -- BusinessMetrics
        businessMetrics.documentUploaded(workspaceId, documentType);

        return summary(e);
    }

    @Transactional(readOnly = true)
    @Auditable(action = "DOCUMENT_DOWNLOADED", resourceType = "document", resourceIdExpr = "#documentId")
    public DocumentEntity loadForDownload(UUID documentId) {
        return documents.findById(documentId)
                .orElseThrow(() -> new NotFoundException("Document inconnu"));
    }

    // ──────────────────────────────────────────────────────────────────────
    // Versioning explicite (Sprint 2026-06-23) — endpoints
    //   POST /documents/{id}/versions          replaceAsNewVersion(...)
    //   GET  /documents/{id}/versions          listVersions(...)
    //   POST /documents/{id}/versions/{vid}/restore  restoreVersion(...)
    //   GET  /documents/{id}/versions/{vid}/download loadVersionForDownload(...)
    //
    // « Document logique » = lignage des lignes dataroom_documents partageant
    // (workspaceId, dossierId, documentType, title). On identifie un Document
    // logique par l'ID de sa version active (is_current=true), ce qui évite
    // d'inventer une nouvelle clé pour V1.
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Remplace le Document ciblé par une nouvelle version : marque l'actuelle
     * is_current=false + replaced_at=NOW + motif persisté ; insère une nouvelle
     * ligne is_current=true, version=prev+1, dans le même slot (dossier+type+title).
     *
     * @param documentId id de la version ACTIVE du Document logique
     * @param file       contenu binaire de la nouvelle version
     * @param motif      raison du remplacement (peut être null/vide)
     * @return summary de la nouvelle version active
     */
    @Transactional
    @Auditable(action = "DOCUMENT_VERSION_REPLACED", resourceType = "document", resourceIdExpr = "#documentId")
    public DocumentSummary replaceAsNewVersion(UUID documentId, MultipartFile file,
                                                String motif, UUID uploaderId) {
        UUID workspaceId = TenantContext.get();
        DocumentEntity previous = documents.findByWorkspaceIdAndId(workspaceId, documentId)
                .orElseThrow(() -> new NotFoundException("Document inconnu"));
        if (!previous.isCurrent()) {
            throw new IllegalStateException(
                    "Le document ciblé n'est plus la version active. "
                            + "Rechargez la liste pour récupérer la version courante.");
        }
        // §A — dossier archive : versionner est interdit. Seule exception, le
        // document appartient deja au ticket de LIQUIDATION en cours (regeneration
        // d'un acte de cloture) : la derogation est portee par le ticket du slot.
        archiveGuard.assertWritable(previous.getDossierId(), previous.getTicketId());
        Instant now = Instant.now();

        // 1. Marquer l'ancienne en historique + persister le motif.
        String trimmedMotif = (motif == null || motif.isBlank()) ? null : motif.trim();
        documents.markReplacedWithMotif(previous.getId(), now, trimmedMotif);

        // 2. Uploader le fichier (clé timestampée pour éviter toute collision).
        short nextVersion = (short) (previous.getVersion() + 1);
        String safeName = (file.getOriginalFilename() == null ? "document" : file.getOriginalFilename())
                .replaceAll("[^a-zA-Z0-9._-]", "_");
        String key = "ws/" + workspaceId + "/dossier/" + previous.getDossierId()
                + "/juridique/" + previous.getDocumentType() + "/v" + nextVersion
                + "_" + System.currentTimeMillis() + "_" + safeName;
        try (var in = file.getInputStream()) {
            storage.upload(key, in, file.getSize(), file.getContentType());
        } catch (java.io.IOException ex) {
            throw new RuntimeException("Echec lecture fichier : " + ex.getMessage(), ex);
        }

        // 3. Persister la nouvelle ligne dans le même slot.
        DocumentEntity e = new DocumentEntity();
        e.setWorkspaceId(workspaceId);
        e.setDossierId(previous.getDossierId());
        e.setTicketId(previous.getTicketId());
        e.setDocumentType(previous.getDocumentType());
        e.setTitle(previous.getTitle());
        e.setVersion(nextVersion);
        e.setCurrent(true);
        e.setObjectKey(key);
        e.setFilename(safeName);
        e.setContentType(file.getContentType());
        e.setSizeBytes(file.getSize());
        e.setUploadedBy(uploaderId);
        e.setCreatedAt(now);
        e.setMotif(trimmedMotif);
        documents.save(e);

        events.publish(new DataroomDocumentEvent(
                Kind.REPLACED, workspaceId, previous.getDossierId(), e.getId(),
                previous.getDocumentType(), previous.getTitle(), uploaderId, now));
        businessMetrics.documentUploaded(workspaceId, previous.getDocumentType());
        return summary(e);
    }

    /**
     * Retourne le lignage complet d'un Document logique (active + historiques),
     * trié de la plus récente à la plus ancienne. {@code documentId} peut être
     * n'importe laquelle des versions — on remonte au slot (dossier+type+title).
     */
    @Transactional(readOnly = true)
    @Auditable(action = "DOCUMENT_VERSIONS_LISTED", resourceType = "document", resourceIdExpr = "#documentId")
    public List<DocumentSummary> listVersions(UUID documentId) {
        UUID workspaceId = TenantContext.get();
        DocumentEntity anchor = documents.findByWorkspaceIdAndId(workspaceId, documentId)
                .orElseThrow(() -> new NotFoundException("Document inconnu"));
        return documents.findLineage(workspaceId, anchor.getDossierId(),
                        anchor.getDocumentType(), anchor.getTitle())
                .stream().map(this::summary).toList();
    }

    /**
     * Restaure une ancienne version : la version actuelle bascule en historique
     * (avec un motif "Restauration de la version N"), la version ciblée redevient
     * is_current=true. Pas d'INSERT — on swap les flags sur les 2 lignes.
     *
     * @param documentId id de l'une quelconque des versions (sert à retrouver le slot)
     * @param versionId  id de la version à restaurer (doit être dans le même slot)
     * @param motif      raison de la restauration (libre, peut être null)
     */
    @Transactional
    @Auditable(action = "DOCUMENT_VERSION_RESTORED", resourceType = "document", resourceIdExpr = "#versionId")
    public DocumentSummary restoreVersion(UUID documentId, UUID versionId, String motif) {
        UUID workspaceId = TenantContext.get();
        DocumentEntity anchor = documents.findByWorkspaceIdAndId(workspaceId, documentId)
                .orElseThrow(() -> new NotFoundException("Document inconnu"));
        DocumentEntity target = documents.findByWorkspaceIdAndId(workspaceId, versionId)
                .orElseThrow(() -> new NotFoundException("Version inconnue"));
        boolean sameSlot = target.getDossierId().equals(anchor.getDossierId())
                && target.getDocumentType().equals(anchor.getDocumentType())
                && target.getTitle().equals(anchor.getTitle());
        if (!sameSlot) {
            throw new IllegalArgumentException(
                    "La version ciblée n'appartient pas au même Document logique.");
        }
        // §A — restaurer une ancienne version modifie l'etat en vigueur : interdit
        // sur un dossier archive, sans derogation.
        archiveGuard.assertWritable(anchor.getDossierId());
        if (target.isCurrent()) {
            // Idempotent : déjà active.
            return summary(target);
        }
        // Trouver la version actuellement active (s'il y en a une).
        List<DocumentEntity> lineage = documents.findLineage(workspaceId,
                anchor.getDossierId(), anchor.getDocumentType(), anchor.getTitle());
        Instant now = Instant.now();
        String trimmedMotif = (motif == null || motif.isBlank())
                ? "Restauration de la version " + target.getVersion()
                : motif.trim();
        for (DocumentEntity row : lineage) {
            if (row.isCurrent() && !row.getId().equals(target.getId())) {
                documents.markReplacedWithMotif(row.getId(), now, trimmedMotif);
            }
        }
        documents.markRestored(target.getId());
        events.publish(new DataroomDocumentEvent(
                Kind.REPLACED, workspaceId, target.getDossierId(), target.getId(),
                target.getDocumentType(), target.getTitle(), null, now));
        // Re-fetch après @Modifying(clearAutomatically=true) pour renvoyer un
        // DocumentSummary avec current=true (sinon on lit la valeur stale=false).
        DocumentEntity refreshed = documents.findByWorkspaceIdAndId(workspaceId, target.getId())
                .orElseThrow(() -> new NotFoundException("Document inconnu"));
        return summary(refreshed);
    }

    /**
     * Charge une version arbitraire (active ou historique) pour téléchargement,
     * en s'assurant qu'elle appartient bien au même slot que {@code documentId}.
     */
    @Transactional(readOnly = true)
    @Auditable(action = "DOCUMENT_VERSION_DOWNLOADED", resourceType = "document", resourceIdExpr = "#versionId")
    public DocumentEntity loadVersionForDownload(UUID documentId, UUID versionId) {
        UUID workspaceId = TenantContext.get();
        DocumentEntity anchor = documents.findByWorkspaceIdAndId(workspaceId, documentId)
                .orElseThrow(() -> new NotFoundException("Document inconnu"));
        DocumentEntity target = documents.findByWorkspaceIdAndId(workspaceId, versionId)
                .orElseThrow(() -> new NotFoundException("Version inconnue"));
        boolean sameSlot = target.getDossierId().equals(anchor.getDossierId())
                && target.getDocumentType().equals(anchor.getDocumentType())
                && target.getTitle().equals(anchor.getTitle());
        if (!sameSlot) {
            throw new IllegalArgumentException(
                    "La version ciblée n'appartient pas au même Document logique.");
        }
        return target;
    }

    /**
     * Suppression d'un document juridique : marque is_current=false + replaced_at=NOW.
     * Le document n'apparait plus dans la liste "documents en vigueur" mais reste accessible
     * via l'historique des tickets clotures (snapshots existants conservees).
     */
    /**
     * Sprint 7 / TASK 4.2 -- Suppression bulk (transaction unique, rollback
     * complet si une seule suppression echoue). 1 seul event audit pour le lot.
     *
     * RG-DR : reutilise la logique soft-delete de delete(documentId) -- chaque
     * document devient is_current=false + replaced_at=NOW. Conserve les snapshots
     * historiques.
     */
    @Transactional
    @Auditable(action = "DOCUMENTS_DELETED_BULK", resourceType = "document",
               resourceIdExpr = "#documentIds")
    public int deleteBulk(List<UUID> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            throw new ma.jurika.common.exception.ValidationException(
                    "Liste de documents vide");
        }
        int n = 0;
        Instant now = Instant.now();
        for (UUID id : documentIds) {
            DocumentEntity doc = documents.findById(id)
                    .orElseThrow(() -> new NotFoundException("Document inconnu : " + id));
            // §A — suppression interdite sur un dossier archive (sans derogation).
            // Dans la transaction unique du bulk : un seul document protege annule
            // tout le lot, ce qui est le comportement voulu (rollback complet).
            archiveGuard.assertWritable(doc.getDossierId());
            n += documents.markReplaced(doc.getId(), now);
            // Sprint 7 / TASK 5.2 -- 1 event par document supprime (le realtime
            // service peut debouncer cote consumer si besoin)
            events.publish(new DataroomDocumentEvent(
                    Kind.DELETED, doc.getWorkspaceId(), doc.getDossierId(),
                    doc.getId(), doc.getDocumentType(), doc.getTitle(),
                    null, now));
        }
        return n;
    }

    /**
     * Sprint 7 / TASK 4.2 -- Export ZIP d'une selection de documents juridiques.
     * Reutilise le pattern de DataroomComptableService.exportYearAsZip.
     *
     * @param dossierId dossier scope (chaque doc verifie pour appartenance)
     * @param documentIds selection
     * @param includeOldVersions si false, filtre is_current=true uniquement
     */
    @Transactional(readOnly = true)
    public byte[] exportSelectionAsZip(UUID dossierId,
                                        List<UUID> documentIds,
                                        boolean includeOldVersions) {
        if (documentIds == null || documentIds.isEmpty()) {
            throw new ma.jurika.common.exception.ValidationException(
                    "Aucun document selectionne");
        }
        org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DataroomJuridiqueService.class);

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        int included = 0;
        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            for (UUID id : documentIds) {
                DocumentEntity doc = documents.findById(id).orElse(null);
                if (doc == null) {
                    log.warn("exportSelectionAsZip : doc {} introuvable, skip", id);
                    continue;
                }
                if (!dossierId.equals(doc.getDossierId())) {
                    log.warn("exportSelectionAsZip : doc {} hors dossier {}, skip", id, dossierId);
                    continue;
                }
                if (!includeOldVersions && !doc.isCurrent()) {
                    log.debug("exportSelectionAsZip : doc {} non current, skip", id);
                    continue;
                }
                String entryName = doc.getDocumentType() + "/" + doc.getFilename();
                zos.putNextEntry(new java.util.zip.ZipEntry(entryName));
                try (var in = storage.download(doc.getObjectKey()).stream()) {
                    in.transferTo(zos);
                    included++;
                } catch (Exception ex) {
                    log.warn("ZIP {} : echec lecture {} : {}", entryName, doc.getId(), ex.getMessage());
                }
                zos.closeEntry();
            }
            zos.putNextEntry(new java.util.zip.ZipEntry("MANIFEST.txt"));
            String manifest = "Export Dossier Juridique JURIKA\n"
                    + "Dossier              : " + dossierId + "\n"
                    + "Documents demandes   : " + documentIds.size() + "\n"
                    + "Documents inclus     : " + included + "\n"
                    + "Anciennes versions   : " + (includeOldVersions ? "incluses" : "exclues") + "\n"
                    + "Exporte              : " + Instant.now() + "\n";
            zos.write(manifest.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();
        } catch (java.io.IOException ex) {
            throw new RuntimeException("Echec generation ZIP : " + ex.getMessage(), ex);
        }
        return baos.toByteArray();
    }

    @Transactional
    @Auditable(action = "DOCUMENT_DELETED", resourceType = "document", resourceIdExpr = "#documentId")
    public void delete(UUID documentId) {
        org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DataroomJuridiqueService.class);
        UUID ws = TenantContext.get();
        log.info("deleteJuridique documentId={} workspace={}", documentId, ws);
        try {
            DocumentEntity doc = documents.findById(documentId)
                    .orElseThrow(() -> new NotFoundException("Document inconnu : " + documentId));
            if (ws != null && doc.getWorkspaceId() != null && !ws.equals(doc.getWorkspaceId())) {
                throw new NotFoundException("Document inconnu (workspace mismatch)");
            }
            // §A — suppression interdite sur un dossier archive (sans derogation).
            archiveGuard.assertWritable(doc.getDossierId());
            int n = documents.markReplaced(doc.getId(), Instant.now());
            log.info("deleteJuridique markReplaced rows={} docId={}", n, doc.getId());
            // Sprint 7 / TASK 5.2 -- notif realtime
            events.publish(new DataroomDocumentEvent(
                    Kind.DELETED, doc.getWorkspaceId(), doc.getDossierId(),
                    doc.getId(), doc.getDocumentType(), doc.getTitle(),
                    null, Instant.now()));
        } catch (Exception ex) {
            log.error("deleteJuridique CRASH doc={} : {} - {}",
                    documentId, ex.getClass().getSimpleName(), ex.getMessage(), ex);
            throw ex;
        }
    }

    /**
     * Liste des dossiers accessibles selon le role du user (scoping 2026-07-03) :
     *  - EMPLOYE            -> seulement SES dossiers (responsable_id = userId) ;
     *  - SUPERVISEUR / SUPER_ADMIN -> tous les dossiers du workspace (inchange) ;
     *  - CLIENT             -> uniquement ses dossiers (client_id = userId).
     * Dans tous les cas les dossiers RADIE sont exclus (Fix 2026-06-07 BUG 3).
     */
    @Transactional(readOnly = true)
    public List<DossierViewEntity> listDossiers(Role role, UUID userId) {
        UUID ws = TenantContext.get();
        if (ws == null) return List.of();
        if (role == Role.CLIENT) {
            return listDossiersForClient(userId);
        }
        if (role == Role.EMPLOYE) {
            // Un employe sans identite ne voit rien (fail-closed) plutot que tout.
            if (userId == null) return List.of();
            return dossiers.findAllByWorkspaceIdAndResponsableIdAndStatutNot(ws, userId, "RADIE");
        }
        // SUPERVISEUR / SUPER_ADMIN : oversight complet sur le workspace.
        return dossiers.findAllByWorkspaceIdAndStatutNot(ws, "RADIE");
    }

    /**
     * Pour le role CLIENT : retourne uniquement les dossiers dont client_id == userId.
     */
    @Transactional(readOnly = true)
    public List<DossierViewEntity> listDossiersForClient(UUID clientUserId) {
        UUID ws = TenantContext.get();
        if (ws == null || clientUserId == null) return List.of();
        return dossiers.findAllByWorkspaceIdAndStatutNot(ws, "RADIE").stream()
                .filter(d -> clientUserId.equals(d.getClientId()))
                .toList();
    }

    DocumentSummary summary(DocumentEntity e) {
        return new DocumentSummary(
                e.getId(), e.getDossierId(), e.getTicketId(),
                e.getDocumentType(), e.getTitle(),
                e.getVersion(), e.isCurrent(),
                e.getFilename(), e.getContentType(), e.getSizeBytes(),
                e.getCreatedAt(), e.getReplacedAt(),
                e.getMotif());
    }
}
