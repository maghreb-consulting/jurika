package ma.jurika.ticket.domain.model;

/**
 * Lot L5 (E6, RG-DEB-01) : les sept types de frais du cahier des charges (10.1), exactement.
 * Les categories anterieures (V3) sont reprises par la migration ticket V40.
 */
public enum DeboursCategorie {
    DROITS_ENREGISTREMENT("Droits d'enregistrement"),
    FRAIS_GREFFE("Frais de greffe"),
    ANNONCE_LEGALE("Annonce l\u00e9gale"),
    TIMBRES("Timbres"),
    LEGALISATION("L\u00e9galisation"),
    FRAIS_ORGANISME("Frais d'organisme"),
    AUTRE("Autre");

    private final String libelle;

    DeboursCategorie(String libelle) {
        this.libelle = libelle;
    }

    /** Libelle affiche (etat des debours, ecrans). */
    public String libelle() {
        return libelle;
    }
}
