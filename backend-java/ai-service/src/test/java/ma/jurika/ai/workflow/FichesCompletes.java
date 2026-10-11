package ma.jurika.ai.workflow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lot L3 : une fiche de test complete. Depuis la regle des variables, une donnee interne
 * absente (civilite, adresse, naissance d'un associe ou d'un gerant) bloque la generation
 * au lieu de s'imprimer en blanc : les tests de rendu partent donc d'une fiche complete.
 */
public final class FichesCompletes {

    private FichesCompletes() {
    }

    /** Copie de la fiche, associes et gerants completes de leur etat civil et adresse. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> completer(Map<String, Object> fiche) {
        Map<String, Object> f = new LinkedHashMap<>(fiche);
        for (String cle : List.of("associes", "gerants", "dirigeants")) {
            Object v = f.get(cle);
            if (v instanceof List<?> l) {
                List<Map<String, Object>> out = new ArrayList<>();
                for (Object o : l) {
                    out.add(personne((Map<String, Object>) o));
                }
                f.put(cle, out);
            }
        }
        f.putIfAbsent("ville", "Rabat");
        // L3 (D6) : exercice social de la societe, tel que ses statuts le fixent.
        f.putIfAbsent("exerciceDebut", "1er janvier");
        f.putIfAbsent("exerciceFin", "31 d\u00e9cembre");
        return f;
    }

    public static Map<String, Object> personne(Map<String, Object> p) {
        Map<String, Object> r = new LinkedHashMap<>(p);
        r.putIfAbsent("civilite", "M");
        r.putIfAbsent("adresse", "10 rue des Tests, Rabat");
        r.putIfAbsent("dateNaissance", "1980-01-15");
        r.putIfAbsent("lieuNaissance", "Rabat");
        r.putIfAbsent("nationalite", "marocaine"); // L4a (E2) : saisie, plus supposee
        r.putIfAbsent("pieceType", "CIN"); // L3 (D6) : valeur proposee, confirmee a l'ecran
        return r;
    }
}
