package ma.jurika.ai.workflow.generator;

import ma.jurika.ai.document.format.FrenchNumberToLetters;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * Générateur déterministe du couple (ORDRE_DU_JOUR_POINT, RESOLUTION_TEXTE) pour
 * le workflow MODIFICATION. Phrases juridiques figées — pas de LLM.
 *
 * <p>Identifiants alignés sur le front {@code ModificationWorkflowPage.MODIFICATIONS}
 * et le backend {@code ModificationWorkflow.TYPES} (source de vérité).
 *
 * <h2>Couverture (18 types)</h2>
 * <ul>
 *   <li>Identité société : {@code CHANGEMENT_DENOMINATION}, {@code CHANGEMENT_OBJET},
 *       {@code TRANSFERT_SIEGE}, {@code PROROGATION_DUREE}.</li>
 *   <li>Capital : {@code AUGMENTATION_CAPITAL}, {@code AUGMENTATION_CAPITAL_RESERVES},
 *       {@code REDUCTION_CAPITAL}, {@code MODIF_VALEUR_NOMINALE}.</li>
 *   <li>Gérance : {@code DESIGNATION_GERANT}, {@code REVOCATION_GERANT},
 *       {@code MODIF_NOMBRE_GERANTS}, {@code MODIF_POUVOIRS_GERANT}.</li>
 *   <li>Fonctionnement : {@code CLAUSE_AGREMENT}, {@code CLAUSE_PREEMPTION},
 *       {@code MODALITES_DECISIONS}.</li>
 *   <li>Décisions structurantes : {@code CONTINUATION_PERTES},
 *       {@code CREATION_SUCCURSALE}, {@code POUVOIRS_FORMALITES}.</li>
 * </ul>
 *
 * <h2>Fallback "annexe à fournir" (9 types)</h2>
 * {@code AUGMENTATION_CAPITAL_NATURE}, {@code CESSION_PARTIELLE},
 * {@code CESSION_TOTALE}, {@code TRANSMISSION_PARTS}, {@code NANTISSEMENT},
 * {@code TRANSFORMATION}, {@code FUSION_SCISSION}, {@code DESIGNATION_CAC},
 * {@code PACTE_ASSOCIES}.
 */
@Component
public class ResolutionTextGenerator {

    public record ResolutionInput(String typeId, Map<String, Object> details) {}

    public record ResolutionOutput(String ordreDuJourPoint, String resolutionTexte) {}

    public ResolutionOutput generate(ResolutionInput input) {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(input.typeId(), "typeId must not be null");
        Map<String, Object> d = input.details() != null ? input.details() : Map.of();

        return switch (input.typeId()) {
            case "CHANGEMENT_DENOMINATION" -> changementDenomination(d);
            case "CHANGEMENT_OBJET" -> changementObjet(d);
            case "TRANSFERT_SIEGE" -> transfertSiege(d);
            case "PROROGATION_DUREE" -> prorogationDuree(d);
            case "AUGMENTATION_CAPITAL" -> augmentationCapital(d);
            case "AUGMENTATION_CAPITAL_RESERVES" -> augmentationCapitalReserves(d);
            case "REDUCTION_CAPITAL" -> reductionCapital(d);
            case "MODIF_VALEUR_NOMINALE" -> modifValeurNominale(d);
            case "DESIGNATION_GERANT" -> designationGerant(d);
            case "REVOCATION_GERANT" -> revocationGerant(d);
            case "MODIF_NOMBRE_GERANTS" -> modifNombreGerants(d);
            case "MODIF_POUVOIRS_GERANT" -> modifPouvoirsGerant(d);
            case "CLAUSE_AGREMENT" -> clauseAgrement(d);
            case "CLAUSE_PREEMPTION" -> clausePreemption(d);
            case "MODALITES_DECISIONS" -> modalitesDecisions(d);
            case "CONTINUATION_PERTES" -> continuationPertes(d);
            case "CREATION_SUCCURSALE" -> creationSuccursale(d);
            case "POUVOIRS_FORMALITES" -> pouvoirsFormalites(d);
            case "AUGMENTATION_CAPITAL_NATURE",
                 "CESSION_PARTIELLE",
                 "CESSION_TOTALE",
                 "TRANSMISSION_PARTS",
                 "NANTISSEMENT",
                 "TRANSFORMATION",
                 "FUSION_SCISSION",
                 "DESIGNATION_CAC",
                 "PACTE_ASSOCIES" -> annexeAFournir(input.typeId(), d);
            default -> throw new UnsupportedOperationException(
                    "ResolutionTextGenerator: type non encore supporté '" + input.typeId() + "'.");
        };
    }

    // ── Identité société ─────────────────────────────────────────────────

    private ResolutionOutput changementDenomination(Map<String, Object> d) {
        String nouvelle = requireString(d, "nouvelleDenomination");
        String ancienne = optString(d, "ancienneDenomination");
        String sigle = optString(d, "sigle");
        String odj = "Changement de dénomination sociale";
        StringBuilder sb = new StringBuilder("L'assemblée générale décide ");
        if (ancienne != null) {
            sb.append("de modifier la dénomination sociale de la société, qui était \"")
              .append(ancienne).append("\", pour devenir \"").append(nouvelle).append("\"");
        } else {
            sb.append("d'adopter la nouvelle dénomination sociale \"").append(nouvelle).append("\"");
        }
        if (sigle != null) sb.append(" (sigle : ").append(sigle).append(")");
        sb.append(". L'article 2 (DÉNOMINATION SOCIALE) des statuts est modifié en conséquence.");
        return new ResolutionOutput(odj, sb.toString());
    }

    private ResolutionOutput changementObjet(Map<String, Object> d) {
        String nouvel = requireString(d, "nouvelObjet");
        String ancien = optString(d, "ancienObjet");
        String odj = "Changement de l'objet social";
        String texte = ancien != null
                ? "L'assemblée générale décide de modifier l'objet social, qui était : \""
                        + ancien + "\", pour devenir : \"" + nouvel + "\"."
                        + " L'article 3 (OBJET SOCIAL) des statuts est modifié en conséquence."
                : "L'assemblée générale décide d'adopter le nouvel objet social suivant : \""
                        + nouvel + "\"."
                        + " L'article 3 (OBJET SOCIAL) des statuts est modifié en conséquence.";
        return new ResolutionOutput(odj, texte);
    }

    private ResolutionOutput transfertSiege(Map<String, Object> d) {
        String nouvelleAdresse = requireString(d, "nouvelleAdresse");
        String nouvelleVille = requireString(d, "nouvelleVille");
        String dateEffet = requireString(d, "dateEffet");
        String ancienne = optString(d, "ancienneAdresse");
        String odj = "Transfert du siège social";
        StringBuilder sb = new StringBuilder("L'assemblée générale décide de transférer le siège social ");
        if (ancienne != null) sb.append("de \"").append(ancienne).append("\" ");
        sb.append("à l'adresse suivante : \"").append(nouvelleAdresse).append(", ")
          .append(nouvelleVille).append("\", avec effet au ").append(dateEffet).append(".")
          .append(" L'article 4 (SIÈGE SOCIAL) des statuts est modifié en conséquence.");
        return new ResolutionOutput(odj, sb.toString());
    }

    private ResolutionOutput prorogationDuree(Map<String, Object> d) {
        long annees = requireLong(d, "annees");
        String dateEffet = requireString(d, "dateEffet");
        String odj = "Prorogation de la durée de la société";
        String texte = "L'assemblée générale décide de proroger la durée de la société de "
                + annees + " (" + FrenchNumberToLetters.numberToLetters(annees) + ") année"
                + (annees > 1 ? "s" : "")
                + " supplémentaire" + (annees > 1 ? "s" : "")
                + ", avec effet au " + dateEffet + "."
                + " L'article 5 (DURÉE) des statuts est modifié en conséquence.";
        return new ResolutionOutput(odj, texte);
    }

    // ── Capital ──────────────────────────────────────────────────────────

    private ResolutionOutput augmentationCapital(Map<String, Object> d) {
        long nouveau = requireLong(d, "nouveauCapital");
        long nouvellesParts = requireLong(d, "nouvellesParts");
        Long ancien = optLong(d, "ancienCapital");
        String dateVersement = optString(d, "dateVersement");
        String odj = "Augmentation de capital social";
        StringBuilder sb = new StringBuilder("L'assemblée générale décide d'augmenter le capital social ");
        if (ancien != null) {
            long diff = nouveau - ancien;
            if (diff <= 0) {
                throw new IllegalArgumentException(
                        "AUGMENTATION_CAPITAL: nouveauCapital (" + nouveau
                                + ") doit être strictement supérieur à ancienCapital (" + ancien + ")");
            }
            sb.append("de ").append(formatMad(ancien)).append(" dirhams (")
              .append(FrenchNumberToLetters.madToLetters(ancien)).append(") à ")
              .append(formatMad(nouveau)).append(" dirhams (")
              .append(FrenchNumberToLetters.madToLetters(nouveau)).append("),")
              .append(" soit une augmentation de ").append(formatMad(diff)).append(" dirhams (")
              .append(FrenchNumberToLetters.madToLetters(diff)).append(")");
        } else {
            sb.append("pour le porter à ").append(formatMad(nouveau)).append(" dirhams (")
              .append(FrenchNumberToLetters.madToLetters(nouveau)).append(")");
        }
        sb.append(", par la création de ").append(nouvellesParts)
          .append(" nouvelle").append(nouvellesParts > 1 ? "s" : "")
          .append(" part").append(nouvellesParts > 1 ? "s" : "")
          .append(" sociale").append(nouvellesParts > 1 ? "s" : "")
          .append(" libérée").append(nouvellesParts > 1 ? "s" : "")
          .append(" en numéraire");
        if (dateVersement != null) {
            sb.append(" (versement des fonds le ").append(dateVersement).append(")");
        }
        sb.append(". Les articles 6 (CAPITAL SOCIAL) et 7 (PARTS SOCIALES)")
          .append(" des statuts sont modifiés en conséquence.");
        return new ResolutionOutput(odj, sb.toString());
    }

    private ResolutionOutput augmentationCapitalReserves(Map<String, Object> d) {
        Long montant = optLong(d, "montantIncorporation");
        Long ancien = optLong(d, "ancienCapital");
        String details = optString(d, "details");
        String odj = "Augmentation de capital par incorporation de réserves";
        StringBuilder sb = new StringBuilder(
                "L'assemblée générale décide d'augmenter le capital social par incorporation de réserves");
        if (montant != null) {
            sb.append(" pour un montant de ").append(formatMad(montant)).append(" dirhams (")
              .append(FrenchNumberToLetters.madToLetters(montant)).append(")");
            if (ancien != null) {
                long nouveau = ancien + montant;
                sb.append(", portant le capital de ").append(formatMad(ancien)).append(" à ")
                  .append(formatMad(nouveau)).append(" dirhams");
            }
        }
        if (details != null) sb.append(". Modalités : ").append(details);
        sb.append(". Les articles 6 (CAPITAL SOCIAL) et 7 (PARTS SOCIALES)")
          .append(" des statuts sont modifiés en conséquence.");
        return new ResolutionOutput(odj, sb.toString());
    }

    private ResolutionOutput reductionCapital(Map<String, Object> d) {
        long montant = requireLong(d, "montantReduction");
        String motif = requireString(d, "motif");
        Long ancien = optLong(d, "ancienCapital");
        String odj = "Réduction de capital social";
        StringBuilder sb = new StringBuilder("L'assemblée générale décide de réduire le capital social de ")
                .append(formatMad(montant)).append(" dirhams (")
                .append(FrenchNumberToLetters.madToLetters(montant)).append(")");
        if (ancien != null) {
            long apres = ancien - montant;
            sb.append(", le portant de ").append(formatMad(ancien)).append(" à ")
              .append(formatMad(apres)).append(" dirhams");
        }
        sb.append(". Motif : ").append(motif).append(".")
          .append(" Les articles 6 (CAPITAL SOCIAL) et 7 (PARTS SOCIALES)")
          .append(" des statuts sont modifiés en conséquence.");
        return new ResolutionOutput(odj, sb.toString());
    }

    private ResolutionOutput modifValeurNominale(Map<String, Object> d) {
        Long nouvelle = optLong(d, "nouvelleValeurNominale");
        String details = optString(d, "details");
        String odj = "Modification de la valeur nominale des parts sociales";
        StringBuilder sb = new StringBuilder(
                "L'assemblée générale décide de modifier la valeur nominale des parts sociales");
        if (nouvelle != null) {
            sb.append(" pour la porter à ").append(nouvelle).append(" dirham")
              .append(nouvelle > 1 ? "s" : "").append(" (")
              .append(FrenchNumberToLetters.madToLetters(nouvelle)).append(") par part");
        }
        if (details != null) sb.append(". Modalités : ").append(details);
        sb.append(". L'article 7 (PARTS SOCIALES) des statuts est modifié en conséquence.");
        return new ResolutionOutput(odj, sb.toString());
    }

    // ── Gérance ──────────────────────────────────────────────────────────

    private ResolutionOutput designationGerant(Map<String, Object> d) {
        String nom = requireString(d, "nom");
        String prenom = requireString(d, "prenom");
        String cin = requireString(d, "cin");
        String dateEffet = requireString(d, "dateEffet");
        String nationalite = requireString(d, "nationalite");
        String remuneration = optString(d, "remuneration");
        String odj = "Nomination d'un gérant";
        StringBuilder sb = new StringBuilder(
                "L'assemblée générale décide de nommer en qualité de gérant Monsieur/Madame ")
                .append(prenom).append(' ').append(nom)
                .append(", de nationalité ").append(nationalite)
                .append(", titulaire de la CIN n° ").append(cin)
                .append(", avec prise d'effet au ").append(dateEffet).append(".");
        if (remuneration != null) {
            sb.append(" Rémunération : ").append(remuneration).append(" MAD.");
        }
        sb.append(" Le nouveau gérant accepte expressément ses fonctions et déclare ne tomber")
          .append(" sous aucune incompatibilité légale ou réglementaire.")
          .append(" L'article 12 (GÉRANCE) des statuts est modifié en conséquence.");
        return new ResolutionOutput(odj, sb.toString());
    }

    private ResolutionOutput revocationGerant(Map<String, Object> d) {
        String identite = requireString(d, "identite");
        String dateEffet = requireString(d, "dateEffet");
        String motif = requireString(d, "motif");
        String odj = "Révocation d'un gérant";
        String texte = "L'assemblée générale décide de révoquer " + identite
                + " de ses fonctions de gérant, avec effet au " + dateEffet + "."
                + " Motif : " + motif + "."
                + " L'article 12 (GÉRANCE) des statuts est modifié en conséquence.";
        return new ResolutionOutput(odj, texte);
    }

    private ResolutionOutput modifNombreGerants(Map<String, Object> d) {
        String details = requireString(d, "details");
        String odj = "Modification du nombre / de la durée des fonctions des gérants";
        String texte = "L'assemblée générale décide de modifier les dispositions relatives au"
                + " nombre et à la durée des fonctions des gérants. Modalités : " + details + "."
                + " L'article 12 (GÉRANCE) des statuts est modifié en conséquence.";
        return new ResolutionOutput(odj, texte);
    }

    private ResolutionOutput modifPouvoirsGerant(Map<String, Object> d) {
        String details = requireString(d, "details");
        String odj = "Modification des pouvoirs ou de la rémunération du gérant";
        String texte = "L'assemblée générale décide de modifier les pouvoirs et/ou la rémunération"
                + " du gérant. Modalités : " + details + "."
                + " L'article 12 (GÉRANCE) des statuts est modifié en conséquence.";
        return new ResolutionOutput(odj, texte);
    }

    // ── Fonctionnement ───────────────────────────────────────────────────

    private ResolutionOutput clauseAgrement(Map<String, Object> d) {
        String details = requireString(d, "details");
        String odj = "Clause d'agrément";
        String texte = "L'assemblée générale décide d'adopter ou de modifier la clause d'agrément"
                + " des cessions de parts sociales. Stipulations : " + details + "."
                + " L'article 10 des statuts est modifié en conséquence.";
        return new ResolutionOutput(odj, texte);
    }

    private ResolutionOutput clausePreemption(Map<String, Object> d) {
        String details = requireString(d, "details");
        String odj = "Droit de préemption / clause d'inaliénabilité";
        String texte = "L'assemblée générale décide d'adopter ou de modifier le droit de préemption"
                + " et/ou la clause d'inaliénabilité des parts sociales. Stipulations : "
                + details + ". Les statuts sont modifiés en conséquence.";
        return new ResolutionOutput(odj, texte);
    }

    private ResolutionOutput modalitesDecisions(Map<String, Object> d) {
        String details = requireString(d, "details");
        String odj = "Modification des modalités de prise de décisions";
        String texte = "L'assemblée générale décide de modifier les modalités de prise de décisions"
                + " collectives. Stipulations : " + details + "."
                + " Les statuts sont modifiés en conséquence.";
        return new ResolutionOutput(odj, texte);
    }

    // ── Décisions structurantes ─────────────────────────────────────────

    private ResolutionOutput continuationPertes(Map<String, Object> d) {
        String details = optString(d, "details");
        String odj = "Continuation de l'activité malgré les pertes";
        StringBuilder sb = new StringBuilder(
                "L'assemblée générale, conformément à l'article 86 de la loi 5-96, décide la")
                .append(" continuation de l'activité sociale malgré les pertes constatées et le")
                .append(" maintien des capitaux propres en deçà du quart du capital social.");
        if (details != null) sb.append(" Précisions : ").append(details).append(".");
        return new ResolutionOutput(odj, sb.toString());
    }

    private ResolutionOutput creationSuccursale(Map<String, Object> d) {
        String details = requireString(d, "details");
        String odj = "Création / transfert / suppression de succursale";
        String texte = "L'assemblée générale décide la création, le transfert ou la suppression"
                + " d'une succursale. Modalités : " + details + "."
                + " L'article 17 des statuts est modifié en conséquence.";
        return new ResolutionOutput(odj, texte);
    }

    private ResolutionOutput pouvoirsFormalites(Map<String, Object> d) {
        String details = optString(d, "details");
        String odj = "Pouvoirs pour formalités";
        String mandataire = details != null
                ? details
                : "tout porteur d'un original, d'une copie ou d'un extrait des présentes";
        String texte = "L'assemblée générale donne tous pouvoirs à " + mandataire
                + " à l'effet d'accomplir toutes les formalités légales de publicité, de dépôt"
                + " et d'inscription au registre du commerce.";
        return new ResolutionOutput(odj, texte);
    }

    // ── Fallback "annexe à fournir" ──────────────────────────────────────

    private ResolutionOutput annexeAFournir(String typeId, Map<String, Object> d) {
        String details = optString(d, "details");
        String odj = humanLabel(typeId);
        StringBuilder sb = new StringBuilder("L'assemblée générale prend acte de l'opération « ")
                .append(odj).append(" ». Les éléments détaillés, justifications et clauses")
                .append(" applicables figurent en annexe à fournir (acte séparé, projet ou rapport")
                .append(" distinct)");
        if (details != null) sb.append(" — précisions saisies : ").append(details);
        sb.append(". Les statuts sont mis à jour en conséquence.");
        return new ResolutionOutput(odj, sb.toString());
    }

    private static String humanLabel(String typeId) {
        return switch (typeId) {
            case "AUGMENTATION_CAPITAL_NATURE" -> "Augmentation de capital par apport en nature";
            case "CESSION_PARTIELLE" -> "Cession partielle de parts sociales";
            case "CESSION_TOTALE" -> "Cession totale de parts sociales";
            case "TRANSMISSION_PARTS" -> "Transmission de parts par succession ou donation";
            case "NANTISSEMENT" -> "Nantissement de parts sociales";
            case "TRANSFORMATION" -> "Transformation de la forme juridique";
            case "FUSION_SCISSION" -> "Fusion / scission / apport partiel d'actif";
            case "DESIGNATION_CAC" -> "Désignation d'un commissaire aux comptes";
            case "PACTE_ASSOCIES" -> "Pacte d'associés";
            default -> typeId;
        };
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private static String requireString(Map<String, Object> d, String key) {
        Object v = d.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new IllegalArgumentException("Champ obligatoire manquant ou vide : '" + key + "'");
        }
        return v.toString();
    }

    private static String optString(Map<String, Object> d, String key) {
        Object v = d.get(key);
        if (v == null) return null;
        String s = v.toString();
        return s.isBlank() ? null : s;
    }

    private static long requireLong(Map<String, Object> d, String key) {
        Long v = optLong(d, key);
        if (v == null) {
            throw new IllegalArgumentException("Champ obligatoire numérique manquant : '" + key + "'");
        }
        return v;
    }

    private static Long optLong(Map<String, Object> d, String key) {
        Object v = d.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        String s = v.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return Long.parseLong(s.replace(" ", "").replace("_", ""));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Champ '" + key + "' doit être numérique (reçu : '" + v + "')", e);
        }
    }

    /** Format MAD façon française : {@code 100 000}. */
    private static String formatMad(long n) {
        String s = Long.toString(Math.abs(n));
        StringBuilder sb = new StringBuilder();
        int len = s.length();
        for (int i = 0; i < len; i++) {
            if (i > 0 && (len - i) % 3 == 0) sb.append(' ');
            sb.append(s.charAt(i));
        }
        return (n < 0 ? "-" : "") + sb.toString();
    }
}
