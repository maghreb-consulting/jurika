package ma.jurika.billing.application.facture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.billing.api.dto.BillingDtos.FactureVue;
import ma.jurika.billing.api.dto.BillingDtos.MontantsVue;
import ma.jurika.billing.infrastructure.persistence.BillingRepositories.FactureJpaRepository;
import ma.jurika.billing.infrastructure.persistence.FactureEntity;
import ma.jurika.common.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Lot L7 (E5), RG-PAY-03 : factures consultables et telechargeables par le cabinet (superviseur,
 * filtre explicite sur son workspace) et par le super-admin (tous cabinets).
 */
@Service
public class FactureQueryService {

    private final FactureJpaRepository factures;
    private final ObjectMapper json;

    public FactureQueryService(FactureJpaRepository factures, ObjectMapper json) {
        this.factures = factures;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<FactureVue> duCabinet(UUID workspaceId) {
        return factures.findAllByWorkspaceIdOrderByDateEmissionDescRangDesc(workspaceId).stream().map(this::vue).toList();
    }

    @Transactional(readOnly = true)
    public FactureEntity pdfDuCabinet(UUID workspaceId, Long id) {
        return factures.findByIdAndWorkspaceId(id, workspaceId).orElseThrow(() -> new NotFoundException("Facture introuvable."));
    }

    @Transactional(readOnly = true)
    public List<FactureVue> toutes(UUID workspaceId) {
        List<FactureEntity> l = workspaceId == null ? factures.findTop500ByOrderByAnneeDescRangDesc()
                : factures.findAllByWorkspaceIdOrderByDateEmissionDescRangDesc(workspaceId);
        return l.stream().map(this::vue).toList();
    }

    @Transactional(readOnly = true)
    public FactureEntity pdf(Long id) {
        return factures.findById(id).orElseThrow(() -> new NotFoundException("Facture introuvable."));
    }

    FactureVue vue(FactureEntity f) {
        String cabinet;
        try {
            JsonNode n = json.readTree(f.getCabinet());
            cabinet = n.path("denomination").asText(null);
        } catch (Exception e) {
            cabinet = null;
        }
        return new FactureVue(f.getId(), f.getNumero(), f.getWorkspaceId(), cabinet, f.getDateEmission(),
                f.getDesignation(), f.getPeriodeDebut(), f.getPeriodeFin(), f.getModePaiement(),
                new MontantsVue(f.getMontantHtCentimes(), f.getTauxTva(), f.getMontantTvaCentimes(), f.getMontantTtcCentimes()));
    }
}
