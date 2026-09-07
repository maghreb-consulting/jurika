package ma.jurika.ticket.domain.state;

import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.port.TransitionChecks;
import org.springframework.stereotype.Component;

/**
 * Statut 4 — « Cloture de dossier ».
 *
 * <p>Point de controle du guide (onglet 2, ligne 3) : toutes les demarches
 * obligatoires accomplies, justificatifs recus et numerises. Mecanise par
 * {@link ParcoursHandler} : les 21 demarches du statut « Deroulement de la
 * demarche » (etapes 13 a 33) doivent etre cochees, et chaque conditionnelle
 * cochee ou explicitement ecartee avec motif.
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
