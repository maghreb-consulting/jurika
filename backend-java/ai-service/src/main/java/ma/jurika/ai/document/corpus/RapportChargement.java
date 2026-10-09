package ma.jurika.ai.document.corpus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Lot L2 : rapport de chargement du corpus (demonstration A : "le rapport de
 * chargement du corpus, 222 modeles, controles au vert").
 *
 * @param version            nom du dossier date du corpus (ex. CORPUS_2026-10-03)
 * @param racine             racine du corpus
 * @param chargeLe           horodatage du chargement
 * @param modeles            nombre de modeles de l'index
 * @param variables          nombre de variables du dictionnaire
 * @param alias              nombre d'alias
 * @param nonRendables       code -> erreurs de structure (gabarit refuse au rendu)
 * @param avertissements     code -> avertissements (a rapporter au cabinet)
 * @param empreintes         code -> SHA-256 du gabarit
 * @param horsCorpusClasspath codes servis depuis le classpath faute d'equivalent dans le
 *                           corpus (A_DECIDER : sort des gabarits hors corpus)
 */
public record RapportChargement(String version, String racine, Instant chargeLe, int modeles,
                                int variables, int alias, Map<String, List<String>> nonRendables,
                                Map<String, List<String>> avertissements, Map<String, String> empreintes,
                                List<String> horsCorpusClasspath) {
}
