package ma.jurika.ai.workflow.mapper;

import ma.jurika.ai.workflow.WorkflowDocumentMapper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Mapper L4 du "workflow" transverse {@code SEANCE_AG} : les 2 documents de seance
 * d'assemblee generale partages et optionnels -- la {@code CONVOCATION_AG} (proposee en
 * debut de workflow, avant l'AG) et la {@code FEUILLE_PRESENCE_AG} (proposee apres l'AG).
 *
 * <p>Comme les 2 PV d'incident ({@link IncidentSeanceMapper}), ces documents sont
 * generables depuis tout workflow qui tient une assemblee (modification, dissolution,
 * liquidation, PV AGO...). Les codes CONVOCATION_AG et FEUILLE_PRESENCE_AG restent les codes
 * logiques du parcours (ecrans, types de la Data Room).
 *
 * <p>Lot L4d (E11b) : ils sont rendus par les modeles du corpus du cabinet, choisis selon la
 * donnee du parcours ({@link SeanceCorpusVarsBuilder#modele}) : convocation AGO, AGE ou
 * seconde convocation ; feuille de presence AGO ou AGE. En SARL a associe unique, aucun de
 * ces documents n'existe (article 76 de la loi n 5-96) : la generation est refusee.
 */
@Component
public class SeanceDocumentMapper implements WorkflowDocumentMapper {

    private static final String WORKFLOW = "SEANCE_AG";

    private static final Set<String> TEMPLATES = Set.of(
            SeanceCorpusVarsBuilder.CONVOCATION_AG,
            SeanceCorpusVarsBuilder.FEUILLE_PRESENCE_AG);

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
        return SeanceCorpusVarsBuilder.build(templateCode, payload == null ? Map.of() : payload);
    }

    /**
     * Modele du corpus choisi selon le type d'assemblee et le rang de la convocation. Sans
     * donnee pour le choisir (type absent, SARL AU), le code logique est rendu tel quel :
     * {@link #map} a deja refuse la generation en le disant.
     */
    @Override
    public String gabarit(String templateCode, Map<String, Object> payload) {
        String modele = SeanceCorpusVarsBuilder.modele(templateCode, payload);
        return modele == null ? templateCode : modele;
    }
}
