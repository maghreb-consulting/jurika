package ma.jurika.ai.workflow.mapper;

import ma.jurika.ai.workflow.WorkflowDocumentMapper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Mapper mince du workflow {@code SUCCURSALE_ETR} — création au Maroc d'une succursale d'une
 * société <b>étrangère</b> (décision de l'organe compétent de la société mère).
 *
 * <p>Couvre les deux modèles directeur :
 * <ul>
 *     <li>{@code PV_CREATION_SUCCURSALE_ETRANGERE_SARL} — organe collégial / pluralité d'associés ;</li>
 *     <li>{@code PV_CREATION_SUCCURSALE_ETRANGERE_SARL_AU} — organe / associé unique.</li>
 * </ul>
 *
 * <p>Contrairement à {@link SuccursaleMaMapper}, la société décisionnaire est <b>étrangère et
 * saisie</b> (aucune identité BD, aucune boucle ASSOCIES marocains) : variables {@code $SOCIETE_MERE_*},
 * {@code $ORGANE_*} et {@code $REPRESENTANT_SUCCURSALE_*}. Délègue au noyau
 * {@link SuccursaleVarsBuilder}.
 *
 * <p>Depuis le lot DIVERS §C (2026-08-13), le mapper porte aussi l'<b>annonce légale
 * d'ouverture — variante ÉTRANGÈRE</b>
 * ({@code ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_ETRANGERE_SARL(/_AU)}). ⚠ Ces deux modèles
 * sont <b>dérivés</b> ({@code origin = derive-jurika}) des modèles 05_ du directeur, qui
 * ne couvrent que la mère marocaine ; ils <b>doivent être validés par le directeur</b>.
 *
 * <p>Lot L4d (E11) : l'avis est desormais rendu par le modele du corpus
 * {@code ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_SOCIETE_ETRANGERE} (sans variante de forme) ; les
 * copies derivees du classpath sont retirees. Le PV d'organe unique
 * ({@code PV_CREATION_SUCCURSALE_ETRANGERE_SARL_AU}) reste servi par le classpath : le modele
 * du corpus renvoie a une variante _AU qu'il ne contient pas (ecart du cabinet, A_DECIDER
 * directeur, ECARTS_MODELES_CABINET.md).
 */
@Component
public class SuccursaleEtrMapper implements WorkflowDocumentMapper {

    private static final String WORKFLOW = "SUCCURSALE_ETR";

    static final String TPL_ANNONCE_SARL = SuccursaleVarsBuilder.TPL_ANNONCE_OUV_ETR_SARL;
    static final String TPL_ANNONCE_SARL_AU = SuccursaleVarsBuilder.TPL_ANNONCE_OUV_ETR_SARL_AU;

    private static final Set<String> TEMPLATES = Set.of(
            SuccursaleVarsBuilder.TPL_ETR_SARL, SuccursaleVarsBuilder.TPL_ETR_SARL_AU,
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
            return SuccursaleVarsBuilder.ouvertureEtrAnnonceVars(templateCode, payload);
        }
        return SuccursaleVarsBuilder.ouvertureEtrVars(templateCode, payload);
    }

    /** Lot L4d (E11) : modele du corpus de l'avis d'ouverture (societe etrangere). */
    static final String ANNONCE_CORPUS = "ANNONCE_LEGALE_OUVERTURE_SUCCURSALE_SOCIETE_ETRANGERE";

    @Override
    public String gabarit(String templateCode, Map<String, Object> payload) {
        if (TPL_ANNONCE_SARL.equals(templateCode) || TPL_ANNONCE_SARL_AU.equals(templateCode)) return ANNONCE_CORPUS;
        return templateCode;
    }
}
