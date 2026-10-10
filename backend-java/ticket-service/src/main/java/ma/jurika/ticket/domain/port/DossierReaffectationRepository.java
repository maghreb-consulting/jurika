package ma.jurika.ticket.domain.port;

import ma.jurika.ticket.domain.model.DossierReaffectation;

import java.util.List;
import java.util.UUID;

/** Lot L1 : historique des changements de responsable d'un dossier. */
public interface DossierReaffectationRepository {

    DossierReaffectation enregistrer(DossierReaffectation reaffectation);

    /** Historique du dossier, du plus ancien au plus recent. */
    List<DossierReaffectation> lister(UUID workspaceId, UUID dossierId);

    /** Lot L1 : historique du dossier avec les noms, du plus ancien au plus recent. */
    List<ma.jurika.ticket.domain.model.ReaffectationVue> listerVues(UUID workspaceId, UUID dossierId);

    /** Lot L1 : rattrapages de V28 du cabinet (non verifies d'abord). */
    List<ma.jurika.ticket.domain.model.ReaffectationVue> listerRattrapages(UUID workspaceId);

    /** Lot L1 (V32) : marque un rattrapage verifie ; vide si la ligne n'est pas un rattrapage du cabinet. */
    java.util.Optional<ma.jurika.ticket.domain.model.ReaffectationVue> marquerVerifie(UUID workspaceId, UUID reaffectationId,
                                                                                     UUID superviseurId);
}
