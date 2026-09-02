package ma.jurika.ai.workflow.mapper;

import ma.jurika.ai.workflow.WorkflowDocumentMapper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Mapper L4 du workflow {@code LIQUIDATION} (Phase C).
 *
 * <p>Bean mince : délègue au noyau partagé {@link DissolutionLiquidationMapper} qui réutilise
 * le noyau séance d'AG ({@link SeancePvVarsBuilder}). Couvre :
 * <ul>
 *   <li>{@code PV_DISSOLUTION_LIQUIDATION_SARL(/_AU)} — même modèle unifié que la dissolution,
 *       à l'étape « clôture » (approbation des comptes définitifs, quitus, boni/mali,
 *       radiation) ; {@code $PV_ETAPE} est fixé par le workflow, jamais saisi librement ;</li>
 *   <li>{@code RAPPORT_LIQUIDATION_DIRECTEUR} — rapport final du liquidateur (document
 *       distinct), délégué à {@link DissolutionLiquidationMapper#rapportVars(Map)}.</li>
 * </ul>
 *
 * <p>Depuis le lot « Liquidation 4 étapes » (2026-08-13), le mapper porte aussi l'<b>annonce
 * légale de clôture de liquidation</b> ({@code ANNONCE_LEGALE_LIQUIDATION_SARL(/_AU)}) —
 * second et dernier avis du cycle, fondant la radiation au RC — déléguée à
 * {@link LiquidationAnnonceVarsBuilder}.
 *
 * <p>Sélection SARL vs SARL AU par le suffixe du {@code templateCode}. Aucune lecture DB :
 * identité société + associés pré-remplis depuis le dossier par l'appelant (payload).
 */
@Component
public class LiquidationMapper implements WorkflowDocumentMapper {

    private static final String WORKFLOW = "LIQUIDATION";
    static final String TPL_RAPPORT = DissolutionLiquidationMapper.TPL_RAPPORT;

    static final String TPL_ANNONCE_SARL = LiquidationAnnonceVarsBuilder.TPL_SARL;
    static final String TPL_ANNONCE_SARL_AU = LiquidationAnnonceVarsBuilder.TPL_SARL_AU;

    private static final Set<String> TEMPLATES = Set.of(
            DissolutionLiquidationMapper.TPL_PV_SARL,
            DissolutionLiquidationMapper.TPL_PV_SARL_AU,
            TPL_RAPPORT,
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
                    "Template non supporté par LiquidationMapper : " + templateCode
                            + " (supportés : " + TEMPLATES + ")");
        }
        Map<String, Object> safe = payload == null ? Map.of() : payload;

        // Annonce légale de clôture (avis JAL — condition boni/mali, aucune boucle).
        if (TPL_ANNONCE_SARL.equals(templateCode) || TPL_ANNONCE_SARL_AU.equals(templateCode)) {
            return LiquidationAnnonceVarsBuilder.build(templateCode, safe);
        }
        // Rapport de liquidation : document distinct (rapport du liquidateur), délégué au noyau.
        if (TPL_RAPPORT.equals(templateCode)) {
            return DissolutionLiquidationMapper.rapportVars(safe);
        }
        // PV unifié — étape « clôture » de la liquidation.
        return DissolutionLiquidationMapper.pvVars(
                templateCode, safe, DissolutionLiquidationMapper.ETAPE_CLOTURE);
    }
}
