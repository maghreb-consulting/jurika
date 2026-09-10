package ma.jurika.ai.workflow.mapper;

import ma.jurika.ai.workflow.WorkflowDocumentMapper;

import java.util.Map;
import java.util.Set;

/**
 * Mapper L4 du workflow {@code CREATION_SARL} — <b>DÉBRANCHÉ AU LOT A
 * (2026-09-10), EN ATTENTE DU LOT B</b>.
 *
 * <p><b>Pourquoi il n'est plus un bean.</b> Le lot A a remplacé le corpus de
 * création : les 7 modèles d'août et de lot 5 sont sortis du manifeste, les 23
 * gabarits livrés par le cabinet le 9 septembre les ont remplacés. Ces 23
 * gabarits emploient 404 variables, dont <b>253 que la plateforme ne résout
 * pas</b> — 160 sont des données à saisir qui n'ont pas encore de champ au
 * parcours. Un mapper qui les déclarerait rendrait des documents troués ; c'est
 * l'objet du lot B, pas de celui-ci.
 *
 * <p><b>Pourquoi {@code @Component} est retiré plutôt que
 * {@link #supportedTemplates()} vidé.</b>
 * {@code WorkflowDocumentMappingService} refuse au démarrage un mapper dont
 * l'ensemble est vide — et il a raison : un mapper qui ne sait rien produire est
 * une erreur de câblage, pas un état. Sans bean, le service n'enregistre
 * simplement pas {@code CREATION_SARL} ; {@code WorkflowDocumentController}
 * traite ce cas explicitement et renvoie une liste de modèles vide plutôt
 * qu'une erreur.
 *
 * <p><b>Conséquence assumée : le parcours de création ne génère plus aucun
 * document</b> jusqu'à ce que le lot B recâble la résolution sur le nouveau
 * corpus.
 *
 * <p>La classe et {@link CreationFormulairesVarsBuilder} sont conservées telles
 * quelles : elles portent la résolution des anciens formulaires, dont le lot B
 * repartira. {@link CreationDirecteurVarsBuilder}, lui, reste <b>en service</b> —
 * la refonte des statuts (MODIFICATION, {@code STATUTS_REFONDUS_*}) l'appelle
 * via {@link RefonteStatutsVarsBuilder}.
 */
public class CreationSarlMapper implements WorkflowDocumentMapper {

    private static final String WORKFLOW_CODE = "CREATION_SARL";

    // Lot 5 (2026-09-07) — TROIS FORMULAIRES administratifs (etapes 19, 20 et 21
    // du guide). Leurs CODES survivent au lot A : deux migrations deja appliquees
    // (dataroom V30, ticket V20) les nomment comme donnees. Leurs GABARITS, eux,
    // ont ete remplaces par ceux du 9 septembre, bien plus fournis (84, 136 et 76
    // variables contre 38, 44 et 22) : le builder ci-dessous ne les couvre plus.
    public static final String TPL_DEMANDE_TP = CreationFormulairesVarsBuilder.TPL_DEMANDE_TP;
    public static final String TPL_DECLARATION_EXISTENCE =
            CreationFormulairesVarsBuilder.TPL_DECLARATION_EXISTENCE;
    public static final String TPL_DECLARATION_RC = CreationFormulairesVarsBuilder.TPL_DECLARATION_RC;

    private static final Set<String> SUPPORTED = Set.of(
            TPL_DEMANDE_TP,
            TPL_DECLARATION_EXISTENCE,
            TPL_DECLARATION_RC
    );

    @Override
    public String workflowCode() {
        return WORKFLOW_CODE;
    }

    @Override
    public Set<String> supportedTemplates() {
        return SUPPORTED;
    }

    @Override
    public Map<String, Object> map(String templateCode, Map<String, Object> payload) {
        if (templateCode == null || !SUPPORTED.contains(templateCode)) {
            throw new IllegalArgumentException(
                    "Template non supporté par CreationSarlMapper : " + templateCode
                            + " (supportés : " + SUPPORTED + ")");
        }
        return CreationFormulairesVarsBuilder.build(templateCode,
                payload == null ? Map.of() : payload);
    }
}
