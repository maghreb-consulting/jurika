package ma.jurika.ticket.application;

import ma.jurika.common.audit.AuditEventEmitter;
import ma.jurika.common.exception.ConflictException;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.notification.NotificationPublisher;
import ma.jurika.common.security.TenantContext;
import ma.jurika.ticket.domain.model.DossierTransfertRequest;
import ma.jurika.ticket.domain.model.DossierTransfertRequestView;
import ma.jurika.ticket.domain.model.EntrepriseDossier;
import ma.jurika.ticket.domain.model.TransfertStatut;
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
 * Transfert d'un dossier d'un employe a un autre (V9).
 *
 * <p>Le transfert est reserve aux EMPLOYES (operation de production). Une seule
 * voie, par consentement :
 * <ul>
 *   <li><b>VOIE A</b> (entre employes, par consentement) : le responsable courant
 *       cree une DEMANDE EN_ATTENTE ; la cible accepte (le transfert s'applique)
 *       ou refuse. L'initiateur peut annuler tant que non resolue.</li>
 * </ul>
 *
 * <p>Le SUPERVISEUR (oversight only) n'a aucune voie de transfert : la voie B
 * directe a ete retiree. Il garde uniquement la lecture des listes inbox/outbox.
 *
 * <p>L'application d'un transfert ({@link #applyTransfer}) — Lot Y (2026-07-04),
 * <b>« seuls les datarooms se transferent, pas les tickets »</b> :
 * <ol>
 *   <li>met a jour {@code entreprise_dossiers.responsable_id} (owner durable) :
 *       le dataroom (dossier) change de responsable ;</li>
 *   <li>ecrit un evenement d'audit {@code DOSSIER_TRANSFERE} (ancien -> nouveau
 *       responsable) pour la tracabilite.</li>
 * </ol>
 *
 * <p>Les tickets NE sont PLUS reassignes : ils gardent leur {@code assigne_id}
 * d'origine (et un eventuel badge « Transfere » du Lot R deja pose reste
 * historique). Le nouvel employe responsable les voit malgre tout via le scoping
 * par {@code responsable_id} (Lot S2) — retirer la reassignation ne lui cache donc
 * pas les tickets, ca change seulement le fait qu'on ne touche plus leur assignation.
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

    public DossierTransferService(TicketRepository ticketRepository,
                                  DossierRepository dossierRepository,
                                  DossierTransfertRequestRepository requestRepository,
                                  TicketEventPublisher eventPublisher,
                                  AuditEventEmitter auditEmitter,
                                  NotificationPublisher notificationPublisher,
                                  MemberDirectory memberDirectory) {
        this.ticketRepository = ticketRepository;
        this.dossierRepository = dossierRepository;
        this.requestRepository = requestRepository;
        this.eventPublisher = eventPublisher;
        this.auditEmitter = auditEmitter;
        this.notificationPublisher = notificationPublisher;
        this.memberDirectory = memberDirectory;
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
        applyTransfer(workspaceId, dossier, req.toUserId(), actorId, req.motif(), false);
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
     * Applique le transfert — Lot Y : owner durable (le dataroom se deplace) + audit
     * {@code DOSSIER_TRANSFERE}. Les tickets ne sont plus reassignes (ils gardent leur
     * {@code assigne_id} d'origine ; le nouveau responsable les voit via le scoping
     * {@code responsable_id} du Lot S2).
     */
    void applyTransfer(UUID workspaceId, EntrepriseDossier dossier, UUID newResponsable,
                       UUID actorId, String motif, boolean direct) {
        UUID dossierId = dossier.id();
        UUID ancienResponsable = dossier.responsableId();

        // 1. Owner durable : le dataroom (dossier) change de responsable.
        dossierRepository.updateResponsable(workspaceId, dossierId, newResponsable);

        // 2. Audit DOSSIER_TRANSFERE (tracabilite E2). Plus aucun ticket n'est reassigne.
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("dossierId", dossierId.toString());
        meta.put("ancienResponsable", ancienResponsable == null ? null : ancienResponsable.toString());
        meta.put("nouveauResponsable", newResponsable.toString());
        meta.put("direct", direct);
        if (motif != null && !motif.isBlank()) meta.put("motif", motif);
        auditEmitter.emit(workspaceId, actorId, "DOSSIER_TRANSFERE", "dossier", dossierId, meta);

        // 3. Notification persistante au nouveau responsable — SAUF quand il est lui-meme
        //    l'acteur (cas VOIE A : la cible accepte, inutile de la notifier de sa propre
        //    action). On notifie des qu'il y a un nouveau responsable (plus de compteur
        //    de tickets, ceux-ci ne bougent plus).
        if (!newResponsable.equals(actorId)) {
            Map<String, Object> notifMeta = new LinkedHashMap<>();
            notifMeta.put("dossierId", dossierId.toString());
            notificationPublisher.notifyUser(newResponsable, workspaceId, "DOSSIER_TRANSFER",
                    "Dossier transfere",
                    "Le dossier « " + dossier.raisonSociale() + " » vous a ete transfere.",
                    "/data-rooms", notifMeta);
        }

        log.info("Transfert dossier {} : {} -> {} (dataroom uniquement, direct={})",
                dossierId, ancienResponsable, newResponsable, direct);
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
