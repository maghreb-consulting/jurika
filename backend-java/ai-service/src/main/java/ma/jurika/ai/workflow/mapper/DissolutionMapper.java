package ma.jurika.ai.workflow.mapper;

import ma.jurika.ai.workflow.WorkflowDocumentMapper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Mapper L4 du workflow {@code DISSOLUTION} (Phase C).
 *
 * <p>Bean mince : délègue au noyau partagé {@link DissolutionLiquidationMapper} qui réutilise
 * le noyau séance d'AG ({@link SeancePvVarsBuilder}). Alimente le PV directeur unifié
 * {@code PV_DISSOLUTION_LIQUIDATION_SARL(/_AU)} à l'étape « dissolution » (décision de
 * dissolution + nomination du liquidateur). Un seul modèle couvre les trois étapes du cycle
 * via {@code $PV_ETAPE} ; l'étape est fixée par le workflow, jamais saisie librement.
 *
 * <p>Depuis le lot « Dissolution 4 étapes » (2026-08-12), le mapper porte aussi l'<b>annonce
 * légale de dissolution</b> ({@code ANNONCE_LEGALE_DISSOLUTION_SARL(/_AU)}) — avis à publier au
 * journal d'annonces légales — déléguée à {@link DissolutionAnnonceVarsBuilder}.
 *
 * <p>Sélection SARL vs SARL AU par le suffixe du {@code templateCode}. Aucune lecture DB :
 * identité société + associés pré-remplis depuis le dossier par l'appelant (payload).
 */
@Component
public class DissolutionMapper implements WorkflowDocumentMapper {

    private static final String WORKFLOW = "DISSOLUTION";

    static final String TPL_ANNONCE_SARL = DissolutionAnnonceVarsBuilder.TPL_SARL;
    static final String TPL_ANNONCE_SARL_AU = DissolutionAnnonceVarsBuilder.TPL_SARL_AU;

    private static final Set<String> TEMPLATES = Set.of(
            DissolutionLiquidationMapper.TPL_PV_SARL,
            DissolutionLiquidationMapper.TPL_PV_SARL_AU,
            TPL_ANNONCE_SARL,
            TPL_ANNONCE_SARL_AU);

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
                    "Template non supporté par DissolutionMapper : " + templateCode
                            + " (supportés : " + TEMPLATES + ")");
        }
        Map<String, Object> safe = payload == null ? Map.of() : payload;

        // Annonce légale de dissolution (avis JAL — aucune boucle, aucune condition).
        if (TPL_ANNONCE_SARL.equals(templateCode) || TPL_ANNONCE_SARL_AU.equals(templateCode)) {
            return DissolutionAnnonceVarsBuilder.build(templateCode, safe);
        }

        return DissolutionLiquidationMapper.pvVars(
                templateCode, safe, DissolutionLiquidationMapper.ETAPE_DISSOLUTION);
    }
}
