package ma.jurika.ticket.application;

import ma.jurika.common.audit.AuditEventEmitter;
import ma.jurika.common.exception.BusinessException;
import ma.jurika.common.exception.ConflictException;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.security.TenantContext;
import ma.jurika.ticket.domain.model.Debours;
import ma.jurika.ticket.domain.model.DeboursSaisie;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.port.DataroomDocumentLookup;
import ma.jurika.ticket.domain.port.DataroomDocumentLookup.DocumentVu;
import ma.jurika.ticket.domain.port.DeboursRepository;
import ma.jurika.ticket.domain.port.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Debours d'un ticket (cahier des charges 10.1).
 * <ul>
 *   <li>RG-DEB-01 : une ligne par frais (libelle, montant, type, piece justificative deposee
 *       en Data Room du dossier) ;</li>
 *   <li>RG-DEB-02 / P7 : chaque ajout, modification ou suppression est trace dans
 *       {@code audit_log} (ressource : le ticket ; ancienne et nouvelle valeur) ;</li>
 *   <li>RG-TKT-11 : ticket clos ou annule, lecture seule (garde serveur).</li>
 * </ul>
 * Le droit d'agir (employe en charge, RG-DOS-01) est controle avant par {@link AccesDeboursTicket}.
 */
@Service
public class DeboursUseCase {

    static final String AJOUT = "DEBOURS_AJOUTE";
    static final String MODIFICATION = "DEBOURS_MODIFIE";
    static final String SUPPRESSION = "DEBOURS_SUPPRIME";

    private final DeboursRepository repository;
    private final TicketRepository tickets;
    private final DataroomDocumentLookup documents;
    private final AuditEventEmitter audit;

    public DeboursUseCase(DeboursRepository repository, TicketRepository tickets,
                          DataroomDocumentLookup documents, AuditEventEmitter audit) {
        this.repository = repository;
        this.tickets = tickets;
        this.documents = documents;
        this.audit = audit;
    }

    public record Summary(List<Debours> items, BigDecimal total) {}

    @Transactional
    public Debours ajouter(UUID workspaceId, UUID actorId, UUID ticketId, DeboursSaisie saisie) {
        TenantContext.set(workspaceId);
        Ticket ticket = ticketModifiable(workspaceId, ticketId);
        DeboursSaisie s = verifierPiece(workspaceId, ticket, saisie);
        Debours cree = repository.create(workspaceId, ticketId, s, actorId);
        tracer(workspaceId, actorId, ticketId, AJOUT, cree.id(), null, cree);
        return cree;
    }

    @Transactional
    public Debours modifier(UUID workspaceId, UUID actorId, UUID ticketId, UUID deboursId, DeboursSaisie saisie) {
        TenantContext.set(workspaceId);
        Debours avant = ligneDuTicket(workspaceId, ticketId, deboursId);
        Ticket ticket = ticketModifiable(workspaceId, ticketId);
        DeboursSaisie s = verifierPiece(workspaceId, ticket, saisie);
        Debours apres = repository.update(workspaceId, deboursId, s);
        tracer(workspaceId, actorId, ticketId, MODIFICATION, deboursId, avant, apres);
        return apres;
    }

    @Transactional
    public void supprimer(UUID workspaceId, UUID actorId, UUID ticketId, UUID deboursId) {
        TenantContext.set(workspaceId);
        Debours avant = ligneDuTicket(workspaceId, ticketId, deboursId);
        ticketModifiable(workspaceId, ticketId);
        repository.delete(workspaceId, deboursId);
        tracer(workspaceId, actorId, ticketId, SUPPRESSION, deboursId, avant, null);
    }

    @Transactional(readOnly = true)
    public Summary listForTicket(UUID workspaceId, UUID ticketId) {
        TenantContext.set(workspaceId);
        List<Debours> items = repository.findByTicket(workspaceId, ticketId);
        BigDecimal total = items.stream().map(Debours::montantMad).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Summary(items, total);
    }

    /** La ligne doit appartenir au ticket du chemin (404 sinon). */
    private Debours ligneDuTicket(UUID workspaceId, UUID ticketId, UUID deboursId) {
        return repository.findById(workspaceId, deboursId)
                .filter(d -> ticketId.equals(d.ticketId()))
                .orElseThrow(() -> new NotFoundException("D\u00e9bours inconnu."));
    }

    /**
     * RG-TKT-11 : un ticket clos s'ouvre en lecture seule ; un ticket annule aussi, tant qu'il
     * n'est pas repris. Ticket illisible dans le workspace : 404.
     */
    private Ticket ticketModifiable(UUID workspaceId, UUID ticketId) {
        Ticket t = tickets.findById(workspaceId, ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket inconnu"));
        if (t.statut() == TicketStatut.CLOTURE_DOSSIER) {
            throw new ConflictException("TICKET_CLOS_LECTURE_SEULE : le ticket est clos, "
                    + "ses d\u00e9bours ne peuvent plus \u00eatre modifi\u00e9s (RG-TKT-11).");
        }
        if (t.statut() == TicketStatut.ANNULE) {
            throw new ConflictException("TICKET_ANNULE_LECTURE_SEULE : le ticket est annul\u00e9, "
                    + "ses d\u00e9bours ne peuvent plus \u00eatre modifi\u00e9s tant qu\u2019il n\u2019est pas repris.");
        }
        return t;
    }

    /**
     * La piece justificative est un document de la Data Room DU DOSSIER du ticket, dans le
     * meme cabinet. Sans nom de fichier saisi, le titre du document de la Data Room le donne.
     */
    private DeboursSaisie verifierPiece(UUID workspaceId, Ticket ticket, DeboursSaisie s) {
        if (s.pieceDocumentId() == null) return s;
        if (ticket.dossierId() == null) {
            throw new BusinessException("DEBOURS_PIECE_SANS_DOSSIER",
                    "Ce ticket n\u2019est rattach\u00e9 \u00e0 aucun dossier : la pi\u00e8ce justificative ne peut "
                            + "pas \u00eatre d\u00e9pos\u00e9e en Data Room.");
        }
        DocumentVu doc = documents.findByIds(workspaceId, List.of(s.pieceDocumentId())).stream()
                .filter(d -> ticket.dossierId().equals(d.dossierId()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "Pi\u00e8ce justificative introuvable dans la Data Room du dossier"));
        String nom = (s.pieceJointeFilename() == null || s.pieceJointeFilename().isBlank())
                ? doc.title() : s.pieceJointeFilename().trim();
        return new DeboursSaisie(s.libelle(), s.categorie(), s.montant(), s.dateEngagement(),
                s.pieceDocumentId(), nom, s.notes());
    }

    /** P7 : auteur, date (audit_log) ; ancienne et nouvelle valeur de la ligne. */
    private void tracer(UUID workspaceId, UUID actorId, UUID ticketId, String action, UUID deboursId,
                        Debours avant, Debours apres) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("deboursId", deboursId.toString());
        meta.put("avant", avant == null ? null : valeur(avant));
        meta.put("apres", apres == null ? null : valeur(apres));
        audit.emit(workspaceId, actorId, action, "ticket", ticketId, meta);
    }

    static Map<String, Object> valeur(Debours d) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("libelle", d.libelle());
        v.put("type", d.categorie().name());
        v.put("montant", d.montantMad() == null ? null : d.montantMad().toPlainString());
        v.put("dateEngagement", d.dateEngagement() == null ? null : d.dateEngagement().toString());
        v.put("pieceDocumentId", d.pieceDocumentId() == null ? null : d.pieceDocumentId().toString());
        v.put("piece", d.pieceJointeFilename());
        v.put("notes", d.notes());
        return v;
    }
}
