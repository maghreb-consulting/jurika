package ma.jurika.ticket.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

/** Corps de requete du cochage des demarches. */
public final class DemarcheRequests {

    private DemarcheRequests() {}

    /**
     * Identifiants des documents deja televerses dans la Data Room et rattaches
     * a ce ticket. Le TYPE n'est pas transmis : il est lu en base et confronte au
     * referentiel, faute de quoi le client pourrait declarer n'importe quel type
     * pour n'importe quel fichier.
     */
    public record Cocher(List<UUID> documentIds) {}

    public record NonApplicable(@NotBlank(message = "Motif obligatoire") String motif) {}

    /**
     * Lot 5 (2026-09-07) — reponse a « la gerance est-elle designee dans les
     * statuts ? », donnee une seule fois a l'etape 5 du workflow et propagee aux
     * trois demarches qui portent cette condition (9, 15 et 18).
     */
    public record ConditionGerance(boolean statutaire) {}
}
