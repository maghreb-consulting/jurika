package ma.jurika.ai.workflow.mapper;

import ma.jurika.ai.workflow.WorkflowDocumentMapper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Mapper L4 du workflow {@code CREATION_SARL}.
 *
 * <p><b>Voie de génération CRÉATION active (unique)</b> : modèles déterministes du
 * directeur (codes {@code *_DIRECTEUR}, routés vers
 * {@link CreationDirecteurVarsBuilder}) :
 * <ul>
 *     <li>{@code STATUTS_SARL_DIRECTEUR} / {@code STATUTS_SARL_AU_DIRECTEUR}</li>
 *     <li>{@code ACTE_NOMINATION_GERANT_DIRECTEUR}</li>
 *     <li>{@code ANNONCE_LEGALE_DIRECTEUR}</li>
 * </ul>
 *
 * <p><b>Phase E2 (2026-08-09)</b> : la voie LEGACY {@code STATUTS_CONSTITUTIFS_SARL} /
 * {@code STATUTS_CONSTITUTIFS_SARL_AU} (placeholder {@code {{}}}) a été retirée. Elle
 * n'était conservée que pour la refonte des statuts côté MODIFICATION, qui passe
 * désormais par la voie directeur (cf. {@code RefonteStatutsVarsBuilder}). Le mapper
 * ne fait donc plus que router les 4 codes directeur vers
 * {@link CreationDirecteurVarsBuilder}.
 *
 * <p>Pas d'accès DB : toutes les données viennent du payload.
 */
@Component
public class CreationSarlMapper implements WorkflowDocumentMapper {

    private static final String WORKFLOW_CODE = "CREATION_SARL";

    // 2026-08 — MODÈLES DÉTERMINISTES DU DIRECTEUR (unique voie de génération).
    public static final String TPL_STATUTS_SARL_DIR = "STATUTS_SARL_DIRECTEUR";
    public static final String TPL_STATUTS_SARL_AU_DIR = "STATUTS_SARL_AU_DIRECTEUR";
    public static final String TPL_ACTE_NOMINATION_GERANT_DIR = "ACTE_NOMINATION_GERANT_DIRECTEUR";
    public static final String TPL_ANNONCE_LEGALE_DIR = "ANNONCE_LEGALE_DIRECTEUR";

    private static final Set<String> SUPPORTED = Set.of(
            TPL_STATUTS_SARL_DIR,
            TPL_STATUTS_SARL_AU_DIR,
            TPL_ACTE_NOMINATION_GERANT_DIR,
            TPL_ANNONCE_LEGALE_DIR
    );

    @Override
    public String workflowCode() {
        return WORKFLOW_CODE;
    }

    @Override
    public Set<String> supportedTemplates() {
        return SUPPORTED;
    }

    @Override
    public Map<String, Object> map(String templateCode, Map<String, Object> payload) {
        if (templateCode == null || !SUPPORTED.contains(templateCode)) {
            throw new IllegalArgumentException(
                    "Template non supporté par CreationSarlMapper : " + templateCode
                            + " (supportés : " + SUPPORTED + ")");
        }
        Map<String, Object> safe = payload == null ? Map.of() : payload;
        // Voie MODÈLES DÉTERMINISTES DU DIRECTEUR : le builder produit EXACTEMENT
        // les variables du dictionnaire officiel + les boucles. Aucune clé hors
        // dictionnaire n'est émise.
        return CreationDirecteurVarsBuilder.build(templateCode, safe);
    }
}
