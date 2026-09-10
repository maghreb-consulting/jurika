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
        if (result == null) return null;
        List<MissingVariableMarker.Manquante> bloquantes = result.manquantesBloquantes();
        if (bloquantes.isEmpty()) return null;

        StringBuilder sb = new StringBuilder(
                "Génération refusée : le document sortirait avec un blanc au milieu d'une phrase. ");
        for (MissingVariableMarker.Manquante m : bloquantes) {
            sb.append('$').append(m.nom()).append(" — « ").append(m.endroit()).append(" » ; ");
        }
        sb.setLength(sb.length() - 2);
        return sb.toString();
    }
}
