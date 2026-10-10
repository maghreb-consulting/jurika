package ma.jurika.ticket.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Lot L1 : ligne d'historique des responsables d'un dossier, avec les noms (ecran
 * "Historique des responsables" et vue du superviseur sur les rattrapages, V32).
 */
public record ReaffectationVue(UUID id, UUID dossierId, String raisonSociale,
                               UUID ancienResponsableId, String ancienResponsableNom,
                               UUID nouveauResponsableId, String nouveauResponsableNom,
                               NatureReaffectation nature, UUID auteurId, String auteurNom,
                               String motif, Instant createdAt,
                               UUID responsableActuelId, String responsableActuelNom,
                               UUID verifiePar, String verifieParNom, Instant verifieLe) {
}
