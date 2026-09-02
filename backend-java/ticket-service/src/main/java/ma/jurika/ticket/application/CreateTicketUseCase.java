package ma.jurika.ticket.application;

import ma.jurika.common.audit.Auditable;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.observability.BusinessMetrics;
import ma.jurika.common.security.TenantContext;
import ma.jurika.ticket.domain.model.EntrepriseDossier;
import ma.jurika.ticket.domain.model.FormeJuridique;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketPriorite;
import ma.jurika.ticket.domain.model.TicketType;
import ma.jurika.ticket.domain.port.DossierRepository;
import ma.jurika.ticket.domain.port.TicketEventPublisher;
import ma.jurika.ticket.domain.port.TicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
public class CreateTicketUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateTicketUseCase.class);

    /**
     * Types qui creent automatiquement un dossier d'entreprise (et donc un Data Room).
     */
    private static final Set<TicketType> AUTO_CREATE_DOSSIER = Set.of(
            TicketType.CREATION, TicketType.IMPORT);

    private final TicketRepository ticketRepository;
    private final DossierRepository dossierRepository;
    private final DossierIdempotenceLookup dossierLookup;
    private final TicketEventPublisher eventPublisher;
    private final BusinessMetrics businessMetrics;

    public CreateTicketUseCase(TicketRepository ticketRepository,
                                DossierRepository dossierRepository,
                                DossierIdempotenceLookup dossierLookup,
                                TicketEventPublisher eventPublisher,
                                BusinessMetrics businessMetrics) {
        this.ticketRepository = ticketRepository;
        this.dossierRepository = dossierRepository;
        this.dossierLookup = dossierLookup;
        this.eventPublisher = eventPublisher;
        this.businessMetrics = businessMetrics;
    }

    public record Command(
            UUID workspaceId,
            String titre,
            TicketType type,
            TicketPriorite priorite,
            UUID dossierId,
            UUID assigneId,
            UUID creeParId,
            String description,
            Instant deadline,
            /** Renseigne pour CREATION et IMPORT : provoque la creation automatique du dossier + Data Room. */
            CompanyInfo companyInfo
    ) {}

    public record CompanyInfo(String raisonSociale, FormeJuridique formeJuridique) {}

    @Transactional
    @Auditable(action = "TICKET_CREATED", resourceType = "ticket")
    public Ticket execute(Command cmd) {
        TenantContext.set(cmd.workspaceId());

        UUID dossierId = cmd.dossierId();
        if (AUTO_CREATE_DOSSIER.contains(cmd.type()) && dossierId == null) {
            CompanyInfo info = cmd.companyInfo();
            if (info == null
                    || info.raisonSociale() == null || info.raisonSociale().isBlank()
                    || info.formeJuridique() == null) {
                throw new ValidationException(
                        "Pour un ticket " + cmd.type().name() + ", la raison sociale et la forme juridique sont obligatoires");
            }
            // Fix 2026-06-07 (BUG 2) — Idempotence : si un dossier VIVANT
            // avec la meme raison sociale existe deja dans le workspace, on
            // le reutilise au lieu d'en creer un nouveau. Cela protege contre
            // les double-submit cote front (StrictMode / clic-clic).
            //
            // Filet de securite en plus : Flyway V6 ajoute un index unique
            // partiel (workspace_id, lower(raison_sociale)) WHERE statut alive.
            // Si la course concurrente passe l'application, l'index DB
            // levera une DataIntegrityViolationException sur le 2e INSERT.
            String trimmedRaison = info.raisonSociale().trim();
            // Fix 2026-06-07 (BUG 2) — Idempotence : tout le bloc
            // findAlive+save+retry tourne dans une transaction SEPAREE
            // (REQUIRES_NEW) via DossierIdempotenceLookup, pour eviter
            // que le rollback-only marque par Hibernate sur une
            // DataIntegrityViolationException ne casse aussi le ticket.
            EntrepriseDossier saved;
            try {
                // responsable_id = createur du ticket : le dossier (et donc le
                // Data Room) appartient a l'employe qui le cree, condition du
                // scoping "un employe ne voit que ses dossiers".
                saved = dossierLookup.getOrCreateAlive(
                        cmd.workspaceId(), trimmedRaison, info.formeJuridique(), cmd.type(),
                        cmd.creeParId());
            } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                // Course concurrente : on n'a pas pu inserer, on re-lit
                // dans une transaction encore separee, qui voit l'insert
                // du concurrent maintenant qu'il est committe.
                saved = dossierLookup.findAliveInNewTx(cmd.workspaceId(), trimmedRaison)
                        .orElseThrow(() -> ex);
                log.info("Idempotence race resolved : reuse dossier {} workspace {}",
                        saved.id(), cmd.workspaceId());
            }
            dossierId = saved.id();
        }

        // V9 — Coherence owner durable : un nouveau ticket sur un dossier existant
        // herite par defaut du responsable du dossier (sauf assignation explicite),
        // pour eviter une double verite divergente entre dossier et tickets.
        UUID assigneId = cmd.assigneId();
        if (assigneId == null && dossierId != null) {
            assigneId = dossierRepository.findById(cmd.workspaceId(), dossierId)
                    .map(EntrepriseDossier::responsableId)
                    .orElse(null);
        }

        String reference = ticketRepository.generateReference();
        Ticket ticket = ticketRepository.create(
                cmd.workspaceId(), reference, cmd.titre(), cmd.type(),
                cmd.priorite() == null ? TicketPriorite.NORMALE : cmd.priorite(),
                dossierId, assigneId, cmd.creeParId(),
                cmd.description(), cmd.deadline());

        // Fix 2026-06-07 (BUG 2) — Trace l'origine du dossier UNIQUEMENT pour
        // les types AUTO_CREATE_DOSSIER (CREATION / IMPORT) et SEULEMENT si
        // la colonne created_by_ticket_id est encore nulle + statut
        // EN_CONSTITUTION (cf DossierJpaRepository.markCreatedByTicket).
        // Cela garantit que :
        //  - un ticket IMPORT qui reutilise un dossier ACTIVE existant via
        //    DossierIdempotenceLookup ne sera PAS marque (statut != EN_CONSTITUTION)
        //    -> l'annulation ulterieure de ce ticket NE supprimera PAS le dataroom.
        //  - un dossier deja marque par un ticket precedent reste fidele a son
        //    origine (le 2e marquage est no-op grace au IS NULL).
        if (AUTO_CREATE_DOSSIER.contains(cmd.type()) && dossierId != null) {
            try {
                boolean marked = dossierRepository.markCreatedByTicket(
                        cmd.workspaceId(), dossierId, ticket.id());
                if (marked) {
                    log.debug("Dossier {} marque autocreate par ticket {} ({})",
                            dossierId, ticket.id(), cmd.type());
                }
            } catch (Exception ex) {
                // Marquage best-effort : ne doit jamais casser la creation du ticket.
                log.warn("Echec marquage created_by_ticket_id dossier={} ticket={} : {}",
                        dossierId, ticket.id(), ex.getMessage());
            }
        }

        eventPublisher.publishTicketCreated(ticket);
        businessMetrics.ticketCreated(cmd.workspaceId());
        return ticket;
    }
}
