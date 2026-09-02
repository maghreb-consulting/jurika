package ma.jurika.ticket.domain.model;

/**
 * Vue enrichie d'une demande de transfert pour l'affichage (panneau
 * "Transferts en attente") : la demande + la raison sociale du dossier.
 */
public record DossierTransfertRequestView(
        DossierTransfertRequest request,
        String raisonSociale
) {}
