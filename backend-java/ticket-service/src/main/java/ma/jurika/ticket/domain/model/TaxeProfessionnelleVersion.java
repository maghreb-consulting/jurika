package ma.jurika.ticket.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lot L1 : une version de la taxe professionnelle d'un dossier (RG-VAR-08, RG-FIC-02).
 * {@code dateEffet} null : date de prise d'effet non renseignee (jamais inventee).
 * {@code enVigueur} : la version la plus recente.
 */
public record TaxeProfessionnelleVersion(UUID id, String numero, LocalDate dateEffet, UUID saisiPar,
                                         Instant saisiLe, String origine, boolean enVigueur) {
}
