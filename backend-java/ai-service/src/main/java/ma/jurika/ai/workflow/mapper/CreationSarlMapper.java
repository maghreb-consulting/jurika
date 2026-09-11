package ma.jurika.ai.workflow.mapper;

import ma.jurika.ai.workflow.WorkflowDocumentMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Mapper L4 du workflow {@code CREATION_SARL} — <b>reconstruit au lot B
 * (2026-09-11) sur le corpus du 9 septembre</b>.
 *
 * <p><b>D'où il repart.</b> Le lot A avait retiré le {@code @Component} : les
 * sept modèles d'août étaient sortis du manifeste, les 23 gabarits du cabinet les
 * avaient remplacés, et 253 de leurs 404 variables n'avaient aucune résolution.
 * Un mapper qui les aurait déclarées aurait rendu des documents troués. L'étape
 * de génération affichait donc une liste vide. Ce lot la remplit.
 *
 * <p><b>Comment la résolution se compose.</b> Trois couches, dans cet ordre, et
 * chacune ne pose que ce que la précédente n'a pas :
 *
 * <ol>
 *   <li>{@link CreationFormulairesVarsBuilder} — qui part lui-même de
 *       {@link CreationDirecteurVarsBuilder}. Les <b>149 variables déjà
 *       résolues</b> et leurs dérivations (activité principale lue dans l'objet
 *       social, type de tribunal déduit de la ville, échéance de société
 *       calculée), plus les quatre boucles historiques {@code ASSOCIES},
 *       {@code APPORTS_PAR_ASSOCIE}, {@code GERANTS}, {@code SIGNATAIRES}.</li>
 *   <li>{@link CreationCorpusVarsBuilder} — les <b>160 saisies</b> du parcours,
 *       lues au catalogue généré, et les <b>37 dérivations</b> propres au corpus
 *       du 9 septembre.</li>
 *   <li>Rien d'autre. Les <b>55 variables sans source</b> ne reçoivent aucune
 *       valeur : elles remontent au contrôle de complétude et au rapport
 *       cabinet.</li>
 * </ol>
 *
 * <p><b>Les noms.</b> Les alignements se font ici, dans la résolution, et jamais
 * dans le {@code .docx}. {@code $ICE} est tranché depuis le 9 septembre et la
 * plateforme l'écrivait déjà : rien à aligner. Les trois arbitrages restés
 * ouverts — {@code $SIEGE_VILLE} contre {@code $VILLE},
 * {@code $SIGNATAIRE_NOM_QUALITE} contre {@code $FORMULAIRE_SIGNATAIRE} — ne sont
 * pas tranchés par ce lot : {@link #alignerNomsEnVigueur} publie la valeur sous
 * les DEUX noms quand la plateforme n'en résout qu'un, de sorte qu'aucun des deux
 * arbitrages n'est préjugé.
 *
 * <p><b>Les 23 modèles viennent du catalogue</b>, pas d'une liste écrite ici :
 * une liste en dur se désynchroniserait de la prochaine livraison du cabinet.
 */
@Component
public class CreationSarlMapper implements WorkflowDocumentMapper {

    private static final String WORKFLOW_CODE = "CREATION_SARL";

    /**
     * Codes des trois formulaires administratifs, conservés en constantes : deux
     * migrations déjà appliquées les nomment comme données (dataroom V30,
     * ticket V20) et {@code ModificationMapper} s'y réfère.
     */
    public static final String TPL_DEMANDE_TP = CreationFormulairesVarsBuilder.TPL_DEMANDE_TP;
    public static final String TPL_DECLARATION_EXISTENCE =
            CreationFormulairesVarsBuilder.TPL_DECLARATION_EXISTENCE;
    public static final String TPL_DECLARATION_RC = CreationFormulairesVarsBuilder.TPL_DECLARATION_RC;

    @Override
    public String workflowCode() {
        return WORKFLOW_CODE;
    }

    @Override
    public Set<String> supportedTemplates() {
        return CreationChampsCatalogue.get().codes();
    }

    @Override
    public Map<String, Object> map(String templateCode, Map<String, Object> payload) {
        Set<String> supportes = supportedTemplates();
        if (templateCode == null || !supportes.contains(templateCode)) {
            throw new IllegalArgumentException(
                    "Template non supporté par CreationSarlMapper : " + templateCode
                            + " (supportés : " + supportes + ")");
        }
        Map<String, Object> safe = payload == null ? Map.of() : payload;

        // Couche 1 — ce que la plateforme résout déjà.
        Map<String, Object> variables =
                new LinkedHashMap<>(CreationFormulairesVarsBuilder.build(templateCode, safe));

        // Couche 2 — les saisies et les dérivations du corpus du 9 septembre.
        // Elles viennent APRÈS et écrasent : une donnée saisie au parcours pour ce
        // document précis est plus précise qu'une valeur générique reprise du
        // dossier. Une clé absente n'écrit rien, donc n'écrase rien.
        variables.putAll(CreationCorpusVarsBuilder.build(templateCode, safe, variables));

        alignerNomsEnVigueur(variables);
        return variables;
    }

    /**
     * LES TROIS ARBITRAGES DE NOMMAGE, NON TRANCHÉS.
     *
     * <p>Le corpus emploie {@code $SIEGE_VILLE} dans sept gabarits et
     * {@code $VILLE} dans deux — et les deux coexistent <b>dans le même
     * document</b> (déclaration d'existence, demande de taxe professionnelle). La
     * plateforme, elle, ne résout que {@code $VILLE}.
     *
     * <p>Trancher reviendrait à réécrire un gabarit du cabinet, ce que ce lot
     * s'interdit. On aligne donc <b>dans la résolution</b> : la valeur connue est
     * publiée sous les deux noms. Le jour où le cabinet tranchera, il suffira de
     * retirer l'alias — aucun document n'aura été touché entre-temps.
     *
     * <p>{@code $FORMULAIRE_SIGNATAIRE} n'est employé par aucun gabarit : l'alias
     * ne sert donc à rien aujourd'hui, mais il rend le renommage sans effet de
     * bord s'il est retenu.
     *
     * <p>{@code $DOMICILIATAIRE_ICE} désigne l'ICE d'un TIERS : il n'est pas
     * concerné et n'est jamais aligné sur {@code $ICE}.
     */
    private static void alignerNomsEnVigueur(Map<String, Object> v) {
        alias(v, "VILLE", "SIEGE_VILLE");
        alias(v, "SIGNATAIRE_NOM_QUALITE", "FORMULAIRE_SIGNATAIRE");
    }

    /** Publie la valeur de {@code source} sous {@code cible}, sans jamais l'écraser. */
    private static void alias(Map<String, Object> v, String source, String cible) {
        Object valeur = v.get(source);
        if (valeur == null) return;
        String s = String.valueOf(valeur);
        if (s.isBlank()) return;
        v.putIfAbsent(cible, valeur);
    }
}
