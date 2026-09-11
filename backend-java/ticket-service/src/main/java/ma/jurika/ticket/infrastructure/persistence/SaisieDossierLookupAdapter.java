package ma.jurika.ticket.infrastructure.persistence;

import ma.jurika.ticket.domain.port.SaisieDossierLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lot B — lecture, EN LECTURE SEULE, d'une date saisie au parcours.
 *
 * <p>La saisie du workflow vit dans une seule colonne JSONB,
 * {@code workflow_progress.data}, propriété du workflow-service. On la lit ici
 * comme le ticket-service lit déjà {@code dataroom_documents} pour le
 * récapitulatif de clôture : par une requête explicite, sans jamais écrire, et
 * sans mapper la table en entité — il s'agit d'aller chercher une chaîne, pas de
 * s'approprier un agrégat qui appartient à un autre service.
 *
 * <h2>Pourquoi la requête balaie tous les objets de `data`</h2>
 *
 * <p>{@code executeStep} range le contenu d'une étape sous {@code step<N>}, et
 * l'étape qui porte une saisie donnée peut changer — elle a déjà changé une fois
 * entre le lot 5 et le lot B. Coder « {@code step7} » en dur ferait taire
 * l'alerte le jour où la saisie déménage, <b>sans rien casser de visible</b> :
 * l'échéance disparaîtrait, et disparaître ressemble à « pas encore saisi ».
 *
 * <p>On balaie donc les objets de premier niveau, et l'on cherche la clé sous
 * les trois emplacements où l'étape 7 publie ses compléments — {@code creation},
 * {@code formulaires}, {@code complements}. Le coût est celui d'une ligne de
 * JSONB par ticket.
 *
 * <h2>Ce qui est renvoyé vide, et pourquoi c'est normal</h2>
 *
 * <p>Date absente, chaîne vide, date illisible, ticket sans workflow : dans tous
 * ces cas le résultat est vide, et l'appelant ne lève aucune alerte. Une date
 * illisible est journalisée — c'est un défaut de saisie ou de format, pas un état
 * normal — mais elle ne devient jamais une échéance : une date fabriquée à partir
 * d'une valeur qu'on n'a pas su lire est pire qu'une absence d'alerte.
 */
@Repository
public class SaisieDossierLookupAdapter implements SaisieDossierLookup {

    private static final Logger log = LoggerFactory.getLogger(SaisieDossierLookupAdapter.class);

    /**
     * Nom de la variable du corpus -> clé de saisie côté parcours.
     *
     * <p>Une liste blanche, et non une conversion mécanique : la clé envoyée par
     * l'étape 7 est celle que le catalogue des champs a dérivée, et rien ne
     * garantit qu'un futur nom de variable s'y traduise de la même façon. Une
     * variable absente d'ici ne lève pas d'alerte — elle n'en levait pas non plus
     * avant.
     */
    private static final Map<String, String> CLES = Map.of(
            "DATE_DEBUT_ACTIVITE", "dateDebutActivite");

    /**
     * Les trois emplacements où l'étape 7 publie ses compléments, dans l'ordre de
     * priorité. `creation` est le contrat du lot B, `formulaires` celui du lot 5
     * que le mapper lit toujours, `complements` la copie brute de l'écran.
     */
    /**
     * Le {@code CAST(? AS text)} rend la resolution d'operateur independante du
     * pilote. {@code ->>} existe en deux versions — par cle texte et par index
     * entier — et c'est le TYPE annonce pour le parametre qui tranche : le pilote
     * PostgreSQL annonce {@code text} pour un {@code setString}, donc cela
     * fonctionne aussi sans le cast, mais une configuration
     * {@code stringtype=unspecified} suffirait a rouvrir la question. Le cast
     * coute zero et referme le sujet.
     *
     * <p>Requete eprouvee sur PostgreSQL 16 reel, six cas : saisie presente,
     * chaine vide, cle absente, saisie deplacee sous une autre etape, format non
     * ISO, ticket inconnu.
     */
    private static final String SQL = """
            SELECT COALESCE(o.valeur -> 'creation'    ->> CAST(? AS text),
                            o.valeur -> 'formulaires' ->> CAST(? AS text),
                            o.valeur -> 'complements' ->> CAST(? AS text))
              FROM workflow_progress wp
              CROSS JOIN LATERAL jsonb_each(wp.data) AS o(cle, valeur)
             WHERE wp.workspace_id = ?
               AND wp.ticket_id = ?
               AND jsonb_typeof(o.valeur) = 'object'
               AND COALESCE(o.valeur -> 'creation'    ->> CAST(? AS text),
                            o.valeur -> 'formulaires' ->> CAST(? AS text),
                            o.valeur -> 'complements' ->> CAST(? AS text), '') <> ''
             LIMIT 1
            """;

    private final JdbcTemplate jdbc;

    public SaisieDossierLookupAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<LocalDate> dateSaisie(UUID workspaceId, UUID ticketId, String nomDonnee) {
        String cle = CLES.get(nomDonnee);
        if (cle == null || workspaceId == null || ticketId == null) return Optional.empty();

        // Le filtre workspace est EXPLICITE : la RLS est inerte en runtime,
        // l'application se connectant avec un rôle qui la contourne.
        String brut;
        try {
            brut = jdbc.queryForObject(SQL, String.class,
                    cle, cle, cle, workspaceId, ticketId, cle, cle, cle);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Optional.empty();
        }
        if (brut == null || brut.isBlank()) return Optional.empty();

        try {
            return Optional.of(LocalDate.parse(brut.trim()));
        } catch (DateTimeParseException e) {
            log.warn("Ticket {} : la saisie {} = « {} » n'est pas une date lisible ; "
                    + "aucune echeance ne sera calculee pour les delais qui en dependent",
                    ticketId, nomDonnee, brut);
            return Optional.empty();
        }
    }
}
