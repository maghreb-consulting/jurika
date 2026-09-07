package ma.jurika.dataroom.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Libelle du dossier de ticket dans la Data Room.
 *
 * <p>Le libelle est CALCULE, jamais saisi : il doit permettre a l'employe de
 * s'y retrouver sans ouvrir le dossier, et rester exact quand le ticket evolue.
 *
 * <pre>
 *   Creation — T-2026-00841 — 15/06/2026
 *   Modification — Transfert de siege, Changement de gerant — T-2026-00902 — 04/09/2026
 *   Dissolution — T-2026-00915 — 20/09/2026
 * </pre>
 *
 * <p>Regle de composition : type de workflow, puis — pour la MODIFICATION
 * seulement — la liste des types de modification retenus, puis l'identifiant du
 * ticket, puis la date. Les separateurs sont des tirets cadratins entoures
 * d'espaces.
 */
public final class TicketDossierLabel {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ZoneId ZONE = ZoneId.of("Africa/Casablanca");
    private static final String SEP = " — ";

    /** Libelle court du type de workflow, tel qu'affiche en tete du dossier. */
    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("CREATION", "Création"),
            Map.entry("IMPORT", "Import"),
            Map.entry("MODIFICATION", "Modification"),
            Map.entry("DISSOLUTION", "Dissolution"),
            Map.entry("LIQUIDATION", "Liquidation"),
            Map.entry("SUCCURSALE_MA", "Succursale"),
            Map.entry("SUCCURSALE_ETR", "Succursale étrangère"),
            Map.entry("FERMETURE_SUCCURSALE", "Fermeture de succursale"),
            Map.entry("PV_AGO", "PV AGO"));

    private TicketDossierLabel() {}

    /**
     * @param type          type de workflow du ticket ({@code CREATION}, ...)
     * @param reference     identifiant du ticket ({@code T-2026-00841})
     * @param date          date d'ouverture du ticket
     * @param sousTypes     types de modification retenus ; ignores hors MODIFICATION,
     *                      et sautes s'ils sont vides
     */
    public static String compose(String type, String reference, Instant date, List<String> sousTypes) {
        StringBuilder sb = new StringBuilder(libelleType(type));

        if ("MODIFICATION".equals(type) && sousTypes != null && !sousTypes.isEmpty()) {
            String liste = sousTypes.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .map(String::trim)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse(null);
            if (liste != null) sb.append(SEP).append(liste);
        }

        if (reference != null && !reference.isBlank()) sb.append(SEP).append(reference.trim());
        if (date != null) sb.append(SEP).append(LocalDate.ofInstant(date, ZONE).format(DATE));
        return sb.toString();
    }

    /**
     * Libelle d'un type inconnu : on ne masque pas le ticket, on affiche le code
     * brut. Un dossier sans nom lisible reste preferable a un dossier invisible.
     */
    private static String libelleType(String type) {
        if (type == null || type.isBlank()) return "Opération";
        return TYPES.getOrDefault(type, type);
    }
}
