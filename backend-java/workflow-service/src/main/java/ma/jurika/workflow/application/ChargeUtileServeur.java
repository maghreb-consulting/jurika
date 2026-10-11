package ma.jurika.workflow.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lot L3 (P2, RG-VAR-05) : la charge utile de la creation, construite par le SERVEUR
 * depuis le magasin, pour ai-service. Le navigateur ne la construit plus : une
 * correction faite au magasin se repercute sur toutes les generations suivantes.
 *
 * <p>Acces : l'employe en charge du ticket (responsable du dossier ; a defaut,
 * assigne ou createur du ticket), dans son workspace (filtre SQL et RLS).
 */
@Service
public class ChargeUtileServeur {

    private final MagasinVariables magasin;
    private final EmployeEnCharge employeEnCharge;
    private final FaitsDuTicket faitsDuTicket;

    public ChargeUtileServeur(MagasinVariables magasin, EmployeEnCharge employeEnCharge, FaitsDuTicket faitsDuTicket) {
        this.magasin = magasin;
        this.employeEnCharge = employeEnCharge;
        this.faitsDuTicket = faitsDuTicket;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> creation(UUID workspaceId, UUID ticketId, UUID employeId) {
        employeEnCharge.exiger(workspaceId, ticketId, employeId);
        Map<String, Object> charge = new LinkedHashMap<>(
                ConstructeurChargeUtileCreation.construire(ConstructeurChargeUtileCreation.avecFaitsDuTicket(
                        magasin.lirePourGeneration(workspaceId, ticketId), faitsDuTicket.lire(workspaceId, ticketId))));
        charge.put("ticketId", ticketId.toString());
        return charge;
    }
}
