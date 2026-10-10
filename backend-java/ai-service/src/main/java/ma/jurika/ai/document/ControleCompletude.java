package ma.jurika.ai.document;

import java.util.List;

/**
 * Lot 5 (2026-09-07) — LE POINT DE CONTRÔLE « JEU DE VARIABLES COMPLET », RENDU
 * FIDÈLE À CE QU'IL ANNONCE.
 *
 * <p>Le guide exige, avant de quitter le statut « Génération des documents »,
 * qu'aucun champ des modèles ne reste non renseigné. Le contrôle existant
 * ({@code GuideTransitionChecks}, ticket-service) porte sur DIX colonnes du dossier
 * — jamais sur les variables réellement consommées par les modèles. C'est pourquoi
 * « né le  à , demeurant à  » est passé.
 *
 * <p>Ce contrôle-ci part du document RENDU. Les variables d'une branche
 * conditionnelle non retenue et celles d'une boucle vide ont disparu du document :
 * elles ne peuvent donc pas être signalées, ce qui règle mécaniquement la
 * distinction « légitimement vide » / « vide qui se voit », sans liste à tenir.
 *
 * <p>Reste à trancher entre les vides qui subsistent :
 * <ul>
 *   <li><b>une phrase</b> — la variable partage son paragraphe avec du texte de
 *       liaison ou une autre variable : le blanc se lit. Générer serait produire un
 *       acte fautif ; on refuse ;</li>
 *   <li><b>une case</b> — la variable est seule et termine sa ligne après un
 *       libellé (« Ville : »). Un imprimé administratif dont une case est vide reste
 *       recevable ; on laisse passer.</li>
 * </ul>
 *
 * @see MissingVariableMarker#applyDetailed
 */
public final class ControleCompletude {

    private ControleCompletude() {}

    /**
     * @return {@code null} si le document peut sortir ; sinon le message de refus,
     *         nommant chaque variable fautive ET la ligne où elle apparaît.
     */
    public static String motifDeRefus(DocxTemplateEngine.DocumentResult result) {
        List<GenerationRefuseeException.DonneeManquante> d = donneesManquantes(result, null);
        return d.isEmpty() ? null : message(d);
    }

    /**
     * Lot L3 (regle des variables) : les donnees INTERNES manquantes, nommees avec le
     * libelle du dictionnaire unique (ou le nom de la variable s'il n'en donne pas).
     */
    public static List<GenerationRefuseeException.DonneeManquante> donneesManquantes(
            DocxTemplateEngine.DocumentResult result, ma.jurika.ai.document.corpus.DictionnaireUnique dico) {
        if (result == null) return List.of();
        return result.manquantesBloquantes().stream()
                .map(m -> new GenerationRefuseeException.DonneeManquante(m.nom(), libelle(m.nom(), dico), m.endroit()))
                .toList();
    }

    /** Lot L3 : leve le refus structure si une donnee interne manque. */
    public static void verifier(String templateCode, DocxTemplateEngine.DocumentResult result,
                                ma.jurika.ai.document.corpus.DictionnaireUnique dico) {
        List<GenerationRefuseeException.DonneeManquante> d = donneesManquantes(result, dico);
        if (!d.isEmpty()) {
            throw new GenerationRefuseeException(templateCode, message(d), d);
        }
    }

    static String libelle(String variable, ma.jurika.ai.document.corpus.DictionnaireUnique dico) {
        String l = dico == null ? null : dico.libelle(variable);
        return l == null ? variable : l;
    }

    private static String message(List<GenerationRefuseeException.DonneeManquante> d) {
        StringBuilder sb = new StringBuilder("Génération refusée : des données manquent. ");
        for (GenerationRefuseeException.DonneeManquante m : d) {
            sb.append(m.libelle()).append(" ($").append(m.variable()).append(") — « ")
                    .append(m.endroit()).append(" » ; ");
        }
        sb.setLength(sb.length() - 3);
        sb.append('.');
        return sb.toString();
    }
}
