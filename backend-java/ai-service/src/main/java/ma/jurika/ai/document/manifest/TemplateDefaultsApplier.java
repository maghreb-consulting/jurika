package ma.jurika.ai.document.manifest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Sprint P2 2026-06-21 (Cowork) — Pre-remplit la data map d'un template avec
 * des placeholders neutres pour toutes les variables declarees au manifest
 * qui n'ont pas encore de valeur.
 *
 * <p>Deux styles de placeholder :
 * <ul>
 *   <li>{@link #PLACEHOLDER_FILL_LATER} ({@value #PLACEHOLDER_FILL_LATER})
 *       pour les variables connues post-immatriculation (RC_NUMERO,
 *       DEPOT_NUMERO, DATE_DEPOT_AU_TC...) listees dans
 *       {@code dictionary.json#fill_later}.</li>
 *   <li>{@link #PLACEHOLDER_TO_FILL} ({@value #PLACEHOLDER_TO_FILL}) pour
 *       toutes les autres variables declarees mais pas encore saisies.</li>
 * </ul>
 *
 * <p>La logique metier (mapper) ecrit ses cles APRES la passe de defaults
 * et ecrase donc les placeholders quand des valeurs reelles sont produites.
 * Resultat : {@link ma.jurika.ai.document.MissingVariableMarker} ne marque
 * plus en rouge AUCUNE variable declaree au manifest -- le rouge reste
 * reserve aux variables hors-manifest (= vrai bug template).
 *
 * <p>Les variables des blocs repetables (BlockDef) ne sont PAS pre-remplies
 * au niveau racine : elles vivent dans le scope local de chaque clone de
 * paragraphe/ligne. Si la liste du bloc est vide, le moteur ne clone rien.
 */
@Component
public class TemplateDefaultsApplier {

    private static final Logger log = LoggerFactory.getLogger(TemplateDefaultsApplier.class);

    /** Placeholder UTF-8 (8 chars points de conduite) — variables post-immatriculation. */
    public static final String PLACEHOLDER_FILL_LATER = "………";

    /** Placeholder discret pour les variables declarees mais non saisies. */
    public static final String PLACEHOLDER_TO_FILL = "[à compléter]";

    private final TemplateManifestLoader manifestLoader;

    @Autowired
    public TemplateDefaultsApplier(TemplateManifestLoader manifestLoader) {
        this.manifestLoader = manifestLoader;
    }

    /**
     * Sprint Cowork 2026-06-21 (C3) — POLITIQUE STRICTE : ne plus injecter
     * AUCUN placeholder par defaut.
     *
     * <p>Le pipeline actuel est :
     * <ol>
     *   <li>Mapper produit son payload (avec defauts metier 3/4, 15j, etc.).</li>
     *   <li>Si une variable manifest reste vide -> sentinel U+E000/E001
     *       puis {@link ma.jurika.ai.document.MissingVariableMarker} rend :
     *       <ul>
     *         <li>"[à compléter]" rouge pour les variables fill_later (post-immat).</li>
     *         <li>"‹ VALEUR MANQUANTE : NOM ›" rouge pour les autres (vrai bug
     *             a corriger : front qui n'a pas saisi, ou mapper qui oublie).</li>
     *       </ul></li>
     * </ol>
     *
     * <p>Ce composant garde sa signature pour ne pas casser les appelants ;
     * il devient un pass-through identite. La logique placeholder a ete
     * supprimee. Anti-bruit pour {@code champ}/{@code champ_variable} retire
     * aussi (ils remonteront en rouge -> signal explicite a nettoyer les .docx).
     */
    public Map<String, Object> withDefaults(String templateCode, Map<String, Object> userVars) {
        if (userVars == null || userVars.isEmpty()) return new LinkedHashMap<>();
        if (log.isTraceEnabled()) {
            log.trace("TemplateDefaultsApplier[{}] : pass-through ({} vars utilisateur)",
                    templateCode, userVars.size());
        }
        return new LinkedHashMap<>(userVars);
    }
}
