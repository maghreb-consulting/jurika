package ma.jurika.ai.document.manifest;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Manifest L3 des templates DOCX (chargé depuis {@code templates/v2/manifest.json}).
 *
 * <p>Structure :
 * <ul>
 *   <li>{@link TemplateManifest} — racine</li>
 *   <li>{@link TemplateEntry} — entrée par code (template direct ou alias)</li>
 *   <li>{@link BlockDef} — définition d'un bloc répétable</li>
 * </ul>
 *
 * <p>Mapping JSON snake_case ↔ Java camelCase via {@link JsonProperty}/{@link JsonAlias}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TemplateManifest(
        String version,
        @JsonProperty("generated_at") @JsonAlias("generatedAt") String generatedAt,
        List<TemplateEntry> templates) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TemplateEntry(
            String code,
            String file,
            @JsonProperty("aliasOf") @JsonAlias({"alias_of"}) String aliasOf,
            String origin,
            @JsonProperty("document_kind") @JsonAlias("documentKind") String documentKind,
            String workflow,
            @JsonProperty("placeholder_style") @JsonAlias("placeholderStyle") String placeholderStyle,
            boolean deprecated,
            String note,
            List<String> variables,
            List<BlockDef> blocks) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BlockDef(
            String name,
            List<String> variables) {
    }
}
