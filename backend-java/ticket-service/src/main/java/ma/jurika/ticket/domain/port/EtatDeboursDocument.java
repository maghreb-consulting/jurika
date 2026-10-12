package ma.jurika.ticket.domain.port;

import ma.jurika.ticket.domain.model.EtatDebours;

/** Lot L5 (E6, RG-DEB-02, P5) : rendu Word (.docx) de l'etat des debours, deterministe. */
public interface EtatDeboursDocument {

    byte[] rendre(EtatDebours etat);
}
