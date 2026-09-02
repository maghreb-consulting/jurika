package ma.jurika.ai.document.manifest;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Dictionnaire des variables L3 (chargé depuis {@code templates/v2/dictionary.json}).
 *
 * <p>Donne, pour chaque variable, sa signification, son format attendu et sa source
 * d'origine (dict_directeur, déduit_modèles_L3...). Les blocs partagés y figurent aussi.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DictionaryManifest(
        String version,
        @JsonProperty("generated_at") @JsonAlias("generatedAt") String generatedAt,
        /**
         * Sprint P2 2026-06-21 (Cowork) — Variables connues POST-immatriculation
         * (RC_NUMERO, DEPOT_NUMERO, DATE_DEPOT_AU_TC...). Quand elles n'ont pas
         * encore de valeur, le moteur rend "............" au lieu du marqueur rouge
         * "VALEUR MANQUANTE".
         */
        @JsonProperty("fill_later") @JsonAlias("fillLater") List<String> fillLater,
        List<VariableDef> variables,
        List<TemplateManifest.BlockDef> blocks) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VariableDef(
            String name,
            String meaning,
            String format,
            String source) {
    }
}
