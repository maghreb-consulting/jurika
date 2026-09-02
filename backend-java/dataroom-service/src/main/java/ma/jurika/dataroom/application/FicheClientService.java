package ma.jurika.dataroom.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.common.audit.Auditable;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.api.dto.FicheClientDtos.DocumentEnVigueur;
import ma.jurika.dataroom.api.dto.FicheClientDtos.DocumentHistoryEntry;
import ma.jurika.dataroom.api.dto.FicheClientDtos.FicheClientView;
import ma.jurika.dataroom.api.dto.FicheClientDtos.Identity;
import ma.jurika.dataroom.api.dto.FicheClientDtos.Operation;
import ma.jurika.dataroom.infrastructure.pdf.FicheClientPdf;
import ma.jurika.dataroom.infrastructure.persistence.DocumentEntity;
import ma.jurika.dataroom.infrastructure.persistence.DocumentJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewEntity;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.TicketViewEntity;
import ma.jurika.dataroom.infrastructure.persistence.TicketViewJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.UserViewEntity;
import ma.jurika.dataroom.infrastructure.persistence.UserViewJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.WorkflowProgressRow;
import ma.jurika.dataroom.infrastructure.persistence.WorkflowProgressViewJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.WorkspaceViewEntity;
import ma.jurika.dataroom.infrastructure.persistence.WorkspaceViewJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Assemble et rend la « Fiche client » (carte d'identite juridique d'une
 * societe) — demande directeur 2026-07-14. Reserve EMPLOYE (responsable du
 * dossier) / SUPERVISEUR / SUPER_ADMIN ; le CLIENT n'y a jamais acces.
 *
 * <p>Dates des operations (section 2) — decision documentee dans le livrable :
 * <ul>
 *   <li><b>debut</b> = date d'ouverture du ticket ({@code tickets.created_at}) ;</li>
 *   <li><b>finalisation</b> = date de cloture du ticket ({@code tickets.cloture_at}),
 *       null tant que l'operation est en cours ;</li>
 *   <li><b>date de l'acte</b> = date juridique stockee dans le workflow
 *       ({@code workflow_progress.data.step1.datePV}) si presente, sinon absente.</li>
 * </ul>
 */
@Service
public class FicheClientService {

    private static final Logger log = LoggerFactory.getLogger(FicheClientService.class);

    private final DocumentJpaRepository documents;
    private final DossierViewJpaRepository dossiers;
    private final TicketViewJpaRepository tickets;
    private final WorkflowProgressViewJpaRepository workflows;
    private final UserViewJpaRepository users;
    private final WorkspaceViewJpaRepository workspaces;
    private final ObjectMapper objectMapper;

    public FicheClientService(DocumentJpaRepository documents,
                              DossierViewJpaRepository dossiers,
                              TicketViewJpaRepository tickets,
                              WorkflowProgressViewJpaRepository workflows,
                              UserViewJpaRepository users,
                              WorkspaceViewJpaRepository workspaces,
                              ObjectMapper objectMapper) {
        this.documents = documents;
        this.dossiers = dossiers;
        this.tickets = tickets;
        this.workflows = workflows;
        this.users = users;
        this.workspaces = workspaces;
        this.objectMapper = objectMapper;
    }

    // ── Libelles ────────────────────────────────────────────────────────────

    /** Types de tickets/workflows -> libelle metier. */
    static final Map<String, String> TYPE_LABELS = Map.ofEntries(
            Map.entry("CREATION", "Création de société"),
            Map.entry("IMPORT", "Import de dossier existant"),
            Map.entry("MODIFICATION", "Modification statutaire"),
            Map.entry("DISSOLUTION", "Dissolution"),
            Map.entry("LIQUIDATION", "Liquidation"),
            Map.entry("SUCCURSALE_MA", "Ouverture de succursale (Maroc)"),
            Map.entry("SUCCURSALE_ETR", "Ouverture de succursale (société étrangère)"),
            Map.entry("FERMETURE_SUCCURSALE", "Fermeture de succursale"),
            Map.entry("PV_AGO", "Assemblée générale ordinaire (PV AGO)"));

    /** 28 sous-types de MODIFICATION (alignes MOD_CATEGORIES front) -> libelle. */
    static final Map<String, String> MODIFICATION_LABELS = Map.ofEntries(
            Map.entry("CHANGEMENT_DENOMINATION", "changement de dénomination"),
            Map.entry("CHANGEMENT_OBJET", "changement de l'objet social"),
            Map.entry("TRANSFERT_SIEGE", "transfert du siège social"),
            Map.entry("PROROGATION_DUREE", "prorogation de la durée"),
            Map.entry("TRANSFORMATION", "transformation de la forme juridique"),
            Map.entry("AUGMENTATION_CAPITAL", "augmentation de capital (numéraire)"),
            Map.entry("AUGMENTATION_CAPITAL_NATURE", "augmentation de capital (apport en nature)"),
            Map.entry("AUGMENTATION_CAPITAL_RESERVES", "augmentation de capital (incorporation de réserves)"),
            Map.entry("REDUCTION_CAPITAL", "réduction de capital"),
            Map.entry("MODIF_VALEUR_NOMINALE", "modification de la valeur nominale des parts"),
            Map.entry("CESSION_PARTIELLE", "cession partielle de parts"),
            Map.entry("CESSION_TOTALE", "cession totale de parts"),
            Map.entry("TRANSMISSION_PARTS", "transmission de parts (succession / donation)"),
            Map.entry("NANTISSEMENT", "nantissement de parts"),
            Map.entry("DESIGNATION_GERANT", "nomination d'un gérant"),
            Map.entry("REVOCATION_GERANT", "révocation d'un gérant"),
            Map.entry("MODIF_NOMBRE_GERANTS", "modification du nombre / durée des gérants"),
            Map.entry("MODIF_POUVOIRS_GERANT", "modification des pouvoirs / rémunération du gérant"),
            Map.entry("MODALITES_DECISIONS", "modalités de décisions"),
            Map.entry("DESIGNATION_CAC", "désignation d'un commissaire aux comptes"),
            Map.entry("CLAUSE_AGREMENT", "clause d'agrément"),
            Map.entry("CLAUSE_PREEMPTION", "clause de préemption / inaliénabilité"),
            Map.entry("PACTE_ASSOCIES", "pacte d'associés"),
            Map.entry("CONTINUATION_PERTES", "continuation malgré pertes"),
            Map.entry("FUSION_SCISSION", "fusion / scission / apport partiel"),
            Map.entry("CREATION_SUCCURSALE", "création / transfert / suppression de succursale"),
            Map.entry("POUVOIRS_FORMALITES", "pouvoirs pour formalités"));

    // ── API ───────────────────────────────────────────────────────────────

    /**
     * Assemble la Fiche client d'un dossier, en appliquant l'acces (responsable
     * du dossier pour un EMPLOYE ; jamais un CLIENT). Audite.
     */
    @Transactional(readOnly = true)
    @Auditable(action = "FICHE_CLIENT_GENERATED", resourceType = "dossier",
               resourceIdExpr = "#dossierId")
    public FicheClientView assemble(UUID dossierId, Role role, UUID userId) {
        UUID ws = TenantContext.get();
        if (ws == null) {
            throw new NotFoundException("Dossier inconnu");
        }
        DossierViewEntity dossier = dossiers.findByWorkspaceIdAndId(ws, dossierId)
                .orElseThrow(() -> new NotFoundException("Dossier inconnu"));
        assertCanAccess(dossier, role, userId);

        Identity identity = new Identity(
                dossier.getRaisonSociale(), dossier.getFormeJuridique(), dossier.getIce(),
                dossier.getRcNumero(), dossier.getRcTribunal(), dossier.getIdentifiantFiscal(),
                dossier.getTaxeProfessionnelle(), dossier.getCnss(), dossier.getAdresseSiege(),
                dossier.getVille(), dossier.getCapitalSocialMad(), dossier.getDateConstitution(),
                dossier.getStatut());

        // Papier a en-tete du cabinet (nom resolu + logo + coordonnees + mentions).
        // Resolution unique via CabinetIdentity.resolve.
        ma.jurika.common.pdf.CabinetIdentity cabinet = workspaces.findById(ws)
                .map(w -> ma.jurika.common.pdf.CabinetIdentity.resolve(
                        w.getNomAfficheDocuments(), w.getName(),
                        w.getLogoBytes(), w.getLogoContentType(),
                        w.getAdresse(), w.getTelephone(), w.getContactEmail(), w.getSiteWeb(),
                        w.getIce(), w.getRcNumber(), w.getIfFiscal()))
                .orElse(ma.jurika.common.pdf.CabinetIdentity.ofName(null, "Cabinet"));

        List<Operation> operations = buildOperations(ws, dossierId);
        List<DocumentEntity> allDocs =
                documents.findAllByWorkspaceIdAndDossierIdOrderByCreatedAtDesc(ws, dossierId);
        List<DocumentEnVigueur> enVigueur = buildDocumentsEnVigueur(allDocs);
        List<DocumentHistoryEntry> historique = buildDocumentHistory(ws, allDocs);

        return new FicheClientView(dossierId, cabinet, identity, operations,
                enVigueur, historique, Instant.now());
    }

    /** Rend une Fiche deja assemblee en PDF (charte JURIKA). Fonction pure. */
    public byte[] renderPdf(FicheClientView view) {
        return new FicheClientPdf(view).generate();
    }

    // ── Acces ───────────────────────────────────────────────────────────────

    private void assertCanAccess(DossierViewEntity dossier, Role role, UUID userId) {
        // Defense-in-depth : le CLIENT est deja exclu par @PreAuthorize cote
        // controller ; on re-verrouille ici pour ne jamais rendre la Fiche a un
        // client meme si l'endpoint evoluait.
        if (role == Role.CLIENT) {
            throw new AccessDeniedException("La Fiche client n'est pas accessible au client.");
        }
        if (role == Role.EMPLOYE) {
            UUID resp = dossier.getResponsableId();
            if (resp == null || !resp.equals(userId)) {
                throw new AccessDeniedException(
                        "Seul le responsable du dossier peut générer la Fiche client.");
            }
        }
        // SUPERVISEUR / SUPER_ADMIN : oversight complet du workspace.
    }

    // ── Section 2 : operations ────────────────────────────────────────────────

    private List<Operation> buildOperations(UUID ws, UUID dossierId) {
        // Copie defensive : le tri en place ne doit pas dependre de la mutabilite
        // de la liste renvoyee par le repository.
        List<TicketViewEntity> tks =
                new ArrayList<>(tickets.findAllByDossierIdAndStatutNot(dossierId, "ANNULE"));
        if (tks.isEmpty()) return List.of();

        // Chronologie : plus recentes d'abord (par date d'ouverture).
        tks.sort(Comparator.comparing(TicketViewEntity::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        Map<UUID, WorkflowProgressRow> wfByTicket = new HashMap<>();
        List<UUID> ids = tks.stream().map(TicketViewEntity::getId).toList();
        for (WorkflowProgressRow row : workflows.findRowsByWorkspaceAndTickets(ws, ids)) {
            wfByTicket.put(row.getTicketId(), row);
        }

        List<Operation> out = new ArrayList<>(tks.size());
        for (TicketViewEntity t : tks) {
            String type = t.getType();
            String typeLabel = TYPE_LABELS.getOrDefault(type, humanize(type));
            WorkflowProgressRow wf = wfByTicket.get(t.getId());
            String sousType = null;
            LocalDate dateActe = null;
            if (wf != null) {
                JsonNode data = parseJson(wf.getDataJson());
                if ("MODIFICATION".equals(type)) {
                    sousType = modificationSousType(data);
                }
                dateActe = extractActeDate(data);
            }
            boolean finalisee = "CLOTURE".equals(t.getStatut());
            out.add(new Operation(type, typeLabel, sousType, t.getReference(),
                    t.getCreatedAt(), t.getClotureAt(), dateActe, finalisee));
        }
        return out;
    }

    /** Extrait « changement de gerant, ... » depuis data.step1.selectedTypes. */
    private String modificationSousType(JsonNode data) {
        JsonNode selected = firstNonMissing(
                data.path("step1").path("selectedTypes"),
                data.path("selectedTypes"));
        if (selected == null || !selected.isArray() || selected.isEmpty()) return null;
        List<String> labels = new ArrayList<>();
        for (JsonNode n : selected) {
            String id = n.asText();
            labels.add(MODIFICATION_LABELS.getOrDefault(id, humanize(id)));
        }
        return labels.isEmpty() ? null : String.join(", ", labels);
    }

    /** date juridique de l'acte : step1.datePV, sinon dateActe / dateEffet. */
    private LocalDate extractActeDate(JsonNode data) {
        JsonNode step1 = data.path("step1");
        for (String key : List.of("datePV", "dateActe", "dateEffet")) {
            JsonNode v = firstNonMissing(step1.path(key), data.path(key));
            LocalDate d = parseLocalDate(v == null ? null : v.asText(null));
            if (d != null) return d;
        }
        return null;
    }

    // ── Section 3 : documents en vigueur ──────────────────────────────────────

    private List<DocumentEnVigueur> buildDocumentsEnVigueur(List<DocumentEntity> allDocs) {
        return allDocs.stream()
                .filter(DocumentEntity::isCurrent)
                .sorted(Comparator.comparing(DocumentEntity::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(d -> new DocumentEnVigueur(
                        d.getDocumentType(), d.getTitle(), d.getCreatedAt(), d.getVersion()))
                .toList();
    }

    // ── Section 4 : historique documentaire ───────────────────────────────────

    private List<DocumentHistoryEntry> buildDocumentHistory(UUID ws, List<DocumentEntity> allDocs) {
        // Resolution batch des auteurs (uploaded_by) -> « Prenom Nom ».
        Set<UUID> authorIds = new HashSet<>();
        for (DocumentEntity d : allDocs) {
            if (d.getUploadedBy() != null) authorIds.add(d.getUploadedBy());
        }
        Map<UUID, String> nameById = new HashMap<>();
        if (!authorIds.isEmpty()) {
            for (UserViewEntity u : users.findAllByWorkspaceIdAndIdIn(ws, authorIds)) {
                nameById.put(u.getId(), u.displayName());
            }
        }

        List<DocumentEntity> sorted = new ArrayList<>(allDocs);
        sorted.sort(Comparator.comparing(DocumentEntity::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        List<DocumentHistoryEntry> out = new ArrayList<>(sorted.size());
        for (DocumentEntity d : sorted) {
            String action = classifyAction(d);
            String auteur = d.getUploadedBy() == null ? "—"
                    : nameById.getOrDefault(d.getUploadedBy(), "—");
            out.add(new DocumentHistoryEntry(d.getCreatedAt(), action,
                    d.getDocumentType(), d.getTitle(), d.getVersion(), auteur, d.getMotif()));
        }
        return out;
    }

    private static String classifyAction(DocumentEntity d) {
        String motif = d.getMotif();
        if (motif != null && motif.toLowerCase().startsWith("restaur")) return "RESTAURATION";
        return d.getVersion() <= 1 ? "AJOUT" : "REMPLACEMENT";
    }

    // ── Helpers JSON / format ────────────────────────────────────────────────

    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) return objectMapper.nullNode();
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            log.debug("Fiche client : data workflow illisible : {}", ex.getMessage());
            return objectMapper.nullNode();
        }
    }

    private static JsonNode firstNonMissing(JsonNode a, JsonNode b) {
        if (a != null && !a.isMissingNode() && !a.isNull()) return a;
        if (b != null && !b.isMissingNode() && !b.isNull()) return b;
        return null;
    }

    private static LocalDate parseLocalDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            // Tolere une date ISO ou un datetime ISO (on ne garde que la partie date).
            String date = s.length() >= 10 ? s.substring(0, 10) : s;
            return LocalDate.parse(date);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    /** Repli lisible pour un code type inconnu : « MODIF_XXX » -> « modif xxx ». */
    private static String humanize(String code) {
        if (code == null || code.isBlank()) return "—";
        return code.replace('_', ' ').toLowerCase();
    }
}
