package ma.jurika.ai.document.corpus;

import java.util.List;
import java.util.Map;

/**
 * Lot L4b (E1, RG-DEM-01) : le referentiel des demarches tel que le corpus du cabinet le
 * porte (onglets "Demarches" des matrices, onglets "Renvois", decisions de l'onglet
 * d'origine), analyse mais jamais complete : une cellule vide reste vide, une condition ou
 * un delai que l'analyse ne sait pas lire reste en texte et figure au rapport.
 */
public record ReferentielDemarches(String version, List<Ligne> lignes, List<Renvoi> renvois,
                                   List<Decision> decisions, Rapport rapport) {

    /**
     * Une demarche du corpus.
     *
     * @param matrice        nom du classeur sans extension (ex. MATRICE_COMPTES_RESULTAT_SARL)
     * @param famille        dossier de famille (ex. 03_COMPTES_RESULTAT_DISTRIBUTIONS)
     * @param forme          SARL, SARL_AU, ETRANGERE, ou null quand la matrice vaut pour toutes
     * @param ordre          rang de la ligne dans l'onglet (1 = premiere ligne de donnees)
     * @param decision       code de la decision ($RESOLUTION_TYPE), sans sa sous-condition
     * @param sousCondition  sous-condition portee par la colonne Decision, ou null
     * @param statut         code du statut du ticket (CREATION_TICKET, GENERATION_DOCUMENTS...)
     * @param numeroDecision "N de la decision" (onglet d'origine), ou null
     */
    public record Ligne(String matrice, String famille, String forme, int ordre, String decision,
                        Condition sousCondition, String statut, String libelle, String organisme,
                        Condition condition, boolean obligatoire, Delai delai, String justificatif,
                        String modele, Integer numeroDecision, String aCompleter,
                        String completePlateforme) {}

    /**
     * Condition d'application.
     *
     * @param operateur EGAL ({@code $VAR = << v >>}), EXISTE ({@code $VAR existe}) ou PHRASE
     *                  (texte a apprecier par l'employe, aucune variable)
     * @param texte     texte d'origine (entre parentheses dans la matrice), ou la phrase entiere
     */
    public record Condition(String variable, String operateur, String valeur, String texte) {}

    /**
     * Delai. {@code valeur} et {@code unite} (JOURS ou MOIS) quand la matrice les donne ;
     * {@code depuisVariable} quand elle nomme le point de depart ; {@code echeanceVariable}
     * quand elle nomme la date limite. {@code texte} est toujours conserve.
     */
    public record Delai(String texte, Integer valeur, String unite, String depuisVariable,
                        String echeanceVariable) {}

    /** Onglet "Renvois" : la matrice de la famille fait foi pour cette decision. */
    public record Renvoi(String matrice, int numero, String section, String decision, String code,
                         String familleRenvoi, String matriceRenvoi, Integer numeroRenvoi, String regle) {}

    /** Decision de l'onglet d'origine (une ligne par decision). */
    public record Decision(String matrice, int numero, String section, String code, String libelle,
                           String typeDecision) {}

    /**
     * Rapport d'analyse. {@code erreurs} non vide = import refuse (statut inconnu, onglet
     * sans en-tete...) ; le reste est informatif.
     */
    public record Rapport(Map<String, Integer> lignesParOnglet, int total, List<String> conditionsPhrases,
                          List<String> delaisTextuels, List<String> erreurs) {}
}
