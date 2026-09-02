package ma.jurika.ticket.domain.port;

import ma.jurika.ticket.domain.model.TicketPriorite;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.model.TicketType;

import java.util.List;
import java.util.UUID;

public record TicketFilter(
        UUID workspaceId,
        List<TicketStatut> statuts,
        List<TicketType> types,
        List<TicketPriorite> priorites,
        UUID assigneId,
        /**
         * Si non null : (assigne_id = X OR cree_par_id = X).
         * Utilise pour la RG-U08 : un EMPLOYE voit ses tickets (assignes OU crees par lui).
         */
        UUID assigneOrCreeParId,
        /**
         * Si non null : etend la visibilite EMPLOYE aux tickets des dossiers dont
         * X est responsable (entreprise_dossiers.responsable_id = X), via sous-
         * requete sur dossier_id. Combine en OR avec {@link #assigneOrCreeParId}.
         * Ainsi, apres un transfert de dossier, le nouvel employe responsable voit
         * TOUS les tickets du dossier — y compris l'historique CLOTURE/ANNULE non
         * reassigne — sans reecrire l'assigne (qui a traite reste preserve).
         */
        UUID responsableId,
        UUID dossierId,
        String searchText,
        String sortBy,
        boolean sortDesc
) {
    public static TicketFilter forWorkspace(UUID workspaceId) {
        return new TicketFilter(workspaceId, List.of(), List.of(), List.of(),
                null, null, null, null, null, "createdAt", true);
    }
}
