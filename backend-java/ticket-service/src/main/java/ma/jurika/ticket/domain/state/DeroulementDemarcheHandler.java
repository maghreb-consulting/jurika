package ma.jurika.ticket.domain.state;

import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.port.TransitionChecks;
import org.springframework.stereotype.Component;

/**
 * Statut 3 — « Deroulement de la demarche ».
 *
 * <p>Points de controle du guide (onglet 2, ligne 2) : certificat negatif
 * obtenu, siege justifie, JEU DE VARIABLES COMPLET, actes valides par le client.
 * Les trois premiers sont mecanises — cochage integral des demarches du statut
 * « Generation des documents » (etapes 4 a 12) + controle des variables du
 * dossier par {@code GuideTransitionChecks}.
 */
@Component
public class DeroulementDemarcheHandler extends ParcoursHandler {

    public DeroulementDemarcheHandler(TransitionChecks checks) {
        super(checks);
    }

    @Override
    public TicketStatut statut() {
        return TicketStatut.DEROULEMENT_DEMARCHE;
    }

    @Override
    protected TicketStatut predecesseur() {
        return TicketStatut.GENERATION_DOCUMENTS;
    }
}
