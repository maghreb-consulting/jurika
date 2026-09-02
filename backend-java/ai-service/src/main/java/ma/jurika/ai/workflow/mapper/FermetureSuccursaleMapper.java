package ma.jurika.ai.workflow.mapper;

import ma.jurika.ai.workflow.WorkflowDocumentMapper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Mapper mince du workflow {@code FERMETURE_SUCCURSALE} — fermeture d'une succursale d'une société
 * <b>marocaine</b> (assemblée générale des associés / décision de l'associé unique).
 *
 * <p>Couvre les deux modèles directeur :
 * <ul>
 *     <li>{@code PV_FERMETURE_SUCCURSALE_SARL} — SARL pluripersonnelle ;</li>
 *     <li>{@code PV_FERMETURE_SUCCURSALE_SARL_AU} — SARL à associé unique.</li>
 * </ul>
 *
 * <p>Réutilise le noyau séance (identité société marocaine pré-remplie BD) via
 * {@link SuccursaleVarsBuilder} et ajoute les variables propres à la fermeture
 * ({@code $SUCCURSALE_RC_NUMERO}, {@code $SUCCURSALE_DATE_FERMETURE}, {@code $SUCCURSALE_MOTIF}).
 *
 * <p>Depuis le lot DIVERS §D (2026-08-13), le mapper porte aussi l'<b>annonce légale de
 * fermeture</b> ({@code ANNONCE_LEGALE_FERMETURE_SUCCURSALE_SARL(/_AU)}) — modèles
 * directeur. Contrairement à l'ouverture, l'avis de fermeture est <b>linéaire</b> (aucun
 * bloc conditionnel) et publie obligatoirement le <b>motif</b>.
 */
@Component
public class FermetureSuccursaleMapper implements WorkflowDocumentMapper {

    private static final String WORKFLOW = "FERMETURE_SUCCURSALE";

    static final String TPL_ANNONCE_SARL = SuccursaleVarsBuilder.TPL_ANNONCE_FERM_SARL;
    static final String TPL_ANNONCE_SARL_AU = SuccursaleVarsBuilder.TPL_ANNONCE_FERM_SARL_AU;

    private static final Set<String> TEMPLATES = Set.of(
            SuccursaleVarsBuilder.TPL_FERM_SARL, SuccursaleVarsBuilder.TPL_FERM_SARL_AU,
            TPL_ANNONCE_SARL, TPL_ANNONCE_SARL_AU);

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
        if (!TEMPLATES.contains(templateCode)) {
            throw new IllegalArgumentException(
                    "Template non supporté par " + WORKFLOW + " : " + templateCode);
        }
        if (TPL_ANNONCE_SARL.equals(templateCode) || TPL_ANNONCE_SARL_AU.equals(templateCode)) {
            return SuccursaleVarsBuilder.fermetureAnnonceVars(templateCode, payload);
        }
        return SuccursaleVarsBuilder.fermetureVars(templateCode, payload);
    }
}
