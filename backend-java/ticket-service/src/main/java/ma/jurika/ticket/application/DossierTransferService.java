package ma.jurika.ticket.application;

import ma.jurika.common.audit.AuditEventEmitter;
import ma.jurika.common.exception.ConflictException;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.notification.NotificationPublisher;
import ma.jurika.common.security.TenantContext;
import ma.jurika.ticket.domain.model.DossierReaffectation;
import ma.jurika.ticket.domain.model.DossierTransfertRequest;
import ma.jurika.ticket.domain.model.DossierTransfertRequestView;
import ma.jurika.ticket.domain.model.EntrepriseDossier;
import ma.jurika.ticket.domain.model.NatureReaffectation;
import ma.jurika.ticket.domain.model.TransfertStatut;
import ma.jurika.ticket.domain.port.DossierReaffectationRepository;
import ma.jurika.ticket.domain.port.DossierRepository;
import ma.jurika.ticket.domain.port.DossierTransfertRequestRepository;
import ma.jurika.ticket.domain.port.MemberDirectory;
import ma.jurika.ticket.domain.port.TicketEventPublisher;
import ma.jurika.ticket.domain.port.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Changement de responsable d'un dossier (V9, lot L1).
 *
 * <ul>
 *   <li><b>Transfert</b> (RG-DOS-02) : le responsable courant cree une DEMANDE
 *       EN_ATTENTE ; la cible accepte (le transfert s'applique) ou refuse ;
 *       l'initiateur peut annuler tant que non resolue.</li>
 *   <li><b>Reaffectation d'office</b> (RG-DOS-03) : le superviseur reaffecte un
 *       dossier quand l'employe responsable est absent ou a quitte le cabinet ;
 *       motif obligatoire ; la reaffectation est tracee et notifiee aux deux
 *       employes. Une demande de transfert en attente devient sans objet (annulee).</li>
 * </ul>
 *
 * <p>Application ({@link #applyTransfer}) : le responsable du dossier change ET
 * tous ses tickets suivent (RG-DOS-02 "le transfert porte sur le dossier et tous
 * ses tickets", RG-TKT-08). Le Lot Y (2026-07-04, "seuls les datarooms se
 * transferent") contredisait le cahier des charges, qui fait foi. Chaque
 * changement est trace dans {@code dossier_reaffectations} (auteur, date, ancien
 * et nouveau responsable, nature) et dans l'audit.
 */
@Service
public class DossierTransferService {

    private static final Logger log = LoggerFactory.getLogger(DossierTransferService.class);

    // Lot Y (2026-07-04) : le transfert ne reassigne plus les tickets. `ticketRepository`
    // et `eventPublisher` restent injectes (contrat de construction / cablage Spring
    // inchange) mais ne sont plus sollicites par ce service.
    private final TicketRepository ticketRepository;
    private final DossierRepository dossierRepository;
    private final DossierTransfertRequestRepository requestRepository;
    private final TicketEventPublisher eventPublisher;
    private final AuditEventEmitter auditEmitter;
    private final NotificationPublisher notificationPublisher;
    private final MemberDirectory memberDirectory;
    private final DossierReaffectationRepository reaffectations;

    public DossierTransferService(TicketRepository ticketRepository,
                                  DossierRepository dossierRepository,
                                  DossierTransfertRequestRepository requestRepository,
                                  TicketEventPublisher eventPublisher,
                                  AuditEventEmitter auditEmitter,
                                  NotificationPublisher notificationPublisher,
                                  MemberDirectory memberDirectory,
                                  DossierReaffectationRepository reaffectations) {
        this.ticketRepository = ticketRepository;
        this.dossierRepository = dossierRepository;
        this.requestRepository = requestRepository;
        this.eventPublisher = eventPublisher;
        this.auditEmitter = auditEmitter;
        this.notificationPublisher = notificationPublisher;
        this.memberDirectory = memberDirectory;
        this.reaffectations = reaffectations;
    }

    // -----------------------------------------------------------------
    // VOIE A — demande / acceptation entre employes
    // -----------------------------------------------------------------

    /** Le responsable courant cree une demande EN_ATTENTE vers un collegue. */
    @Transactional
    public DossierTransfertRequest requestTransfer(UUID workspaceId, UUID actorId,
                                                   UUID dossierId, UUID toUserId, String motif) {
        TenantContext.set(workspaceId);
        EntrepriseDossier dossier = loadDossier(workspaceId, dossierId);
        if (toUserId == null || toUserId.equals(actorId)) {
            throw new ValidationException("Le destinataire doit etre un autre employe");
        }
        // VOIE A : seul le responsable courant du dossier peut initier une demande.
        if (!actorId.equals(dossier.responsableId())) {
            throw new AccessDeniedException("Seul le responsable du dossier peut le transferer");
        }
        // Defense en profondeur (2026-06-30) : la cible DOIT etre un EMPLOYE. Un
        // SUPERVISEUR (oversight only) ne traite pas les workflows et n'a aucune
        // voie de transfert (G1) ; SUPER_ADMIN/CLIENT non plus. Le front filtre
        // deja le picker, mais on revalide cote serveur via auth-service (role
        // resolu hors de ticket-service). `roleOf` vide = membre inconnu/non
        // interne -> refus ; auth-service injoignable -> exception (fail-closed).
        String targetRole = memberDirectory.roleOf(workspaceId, toUserId).orElse(null);
        if (!"EMPLOYE".equals(targetRole)) {
            throw new ValidationException("Le destinataire d'un transfert doit etre un employe");
        }
        if (requestRepository.existsPendingForDossier(workspaceId, dossierId)) {
            throw new ConflictException("Une demande de transfert est deja en attente pour ce dossier");
        }
        DossierTransfertRequest saved = requestRepository.save(new DossierTransfertRequest(
                null, workspaceId, dossierId, actorId, toUserId,
                TransfertStatut.EN_ATTENTE, false, motif, Instant.now(), null));
        notify(toUserId, workspaceId, "transfer:received", saved, dossier.raisonSociale());
        log.info("Demande de transfert {} dossier {} {} -> {}", saved.id(), dossierId, actorId, toUserId);
        return saved;
    }

    /** La cible accepte : le transfert s'applique. */
    @Transactional
    public DossierTransfertRequest acceptTransfer(UUID workspaceId, UUID actorId, UUID requestId) {
        TenantContext.set(workspaceId);
        DossierTransfertRequest req = loadPendingForTarget(workspaceId, actorId, requestId);
        EntrepriseDossier dossier = loadDossier(workspaceId, req.dossierId());
        applyTransfer(workspaceId, dossier, req.toUserId(), actorId, req.motif(),
                NatureReaffectation.ACCEPTEE, req.id());
        DossierTransfertRequest resolved = requestRepository.save(new DossierTransfertRequest(
                req.id(), req.workspaceId(), req.dossierId(), req.fromUserId(), req.toUserId(),
                TransfertStatut.ACCEPTE, req.direct(), req.motif(), req.createdAt(), Instant.now()));
        notify(req.fromUserId(), workspaceId, "transfer:accepted", resolved, dossier.raisonSociale());
        return resolved;
    }

    /** La cible refuse : rien ne change cote dossier. */
    @Transactional
    public DossierTransfertRequest rejectTransfer(UUID workspaceId, UUID actorId, UUID requestId) {
        TenantContext.set(workspaceId);
        DossierTransfertRequest req = loadPendingForTarget(workspaceId, actorId, requestId);
        DossierTransfertRequest resolved = requestRepository.save(new DossierTransfertRequest(
                req.id(), req.workspaceId(), req.dossierId(), req.fromUserId(), req.toUserId(),
                TransfertStatut.REFUSE, req.direct(), req.motif(), req.createdAt(), Instant.now()));
        notify(req.fromUserId(), workspaceId, "transfer:rejected", resolved, null);
        return resolved;
    }

    /** L'initiateur annule sa demande tant qu'elle est EN_ATTENTE. */
    @Transactional
    public DossierTransfertRequest cancelTransfer(UUID workspaceId, UUID actorId, UUID requestId) {
        TenantContext.set(workspaceId);
        DossierTransfertRequest req = requestRepository.findById(workspaceId, requestId)
                .orElseThrow(() -> new NotFoundException("Demande de transfert introuvable"));
        if (!actorId.equals(req.fromUserId())) {
            throw new AccessDeniedException("Seul l'initiateur peut annuler la demande");
        }
        if (req.statut() != TransfertStatut.EN_ATTENTE) {
            throw new ConflictException("Demande deja resolue");
        }
        return requestRepository.save(new DossierTransfertRequest(
                req.id(), req.workspaceId(), req.dossierId(), req.fromUserId(), req.toUserId(),
                TransfertStatut.ANNULE, req.direct(), req.motif(), req.createdAt(), Instant.now()));
    }

    // -----------------------------------------------------------------
    // Reaffectation d'office par le superviseur (RG-DOS-03)
    // -----------------------------------------------------------------

    /**
     * Le superviseur reaffecte d'office le dossier a un autre employe (absence
     * prolongee, depart). Motif obligatoire ; la cible doit etre un EMPLOYE.
     */
    @Transactional
    public DossierReaffectation reaffecterDOffice(UUID workspaceId, UUID superviseurId,
                                                  UUID dossierId, UUID toUserId, String motif) {
        if (motif == null || motif.isBlank()) {
            throw new ValidationException("Le motif de la réaffectation est obligatoire.");
        }
        TenantContext.set(workspaceId);
        EntrepriseDossier dossier = loadDossier(workspaceId, dossierId);
        if (toUserId == null || toUserId.equals(dossier.responsableId())) {
            throw new ValidationException("Cet employé est déjà responsable du dossier.");
        }
        String targetRole = memberDirectory.roleOf(workspaceId, toUserId).orElse(null);
        if (!"EMPLOYE".equals(targetRole)) {
            throw new ValidationException("Le nouveau responsable doit être un employé du cabinet.");
        }
        if (!memberDirectory.estActif(workspaceId, toUserId)) {
            throw new ValidationException("Le nouveau responsable doit être un employé actif : "
                    + "ce compte est en attente ou désactivé.");
        }
        requestRepository.findPendingForDossier(workspaceId, dossierId).ifPresent(req ->
                requestRepository.save(new DossierTransfertRequest(
                        req.id(), req.workspaceId(), req.dossierId(), req.fromUserId(), req.toUserId(),
                        TransfertStatut.ANNULE, req.direct(), req.motif(), req.createdAt(), Instant.now())));
        return applyTransfer(workspaceId, dossier, toUserId, superviseurId, motif.trim(),
                NatureReaffectation.FORCEE, null);
    }

    /**
     * Historique des changements de responsable du dossier : le superviseur (en
     * observation) ou l'employe responsable ; tout autre employe recoit 404.
     */
    @Transactional(readOnly = true)
    public List<ma.jurika.ticket.domain.model.ReaffectationVue> historique(UUID workspaceId, UUID actorId,
                                                                          boolean superviseur, UUID dossierId) {
        TenantContext.set(workspaceId);
        EntrepriseDossier dossier = loadDossier(workspaceId, dossierId);
        if (!superviseur && !actorId.equals(dossier.responsableId())) {
            throw new NotFoundException("Dossier introuvable");
        }
        return reaffectations.listerVues(workspaceId, dossierId);
    }

    /** Lot L1 (D1) : rattrapages de V28 du cabinet, pour verification par le superviseur. */
    @Transactional(readOnly = true)
    public List<ma.jurika.ticket.domain.model.ReaffectationVue> rattrapages(UUID workspaceId) {
        TenantContext.set(workspaceId);
        return reaffectations.listerRattrapages(workspaceId);
    }

    /** Lot L1 (V32) : le superviseur marque un rattrapage verifie (trace : qui, quand). */
    @Transactional
    public ma.jurika.ticket.domain.model.ReaffectationVue verifierRattrapage(UUID workspaceId, UUID superviseurId,
                                                                           UUID reaffectationId) {
        TenantContext.set(workspaceId);
        var vue = reaffectations.marquerVerifie(workspaceId, reaffectationId, superviseurId)
                .orElseThrow(() -> new NotFoundException("Rattrapage introuvable"));
        auditEmitter.emit(workspaceId, superviseurId, "RATTRAPAGE_VERIFIE", "dossier", vue.dossierId(),
                Map.of("reaffectationId", reaffectationId.toString()));
        return vue;
    }

    // -----------------------------------------------------------------
    // Lectures (panneau "Transferts en attente")
    // -----------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<DossierTransfertRequestView> listInbox(UUID workspaceId, UUID userId) {
        TenantContext.set(workspaceId);
        return requestRepository.listInbox(workspaceId, userId);
    }

    @Transactional(readOnly = true)
    public List<DossierTransfertRequestView> listOutbox(UUID workspaceId, UUID userId) {
        TenantContext.set(workspaceId);
        return requestRepository.listOutbox(workspaceId, userId);
    }

    // -----------------------------------------------------------------
    // Coeur : application effective du transfert
    // -----------------------------------------------------------------

    /**
     * Applique le changement de responsable : dossier, tous ses tickets (RG-TKT-08),
     * trace {@code dossier_reaffectations}, audit et notifications.
     */
    DossierReaffectation applyTransfer(UUID workspaceId, EntrepriseDossier dossier, UUID newResponsable,
                                       UUID actorId, String motif, NatureReaffectation nature,
                                       UUID transfertId) {
        UUID dossierId = dossier.id();
        UUID ancienResponsable = dossier.responsableId();

        // 1. Le dossier change de responsable, et tous ses tickets le suivent.
        dossierRepository.updateResponsable(workspaceId, dossierId, newResponsable);
        int tickets = ticketRepository.realignerSurResponsable(workspaceId, dossierId, newResponsable);

        // 2. Trace (auteur, date, ancien et nouveau responsable, nature).
        DossierReaffectation trace = reaffectations.enregistrer(new DossierReaffectation(
                null, workspaceId, dossierId, ancienResponsable, newResponsable, nature, actorId,
                transfertId, motif, null));

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("dossierId", dossierId.toString());
        meta.put("ancienResponsable", ancienResponsable == null ? null : ancienResponsable.toString());
        meta.put("nouveauResponsable", newResponsable.toString());
        meta.put("nature", nature.name());
        meta.put("ticketsReaffectes", tickets);
        if (motif != null && !motif.isBlank()) meta.put("motif", motif);
        auditEmitter.emit(workspaceId, actorId,
                nature == NatureReaffectation.FORCEE ? "DOSSIER_REAFFECTE" : "DOSSIER_TRANSFERE",
                "dossier", dossierId, meta);

        // 3. Notifications : le nouveau responsable (sauf s'il est l'acteur) ; en cas de
        //    reaffectation d'office, l'ancien responsable aussi (RG-DOS-03).
        Map<String, Object> notifMeta = new LinkedHashMap<>();
        notifMeta.put("dossierId", dossierId.toString());
        if (!newResponsable.equals(actorId)) {
            notificationPublisher.notifyUser(newResponsable, workspaceId, "DOSSIER_TRANSFER",
                    nature == NatureReaffectation.FORCEE ? "Dossier reaffecte" : "Dossier transfere",
                    "Le dossier « " + dossier.raisonSociale() + " » vous a ete "
                            + (nature == NatureReaffectation.FORCEE ? "reaffecte par le superviseur." : "transfere."),
                    "/data-rooms", notifMeta);
        }
        if (nature == NatureReaffectation.FORCEE && ancienResponsable != null
                && !ancienResponsable.equals(actorId)) {
            notificationPublisher.notifyUser(ancienResponsable, workspaceId, "DOSSIER_TRANSFER",
                    "Dossier reaffecte",
                    "Le superviseur a reaffecte le dossier « " + dossier.raisonSociale()
                            + " » a un autre employe. Motif : " + motif,
                    "/data-rooms", notifMeta);
        }

        log.info("Changement de responsable {} dossier {} : {} -> {} ({} ticket(s) realigne(s))",
                nature, dossierId, ancienResponsable, newResponsable, tickets);
        return trace;
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private EntrepriseDossier loadDossier(UUID workspaceId, UUID dossierId) {
        return dossierRepository.findById(workspaceId, dossierId)
                .orElseThrow(() -> new NotFoundException("Dossier introuvable"));
    }

    private DossierTransfertRequest loadPendingForTarget(UUID workspaceId, UUID actorId, UUID requestId) {
        DossierTransfertRequest req = requestRepository.findById(workspaceId, requestId)
                .orElseThrow(() -> new NotFoundException("Demande de transfert introuvable"));
        if (!actorId.equals(req.toUserId())) {
            throw new AccessDeniedException("Cette demande ne vous est pas destinee");
        }
        if (req.statut() != TransfertStatut.EN_ATTENTE) {
            throw new ConflictException("Demande deja resolue");
        }
        return req;
    }

    /**
     * Emet une notification PERSISTANTE (cloche) liee a une demande de transfert.
     * Le {@code event} ("transfer:received|accepted|rejected") est traduit en
     * type/titre/message in-app. Le destinataire est la bonne partie : la cible
     * pour une demande recue, l'initiateur pour une acceptation/refus.
     */
    private void notify(UUID userId, UUID workspaceId, String event,
                        DossierTransfertRequest req, String raisonSociale) {
        try {
            String dossierLabel = (raisonSociale != null && !raisonSociale.isBlank())
                    ? "« " + raisonSociale + " »" : "un dossier";
            String title;
            String message;
            switch (event) {
                case "transfer:received" -> {
                    title = "Demande de transfert recue";
                    message = "Un collegue vous propose de reprendre le dossier "
                            + dossierLabel + ".";
                }
                case "transfer:accepted" -> {
                    title = "Transfert accepte";
                    message = "Votre demande de transfert du dossier " + dossierLabel
                            + " a ete acceptee.";
                }
                case "transfer:rejected" -> {
                    title = "Transfert refuse";
                    message = "Votre demande de transfert du dossier " + dossierLabel
                            + " a ete refusee.";
                }
                default -> {
                    title = "Transfert de dossier";
                    message = "Mise a jour du transfert du dossier " + dossierLabel + ".";
                }
            }
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("requestId", req.id().toString());
            meta.put("dossierId", req.dossierId().toString());
            meta.put("fromUserId", req.fromUserId().toString());
            meta.put("toUserId", req.toUserId().toString());
            meta.put("statut", req.statut().name());
            // Les transferts s'acceptent/refusent depuis le panneau du tableau de bord.
            notificationPublisher.notifyUser(userId, workspaceId, "DOSSIER_TRANSFER",
                    title, message, "/dashboard", meta);
        } catch (Exception ex) {
            // Best-effort : une notif ratee ne casse jamais le transfert.
            log.warn("Notification transfert {} echouee : {}", event, ex.getMessage());
        }
    }
}
