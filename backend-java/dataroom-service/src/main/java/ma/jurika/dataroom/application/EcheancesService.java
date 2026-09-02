package ma.jurika.dataroom.application;

import ma.jurika.common.audit.Auditable;
import ma.jurika.common.exception.NotFoundException;
import ma.jurika.dataroom.api.dto.DataroomDtos.EcheanceSummary;
import ma.jurika.dataroom.infrastructure.persistence.AlerteEcheanceEntity;
import ma.jurika.dataroom.infrastructure.persistence.AlerteEcheanceJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class EcheancesService {

    private final AlerteEcheanceJpaRepository repo;

    public EcheancesService(AlerteEcheanceJpaRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<EcheanceSummary> listForDossier(UUID dossierId, LocalDate from, LocalDate to,
                                                 String statut) {
        List<AlerteEcheanceEntity> rows;
        if (from != null && to != null) {
            rows = repo.findByDossierBetween(dossierId, from, to);
        } else if (statut != null && !statut.isBlank()) {
            rows = repo.findByDossierAndStatut(dossierId, statut);
        } else {
            LocalDate today = LocalDate.now();
            rows = repo.findByDossierBetween(dossierId, today, today.plusYears(1));
        }
        return rows.stream().map(this::toSummary).toList();
    }

    @Transactional
    @Auditable(action = "ECHEANCE_MARQUEE_TRAITEE", resourceType = "alerte_echeance",
            resourceIdExpr = "#echeanceId")
    public EcheanceSummary marquerTraitee(UUID echeanceId, UUID documentId, String note, UUID byUserId) {
        AlerteEcheanceEntity a = repo.findById(echeanceId)
                .orElseThrow(() -> new NotFoundException("Echeance inconnue"));
        a.setStatut("TRAITEE");
        a.setTraitePar(byUserId);
        a.setTraiteAt(Instant.now());
        if (documentId != null) a.setDocumentId(documentId);
        if (note != null && !note.isBlank()) a.setNote(note);
        return toSummary(a);
    }

    private EcheanceSummary toSummary(AlerteEcheanceEntity a) {
        return new EcheanceSummary(
                a.getId(), a.getExerciceFiscalId(), a.getTypeEcheance(),
                a.getDateEcheance() != null ? a.getDateEcheance().toString() : null,
                a.getDateAlerte() != null ? a.getDateAlerte().toString() : null,
                a.getStatut(), a.getDocumentId(), a.getSentAt(), a.getTraiteAt());
    }
}
