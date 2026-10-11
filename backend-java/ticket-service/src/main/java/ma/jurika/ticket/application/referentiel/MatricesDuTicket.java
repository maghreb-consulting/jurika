package ma.jurika.ticket.application.referentiel;

import ma.jurika.ticket.domain.model.TicketType;

import java.util.ArrayList;
import java.util.List;

/**
 * Lot L4b (E4, RG-DEM-01) : quelles matrices du corpus portent les demarches d'un ticket.
 *
 * <p>La forme de la societe choisit la matrice SARL ou SARL AU ; les incidents d'assemblee
 * (matrice transverse) accompagnent les tickets qui tiennent une assemblee. La creation
 * n'est pas ici : elle garde ses lignes en service.
 *
 * <p>Le rang de chaque matrice est fixe : il compose l'ordre stable d'une ligne sur un
 * ticket ({@code rang * 10 000 + ordre dans l'onglet}), que l'ecran emploie pour cocher.
 * Une matrice nouvelle au corpus fait refuser l'import tant qu'elle n'a pas de rang.
 */
public final class MatricesDuTicket {

    public static final List<String> RANGS = List.of(
            "MATRICE_DECISIONS_MODIFICATIONS_SARL",
            "MATRICE_DECISIONS_MODIFICATIONS_SARL_AU",
            "MATRICE_COMPTES_RESULTAT_SARL",
            "MATRICE_COMPTES_RESULTAT_SARL_AU",
            "MATRICE_SUCCURSALE_SARL",
            "MATRICE_SUCCURSALE_SARL_AU",
            "MATRICE_SUCCURSALE_SOCIETE_ETRANGERE",
            "MATRICE_DISSOLUTION_LIQUIDATION_SARL",
            "MATRICE_DISSOLUTION_LIQUIDATION_SARL_AU",
            "MATRICE_INCIDENTS_ASSEMBLEE",
            "MATRICE_IMPORT_DOSSIER");

    static final int PAS = 10_000;

    private MatricesDuTicket() {
    }

    /** Rang (1 a 11) d'une matrice, 0 si elle est inconnue. */
    public static int rang(String matrice) {
        return RANGS.indexOf(matrice) + 1;
    }

    /** Ordre stable d'une ligne du corpus sur un ticket. */
    public static int ordreTicket(String matrice, int ordre) {
        return rang(matrice) * PAS + ordre;
    }

    /** true si le ticket est servi par le corpus (tout sauf la creation). */
    public static boolean servieParLeCorpus(TicketType type) {
        return type != null && type != TicketType.CREATION;
    }

    /**
     * Matrices d'un ticket, la premiere etant celle de sa famille. Forme inconnue : la
     * famille n'a pas de matrice (la forme est demandee, jamais supposee).
     */
    public static List<String> matrices(TicketType type, String forme) {
        String suffixe = "SARL_AU".equals(forme) ? "_SARL_AU" : "SARL".equals(forme) ? "_SARL" : null;
        List<String> out = new ArrayList<>();
        switch (type) {
            case MODIFICATION -> {
                if (suffixe != null) out.add("MATRICE_DECISIONS_MODIFICATIONS" + suffixe);
                out.add("MATRICE_INCIDENTS_ASSEMBLEE");
            }
            case PV_AGO -> {
                if (suffixe != null) out.add("MATRICE_COMPTES_RESULTAT" + suffixe);
                out.add("MATRICE_INCIDENTS_ASSEMBLEE");
            }
            case SUCCURSALE_MA, FERMETURE_SUCCURSALE -> {
                if (suffixe != null) out.add("MATRICE_SUCCURSALE" + suffixe);
            }
            case SUCCURSALE_ETR -> out.add("MATRICE_SUCCURSALE_SOCIETE_ETRANGERE");
            case DISSOLUTION, LIQUIDATION -> {
                if (suffixe != null) out.add("MATRICE_DISSOLUTION_LIQUIDATION" + suffixe);
                out.add("MATRICE_INCIDENTS_ASSEMBLEE");
            }
            case IMPORT -> out.add("MATRICE_IMPORT_DOSSIER");
            default -> {
            }
        }
        return out;
    }
}
