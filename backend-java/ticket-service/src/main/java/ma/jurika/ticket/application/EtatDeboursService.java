package ma.jurika.ticket.application;

import ma.jurika.common.exception.BusinessException;
import ma.jurika.common.pdf.CabinetIdentity;
import ma.jurika.common.security.TenantContext;
import ma.jurika.ticket.domain.model.Debours;
import ma.jurika.ticket.domain.model.EntrepriseDossier;
import ma.jurika.ticket.domain.model.EtatDebours;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.port.DeboursRepository;
import ma.jurika.ticket.domain.port.DossierRepository;
import ma.jurika.ticket.domain.port.EtatDeboursDocument;
import ma.jurika.ticket.domain.port.ReferenceDossierLookup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Lot L5 (E6, RG-DEB-02) : etablit l'etat des debours d'un ticket au format Word (P5),
 * regenerable a tout moment. Les droits sont controles avant ({@link AccesDeboursTicket}).
 * Refus nommes : ticket sans dossier (pas de societe a identifier), ticket sans debours.
 */
@Service
public class EtatDeboursService {

    private final DeboursRepository debours;
    private final DossierRepository dossiers;
    private final ReferenceDossierLookup references;
    private final EtatDeboursDocument document;

    public EtatDeboursService(DeboursRepository debours, DossierRepository dossiers,
                              ReferenceDossierLookup references, EtatDeboursDocument document) {
        this.debours = debours;
        this.dossiers = dossiers;
        this.references = references;
        this.document = document;
    }

    @Transactional(readOnly = true)
    public byte[] etablir(UUID workspaceId, Ticket ticket, CabinetIdentity cabinet, LocalDate dateEtat) {
        TenantContext.set(workspaceId);
        EntrepriseDossier dossier = ticket.dossierId() == null ? null
                : dossiers.findById(workspaceId, ticket.dossierId()).orElse(null);
        if (dossier == null) {
            throw new BusinessException("ETAT_DEBOURS_SANS_SOCIETE",
                    "L'\u00e9tat des d\u00e9bours identifie la soci\u00e9t\u00e9 : ce ticket n'est rattach\u00e9 "
                            + "\u00e0 aucun dossier.");
        }
        List<Debours> lignes = debours.findByTicket(workspaceId, ticket.id());
        if (lignes.isEmpty()) {
            throw new BusinessException("ETAT_DEBOURS_VIDE",
                    "Aucun d\u00e9bours n'est saisi pour ce ticket : l'\u00e9tat ne peut pas \u00eatre \u00e9tabli.");
        }
        BigDecimal total = lignes.stream().map(Debours::montantMad).reduce(BigDecimal.ZERO, BigDecimal::add);
        return document.rendre(new EtatDebours(cabinet, dossier,
                references.reference(workspaceId, dossier.id()), ticket, dateEtat, lignes, total));
    }
}
