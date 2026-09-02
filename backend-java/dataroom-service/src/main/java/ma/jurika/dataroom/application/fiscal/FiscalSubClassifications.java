package ma.jurika.dataroom.application.fiscal;

import java.util.List;
import java.util.Map;

/**
 * RG-DF16 -- sous-classifications autorisees par categorie CGI.
 * Validees backend lors d'un upload (sinon 422).
 */
public final class FiscalSubClassifications {

    private FiscalSubClassifications() {}

    public static final Map<String, List<String>> ALLOWED = java.util.Map.ofEntries(
            Map.entry("TVA",          List.of("DECLARATION_MENSUELLE","DECLARATION_TRIMESTRIELLE",
                                     "PAIEMENT","DEMANDE_REMBOURSEMENT","ATTESTATION")),
            Map.entry("IS",           List.of("ACOMPTE_T1","ACOMPTE_T2","ACOMPTE_T3","ACOMPTE_T4",
                                     "DECLARATION_ANNUELLE","ETATS_DE_SYNTHESE","COTISATION_MINIMALE")),
            Map.entry("IR",           List.of("RAS_SALARIES_MENSUEL","DECLARATION_IR_PRO","ETAT_9421")),
            Map.entry("TP_TSC",       List.of("ROLE_ANNUEL","DECLARATION_EXISTENCE","DECLARATION_CESSATION","RECLAMATION")),
            Map.entry("RAS",          List.of("HONORAIRES_10","HONORAIRES_20","DIVIDENDES_15","INTERETS",
                                     "LOCATIONS","OPCVM","ETAT_9421")),
            Map.entry("ATTESTATIONS", List.of("REGULARITE_FISCALE","QUITUS_FISCAL","ATTESTATION_IS",
                                     "ATTESTATION_TVA","ETAT_IMPOSITION")),
            Map.entry("CONTENTIEUX",  List.of("NOTIFICATION_DGI","AVIS_REDRESSEMENT","ACCORD",
                                     "RECOURS_HIERARCHIQUE","JUGEMENT_TRIBUNAL")),
            // Prompt G (2026-06-23) — catégorie fourre-tout pour les imports.
            Map.entry("AUTRE",        List.of("AUTRE"))
    );

    public static boolean isValid(String categorie, String sousClassification) {
        List<String> sub = ALLOWED.get(categorie);
        return sub != null && sub.contains(sousClassification);
    }
}
