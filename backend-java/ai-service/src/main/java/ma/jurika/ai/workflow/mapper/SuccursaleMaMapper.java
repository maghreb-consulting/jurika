package ma.jurika.ai.workflow.mapper;

import ma.jurika.ai.workflow.WorkflowDocumentMapper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Mapper du workflow {@code SUCCURSALE_MA} — création d'une succursale d'une société
 * <b>marocaine</b> (assemblée générale des associés / décision de l'associé unique).
 *
 * <p>Couvre :
 * <ul>
 *     <li>{@code PV_CREATION_SUCCURSALE_MAROC_SARL} / {@code _AU} — modèles directeur unifiés
 *         (Phase D) : délégués au noyau {@link SuccursaleVarsBuilder} qui réutilise le noyau
 *         séance d'AG (identité société marocaine pré-remplie BD via {@code SocieteIdentityEnricher}) ;</li>
 *     <li>{@code ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_SARL} / {@code _AU} — <b>avis d'ouverture</b>
 *         à publier au journal d'annonces légales (lot DIVERS §B, 2026-08-13), délégué à
 *         {@link SuccursaleVarsBuilder#ouvertureAnnonceVars}. Il partage EXACTEMENT les mêmes
 *         variables succursale que le PV : aucun risque de divergence entre l'acte et l'avis.</li>
 * </ul>
 *
 * <p><b>Retrait du compagnon LEGACY</b> (lot DIVERS §B) : l'ancien
 * {@code ANNONCE_JAL_OUVERTURE_SUCCURSALE} (origin {@code directeur}, style
 * {@code uppercase_dollar}) faisait doublon avec le nouvel avis directeur — même objet, mêmes
 * faits, variables incompatibles ({@code $DATE_AGE} / {@code $NUMERO_DEPOT} vs
 * {@code $ASSEMBLEE_DATE} / {@code $DEPOT_LEGAL_NUMERO}) et aucun bloc conditionnel
 * dotation/responsable. Il a été retiré (modèle, entrée de manifest, constante et câblage).
 */
@Component
public class SuccursaleMaMapper implements WorkflowDocumentMapper {

    private static final String WORKFLOW = "SUCCURSALE_MA";

    static final String TPL_ANNONCE_SARL = SuccursaleVarsBuilder.TPL_ANNONCE_OUV_SARL;
    static final String TPL_ANNONCE_SARL_AU = SuccursaleVarsBuilder.TPL_ANNONCE_OUV_SARL_AU;

    private static final Set<String> TEMPLATES = Set.of(
            SuccursaleVarsBuilder.TPL_MA_SARL, SuccursaleVarsBuilder.TPL_MA_SARL_AU,
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
            return SuccursaleVarsBuilder.ouvertureAnnonceVars(templateCode, payload);
        }
        return SuccursaleVarsBuilder.ouvertureMaVars(templateCode, payload);
    }
}
