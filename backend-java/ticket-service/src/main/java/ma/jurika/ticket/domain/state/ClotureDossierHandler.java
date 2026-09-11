package ma.jurika.ticket.domain.state;

import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.port.TransitionChecks;
import org.springframework.stereotype.Component;

/**
 * Statut 4 — « Cloture de dossier ».
 *
 * <p>Point de controle : toutes les demarches obligatoires accomplies,
 * justificatifs recus et numerises. Mecanise par {@link ParcoursHandler} : les
 * demarches du statut « Deroulement de la demarche » — lignes 13 a 46 du parcours
 * du 9 septembre, soit 34 lignes — doivent etre cochees, et chaque conditionnelle
 * cochee ou explicitement ecartee avec motif.
 *
 * <p>Lot B — le RECAPITULATIF que l'ecran affiche avant de clore est produit par
 * {@code RecapitulatifClotureService} : il montre les documents, les demarches
 * accomplies, les identifiants obtenus et, surtout, les justificatifs qui
 * manquent. La cloture reste une decision explicite — ce handler la controle, le
 * recapitulatif l'eclaire.
 *
 * <p>Reste non mecanisable : « facture soldee ».
 *
 * <p>Ce statut n'est PAS terminal : un dossier cloture peut encore etre annule
 * (sortie laterale).
 */
@Component
public class ClotureDossierHandler extends ParcoursHandler {

    public ClotureDossierHandler(TransitionChecks checks) {
        super(checks);
    }

    @Override
    public TicketStatut statut() {
        return TicketStatut.CLOTURE_DOSSIER;
    }

    @Override
    protected TicketStatut predecesseur() {
        return TicketStatut.DEROULEMENT_DEMARCHE;
    }
}
