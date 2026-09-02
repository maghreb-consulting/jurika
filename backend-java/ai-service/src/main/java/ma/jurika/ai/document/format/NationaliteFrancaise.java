package ma.jurika.ai.document.format;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

/**
 * Correspondance <b>nom de pays → adjectif de nationalité</b> (lot « grammaire
 * d'assemblage », 2026-08-17).
 *
 * <h2>Le problème</h2>
 * Les modèles de succursale étrangère écrivent {@code « $SOCIETE_MERE_FORME de droit
 * $SOCIETE_MERE_PAYS »}. La saisie porte un <b>nom de pays</b> ({@code Allemagne}),
 * alors que la locution « de droit … » appelle un <b>adjectif</b>. D'où le rendu
 * {@code « GmbH DE DROIT Allemagne »} au lieu de {@code « GmbH de droit allemand »}.
 *
 * <p>Le nom du pays reste juste ailleurs — l'annonce légale publie
 * {@code « Siège social : …, $SOCIETE_MERE_PAYS »}. La même donnée occupe donc deux
 * rôles grammaticaux ; c'est le <b>modèle appelant</b> qui décide de la forme, pas la
 * saisie (cf. {@code SuccursaleVarsBuilder.putSocieteMere}).
 *
 * <h2>Le repli</h2>
 * Un pays inconnu ne doit JAMAIS produire « de droit &lt;Pays&gt; » : la faute
 * réapparaîtrait en silence dès qu'un pays sort de la liste. On rend alors
 * {@code « étranger (Pays) »} → « société de droit <b>étranger (Norvège)</b> », qui est
 * grammatical, juridiquement exact et signale visiblement le pays.
 *
 * <p>La locution « de droit » étant toujours masculine singulière, seule cette forme est
 * nécessaire ici.
 */
public final class NationaliteFrancaise {

    private NationaliteFrancaise() {}

    /** Adjectif masculin singulier, indexé par nom de pays normalisé (sans accent, minuscule). */
    private static final Map<String, String> ADJECTIFS = Map.ofEntries(
            Map.entry("allemagne", "allemand"),
            Map.entry("espagne", "espagnol"),
            Map.entry("france", "français"),
            Map.entry("royaume-uni", "britannique"),
            Map.entry("royaume uni", "britannique"),
            Map.entry("grande-bretagne", "britannique"),
            Map.entry("angleterre", "anglais"),
            Map.entry("italie", "italien"),
            Map.entry("portugal", "portugais"),
            Map.entry("belgique", "belge"),
            Map.entry("pays-bas", "néerlandais"),
            Map.entry("pays bas", "néerlandais"),
            Map.entry("suisse", "suisse"),
            Map.entry("luxembourg", "luxembourgeois"),
            Map.entry("etats-unis", "américain"),
            Map.entry("etats unis", "américain"),
            Map.entry("etats-unis d'amerique", "américain"),
            Map.entry("usa", "américain"),
            Map.entry("canada", "canadien"),
            Map.entry("turquie", "turc"),
            Map.entry("chine", "chinois"),
            Map.entry("japon", "japonais"),
            Map.entry("emirats arabes unis", "émirati"),
            Map.entry("emirats-arabes-unis", "émirati"),
            Map.entry("arabie saoudite", "saoudien"),
            Map.entry("qatar", "qatari"),
            Map.entry("koweit", "koweïtien"),
            Map.entry("bahrein", "bahreïni"),
            Map.entry("oman", "omanais"),
            Map.entry("egypte", "égyptien"),
            Map.entry("tunisie", "tunisien"),
            Map.entry("algerie", "algérien"),
            Map.entry("mauritanie", "mauritanien"),
            Map.entry("senegal", "sénégalais"),
            Map.entry("cote d'ivoire", "ivoirien"),
            Map.entry("maroc", "marocain"),
            Map.entry("suede", "suédois"),
            Map.entry("norvege", "norvégien"),
            Map.entry("danemark", "danois"),
            Map.entry("finlande", "finlandais"),
            Map.entry("irlande", "irlandais"),
            Map.entry("autriche", "autrichien"),
            Map.entry("pologne", "polonais"),
            Map.entry("grece", "grec"),
            Map.entry("russie", "russe"),
            Map.entry("inde", "indien"),
            Map.entry("bresil", "brésilien"),
            Map.entry("singapour", "singapourien"));

    /**
     * Forme à injecter dans la locution « de droit … ».
     *
     * @param pays nom du pays tel que saisi ({@code null} / vide toléré)
     * @return l'adjectif si le pays est connu, sinon {@code « étranger (Pays) »} ;
     *         {@code « étranger »} si aucun pays n'est renseigné
     */
    public static String adjectifDeDroit(String pays) {
        String brut = pays == null ? "" : pays.trim();
        if (brut.isEmpty()) return "étranger";
        String adjectif = ADJECTIFS.get(normaliser(brut));
        // Repli explicite : jamais « de droit Allemagne ». Le pays reste visible.
        return adjectif != null ? adjectif : "étranger (" + brut + ")";
    }

    /** {@code true} si le pays est couvert par la table (utile aux tests / diagnostics). */
    public static boolean estConnu(String pays) {
        return pays != null && ADJECTIFS.containsKey(normaliser(pays.trim()));
    }

    private static String normaliser(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return n.toLowerCase(Locale.FRANCE).replaceAll("\\s+", " ").trim();
    }
}
