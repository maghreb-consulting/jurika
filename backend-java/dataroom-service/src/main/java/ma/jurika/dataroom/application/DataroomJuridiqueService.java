package ma.jurika.dataroom.application;

import ma.jurika.common.audit.Auditable;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.observability.BusinessMetrics;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.dataroom.api.dto.DataroomDtos.DocumentSummary;
import ma.jurika.dataroom.api.dto.DataroomDtos.DossierJuridiqueView;
import ma.jurika.dataroom.api.dto.DataroomDtos.DossierTicket;
import ma.jurika.dataroom.api.dto.DataroomDtos.GroupeDocuments;
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
import ma.jurika.dataroom.infrastructure.persistence.WorkflowProgressRow;
import ma.jurika.dataroom.infrastructure.persistence.WorkflowProgressViewJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.spec.TicketSpecifications;
import ma.jurika.dataroom.domain.port.DataroomEventPublisher;
import ma.jurika.dataroom.domain.port.DataroomEventPublisher.DataroomDocumentEvent;
import ma.jurika.dataroom.domain.port.DataroomEventPublisher.Kind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DataroomJuridiqueService {

    private static final Logger log = LoggerFactory.getLogger(DataroomJuridiqueService.class);

    private final DocumentJpaRepository documents;
    private final DossierViewJpaRepository dossiers;
    private final TicketViewJpaRepository tickets;
    /** Sous-types de MODIFICATION, pour le libelle du dossier de ticket. */
    private final WorkflowProgressViewJpaRepository workflows;
    private final ObjectMapper objectMapper;
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
    /** Lot B — le journal des changements de visibilite client (migration V32). */
    private final ma.jurika.dataroom.infrastructure.persistence.VisibiliteEvenementJpaRepository
            visibiliteEvenements;

    public DataroomJuridiqueService(DocumentJpaRepository documents,
                                     DossierViewJpaRepository dossiers,
                                     TicketViewJpaRepository tickets,
                                     WorkflowProgressViewJpaRepository workflows,
                                     ObjectMapper objectMapper,
                                     SnapshotJpaRepository snapshots,
                                     ObjectStorage storage,
                                     DataroomEventPublisher events,
                                     BusinessMetrics businessMetrics,
                                     DossierArchiveGuard archiveGuard,
                                     ma.jurika.dataroom.infrastructure.persistence
                                             .VisibiliteEvenementJpaRepository visibiliteEvenements) {
        this.documents = documents;
        this.dossiers = dossiers;
        this.tickets = tickets;
        this.workflows = workflows;
        this.objectMapper = objectMapper;
        this.snapshots = snapshots;
        this.storage = storage;
        this.events = events;
        this.businessMetrics = businessMetrics;
        this.archiveGuard = archiveGuard;
        this.visibiliteEvenements = visibiliteEvenements;
    }

    @Transactional(readOnly = true)
    public DossierJuridiqueView view(UUID dossierId) {
        return view(dossierId, null, null, null, false);
    }

    /** Surcharge de compatibilite : vue complete, sans filtre de visibilite. */
    @Transactional(readOnly = true)
    public DossierJuridiqueView view(UUID dossierId,
                                      List<String> types,
                                      java.time.Instant from,
                                      java.time.Instant to) {
        return view(dossierId, types, from, to, false);
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
                                      java.time.Instant to,
                                      boolean pourClient) {
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

        List<DocumentEntity> enVigueur = visiblesPour(
                documents.findAllByWorkspaceIdAndDossierIdAndCurrentTrueOrderByCreatedAtDesc(
                        ws, dossierId), pourClient);

        Specification<TicketViewEntity> spec = Specification
                .where(TicketSpecifications.byDossier(dossierId))
                .and(TicketSpecifications.ofStatut("CLOTURE_DOSSIER"));
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
                historique,
                buildDossiersParTicket(ws, dossierId, pourClient));
    }

    /**
     * Le dossier juridique, organise PAR TICKET.
     *
     * <p>Deux principes, tous deux destines a ce qu'aucun document ne disparaisse :
     * <ul>
     *   <li>on prend TOUS les documents du dossier, versions historiques comprises.
     *       Un acte remplace lors d'une operation ulterieure reste visible dans le
     *       ticket qui l'a produit — c'est la definition meme de l'archive ;</li>
     *   <li>un document rattache a aucun ticket (anterieur a ce lot, ou depose hors
     *       workflow) rejoint un regroupement « Hors ticket » plutot que d'etre
     *       masque.</li>
     * </ul>
     */
    /**
     * L'« emplacement » d'un document logique : c'est la cle que protege l'index
     * unique {@code ux_dataroom_documents_courant_par_slot}. Toutes les versions
     * d'un meme acte la partagent.
     */
    private static String emplacement(DocumentEntity d) {
        return d.getDocumentType() + " " + d.getTitle();
    }

    private List<DossierTicket> buildDossiersParTicket(UUID ws, UUID dossierId,
                                                        boolean pourClient) {
        List<DocumentEntity> tous = visiblesPour(
                documents.findAllByWorkspaceIdAndDossierIdOrderByCreatedAtDesc(ws, dossierId),
                pourClient);
        if (tous.isEmpty()) return List.of();

        // Lot 2 (2026-09-07) — PLUS DE VERSION AFFICHEE DEUX FOIS.
        //
        // On listait toutes les versions a plat dans le dossier du ticket. Une
        // version historique apparaissait donc DEUX fois : sous « Anciennes
        // versions » de la version courante, et comme ligne de premier niveau
        // portant les memes actions, sans rien qui la distingue — et le
        // compteur du dossier annoncait 3 pour 2 documents.
        //
        // La regle qui evite le doublon SANS reperdre d'archive : une version
        // historique n'a sa propre ligne que si la version courante de son
        // emplacement a ete produite AILLEURS (autre ticket, ou hors ticket).
        // Le principe « un ticket clos est l'archive » tient toujours : l'acte
        // remplace lors d'une operation ULTERIEURE reste visible dans le ticket
        // qui l'a produit, la ou on le cherche.
        Map<String, DocumentEntity> courantParEmplacement = new HashMap<>();
        for (DocumentEntity d : tous) {
            if (d.isCurrent()) {
                courantParEmplacement.putIfAbsent(emplacement(d), d);
            }
        }

        Map<UUID, List<DocumentEntity>> parTicket = new LinkedHashMap<>();
        List<DocumentEntity> horsTicket = new ArrayList<>();
        for (DocumentEntity d : tous) {
            if (!d.isCurrent()) {
                DocumentEntity courant = courantParEmplacement.get(emplacement(d));
                // Meme emplacement, meme ticket : la ligne courante porte deja
                // cette version dans « Anciennes versions ».
                if (courant != null && Objects.equals(courant.getTicketId(), d.getTicketId())) {
                    continue;
                }
            }
            if (d.getTicketId() == null) horsTicket.add(d);
            else parTicket.computeIfAbsent(d.getTicketId(), k -> new ArrayList<>()).add(d);
        }

        // Tickets porteurs de documents, quel que soit leur statut : un dossier en
        // cours doit se consulter comme un dossier clos.
        Map<UUID, TicketViewEntity> ticketsById = new LinkedHashMap<>();
        if (!parTicket.isEmpty()) {
            for (TicketViewEntity t : tickets.findAllById(parTicket.keySet())) {
                if (ws.equals(t.getWorkspaceId())) ticketsById.put(t.getId(), t);
            }
        }
        Map<UUID, WorkflowProgressRow> wfByTicket = new HashMap<>();
        if (!ticketsById.isEmpty()) {
            for (WorkflowProgressRow row :
                    workflows.findRowsByWorkspaceAndTickets(ws, List.copyOf(ticketsById.keySet()))) {
                wfByTicket.put(row.getTicketId(), row);
            }
        }

        List<DossierTicket> out = new ArrayList<>();
        for (Map.Entry<UUID, List<DocumentEntity>> e : parTicket.entrySet()) {
            TicketViewEntity t = ticketsById.get(e.getKey());
            if (t == null) {
                // Le ticket n'existe plus (supprime) : les documents restent, sous
                // un libelle honnete plutot que d'etre perdus.
                out.add(new DossierTicket(e.getKey(), "Opération supprimée", null, null, null,
                        null, grouper(e.getValue()), e.getValue().size()));
                continue;
            }
            List<String> sousTypes = List.of();
            WorkflowProgressRow wf = wfByTicket.get(t.getId());
            if (wf != null && "MODIFICATION".equals(t.getType())) {
                sousTypes = ModificationSousTypes.extraire(parseWorkflowJson(wf.getDataJson()));
            }
            out.add(new DossierTicket(
                    t.getId(),
                    TicketDossierLabel.compose(t.getType(), t.getReference(), t.getCreatedAt(), sousTypes),
                    t.getReference(), t.getType(), t.getStatut(), t.getCreatedAt(),
                    grouper(e.getValue()), e.getValue().size()));
        }
        // Les dossiers les plus recents d'abord ; le ticket sans date passe en fin.
        out.sort(Comparator.comparing(DossierTicket::ouvertLe,
                Comparator.nullsLast(Comparator.reverseOrder())));

        if (!horsTicket.isEmpty()) {
            out.add(new DossierTicket(null, "Hors ticket", null, null, null, null,
                    grouper(horsTicket), horsTicket.size()));
        }
        return out;
    }

    /**
     * Repartit les documents dans les trois groupes. Un groupe vide n'est pas
     * rendu ; un document dont la nature n'est pas deductible s'affiche sous
     * « Pieces client » SANS que son type soit ecrit en base.
     */
    private List<GroupeDocuments> grouper(List<DocumentEntity> docs) {
        Map<GroupeDocument, List<DocumentSummary>> parGroupe = new EnumMap<>(GroupeDocument.class);
        for (DocumentEntity d : docs) {
            GroupeDocument g = GroupeDocument.pourAffichage(d.getGroupe(), d.getDocumentType());
            parGroupe.computeIfAbsent(g, k -> new ArrayList<>()).add(summary(d));
        }
        List<GroupeDocuments> out = new ArrayList<>();
        for (GroupeDocument g : GroupeDocument.values()) {
            List<DocumentSummary> liste = parGroupe.get(g);
            if (liste != null && !liste.isEmpty()) {
                out.add(new GroupeDocuments(g.name(), g.libelle(), liste));
            }
        }
        return out;
    }

    private com.fasterxml.jackson.databind.JsonNode parseWorkflowJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            // Donnees de workflow illisibles : le libelle se passera des sous-types
            // plutot que de faire echouer l'affichage du dossier.
            log.warn("Donnees de workflow illisibles pour le libelle du dossier : {}", ex.getMessage());
            return null;
        }
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
        // Rangement dans le dossier du ticket, deduit du type. NULL si la
        // nature ne se deduit pas : on ne range pas de force dans un groupe faux.
        GroupeDocument groupe = GroupeDocument.deduire(documentType);
        e.setGroupe(groupe == null ? null : groupe.name());
        e.setVisibleClient(visibiliteParDefaut(documentType));
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

    // =================================================================
    //  BROUILLONS DE GENERATION (lot 2, 2026-09-07)
    //
    //  Un acte genere par un workflow est desormais PERSISTE des sa
    //  generation, avant toute validation. Il l'est ici, comme un document de
    //  la Data Room portant le drapeau `brouillon` — et non en base64 dans le
    //  brouillon de workflow, ni dans une table parallele : le stockage objet,
    //  le telechargement, l'apercu et l'audit existent deja a cet endroit, et
    //  la validation devient une bascule de drapeaux qui emprunte ensuite le
    //  versionnement juridique en place.
    //
    //  Aucune lecture du dossier juridique ne renvoie un brouillon
    //  (cf. DocumentJpaRepository et DocumentSpecifications).
    // =================================================================

    /**
     * Enregistre — ou remplace — le brouillon d'un acte genere.
     *
     * <p>Regenerer ou re-editer un document REMPLACE son brouillon au lieu de
     * l'empiler : l'index unique de V26 le garantit en base, et l'ancien objet
     * de stockage est supprime pour ne pas laisser de fichier orphelin.
     */
    @Transactional
    public DocumentSummary enregistrerBrouillon(UUID dossierId, UUID ticketId,
                                                 String documentType, String title,
                                                 MultipartFile file, UUID uploaderId) {
        if (ticketId == null) {
            throw new ValidationException(
                    "Un brouillon appartient a une operation : ticketId obligatoire");
        }
        // Un dossier archive n'accueille pas davantage de brouillons que d'actes.
        archiveGuard.assertWritable(dossierId, ticketId);
        UUID workspaceId = TenantContext.get();
        Instant now = Instant.now();

        String safeName = (file.getOriginalFilename() == null ? "document" : file.getOriginalFilename())
                .replaceAll("[^a-zA-Z0-9._-]", "_");
        String key = "ws/" + workspaceId + "/dossier/" + dossierId
                + "/brouillons/" + ticketId + "/" + documentType
                + "_" + System.currentTimeMillis() + "_" + safeName;
        try (var in = file.getInputStream()) {
            storage.upload(key, in, file.getSize(), file.getContentType());
        } catch (java.io.IOException ex) {
            throw new RuntimeException("Echec lecture fichier : " + ex.getMessage(), ex);
        }

        DocumentEntity e = documents.findBrouillon(workspaceId, ticketId, documentType, title)
                .orElseGet(DocumentEntity::new);
        String ancienneCle = e.getObjectKey();

        e.setWorkspaceId(workspaceId);
        e.setDossierId(dossierId);
        e.setTicketId(ticketId);
        e.setDocumentType(documentType);
        GroupeDocument groupe = GroupeDocument.deduire(documentType);
        e.setGroupe(groupe == null ? null : groupe.name());
        // Lot B — un brouillon n'est visible de personne (il n'est pas « en
        // vigueur », donc aucune lecture du dossier ne le renvoie), mais il
        // portera cette valeur en devenant l'acte valide : elle se pose donc
        // ici, à la création, et une régénération ne la réécrit pas.
        if (e.getId() == null) {
            e.setVisibleClient(visibiliteParDefaut(documentType));
        }
        e.setTitle(title);
        e.setVersion((short) 1);
        // Un brouillon n'est JAMAIS en vigueur : il reste hors de l'index unique
        // par emplacement, donc il n'occupe pas la place de l'acte valide.
        e.setCurrent(false);
        e.setBrouillon(true);
        e.setObjectKey(key);
        e.setFilename(safeName);
        e.setContentType(file.getContentType());
        e.setSizeBytes(file.getSize());
        e.setUploadedBy(uploaderId);
        if (e.getCreatedAt() == null) e.setCreatedAt(now);
        e.setReplacedAt(null);
        documents.save(e);

        if (ancienneCle != null && !ancienneCle.equals(key)) {
            try {
                storage.delete(ancienneCle);
            } catch (Exception ex) {
                // Le brouillon a bien ete remplace ; un objet orphelin ne doit
                // pas faire echouer la generation.
                log.warn("Brouillon : ancien objet non supprime ({}) : {}", ancienneCle, ex.getMessage());
            }
        }
        return summary(e);
    }

    /** Les brouillons d'une operation — ré-hydratation de l'etape de generation. */
    @Transactional(readOnly = true)
    public List<DocumentSummary> listBrouillons(UUID ticketId) {
        UUID ws = TenantContext.get();
        if (ws == null) return List.of();
        return documents.findBrouillonsByTicket(ws, ticketId).stream().map(this::summary).toList();
    }

    /**
     * Valide un brouillon : il devient l'acte en vigueur de son emplacement.
     *
     * <p>C'est ici que le brouillon rejoint le versionnement juridique existant.
     * Si l'emplacement (dossier, type, titre) est deja occupe, l'occupant
     * bascule en historique par {@code markReplaced} et le nouveau document
     * prend le numero de version suivant — exactement comme un depot manuel.
     * Le document garde son identifiant : ce qu'on a apercu et edite est ce qui
     * est valide, sans recopie.
     */
    @Transactional
    public DocumentSummary validerBrouillon(UUID documentId, String motif, UUID acteurId) {
        UUID ws = TenantContext.get();
        DocumentEntity e = documents.findByWorkspaceIdAndId(ws, documentId)
                .orElseThrow(() -> new NotFoundException("Brouillon inconnu"));
        if (!e.isBrouillon()) {
            // Idempotent : valider deux fois n'est pas une erreur pour l'employe.
            return summary(e);
        }
        archiveGuard.assertWritable(e.getDossierId(), e.getTicketId());
        Instant now = Instant.now();

        short version = 1;
        Optional<DocumentEntity> occupant =
                documents.findCurrentBySlot(e.getDossierId(), e.getDocumentType(), e.getTitle());
        if (occupant.isPresent() && !occupant.get().getId().equals(e.getId())) {
            DocumentEntity p = occupant.get();
            // Lot B — LA VISIBILITÉ SE TRANSMET À LA VERSION SUIVANTE.
            //
            // Régénérer un acte n'est pas décider de le montrer. Si l'annonce
            // légale du statut 2 a été masquée au client, celle qu'on complète
            // du numéro RC au statut 4 doit l'être aussi : sans cette ligne, la
            // valeur par défaut reprenait la main et la décision de l'employé
            // était silencieusement annulée par une régénération.
            //
            // Lu AVANT `markReplaced`, dont le `clearAutomatically` vide le
            // contexte de persistance.
            e.setVisibleClient(p.isVisibleClient());
            if (motif == null || motif.isBlank()) {
                documents.markReplaced(p.getId(), now);
            } else {
                documents.markReplacedWithMotif(p.getId(), now, motif);
            }
            version = (short) (p.getVersion() + 1);
            TicketSnapshotEntity snap = new TicketSnapshotEntity();
            snap.setWorkspaceId(e.getWorkspaceId());
            snap.setTicketId(e.getTicketId());
            snap.setDocumentId(p.getId());
            snap.setSnapshotKind("REPLACED");
            snapshots.save(snap);
        }

        e.setBrouillon(false);
        e.setCurrent(true);
        e.setVersion(version);
        e.setMotif(motif);
        documents.save(e);

        TicketSnapshotEntity gen = new TicketSnapshotEntity();
        gen.setWorkspaceId(e.getWorkspaceId());
        gen.setTicketId(e.getTicketId());
        gen.setDocumentId(e.getId());
        gen.setSnapshotKind("GENERATED");
        snapshots.save(gen);

        events.publish(new DataroomDocumentEvent(
                version > 1 ? Kind.REPLACED : Kind.UPLOADED,
                e.getWorkspaceId(), e.getDossierId(), e.getId(),
                e.getDocumentType(), e.getTitle(), acteurId, now));
        businessMetrics.documentUploaded(e.getWorkspaceId(), e.getDocumentType());
        return summary(e);
    }

    /** Abandonne un brouillon : ligne et objet de stockage supprimes. */
    @Transactional
    public void supprimerBrouillon(UUID documentId) {
        UUID ws = TenantContext.get();
        DocumentEntity e = documents.findByWorkspaceIdAndId(ws, documentId)
                .orElseThrow(() -> new NotFoundException("Brouillon inconnu"));
        if (!e.isBrouillon()) {
            throw new ValidationException("Ce document n'est pas un brouillon");
        }
        documents.delete(e);
        try {
            storage.delete(e.getObjectKey());
        } catch (Exception ex) {
            log.warn("Brouillon supprime, objet non purge ({}) : {}", e.getObjectKey(), ex.getMessage());
        }
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
        e.setGroupe(previous.getGroupe());
        // Une nouvelle version HERITE de la visibilite de celle qu'elle remplace :
        // remplacer un fichier n'est pas decider de le montrer.
        e.setVisibleClient(previous.isVisibleClient());
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
     * Arborescence {type}/{filename} + MANIFEST.
     *
     * @param dossierId dossier scope (chaque doc verifie pour appartenance)
     * @param documentIds selection
     * @param includeOldVersions si false, filtre is_current=true uniquement
     */
    @Transactional(readOnly = true)
    public byte[] exportSelectionAsZip(UUID dossierId,
                                        List<UUID> documentIds,
                                        boolean includeOldVersions) {
        return exportSelectionAsZip(dossierId, documentIds, includeOldVersions, false);
    }

    /** Lot L0 (E19) : {@code pourClient} exclut les documents non visibles client. */
    @Transactional(readOnly = true)
    public byte[] exportSelectionAsZip(UUID dossierId,
                                        List<UUID> documentIds,
                                        boolean includeOldVersions,
                                        boolean pourClient) {
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
                if (pourClient && !doc.isVisibleClient()) {
                    log.debug("exportSelectionAsZip : doc {} non visible client, skip", id);
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
                e.getMotif(), e.getEditeManuellementAt(),
                e.isVisibleClient());
    }

    // =====================================================================
    //  Lot B — la visibilite client, appliquee cote SERVEUR
    // =====================================================================

    /**
     * Retire de la liste ce que le client ne doit pas voir.
     *
     * <p>Le filtre est pose ICI, sur la liste issue de la base, et non a
     * l'affichage : un indicateur que seule l'interface respecterait ne serait pas
     * une visibilite, ce serait une convention. Les employes, superviseurs et
     * super-admins voient tout, y compris ce qui est masque — ils doivent pouvoir
     * constater qu'une piece existe et decider de la montrer.
     */
    /**
     * LA VALEUR PAR DEFAUT DE « VISIBLE POUR LE CLIENT », A CHAQUE DEPOT.
     *
     * <p>Visible, sauf pour le type {@code AUTRE}.
     *
     * <p>La Data Room EST le dossier du client : les pieces que le parcours fait
     * televerser sont, par construction, les « justificatifs a obtenir et
     * archiver » de ses propres formalites. Les masquer par defaut rendrait son
     * dossier silencieusement incomplet, et l'oubli le plus probable — un employe
     * qui ne pense pas a cocher « visible » — le priverait d'une piece a laquelle
     * il a droit sans que personne le sache. L'oubli inverse, lui, se voit.
     *
     * <p>{@code AUTRE} est l'exception, et c'est la seule : c'est le fourre-tout
     * des documents dont la nature n'est pas deductible, donc le seul endroit ou
     * une note interne peut atterrir. Un recepisse de depot n'a pas le meme statut
     * qu'une note interne — la difference tient a ce que le referentiel sait
     * nommer.
     *
     * <p>Dans les deux sens, le reglage reste un clic, au depot comme depuis la
     * Data Room.
     */
    static boolean visibiliteParDefaut(String documentType) {
        return !"AUTRE".equals(documentType);
    }

    static List<DocumentEntity> visiblesPour(List<DocumentEntity> docs, boolean pourClient) {
        if (!pourClient) return docs;
        return docs.stream().filter(DocumentEntity::isVisibleClient).toList();
    }

    /**
     * Lot L0 (E18, RG-DR-07) : un CLIENT n'accede au contenu d'un document
     * (apercu, telechargement, version) que s'il est VISIBLE client ET rattache
     * a SON dossier. Document illisible ou masque : 404 ; dossier d'un autre
     * client : 403 ({@link #assertClientAccess}).
     */
    @Transactional(readOnly = true)
    public void assertDocumentPourClient(UUID documentId, UUID clientUserId) {
        UUID ws = TenantContext.get();
        DocumentEntity d = ws == null ? null
                : documents.findByWorkspaceIdAndId(ws, documentId).orElse(null);
        if (d == null || !d.isVisibleClient()) {
            throw new NotFoundException("Document inconnu");
        }
        assertClientAccess(d.getDossierId(), clientUserId);
    }

    /**
     * Ce document est-il montrable a cet utilisateur ? Utilise aux acces
     * UNITAIRES — apercu, telechargement, versions — ou aucune liste ne filtre.
     */
    @Transactional(readOnly = true)
    public boolean estVisiblePour(UUID documentId, boolean pourClient) {
        if (!pourClient) return true;
        return documents.findById(documentId)
                .map(DocumentEntity::isVisibleClient)
                .orElse(false);
    }

    /**
     * Montrer ou masquer un document, depuis la Data Room ou depuis le panneau de
     * cochage — c'est la meme colonne, et le meme journal.
     *
     * <p>Le changement est JOURNALISE. Retirer une piece de la vue du client est
     * une decision ; la rendre a nouveau visible en est une autre. « Qui a masque
     * ce recepisse, et quand ? » doit avoir une reponse.
     *
     * @param origine {@code DEPOT}, {@code DATAROOM} ou {@code WORKFLOW}
     */
    @Transactional
    public DocumentSummary changerVisibilite(UUID documentId, boolean visible,
                                              String origine, UUID acteurId) {
        UUID ws = TenantContext.get();
        DocumentEntity doc = documents.findById(documentId)
                .filter(d -> ws == null || ws.equals(d.getWorkspaceId()))
                .orElseThrow(() -> new NotFoundException("Document inconnu"));
        if (doc.isVisibleClient() != visible) {
            doc.setVisibleClient(visible);
            documents.save(doc);
        }
        visibiliteEvenements.enregistrer(doc.getWorkspaceId(), doc.getId(), visible,
                origine, acteurId);
        return summary(doc);
    }
}
