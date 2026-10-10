package ma.jurika.ticket.domain.port;

import ma.jurika.ticket.domain.model.TaxeProfessionnelleVersion;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Lot L1 : versions de la taxe professionnelle (V30). Aucune suppression. */
public interface TaxeProfessionnelleVersionRepository {

    void ajouter(UUID workspaceId, UUID dossierId, String numero, LocalDate dateEffet, UUID auteurId);

    /** Complete la date d'effet de la version en vigueur, si elle est vide et porte ce numero. */
    boolean completerDateEffet(UUID workspaceId, UUID dossierId, String numero, LocalDate dateEffet);

    /** Versions du plus ancien au plus recent ; la derniere est en vigueur. */
    List<TaxeProfessionnelleVersion> lister(UUID workspaceId, UUID dossierId);
}
