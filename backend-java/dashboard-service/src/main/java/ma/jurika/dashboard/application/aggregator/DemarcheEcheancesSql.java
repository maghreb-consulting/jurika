package ma.jurika.dashboard.application.aggregator;

/**
 * Echeances legales issues des DEMARCHES du parcours (ticket-service V20/V21).
 *
 * <p>Elles remplacent les alertes d'echeances fiscales, abandonnees avec le
 * dossier fiscal. La source change, le contrat non : meme forme de ligne
 * ({@code id, dossier_id, libelle, date_echeance, severite}).
 *
 * <p>Une echeance n'apparait que si :
 * <ul>
 *   <li>le referentiel donne un delai CALCULABLE ({@code delai_valeur},
 *       {@code delai_unite} et {@code delai_reference_ordre} non nuls) -- les
 *       etapes dont le guide ne permet pas de determiner le point de depart
 *       (16, 19, 26) n'en produisent jamais ;</li>
 *   <li>l'etape de reference est effectivement COCHEE : le delai court ;</li>
 *   <li>la demarche elle-meme reste a faire.</li>
 * </ul>
 *
 * <p><b>Les mois sont des mois calendaires.</b> {@code make_interval(months =>)}
 * ramene au dernier jour du mois cible (31/01 + 1 mois = 28/02), exactement
 * comme {@code LocalDate.plusMonths} cote Java. Additionner des jours donnerait
 * une echeance posterieure a l'echeance reelle, donc une alerte trop tardive.
 *
 * <p>Le fuseau est fige a Africa/Casablanca : une date legale ne doit pas
 * dependre du fuseau de la machine qui execute la requete.
 */
final class DemarcheEcheancesSql {

    private DemarcheEcheancesSql() {}

    /** Date limite, calculee depuis la date de cochage de l'etape de reference. */
    static final String ECHEANCE = """
            ((r.coche_at AT TIME ZONE 'Africa/Casablanca')::date
               + make_interval(
                   days   => CASE WHEN dr.delai_unite = 'JOURS' THEN dr.delai_valeur ELSE 0 END,
                   months => CASE WHEN dr.delai_unite = 'MOIS'  THEN dr.delai_valeur ELSE 0 END))::date
            """;

    /**
     * Jointures communes : la demarche porteuse du delai, son etape de reference
     * cochee, et l'etat de la demarche elle-meme.
     */
    static final String JOINTURES = """
            JOIN demarches_referentiel dr
                  ON dr.workflow_type = t.type
                 AND dr.delai_valeur IS NOT NULL
                 AND dr.delai_unite IS NOT NULL
                 AND dr.delai_reference_ordre IS NOT NULL
            JOIN demarches_referentiel ref
                  ON ref.workflow_type = dr.workflow_type
                 AND ref.ordre = dr.delai_reference_ordre
            JOIN ticket_demarches r
                  ON r.ticket_id = t.id AND r.demarche_id = ref.id
                 AND r.etat = 'COCHEE' AND r.coche_at IS NOT NULL
            LEFT JOIN ticket_demarches cur
                  ON cur.ticket_id = t.id AND cur.demarche_id = dr.id
            """;

    /** Filtre commun : ticket vivant, demarche encore a faire. */
    static final String FILTRE = """
            WHERE t.workspace_id = ?
              AND t.statut <> 'ANNULE'
              AND (cur.id IS NULL OR cur.etat = 'A_FAIRE')
            """;

    /**
     * Remplace les jetons {@code {{ECHEANCE}}}, {@code {{JOINTURES}}} et
     * {@code {{FILTRE}}} dans une requete. Permet aux agregateurs de garder des
     * blocs de texte SQL lisibles au lieu de concatenations echappees.
     */
    static String resoudre(String sql) {
        return sql.replace("{{ECHEANCE}}", ECHEANCE.strip())
                  .replace("{{JOINTURES}}", JOINTURES)
                  .replace("{{FILTRE}}", FILTRE);
    }

    static final String BASE =
            "SELECT md5(t.id::text || dr.id::text)::uuid AS id,\n"
          + "       t.dossier_id,\n"
          + "       dr.libelle AS libelle,\n"
          + "       " + ECHEANCE.strip() + " AS date_echeance,\n"
          + "       CASE\n"
          + "         WHEN " + ECHEANCE.strip() + " <  CURRENT_DATE     THEN 'DEPASSE'\n"
          + "         WHEN " + ECHEANCE.strip() + " <= CURRENT_DATE + 3 THEN 'CRITIQUE'\n"
          + "         ELSE 'APPROCHE'\n"
          + "       END AS severite\n"
          + "FROM tickets t\n"
          + "JOIN entreprise_dossiers dos\n"
          + "      ON dos.id = t.dossier_id AND dos.workspace_id = t.workspace_id\n"
          + JOINTURES
          + FILTRE
          + "  AND " + ECHEANCE.strip() + " <= CURRENT_DATE + INTERVAL '30 day'\n";

    /** Portee employe : les dossiers dont il est responsable. */
    static final String SCOPE_RESPONSABLE = "  AND dos.responsable_id = ?\n";

    /** Portee client : ses propres dossiers. */
    static final String SCOPE_CLIENT = "  AND dos.client_id = ?\n";

    static final String ORDER = "ORDER BY date_echeance ASC LIMIT 20";
}
