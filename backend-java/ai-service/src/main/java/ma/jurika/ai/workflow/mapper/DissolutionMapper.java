package ma.jurika.ai.workflow.mapper;

import ma.jurika.ai.workflow.WorkflowDocumentMapper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Mapper L4 du workflow {@code DISSOLUTION} (Phase C).
 *
 * <p>Codes demandes par l'ecran et modeles rendus (lot L4d, E11d : modeles du corpus) :
 * <ul>
 *   <li>{@code PV_DISSOLUTION_LIQUIDATION_SARL(/_AU)} : proces-verbal de dissolution
 *       anticipee, d'ouverture de la liquidation et de nomination du liquidateur, rendu par
 *       {@code PV_DISSOLUTION_ANTICIPEE_SARL(/_AU)} ({@link DissolutionPvCorpusVarsBuilder}) ;</li>
 *   <li>{@code ANNONCE_LEGALE_DISSOLUTION_SARL(/_AU)} : avis de dissolution
 *       ({@link DissolutionAnnonceVarsBuilder}).</li>
 * </ul>
 *
 * <p>Le PV unifie du classpath (trois etapes via {@code $PV_ETAPE}) n'est plus servi : la
 * dissolution et la cloture ont chacune leur modele du corpus (LiquidationMapper pour la
 * cloture). Aucune lecture DB : identite societe + associes pre-remplis depuis le dossier.
 */
@Component
public class DissolutionMapper implements WorkflowDocumentMapper {

    private static final String WORKFLOW = "DISSOLUTION";

    /** Codes du PV demandes par l'ecran (dissolution et liquidation) ; jamais rendus tels quels. */
    static final String TPL_PV_SARL = "PV_DISSOLUTION_LIQUIDATION_SARL";
    static final String TPL_PV_SARL_AU = "PV_DISSOLUTION_LIQUIDATION_SARL_AU";

    static final String TPL_ANNONCE_SARL = DissolutionAnnonceVarsBuilder.TPL_SARL;
    static final String TPL_ANNONCE_SARL_AU = DissolutionAnnonceVarsBuilder.TPL_SARL_AU;

    private static final Set<String> TEMPLATES = Set.of(
            TPL_PV_SARL,
            TPL_PV_SARL_AU,
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

        // Annonce legale de dissolution (avis JAL).
        if (TPL_ANNONCE_SARL.equals(templateCode) || TPL_ANNONCE_SARL_AU.equals(templateCode)) {
            return DissolutionAnnonceVarsBuilder.build(templateCode, safe);
        }
        // Lot L4d (E11d) : PV de dissolution anticipee du corpus.
        return DissolutionPvCorpusVarsBuilder.build(templateCode, safe);
    }

    /**
     * Lot L4d (E11d) : le PV de dissolution est rendu par le modele du corpus
     * {@code PV_DISSOLUTION_ANTICIPEE_SARL(_AU)} (L2_correspondance_gabarits.md). Les autres
     * documents gardent leur code.
     */
    @Override
    public String gabarit(String templateCode, Map<String, Object> payload) {
        if (TPL_PV_SARL.equals(templateCode)) return DissolutionPvCorpusVarsBuilder.CORPUS_SARL;
        if (TPL_PV_SARL_AU.equals(templateCode)) return DissolutionPvCorpusVarsBuilder.CORPUS_SARL_AU;
        return templateCode;
    }
}
