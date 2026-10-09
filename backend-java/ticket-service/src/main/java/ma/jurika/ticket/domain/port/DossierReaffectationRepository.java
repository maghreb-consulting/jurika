package ma.jurika.ticket.domain.port;

import ma.jurika.ticket.domain.model.DossierReaffectation;

import java.util.List;
import java.util.UUID;

/** Lot L1 : historique des changements de responsable d'un dossier. */
public interface DossierReaffectationRepository {

    DossierReaffectation enregistrer(DossierReaffectation reaffectation);

    /** Historique du dossier, du plus ancien au plus recent. */
    List<DossierReaffectation> lister(UUID workspaceId, UUID dossierId);
}
