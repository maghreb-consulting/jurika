package ma.jurika.ticket.domain.port;

import ma.jurika.ticket.domain.model.EtatDebours;

/** Lot L5b (E3, RG-DEB-02, RG-FIC-08) : rendu PDF de l'etat des debours a la charte JURIKA, deterministe. */
public interface EtatDeboursDocument {

    byte[] rendre(EtatDebours etat);
}
