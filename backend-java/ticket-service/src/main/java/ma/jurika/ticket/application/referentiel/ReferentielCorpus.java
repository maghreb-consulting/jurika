package ma.jurika.ticket.application.referentiel;

import java.util.List;
import java.util.Map;

/**
 * Lot L4b (E2) : le referentiel des demarches tel qu'ai-service le lit dans le corpus
 * (miroir de {@code ReferentielDemarches} d'ai-service, champs JSON identiques).
 */
public record ReferentielCorpus(String version, List<Ligne> lignes, List<Renvoi> renvois,
                                List<Decision> decisions, Rapport rapport) {

    public record Ligne(String matrice, String famille, String forme, int ordre, String decision,
                        Condition sousCondition, String statut, String libelle, String organisme,
                        Condition condition, boolean obligatoire, Delai delai, String justificatif,
                        String modele, Integer numeroDecision, String aCompleter, String completePlateforme) {}

    public record Condition(String variable, String operateur, String valeur, String texte) {}

    public record Delai(String texte, Integer valeur, String unite, String depuisVariable,
                        String echeanceVariable) {}

    public record Renvoi(String matrice, int numero, String section, String decision, String code,
                         String familleRenvoi, String matriceRenvoi, Integer numeroRenvoi, String regle) {}

    public record Decision(String matrice, int numero, String section, String code, String libelle,
                           String typeDecision) {}

    public record Rapport(Map<String, Integer> lignesParOnglet, int total, List<String> conditionsPhrases,
                          List<String> delaisTextuels, List<String> erreurs) {}
}
