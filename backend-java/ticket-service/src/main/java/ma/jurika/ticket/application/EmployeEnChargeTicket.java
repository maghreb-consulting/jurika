package ma.jurika.ticket.application;

import ma.jurika.common.exception.NotFoundException;
import ma.jurika.ticket.domain.model.EntrepriseDossier;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.port.DossierRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Lot L4d (E2, RG-DOS-01) : l'employe en charge d'un ticket, regle unique : le responsable
 * de son dossier ; sans dossier, l'assigne ou le createur. Un autre employe ne voit pas le
 * ticket (404, comme pour les debours et la note) ; le superviseur observe sans agir.
 */
@Component
public class EmployeEnChargeTicket {

    private final DossierRepository dossiers;

    public EmployeEnChargeTicket(DossierRepository dossiers) {
        this.dossiers = dossiers;
    }

    public boolean estEnCharge(UUID workspaceId, Ticket t, UUID userId) {
        if (userId == null) return false;
        if (t.dossierId() != null) {
            UUID responsable = dossiers.findById(workspaceId, t.dossierId())
                    .map(EntrepriseDossier::responsableId).orElse(null);
            if (responsable != null) return userId.equals(responsable);
        }
        return userId.equals(t.assigneId()) || userId.equals(t.creeParId());
    }

    public void exiger(UUID workspaceId, Ticket t, UUID userId) {
        if (!estEnCharge(workspaceId, t, userId)) throw new NotFoundException("Ticket inconnu");
    }
}
