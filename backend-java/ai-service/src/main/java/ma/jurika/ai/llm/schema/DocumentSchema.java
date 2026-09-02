package ma.jurika.ai.llm.schema;

import java.util.List;

/**
 * Définition d'un schéma d'extraction pour un type de document métier.
 * <p>
 * Utilisé par {@link ma.jurika.ai.llm.LlmExtractionPort} pour structurer le prompt
 * et contraindre l'output JSON du LLM aux champs attendus.
 *
 * @param typeCode    Code interne du type (aligné sur {@code DocumentTypes} si applicable).
 * @param description Description courte du document (incluse dans le prompt).
 * @param fields      Liste ordonnée des champs à extraire.
 */
public record DocumentSchema(
        String typeCode,
        String description,
        List<FieldDef> fields
) {

    public DocumentSchema {
        if (typeCode == null || typeCode.isBlank()) {
            throw new IllegalArgumentException("typeCode obligatoire");
        }
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    /**
     * Définition d'un champ attendu dans l'extraction.
     *
     * @param name        Nom du champ (clé JSON, camelCase).
     * @param type        Type primitif : "string", "number", "date" (ISO YYYY-MM-DD).
     * @param description Description sémantique (libellé humain en français, va dans le prompt).
     * @param required    Si true, le champ est marqué obligatoire dans le prompt.
     */
    public record FieldDef(
            String name,
            String type,
            String description,
            boolean required
    ) {
        public FieldDef {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name obligatoire");
            }
            if (type == null || type.isBlank()) {
                type = "string";
            }
        }
    }
}
