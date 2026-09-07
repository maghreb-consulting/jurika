package ma.jurika.dataroom.application;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Types de MODIFICATION retenus dans un workflow, extraits de ses donnees
 * d'etape.
 *
 * <p>Un seul endroit dans le code sait lire {@code step1.selectedTypes} et
 * traduire les codes en libelles : la fiche client et le libelle du dossier de
 * ticket s'appuient tous deux dessus. Deux implementations divergeraient au
 * premier ajout de sous-type.
 */
final class ModificationSousTypes {

    /** 28 sous-types de MODIFICATION (alignes MOD_CATEGORIES front) -> libelle. */
    static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("CHANGEMENT_DENOMINATION", "changement de dénomination"),
            Map.entry("CHANGEMENT_OBJET", "changement de l'objet social"),
            Map.entry("TRANSFERT_SIEGE", "transfert du siège social"),
            Map.entry("PROROGATION_DUREE", "prorogation de la durée"),
            Map.entry("TRANSFORMATION", "transformation de la forme juridique"),
            Map.entry("AUGMENTATION_CAPITAL", "augmentation de capital (numéraire)"),
            Map.entry("AUGMENTATION_CAPITAL_NATURE", "augmentation de capital (apport en nature)"),
            Map.entry("AUGMENTATION_CAPITAL_RESERVES", "augmentation de capital (incorporation de réserves)"),
            Map.entry("REDUCTION_CAPITAL", "réduction de capital"),
            Map.entry("MODIF_VALEUR_NOMINALE", "modification de la valeur nominale des parts"),
            Map.entry("CESSION_PARTIELLE", "cession partielle de parts"),
            Map.entry("CESSION_TOTALE", "cession totale de parts"),
            Map.entry("TRANSMISSION_PARTS", "transmission de parts (succession / donation)"),
            Map.entry("NANTISSEMENT", "nantissement de parts"),
            Map.entry("DESIGNATION_GERANT", "nomination d'un gérant"),
            Map.entry("REVOCATION_GERANT", "révocation d'un gérant"),
            Map.entry("MODIF_NOMBRE_GERANTS", "modification du nombre / durée des gérants"),
            Map.entry("MODIF_POUVOIRS_GERANT", "modification des pouvoirs / rémunération du gérant"),
            Map.entry("MODALITES_DECISIONS", "modalités de décisions"),
            Map.entry("DESIGNATION_CAC", "désignation d'un commissaire aux comptes"),
            Map.entry("CLAUSE_AGREMENT", "clause d'agrément"),
            Map.entry("CLAUSE_PREEMPTION", "clause de préemption / inaliénabilité"),
            Map.entry("PACTE_ASSOCIES", "pacte d'associés"),
            Map.entry("CONTINUATION_PERTES", "continuation malgré pertes"),
            Map.entry("FUSION_SCISSION", "fusion / scission / apport partiel"),
            Map.entry("CREATION_SUCCURSALE", "création / transfert / suppression de succursale"),
            Map.entry("POUVOIRS_FORMALITES", "pouvoirs pour formalités"));

    /**
     * Codes hérités d'une version antérieure du formulaire, encore présents
     * dans les workflows déjà enregistrés.
     *
     * <p>Constaté à l'audit du lot 1, vérifié dans l'application : le dossier de
     * T-2026-00616 s'affichait « Modification — modification denomination,
     * transfert siege — … ». Le libellé se construisait bien, mais sur le repli
     * qui se contente de retirer les tirets bas : sans accent, sans majuscule,
     * et en répétant le mot « Modification » que le libellé porte déjà. Illisible
     * pour un juriste, et faussement bogué.
     *
     * <p>On ne réécrit pas les données déjà enregistrées : une migration de
     * contenu se prouve mal et casse au premier workflow qu'on aurait oublié.
     * On traduit à l'affichage.
     */
    private static final Map<String, String> CODES_HERITES = Map.of(
            "MODIFICATION_DENOMINATION", "CHANGEMENT_DENOMINATION",
            "MODIFICATION_OBJET", "CHANGEMENT_OBJET",
            "TRANSFERT_SIEGE", "TRANSFERT_SIEGE");

    private ModificationSousTypes() {}

    /**
     * Libellé d'un code, quelle que soit sa casse et quel que soit son âge.
     *
     * <p>La casse est normalisée : les workflows anciens ont enregistré leurs
     * codes en minuscules, les récents en majuscules. Un code réellement inconnu
     * reste affiché en clair — un dossier au nom approximatif vaut mieux qu'un
     * dossier sans nom.
     */
    static String libelle(String code) {
        if (code == null || code.isBlank()) return null;
        String normalise = code.trim().toUpperCase(java.util.Locale.ROOT);
        String canonique = CODES_HERITES.getOrDefault(normalise, normalise);
        String label = LABELS.get(canonique);
        return label != null ? label : humanize(code);
    }

    /**
     * Libelles des types retenus, dans l'ordre de selection. Liste vide si le
     * workflow n'en porte aucun : on ne devine pas, on n'affiche rien.
     */
    static List<String> extraire(JsonNode data) {
        if (data == null) return List.of();
        JsonNode selected = data.path("step1").path("selectedTypes");
        if (selected.isMissingNode() || selected.isNull()) selected = data.path("selectedTypes");
        if (selected == null || !selected.isArray() || selected.isEmpty()) return List.of();

        List<String> labels = new ArrayList<>();
        for (JsonNode n : selected) {
            String label = libelle(n.asText());
            if (label != null) labels.add(label);
        }
        return labels;
    }

    /** Rendu « changement de dénomination, transfert du siège social », ou null. */
    static String enPhrase(JsonNode data) {
        List<String> labels = extraire(data);
        return labels.isEmpty() ? null : String.join(", ", labels);
    }

    /** Repli lisible pour un code inconnu : « MODIF_XXX » -> « modif xxx ». */
    private static String humanize(String code) {
        return code.replace('_', ' ').toLowerCase();
    }
}
