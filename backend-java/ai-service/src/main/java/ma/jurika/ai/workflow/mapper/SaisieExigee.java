package ma.jurika.ai.workflow.mapper;

import ma.jurika.ai.document.GenerationRefuseeException;

import java.util.List;
import java.util.Locale;

/**
 * Lot L4a (E2) : une donnee qui PILOTE une branche du modele (oui / non, categorie) ne
 * prend jamais de valeur par defaut. Vide, la condition du modele prendrait sa branche
 * "SINON" en silence ; la generation est refusee et la donnee nommee.
 */
final class SaisieExigee {

    private SaisieExigee() {
    }

    /** "oui" ou "non" ; absent ou illisible : refus nomme. */
    static String ouiNon(String templateCode, Object raw, String variable, String libelle) {
        String s = raw == null ? "" : String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        if (raw instanceof Boolean b) return b ? "oui" : "non";
        if (s.equals("oui") || s.equals("true") || s.equals("o") || s.equals("yes")) return "oui";
        if (s.equals("non") || s.equals("false") || s.equals("n") || s.equals("no")) return "non";
        throw refus(templateCode, variable, libelle);
    }

    static GenerationRefuseeException refus(String templateCode, String variable, String libelle) {
        return new GenerationRefuseeException(templateCode, libelle + " : non indiqué(e)",
                List.of(new GenerationRefuseeException.DonneeManquante(variable, libelle, null)));
    }

    /**
     * Donnees inserees par le mapper dans un texte qu'il compose lui-meme (resolution) :
     * le moteur ne les voit pas comme variables, un blanc y passerait sans marqueur. Toutes
     * sont exigees ; les manquantes sont nommees ensemble. {@code paires} : variable, libelle...
     */
    static void toutes(String templateCode, java.util.Map<String, Object> v, String... paires) {
        List<GenerationRefuseeException.DonneeManquante> manquantes = new java.util.ArrayList<>();
        for (int i = 0; i + 1 < paires.length; i += 2) {
            Object val = v.get(paires[i]);
            if (val == null || String.valueOf(val).isBlank()) {
                manquantes.add(new GenerationRefuseeException.DonneeManquante(paires[i], paires[i + 1], null));
            }
        }
        if (!manquantes.isEmpty()) {
            throw new GenerationRefuseeException(templateCode, "Donn\u00e9es manquantes : "
                    + manquantes.stream().map(GenerationRefuseeException.DonneeManquante::libelle)
                    .collect(java.util.stream.Collectors.joining(", ")), manquantes);
        }
    }
}
