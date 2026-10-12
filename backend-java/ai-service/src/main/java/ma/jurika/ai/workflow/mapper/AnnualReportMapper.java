package ma.jurika.ai.workflow.mapper;

import ma.jurika.ai.workflow.WorkflowDocumentMapper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Mapper du workflow {@code ANNUAL_REPORT} (rapport de gestion annuel de la gerance).
 *
 * <p>Lot L4d (E11e) : le rapport est rendu par les modeles du corpus
 * RAPPORT_GESTION_EXERCICE_SARL(_AU), choisis par la forme de la societe. Ses variables
 * sont construites en un seul endroit, {@link RapportGestionVarsBuilder}, que le parcours
 * PV_AGO ({@link ApprobationComptesMapper}) appelle aussi : meme charge utile, meme acte.
 */
@Component
public class AnnualReportMapper implements WorkflowDocumentMapper {

    private static final String WORKFLOW = "ANNUAL_REPORT";
    static final String TPL = RapportGestionVarsBuilder.TPL;
    private static final Set<String> TEMPLATES = Set.of(TPL);

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
        verifier(templateCode);
        return RapportGestionVarsBuilder.build(payload);
    }

    @Override
    public String gabarit(String templateCode, Map<String, Object> payload) {
        verifier(templateCode);
        return RapportGestionVarsBuilder.modele(payload);
    }

    private static void verifier(String templateCode) {
        if (templateCode == null || !TEMPLATES.contains(templateCode)) {
            throw new IllegalArgumentException(
                    "Template non support\u00e9 par AnnualReportMapper : " + templateCode
                            + " (support\u00e9s : " + TEMPLATES + ")");
        }
    }
}
