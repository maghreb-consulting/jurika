package ma.jurika.ai.workflow.identity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lot L3 (RG-VAR-03, RG-FIC-03) : reprise automatique des donnees de la societe dans les
 * actes. Les modeles du corpus nomment les donnees de la fiche par leur nom du
 * dictionnaire unique ($SIEGE_VILLE, $ICE, $TRIBUNAL_VILLE...) ; les mappers ne les
 * fournissent pas toutes, alors que la societe les connait. Chaque variable canonique
 * VIDE ou absente est remplie depuis l'objet {@code societe} de la charge utile (enrichi
 * depuis la base par {@link SocieteIdentityEnricher}) ; une valeur du mapper n'est jamais
 * ecrasee, et rien n'est invente : sans donnee, la variable reste manquante (nommee ou
 * reclamee selon la regle des variables).
 */
public final class RepriseFicheSociete {

    /** Variable canonique (dictionnaire unique) -> cle de l'objet {@code societe}. */
    static final Map<String, String> CANONIQUES = Map.of(
            "DENOMINATION", "denomination",
            "SIEGE_SOCIAL", "siegeSocial",
            "SIEGE_VILLE", "ville",
            "ICE", "ice",
            "IDENTIFIANT_FISCAL", "ifNumero",
            "IDENTIFIANT_TP", "identifiantTp",
            "CNSS_NUMERO", "cnssNumero",
            "RC_NUMERO", "rcNumero",
            "RC_VILLE", "rcVille",
            // rc_tribunal : ville du tribunal de commerce ou la societe est immatriculee.
            "TRIBUNAL_VILLE", "villeGreffe");

    private RepriseFicheSociete() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> completer(Map<String, Object> variables, Map<String, Object> payload) {
        Map<String, Object> out = new LinkedHashMap<>(variables == null ? Map.of() : variables);
        Object s = payload == null ? null : payload.get("societe");
        if (!(s instanceof Map<?, ?> societe)) {
            return out;
        }
        CANONIQUES.forEach((variable, cle) -> {
            Object fiche = ((Map<String, Object>) societe).get(cle);
            if (vide(out.get(variable)) && !vide(fiche)) {
                out.put(variable, String.valueOf(fiche));
            }
        });
        return out;
    }

    private static boolean vide(Object v) {
        return v == null || String.valueOf(v).isBlank();
    }
}
