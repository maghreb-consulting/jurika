package ma.jurika.ai.workflow.mapper;

import ma.jurika.ai.workflow.WorkflowDocumentMapper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Mapper L4 du « workflow » transverse {@code INCIDENT_SEANCE} : les 2 PV d'incident
 * de séance (constat de défaut de quorum / irrégularité de convocation), déclinés en
 * SARL et SARL AU.
 *
 * <p>Ces PV sont générables depuis TOUT workflow qui tient une assemblée générale
 * (modification, dissolution, liquidation, PV AGO, …). Ils forment une <b>allowlist
 * dédiée</b> « incident de séance » alimentée par les données de séance déjà saisies.
 * La logique de production des variables vit dans {@link SeancePvVarsBuilder}
 * (réutilisable), ce mapper ne fait que router + sélectionner SARL vs SARL AU.
 */
@Component
public class IncidentSeanceMapper implements WorkflowDocumentMapper {

    private static final String WORKFLOW = "INCIDENT_SEANCE";

    private static final Set<String> TEMPLATES = Set.of(
            "PV_DEFAUT_QUORUM_SARL",
            "PV_DEFAUT_QUORUM_SARL_AU",
            "PV_IRREGULARITE_CONVOCATION_SARL",
            "PV_IRREGULARITE_CONVOCATION_SARL_AU");

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
                    "Template non supporté par IncidentSeanceMapper : " + templateCode
                            + " (supportés : " + TEMPLATES + ")");
        }
        return SeancePvVarsBuilder.build(templateCode, payload == null ? Map.of() : payload);
    }
}
