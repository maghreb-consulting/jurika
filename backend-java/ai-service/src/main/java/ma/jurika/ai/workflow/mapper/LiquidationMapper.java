package ma.jurika.ai.workflow.mapper;

import ma.jurika.ai.workflow.WorkflowDocumentMapper;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Mapper L4 du workflow {@code LIQUIDATION} (Phase C).
 *
 * <p>Codes demandes par l'ecran et modeles rendus (lot L4d, E11d : modeles du corpus) :
 * <ul>
 *   <li>{@code PV_DISSOLUTION_LIQUIDATION_SARL(/_AU)} : proces-verbal de cloture de la
 *       liquidation, rendu par {@code PV_CLOTURE_LIQUIDATION_SARL(/_AU)}
 *       ({@link CloturePvCorpusVarsBuilder}) ;</li>
 *   <li>{@code RAPPORT_LIQUIDATION_DIRECTEUR} : rapport du liquidateur, rendu par
 *       {@code RAPPORT_LIQUIDATEUR_SARL(/_AU)} selon la forme de la societe
 *       ({@link RapportLiquidateurCorpusVarsBuilder}, non resolu : refus nomme) ;</li>
 *   <li>{@code ANNONCE_LEGALE_LIQUIDATION_SARL(/_AU)} : avis de cloture
 *       ({@link LiquidationAnnonceVarsBuilder}).</li>
 * </ul>
 *
 * <p>Aucune lecture DB : identite societe + associes pre-remplis depuis le dossier (payload).
 */
@Component
public class LiquidationMapper implements WorkflowDocumentMapper {

    private static final String WORKFLOW = "LIQUIDATION";
    static final String TPL_PV_SARL = DissolutionMapper.TPL_PV_SARL;
    static final String TPL_PV_SARL_AU = DissolutionMapper.TPL_PV_SARL_AU;
    static final String TPL_RAPPORT = "RAPPORT_LIQUIDATION_DIRECTEUR";

    static final String TPL_ANNONCE_SARL = LiquidationAnnonceVarsBuilder.TPL_SARL;
    static final String TPL_ANNONCE_SARL_AU = LiquidationAnnonceVarsBuilder.TPL_SARL_AU;

    private static final Set<String> TEMPLATES = Set.of(
            TPL_PV_SARL,
            TPL_PV_SARL_AU,
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

        // Annonce legale de cloture (avis JAL).
        if (TPL_ANNONCE_SARL.equals(templateCode) || TPL_ANNONCE_SARL_AU.equals(templateCode)) {
            return LiquidationAnnonceVarsBuilder.build(templateCode, safe);
        }
        if (TPL_RAPPORT.equals(templateCode)) {
            // Lot L4d (E11d) : bascule du rapport non resolue, rapport du classpath conserve.
            return RapportLiquidationDirecteurVars.rapportVars(safe);
        }
        return CloturePvCorpusVarsBuilder.build(templateCode, safe);
    }

    /**
     * Lot L4d (E11d) : modele du corpus rendu pour chaque document (L2_correspondance_gabarits.md).
     * Le rapport du liquidateur reste rendu par le classpath (bascule non resolue).
     */
    @Override
    public String gabarit(String templateCode, Map<String, Object> payload) {
        if (TPL_PV_SARL.equals(templateCode)) return CloturePvCorpusVarsBuilder.CORPUS_SARL;
        if (TPL_PV_SARL_AU.equals(templateCode)) return CloturePvCorpusVarsBuilder.CORPUS_SARL_AU;
        return templateCode;
    }
}
