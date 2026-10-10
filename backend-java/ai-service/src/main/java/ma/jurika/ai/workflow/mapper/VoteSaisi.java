package ma.jurika.ai.workflow.mapper;

import java.util.Map;

/**
 * Lot L3 (D6, 2026-10-10) : le vote des resolutions est SAISI, jamais suppose.
 *
 * <p>Avant : "adoptee", "a l'unanimite" et "voix pour = parts presentes" etaient
 * imprimes par defaut. Le vote vient desormais de la charge utile ({@code vote} :
 * resultat, majorite, voix pour, contre, abstentions) ; absent, la variable reste vide et
 * la generation la nomme (donnee interne manquante).
 *
 * <p>Deux redactions coexistent dans les modeles : "est adoptee $RESOLUTION_RESULTAT"
 * (modification, dissolution et liquidation : la MAJORITE) et "est $RESOLUTION_RESULTAT
 * par ... voix pour" (approbation, succursale, incidents : le RESULTAT et le decompte).
 */
final class VoteSaisi {

    final String resultat;
    final String majorite;
    final String voixPour;
    final String voixContre;
    final String abstentions;

    private VoteSaisi(String resultat, String majorite, String voixPour, String voixContre, String abstentions) {
        this.resultat = resultat;
        this.majorite = majorite;
        this.voixPour = voixPour;
        this.voixContre = voixContre;
        this.abstentions = abstentions;
    }

    @SuppressWarnings("unchecked")
    static VoteSaisi de(Map<String, Object> payload) {
        Object o = payload == null ? null : payload.get("vote");
        Map<String, Object> v = o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
        return new VoteSaisi(s(v.get("resultat")), s(v.get("majorite")), s(v.get("voixPour")),
                s(v.get("voixContre")), s(v.get("abstentions")));
    }

    /** Meme vote, voix "pour" donnees par l'organe qui decide (succursale d'une societe etrangere). */
    VoteSaisi avecVoixPour(String voix) {
        return voix == null || voix.isBlank() ? this : new VoteSaisi(resultat, majorite, voix.trim(), voixContre, abstentions);
    }

    /** Ce que $RESOLUTION_RESULTAT imprime pour ce modele (majorite ou resultat). */
    String resultatPour(String templateCode) {
        String code = templateCode == null ? "" : templateCode;
        return code.startsWith("PV_MODIFICATION") || code.startsWith("PV_DISSOLUTION_LIQUIDATION")
                ? majorite : resultat;
    }

    private static String s(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}
