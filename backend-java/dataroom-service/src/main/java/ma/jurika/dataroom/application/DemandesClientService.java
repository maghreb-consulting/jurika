package ma.jurika.dataroom.application;

import ma.jurika.common.exception.ConflictException;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.notification.NotificationPublisher;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.api.dto.DataroomDtos.ComplementRequest;
import ma.jurika.dataroom.api.dto.DataroomDtos.CreateDemandeRequest;
import ma.jurika.dataroom.api.dto.DataroomDtos.CreateRequeteRequest;
import ma.jurika.dataroom.application.access.ClientAccessLogger;
import ma.jurika.dataroom.api.dto.DataroomDtos.DemandeSummary;
import ma.jurika.dataroom.api.dto.DataroomDtos.DemandeSupervisionRow;
import ma.jurika.dataroom.api.dto.DataroomDtos.RepondreRequest;
import ma.jurika.dataroom.api.dto.DataroomDtos.UpdateDemandeStatusRequest;
import ma.jurika.dataroom.infrastructure.persistence.DemandeEntity;
import ma.jurika.dataroom.infrastructure.persistence.DemandeJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewEntity;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.UserViewEntity;
import ma.jurika.dataroom.infrastructure.persistence.UserViewJpaRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DemandesClientService {

    private static final Set<String> STATUTS = Set.of("NON_TRAITEE", "EN_COURS", "TRAITEE");

    // Lot AG -- machine a etats des requetes EMPLOYE_TO_CLIENT.
    private static final String CLIENT_TO_EMPLOYE = "CLIENT_TO_EMPLOYE";
    private static final String EMPLOYE_TO_CLIENT = "EMPLOYE_TO_CLIENT";
    private static final Set<String> TYPES_REQUETE = Set.of("PIECE", "INFO", "SIGNATURE");
    // Statuts actionnables COTE CLIENT (repondre) : OUVERTE ou A_COMPLETER.
    private static final Set<String> CLIENT_ACTIONNABLE = Set.of("OUVERTE", "A_COMPLETER");

    private final DemandeJpaRepository repo;
    private final DossierViewJpaRepository dossiers;
    private final UserViewJpaRepository users;
    private final ClientAccessLogger accessLogger;
    private final NotificationPublisher notifier;
    /**
     * Lot DIVERS §A — societe dissoute : plus aucune nouvelle demande ni requete
     * client. Les echanges deja ouverts restent consultables et cloturables.
     */
    private final DossierArchiveGuard archiveGuard;

    public DemandesClientService(DemandeJpaRepository repo, DossierViewJpaRepository dossiers,
                                 UserViewJpaRepository users,
                                 ClientAccessLogger accessLogger, NotificationPublisher notifier,
                                 DossierArchiveGuard archiveGuard) {
        this.repo = repo;
        this.dossiers = dossiers;
        this.users = users;
        this.accessLogger = accessLogger;
        this.notifier = notifier;
        this.archiveGuard = archiveGuard;
    }

    private static String directionOf(DemandeEntity e) {
        return e.getDirection() == null ? CLIENT_TO_EMPLOYE : e.getDirection();
    }

    @Transactional
    public DemandeSummary create(CreateDemandeRequest req, UUID userId) {
        UUID ws = TenantContext.get();
        // §A — societe dissoute : la Data Room est figee, plus de nouvelle demande.
        archiveGuard.assertWritable(req.dossierId());
        DemandeEntity e = new DemandeEntity();
        e.setWorkspaceId(ws);
        e.setDossierId(req.dossierId());
        e.setSoumisPar(userId);
        e.setSujet(req.sujet());
        // Description facultative -> colonne NOT NULL : on stocke "" si absente.
        e.setDescription(req.description() == null ? "" : req.description());
        e.setStatut("NON_TRAITEE");
        repo.save(e);

        // Lot X -- tracer la demande dans l'"Activite client" (best-effort, sans
        // document). Le compteur "Acces client" ne compte que user_id = client_id,
        // donc une demande creee par un EMPLOYE n'y apparait pas : inoffensif.
        accessLogger.log(req.dossierId(), null, "DEMANDE", ws, userId);
        return summary(e);
    }

    @Transactional(readOnly = true)
    public List<DemandeSummary> listByDossier(UUID dossierId, UUID userId, Role role) {
        return listByDossier(dossierId, userId, role, CLIENT_TO_EMPLOYE);
    }

    /**
     * Lot AG — variante avec filtre {@code direction} (defaut CLIENT_TO_EMPLOYE
     * pour la retro-compat). {@code EMPLOYE_TO_CLIENT} = les requetes de l'employe
     * au client (ecran « Demandes de mon conseiller » cote client).
     */
    @Transactional(readOnly = true)
    public List<DemandeSummary> listByDossier(UUID dossierId, UUID userId, Role role, String direction) {
        // Defense-in-depth multi-tenant : filtre workspace_id explicite. La RLS
        // n'est PAS fiable (jurika_user a BYPASSRLS sous le conteneur officiel).
        // Sans ce filtre, un user pouvait lire les demandes client d'un dossier
        // d'un autre workspace en devinant son UUID (PII / contenu confidentiel).
        UUID ws = TenantContext.get();
        if (ws == null) return List.of();
        // Scoping par role (2026-07-03) : l'onglet Demandes d'un dossier ne doit
        // PAS fuiter entre employes. Coherent avec le PATCH updateStatus qui gate
        // deja le traitement par l'ownership du dossier.
        //   - EMPLOYE   : uniquement s'il est responsable du dossier -> sinon 403 ;
        //   - CLIENT    : uniquement SON dossier (RG-DR15)          -> sinon 403 ;
        //   - SUPERVISEUR / SUPER_ADMIN : non restreints (oversight plateforme).
        if (role == Role.EMPLOYE || role == Role.CLIENT) {
            UUID owner = dossiers.findByWorkspaceIdAndId(ws, dossierId)
                    .map(d -> role == Role.CLIENT ? d.getClientId() : d.getResponsableId())
                    .orElse(null);
            if (userId == null || !userId.equals(owner)) {
                throw new AccessDeniedException(role == Role.CLIENT
                        ? "Acces refuse a ce dossier (RG-DR15 : un client n'a acces qu'a SON dossier)."
                        : "Vous n'etes pas responsable de ce dossier : acces aux demandes interdit.");
            }
        }
        String dir = direction == null ? CLIENT_TO_EMPLOYE : direction;
        return repo.findAllByWorkspaceIdAndDossierIdOrderByCreatedAtDesc(ws, dossierId)
                .stream().filter(e -> dir.equals(directionOf(e)))
                .map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public List<DemandeSummary> listAll(String statutFilter) {
        return listAll(statutFilter, CLIENT_TO_EMPLOYE);
    }

    @Transactional(readOnly = true)
    public List<DemandeSummary> listAll(String statutFilter, String direction) {
        UUID ws = TenantContext.get();
        if (ws == null) return List.of();
        String dir = direction == null ? CLIENT_TO_EMPLOYE : direction;
        List<DemandeEntity> found = (statutFilter != null && !statutFilter.isBlank())
                ? repo.findAllByWorkspaceIdAndStatutOrderByCreatedAtDesc(ws, statutFilter)
                : repo.findAllByWorkspaceIdOrderByCreatedAtDesc(ws);
        return found.stream().filter(e -> dir.equals(directionOf(e))).map(this::summary).toList();
    }

    /**
     * Vue SUPERVISEUR enrichie (workspace-wide) : toutes les demandes OU requetes
     * du cabinet, avec le nom du dataroom ({@code raisonSociale}) et le nom de
     * l'employe responsable du dossier resolus <b>par jointure</b> (jamais d'UUID
     * brut). Reserve SUPERVISEUR / SUPER_ADMIN (RBAC controller) : lecture seule,
     * aucune donnee metier n'est mutee (RG-U02-04).
     *
     * <p>Isolation multi-tenant : filtre {@code workspace_id} explicite partout
     * (la RLS ne suffit pas, jurika_user a BYPASSRLS). Les jointures dossiers /
     * users sont elles aussi scopees au workspace courant.
     */
    @Transactional(readOnly = true)
    public List<DemandeSupervisionRow> listAllEnriched(String direction, String statutFilter) {
        UUID ws = TenantContext.get();
        if (ws == null) return List.of();
        String dir = direction == null ? CLIENT_TO_EMPLOYE : direction;
        List<DemandeEntity> found = (statutFilter != null && !statutFilter.isBlank())
                ? repo.findAllByWorkspaceIdAndStatutOrderByCreatedAtDesc(ws, statutFilter)
                : repo.findAllByWorkspaceIdOrderByCreatedAtDesc(ws);
        List<DemandeEntity> filtered = found.stream()
                .filter(e -> dir.equals(directionOf(e))).toList();
        if (filtered.isEmpty()) return List.of();

        // Jointure dossiers (raisonSociale + responsable_id) scopee workspace.
        Map<UUID, DossierViewEntity> dossierById = dossiers.findAllByWorkspaceId(ws).stream()
                .collect(Collectors.toMap(DossierViewEntity::getId, Function.identity(),
                        (a, b) -> a, LinkedHashMap::new));
        // Jointure noms employes responsables (batch, scopee workspace).
        Set<UUID> responsableIds = dossierById.values().stream()
                .map(DossierViewEntity::getResponsableId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> nameById = responsableIds.isEmpty() ? Map.of()
                : users.findAllByWorkspaceIdAndIdIn(ws, responsableIds).stream()
                        .collect(Collectors.toMap(UserViewEntity::getId, UserViewEntity::displayName,
                                (a, b) -> a));

        return filtered.stream().map(e -> {
            DossierViewEntity d = dossierById.get(e.getDossierId());
            String raison = d != null ? d.getRaisonSociale() : null;
            UUID responsableId = d != null ? d.getResponsableId() : null;
            String responsableNom = responsableId != null ? nameById.get(responsableId) : null;
            return new DemandeSupervisionRow(e.getId(), e.getDossierId(), raison,
                    responsableId, responsableNom, e.getSujet(), e.getDescription(),
                    e.getStatut(), directionOf(e), e.getTypeRequete(), e.getCreatedAt());
        }).toList();
    }

    /**
     * Scoping EMPLOYE (2026-07-03) — un employe ne voit que les demandes des
     * dossiers dont il est le responsable ({@code entreprise_dossiers.responsable_id
     * = employeeId}). Apres un transfert de dossier, le nouvel employe herite
     * donc de ses demandes ; l'ancien ne les voit plus. SUPERVISEUR / SUPER_ADMIN
     * gardent la vue globale via {@link #listAll(String)}.
     *
     * <p>Fail-closed : un employe sans identite ou sans dossier ne voit rien (on
     * ne retombe jamais sur la liste workspace complete).
     */
    @Transactional(readOnly = true)
    public List<DemandeSummary> listAllForEmployee(UUID employeeId, String statutFilter) {
        return listAllForEmployee(employeeId, statutFilter, CLIENT_TO_EMPLOYE);
    }

    @Transactional(readOnly = true)
    public List<DemandeSummary> listAllForEmployee(UUID employeeId, String statutFilter, String direction) {
        UUID ws = TenantContext.get();
        if (ws == null || employeeId == null) return List.of();
        String dir = direction == null ? CLIENT_TO_EMPLOYE : direction;
        List<UUID> dossierIds = dossiers
                .findAllByWorkspaceIdAndResponsableIdAndStatutNot(ws, employeeId, "RADIE")
                .stream().map(DossierViewEntity::getId).toList();
        // Aucun dossier -> aucune demande. On court-circuite pour ne pas emettre
        // un IN () invalide vers le repo (cf. contrat du finder).
        if (dossierIds.isEmpty()) return List.of();
        List<DemandeEntity> found = (statutFilter != null && !statutFilter.isBlank())
                ? repo.findAllByWorkspaceIdAndDossierIdInAndStatutOrderByCreatedAtDesc(ws, dossierIds, statutFilter)
                : repo.findAllByWorkspaceIdAndDossierIdInOrderByCreatedAtDesc(ws, dossierIds);
        return found.stream().filter(e -> dir.equals(directionOf(e))).map(this::summary).toList();
    }

    @Transactional
    public DemandeSummary updateStatus(UUID id, UpdateDemandeStatusRequest req, UUID userId, Role role) {
        if (!STATUTS.contains(req.statut())) {
            throw new ValidationException("Statut invalide. Valeurs : " + STATUTS);
        }
        // Defense-in-depth : findByWorkspaceIdAndId au lieu de findById brut
        // (la RLS n'est pas fiable car jurika_user a BYPASSRLS). Sans ce filtre,
        // un EMPLOYE de A pouvait UPDATE le statut d'une demande de B en
        // devinant son UUID.
        UUID wsForUpdate = TenantContext.get();
        if (wsForUpdate == null) {
            throw new NotFoundException("Demande introuvable");
        }
        DemandeEntity e = repo.findByWorkspaceIdAndId(wsForUpdate, id)
                .orElseThrow(() -> new NotFoundException("Demande introuvable"));
        // Ownership (2026-07-03) : un EMPLOYE ne traite QUE les demandes des
        // dossiers dont il est responsable. Un autre employe (ex. l'ancien
        // responsable apres transfert) recoit 403. SUPER_ADMIN n'est pas bloque
        // (override plateforme) ; le SUPERVISEUR reste oversight/lecture (Lot G)
        // et n'atteint pas ce endpoint (RBAC controller).
        if (role == Role.EMPLOYE) {
            UUID responsable = dossiers.findByWorkspaceIdAndId(wsForUpdate, e.getDossierId())
                    .map(DossierViewEntity::getResponsableId)
                    .orElse(null);
            if (userId == null || !userId.equals(responsable)) {
                throw new AccessDeniedException(
                        "Vous n'etes pas responsable de ce dossier : traitement interdit.");
            }
        }
        if ("TRAITEE".equals(req.statut()) && !"TRAITEE".equals(e.getStatut())) {
            e.setTraiteAt(Instant.now());
        }
        e.setStatut(req.statut());
        e.setPrisEnChargePar(userId);
        if (req.noteInterne() != null) e.setNoteInterne(req.noteInterne());
        if (req.ticketId() != null) e.setTicketId(req.ticketId());
        repo.save(e);
        return summary(e);
    }

    // =====================================================================
    // Lot AG -- Requetes EMPLOYE_TO_CLIENT (machine a etats, cloture 2 etapes).
    //   OUVERTE --(client repond)--> REPONDUE --(employe valide)--> CLOTUREE
    //                                  REPONDUE --(employe +)--> A_COMPLETER --(client)--> REPONDUE
    // =====================================================================

    /** L'employe responsable (ou superviseur) cree une requete au client -> OUVERTE. */
    @Transactional
    public DemandeSummary createRequete(CreateRequeteRequest req, UUID userId, Role role) {
        UUID ws = TenantContext.get();
        if (ws == null) throw new NotFoundException("Dossier introuvable");
        if (req.typeRequete() == null || !TYPES_REQUETE.contains(req.typeRequete())) {
            throw new ValidationException("type_requete invalide. Valeurs : " + TYPES_REQUETE);
        }
        DossierViewEntity dossier = dossiers.findByWorkspaceIdAndId(ws, req.dossierId())
                .orElseThrow(() -> new NotFoundException("Dossier introuvable"));
        requireResponsableOrSuperviseur(dossier, userId, role);
        // §A — societe dissoute : plus de nouvelle requete au client.
        archiveGuard.assertWritable(req.dossierId());

        DemandeEntity e = new DemandeEntity();
        e.setWorkspaceId(ws);
        e.setDossierId(req.dossierId());
        e.setSoumisPar(userId);          // createur = l'employe (EMPLOYE_TO_CLIENT)
        e.setPrisEnChargePar(userId);    // employe en charge du suivi
        e.setSujet(req.sujet());
        e.setDescription(req.description() == null ? "" : req.description());
        e.setDirection(EMPLOYE_TO_CLIENT);
        e.setTypeRequete(req.typeRequete());
        e.setStatut("OUVERTE");
        repo.save(e);

        // Notifie le client du dossier (best-effort).
        notify(dossier.getClientId(), ws, "Nouvelle requete de votre conseiller",
                req.sujet(), "/mes-requetes", e.getId(), req.dossierId());
        return summary(e);
    }

    /** Le CLIENT du dossier repond/fournit -> REPONDUE (depuis OUVERTE ou A_COMPLETER). */
    @Transactional
    public DemandeSummary repondre(UUID id, RepondreRequest req, UUID userId, Role role) {
        UUID ws = TenantContext.get();
        DemandeEntity e = loadRequete(ws, id);
        DossierViewEntity dossier = requireDossier(ws, e.getDossierId());
        // Seul le CLIENT du dossier peut repondre.
        if (role != Role.CLIENT || userId == null || !userId.equals(dossier.getClientId())) {
            throw new AccessDeniedException("Seul le client du dossier peut repondre a cette requete.");
        }
        if (!CLIENT_ACTIONNABLE.contains(e.getStatut())) {
            throw new ConflictException("Transition invalide : la requete n'est pas en attente de votre reponse.");
        }
        e.setStatut("REPONDUE");
        e.setReponduAt(Instant.now());
        if (req != null && req.noteClient() != null) e.setNoteClient(req.noteClient());
        repo.save(e);
        // Tracer l'activite client (comme un depot/demande).
        accessLogger.log(e.getDossierId(), null, "DEMANDE", ws, userId);
        // Notifie le responsable courant.
        notify(dossier.getResponsableId(), ws, "Requete traitee par le client",
                e.getSujet(), "/data-rooms?dossier=" + e.getDossierId() + "&tab=demandes",
                e.getId(), e.getDossierId());
        return summary(e);
    }

    /** L'employe responsable (ou superviseur) valide -> CLOTUREE (depuis REPONDUE). */
    @Transactional
    public DemandeSummary valider(UUID id, UUID userId, Role role) {
        UUID ws = TenantContext.get();
        DemandeEntity e = loadRequete(ws, id);
        DossierViewEntity dossier = requireDossier(ws, e.getDossierId());
        requireResponsableOrSuperviseur(dossier, userId, role);
        if (!"REPONDUE".equals(e.getStatut())) {
            throw new ConflictException("Transition invalide : seule une requete REPONDUE peut etre cloturee.");
        }
        e.setStatut("CLOTUREE");
        e.setClotureAt(Instant.now());
        repo.save(e);
        notify(dossier.getClientId(), ws, "Requete cloturee",
                e.getSujet(), "/mes-requetes", e.getId(), e.getDossierId());
        return summary(e);
    }

    /** L'employe responsable (ou superviseur) demande un complement -> A_COMPLETER (depuis REPONDUE). */
    @Transactional
    public DemandeSummary complement(UUID id, ComplementRequest req, UUID userId, Role role) {
        UUID ws = TenantContext.get();
        DemandeEntity e = loadRequete(ws, id);
        DossierViewEntity dossier = requireDossier(ws, e.getDossierId());
        requireResponsableOrSuperviseur(dossier, userId, role);
        if (!"REPONDUE".equals(e.getStatut())) {
            throw new ConflictException("Transition invalide : un complement se demande sur une requete REPONDUE.");
        }
        e.setStatut("A_COMPLETER");
        if (req != null && req.note() != null) e.setNoteInterne(req.note());
        repo.save(e);
        notify(dossier.getClientId(), ws, "Complement demande sur une requete",
                e.getSujet(), "/mes-requetes", e.getId(), e.getDossierId());
        return summary(e);
    }

    // ------------------------------------------------------------------ helpers

    private DemandeEntity loadRequete(UUID ws, UUID id) {
        if (ws == null) throw new NotFoundException("Requete introuvable");
        DemandeEntity e = repo.findByWorkspaceIdAndId(ws, id)
                .orElseThrow(() -> new NotFoundException("Requete introuvable"));
        if (!EMPLOYE_TO_CLIENT.equals(directionOf(e))) {
            throw new ConflictException("Cette demande n'est pas une requete au client.");
        }
        return e;
    }

    private DossierViewEntity requireDossier(UUID ws, UUID dossierId) {
        return dossiers.findByWorkspaceIdAndId(ws, dossierId)
                .orElseThrow(() -> new NotFoundException("Dossier introuvable"));
    }

    private void requireResponsableOrSuperviseur(DossierViewEntity dossier, UUID userId, Role role) {
        boolean superviseur = role == Role.SUPERVISEUR || role == Role.SUPER_ADMIN;
        boolean responsable = userId != null && userId.equals(dossier.getResponsableId());
        if (!superviseur && !responsable) {
            throw new AccessDeniedException("Vous n'etes pas responsable de ce dossier.");
        }
    }

    private void notify(UUID userId, UUID ws, String title, String message,
                        String actionUrl, UUID demandeId, UUID dossierId) {
        if (userId == null || ws == null) return;
        try {
            notifier.notifyUser(userId, ws, "DEMANDE_CLIENT", title, message, actionUrl,
                    Map.of("demandeId", demandeId.toString(), "dossierId", dossierId.toString()));
        } catch (Exception ex) {
            // Best-effort : une notif ratee ne casse jamais la transition.
        }
    }

    DemandeSummary summary(DemandeEntity e) {
        return new DemandeSummary(e.getId(), e.getDossierId(), e.getSoumisPar(),
                e.getSujet(), e.getDescription(), e.getStatut(), e.getTicketId(),
                e.getPrisEnChargePar(), e.getNoteInterne(), e.getTraiteAt(), e.getCreatedAt(),
                directionOf(e), e.getTypeRequete(), e.getReponduAt(), e.getClotureAt(), e.getNoteClient());
    }
}
