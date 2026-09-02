package ma.jurika.ai.workflow.mapper;

import ma.jurika.ai.workflow.WorkflowDocumentMapper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Mapper L4 du « workflow » transverse {@code SEANCE_AG} : les 2 documents de séance
 * d'assemblée générale <b>partagés</b> et <b>optionnels</b> — la {@code CONVOCATION_AG}
 * (proposée en début de workflow, avant l'AG) et la {@code FEUILLE_PRESENCE_AG}
 * (proposée après l'AG).
 *
 * <p>Comme les 2 PV d'incident ({@link IncidentSeanceMapper}), ces documents sont
 * générables depuis TOUT workflow qui tient une assemblée (modification, dissolution,
 * liquidation, PV AGO, …). Les 2 modèles directeur sont <b>unifiés</b> (un seul fichier
 * couvre SARL et SARL AU via {@code $ASSOCIE_UNIQUE}) : il n'y a donc pas de variante
 * {@code _SARL} / {@code _SARL_AU} au niveau du code.
 *
 * <p>La logique de production des variables vit dans {@link SeancePvVarsBuilder}
 * (réutilisable, double nommage) ; ce mapper ne fait que router + valider.
 */
@Component
public class SeanceDocumentMapper implements WorkflowDocumentMapper {

    private static final String WORKFLOW = "SEANCE_AG";

    private static final Set<String> TEMPLATES = Set.of(
            "CONVOCATION_AG",
            "FEUILLE_PRESENCE_AG");

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
                    "Template non supporté par SeanceDocumentMapper : " + templateCode
                            + " (supportés : " + TEMPLATES + ")");
        }
        return SeancePvVarsBuilder.build(templateCode, payload == null ? Map.of() : payload);
    }
}
