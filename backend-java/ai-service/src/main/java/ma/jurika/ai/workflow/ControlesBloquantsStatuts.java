package ma.jurika.ai.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lot B (2026-09-11) — LES TROIS CONTRÔLES BLOQUANTS AU LANCEMENT DE LA
 * GÉNÉRATION DES STATUTS.
 *
 * <p>Le parcours du 9 septembre ne comporte pas d'étape pour ces vérifications.
 * L'ancien référentiel, lui, en faisait trois lignes à cocher — le certificat
 * négatif (étape 4), le contrôle d'identité des associés (étape 2) et le rapport
 * du commissaire aux apports (étape 10). Le cabinet les a retirées du parcours :
 * <b>ce sont des contrôles, pas des étapes</b>. Ils sont faits par le système au
 * moment où la génération des statuts est lancée, et ils bloquent l'opération
 * tant qu'ils ne sont pas satisfaits (§ 18 du dictionnaire des variables).
 *
 * <p>La différence n'est pas de vocabulaire. Une étape se coche — donc elle peut
 * se cocher à tort, et l'acte sort quand même. Un contrôle interroge les
 * <b>variables réellement résolues</b>, celles-là mêmes qui seraient imprimées :
 * il ne peut pas être satisfait par un geste, seulement par la donnée.
 *
 * <p>Il s'ensuit que le contrôle s'exécute APRÈS la résolution et AVANT le rendu.
 * Le faire avant la résolution reviendrait à interroger le payload brut, dont la
 * forme dépend du front ; le faire après le rendu laisserait produire un document
 * qu'on refuserait ensuite.
 *
 * <p>Le message nomme ce qui manque, variable par variable et occurrence par
 * occurrence : « le 2ᵉ associé » et non « un associé ».
 */
public final class ControlesBloquantsStatuts {

    /** Les deux seuls modèles concernés : le contrôle porte sur les statuts. */
    private static final java.util.Set<String> STATUTS =
            java.util.Set.of("STATUTS_SARL", "STATUTS_SARL_AU");

    /**
     * Valeur de {@code $COMMISSAIRE_APPORTS_DESIGNATION} qui rend le rapport
     * exigible. Le dictionnaire la fixe : « requis ». Toute autre valeur — « non
     * requis », « dispensé », vide — laisse le contrôle sans objet.
     */
    private static final String DESIGNATION_REQUISE = "requis";

    private ControlesBloquantsStatuts() {}

    public static boolean concerne(String templateCode) {
        return templateCode != null && STATUTS.contains(templateCode);
    }

    /**
     * @param variables les variables résolues pour ce modèle
     * @return {@code null} si la génération peut être lancée ; sinon le motif de
     *         refus, nommant chaque pièce manquante
     */
    public static String motifDeRefus(Map<String, Object> variables) {
        Map<String, Object> v = variables == null ? Map.of() : variables;
        List<String> manques = new ArrayList<>();

        // ---- 1. Certificat négatif obtenu ----------------------------------
        // Condition de passage : les DEUX variables existent.
        if (vide(v.get("CERTIFICAT_NEGATIF_NUMERO"))) {
            manques.add("le numéro du certificat négatif ($CERTIFICAT_NEGATIF_NUMERO)");
        }
        if (vide(v.get("CERTIFICAT_NEGATIF_DATE"))) {
            manques.add("la date du certificat négatif ($CERTIFICAT_NEGATIF_DATE)");
        }

        // ---- 2. Pièces d'identité et de capacité réunies --------------------
        // Condition de passage : aucune de ces variables n'est vide POUR CHAQUE
        // occurrence des boucles. Une pièce manquante sur le troisième associé
        // bloque autant qu'une pièce manquante sur le premier.
        List<Map<String, Object>> associes = liste(v.get("ASSOCIES"));
        for (int i = 0; i < associes.size(); i++) {
            Map<String, Object> a = associes.get(i);
            String qui = rang(i + 1) + " associé";
            if (vide(a.get("ASSOCIE_PIECE_TYPE"))) {
                manques.add("le type de pièce d'identité du " + qui + " ($ASSOCIE_PIECE_TYPE)");
            }
            if (vide(a.get("ASSOCIE_PIECE_NUMERO"))) {
                manques.add("le numéro de pièce d'identité du " + qui + " ($ASSOCIE_PIECE_NUMERO)");
            }
            // Un associé personne morale ne produit pas une pièce d'identité mais
            // son immatriculation et le nom de son représentant : c'est là que se
            // vérifient son existence et sa capacité.
            if (estPersonneMorale(a)) {
                if (vide(a.get("ASSOCIE_RC_NUMERO"))) {
                    manques.add("le numéro RC du " + qui
                            + ", personne morale ($ASSOCIE_RC_NUMERO)");
                }
                if (vide(a.get("ASSOCIE_REPRESENTANT_NOM"))) {
                    manques.add("le représentant du " + qui
                            + ", personne morale ($ASSOCIE_REPRESENTANT_NOM)");
                }
            }
        }

        List<Map<String, Object>> gerants = liste(v.get("GERANTS"));
        for (int i = 0; i < gerants.size(); i++) {
            Map<String, Object> g = gerants.get(i);
            String qui = rang(i + 1) + " gérant";
            if (vide(g.get("GERANT_PIECE_TYPE"))) {
                manques.add("le type de pièce d'identité du " + qui + " ($GERANT_PIECE_TYPE)");
            }
            if (vide(g.get("GERANT_PIECE_NUMERO"))) {
                manques.add("le numéro de pièce d'identité du " + qui + " ($GERANT_PIECE_NUMERO)");
            }
        }

        // ---- 3. Rapport du commissaire aux apports obtenu -------------------
        // Condition de passage : SI la désignation vaut « requis », les deux autres
        // variables existent. Sinon, contrôle sans objet — et c'est important :
        // exiger un rapport là où la loi n'en demande pas bloquerait les dossiers
        // sans apport en nature, c'est-à-dire la majorité.
        String designation = texte(v.get("COMMISSAIRE_APPORTS_DESIGNATION"));
        if (DESIGNATION_REQUISE.equalsIgnoreCase(designation)) {
            if (vide(v.get("COMMISSAIRE_APPORTS_NOM"))) {
                manques.add("le nom du commissaire aux apports ($COMMISSAIRE_APPORTS_NOM)");
            }
            if (vide(v.get("COMMISSAIRE_APPORTS_DATE_RAPPORT"))) {
                manques.add("la date du rapport du commissaire aux apports "
                        + "($COMMISSAIRE_APPORTS_DATE_RAPPORT)");
            }
        }

        if (manques.isEmpty()) return null;
        return "Génération des statuts bloquée — il manque : " + String.join(" ; ", manques) + ".";
    }

    /**
     * « personne morale » au sens du dictionnaire. On teste {@code ASSOCIE_TYPE}
     * — la valeur normalisée par {@code CreationDirecteurVarsBuilder} — plutôt que
     * la présence d'une dénomination : un associé personne physique peut porter un
     * nom commercial sans être une société.
     */
    private static boolean estPersonneMorale(Map<String, Object> associe) {
        String type = texte(associe.get("ASSOCIE_TYPE"));
        return type.contains("morale");
    }

    private static String rang(int n) {
        return switch (n) {
            case 1 -> "1er";
            default -> n + "e";
        };
    }

    private static boolean vide(Object o) {
        return texte(o).isBlank();
    }

    private static String texte(Object o) {
        return o == null ? "" : String.valueOf(o).trim().toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> liste(Object o) {
        if (!(o instanceof List<?> list)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
        }
        return out;
    }
}
