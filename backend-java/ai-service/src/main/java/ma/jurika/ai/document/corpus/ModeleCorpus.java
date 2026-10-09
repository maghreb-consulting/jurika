package ma.jurika.ai.document.corpus;

/**
 * Lot L2 : une ligne de l'onglet "Modeles" de {@code INDEX_CORPUS.xlsx}.
 *
 * @param code           nom du modele (ne change jamais, convention du corpus)
 * @param familleDossier dossier de la famille (ex. {@code 01_CREATION})
 * @param famille        libelle de la famille
 * @param forme          {@code SARL}, {@code SARL AU} ou sans variante de forme
 * @param modeleBase     modele de base commun aux deux formes
 * @param jumeau         modele jumeau de l'autre forme, ou un tiret
 * @param gabarit        chemin du gabarit Word, relatif a la racine du corpus
 */
public record ModeleCorpus(String code, String familleDossier, String famille, String forme,
                           String modeleBase, String jumeau, String gabarit) {
}
