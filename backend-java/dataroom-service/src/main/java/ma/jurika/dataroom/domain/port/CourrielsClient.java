package ma.jurika.dataroom.domain.port;

import ma.jurika.common.courriel.CourrielClientEvenement;

/**
 * Lot L5 (E9, RG-COM-09) : demande l'avertissement du CLIENT par courriel.
 * L'envoi lui-meme est fait par auth-service (port EmailSender), qui consomme
 * l'evenement publie ici. Le client n'est plus notifie dans l'application.
 */
public interface CourrielsClient {

    void demander(CourrielClientEvenement evenement);
}
