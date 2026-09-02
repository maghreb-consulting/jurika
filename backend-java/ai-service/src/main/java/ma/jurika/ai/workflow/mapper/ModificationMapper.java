package ma.jurika.ai.workflow.mapper;

import ma.jurika.ai.workflow.WorkflowDocumentMapper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Mapper L4b du workflow {@code MODIFICATION}.
 *
 * <p>Templates supportés après Phase E2 (2026-08-09) + retrait de l'avenant
 * {@code STATUTS_MODIFIES_*} (audit directeur, 2026-08-10) :
 * <ul>
 *     <li>{@code PV_MODIFICATION_SARL} / {@code PV_MODIFICATION_SARL_AU} — PV de
 *         Modification à résolutions typées (modèles directeur, Phase E1) : délégués
 *         au noyau {@link ModificationDirecteurMapper}.</li>
 *     <li>{@code STATUTS_REFONDUS_SARL} / {@code STATUTS_REFONDUS_SARL_AU} — statut
 *         COMPLET refondu par la voie <b>directeur</b> (Phase E2) : délégué à
 *         {@link RefonteStatutsVarsBuilder} (état structuré ⊕ nouvelles valeurs).</li>
 * </ul>
 *
 * <p><b>Consigne directeur</b> : la modification <b>régénère les statuts issus de la
 * création en substituant les anciennes valeurs par les nouvelles</b> — c'est la voie
 * {@code STATUTS_REFONDUS_*}. L'avenant partiel {@code STATUTS_MODIFIES_*} faisait
 * doublon et a été retiré (0 référence active).
 *
 * <p><b>Phase E2</b> : la refonte des statuts passe désormais par la voie directeur
 * (plus par {@code CreationSarlMapper} + {@code STATUTS_CONSTITUTIFS_*}). Les voies
 * LEGACY {@code PV_AGE_*}, {@code CONVOCATION_ASSOCIES}, {@code FEUILLE_DE_PRESENCE}
 * et l'avenant {@code STATUTS_MODIFIES_*} ont été retirées.
 */
@Component
public class ModificationMapper implements WorkflowDocumentMapper {

    private static final String WORKFLOW = "MODIFICATION";

    // 2026-08-09 (E2) — Statut COMPLET refondu par la voie DIRECTEUR (le PV de
    // modification reste l'acte modificatif ; ces codes produisent le statut refondu).
    static final String TPL_REFONDUS_SARL = RefonteStatutsVarsBuilder.TPL_SARL;
    static final String TPL_REFONDUS_SARL_AU = RefonteStatutsVarsBuilder.TPL_SARL_AU;
    // Phase E1 (2026-08-09) — PV de Modification à résolutions typées (modèles directeur).
    static final String TPL_MODIFICATION_SARL = ModificationDirecteurMapper.TPL_SARL;
    static final String TPL_MODIFICATION_SARL_AU = ModificationDirecteurMapper.TPL_SARL_AU;
    // Phase 2 (2026-08-11) — Annonce légale de modification (avis à publier au JAL).
    static final String TPL_ANNONCE_SARL = ModificationAnnonceVarsBuilder.TPL_SARL;
    static final String TPL_ANNONCE_SARL_AU = ModificationAnnonceVarsBuilder.TPL_SARL_AU;

    private static final Set<String> TEMPLATES = Set.of(
            TPL_MODIFICATION_SARL,
            TPL_MODIFICATION_SARL_AU,
            TPL_REFONDUS_SARL,
            TPL_REFONDUS_SARL_AU,
            TPL_ANNONCE_SARL,
            TPL_ANNONCE_SARL_AU
    );

    @Override
    public String workflowCode() {
        return WORKFLOW;
    }

    @Override
    public Set<String> supportedTemplates() {
        return TEMPLATES;
    }

    @Override
    public Map<String, Object> map(String templateCode, Map<String, Object> payload) {
        if (templateCode == null || !TEMPLATES.contains(templateCode)) {
            throw new IllegalArgumentException(
                    "Template non supporté par ModificationMapper : " + templateCode
                            + " (supportés : " + TEMPLATES + ")");
        }
        Map<String, Object> safe = payload == null ? Map.of() : payload;

        // Phase E1 — PV de Modification à résolutions typées (modèles directeur).
        if (TPL_MODIFICATION_SARL.equals(templateCode)
                || TPL_MODIFICATION_SARL_AU.equals(templateCode)) {
            return ModificationDirecteurMapper.pvVars(templateCode, safe);
        }

        // Phase 2 — Annonce légale de modification (avis JAL, boucle DECISIONS publiables).
        if (TPL_ANNONCE_SARL.equals(templateCode)
                || TPL_ANNONCE_SARL_AU.equals(templateCode)) {
            return ModificationAnnonceVarsBuilder.build(templateCode, safe);
        }

        // Phase E2 — Statut COMPLET refondu par la voie DIRECTEUR (seuls codes restants),
        // alimenté par la fiche structurée superposée aux nouvelles valeurs des modifications.
        return RefonteStatutsVarsBuilder.build(templateCode, safe);
    }
}
