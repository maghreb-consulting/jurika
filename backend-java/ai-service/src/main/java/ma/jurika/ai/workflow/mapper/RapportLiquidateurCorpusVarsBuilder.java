package ma.jurika.ai.workflow.mapper;

import java.util.LinkedHashMap;
import java.util.Map;

import static ma.jurika.ai.workflow.mapper.LiquidationCorpusCommun.*;

/**
 * Lot L4d (E11d) : variables du rapport du liquidateur du corpus
 * ({@code RAPPORT_LIQUIDATEUR_SARL} / {@code _SARL_AU}), qui remplace le rapport du classpath
 * ({@code RAPPORT_LIQUIDATION_DIRECTEUR}).
 *
 * <p>NON RESOLU au lot L4d : le modele du corpus decrit les operations de liquidation par
 * onze tableaux (realisations d'actif, apurement du passif, frais, diligences fiscales et
 * sociales, contrats en cours, creances irrecouvrables, passifs contestes, personnel,
 * cessions restreintes, annexes) que le parcours ne saisit pas encore. Seules les donnees
 * connues sont fournies ici ; les autres restent absentes et le controle de completude
 * refuse la generation en les nommant. Aucune section n'est supposee vide.
 */
final class RapportLiquidateurCorpusVarsBuilder {

    static final String CORPUS_SARL = "RAPPORT_LIQUIDATEUR_SARL";
    static final String CORPUS_SARL_AU = "RAPPORT_LIQUIDATEUR_SARL_AU";

    private RapportLiquidateurCorpusVarsBuilder() {
    }

    static Map<String, Object> build(String gabarit, Map<String, Object> payload) {
        Map<String, Object> safe = payload == null ? Map.of() : payload;
        Map<String, Object> v = new LinkedHashMap<>(SeancePvVarsBuilder.build(gabarit, safe));
        // Le rapport n'est pas un proces-verbal de seance : ni presence ni resolutions.
        v.remove("ASSOCIES");
        v.remove("RESOLUTIONS");
        Map<String, Object> c = asMap(safe.get("cloture"));
        Map<String, Object> liquidateur = asMap(safe.get("liquidateur"));
        Map<String, Object> mentions = asMap(safe.get("mentionsPv"));
        Map<String, Object> dissolution = asMap(safe.get("dissolution"));

        // Le parcours de liquidation produit le rapport FINAL, joint a l'assemblee de cloture.
        v.put("RAPPORT_LIQUIDATEUR_NATURE", "final");
        putSi(v, "VALEUR_NOMINALE_PART", valeurNominale(asMap(safe.get("societe"))));
        mentionsActe(v, safe, mentions);
        putSi(v, "DATE_SIGNATURE", dateOuNull(asMap(safe.get("signature")).get("date")));

        DissolutionAnnonceVarsBuilder.liquidateurCorpus(v, liquidateur);
        DissolutionPvCorpusVarsBuilder.liquidateurPhysique(v, liquidateur);
        putSi(v, "LIQUIDATEUR_MANDAT_DATE_EFFET", dateOuNull(liquidateur.get("mandatDateEffet")));
        putSi(v, "LIQUIDATEUR_DUREE_MANDAT", str(liquidateur.get("dureeMandat")));
        DissolutionAnnonceVarsBuilder.tribunal(v, safe);

        putSi(v, "DISSOLUTION_DATE_DECISION", dateOuNull(first0(c.get("dissolutionDateDecision"),
                dissolution.get("date"), dissolution.get("dateDissolution"))));
        putSi(v, "DISSOLUTION_DATE_EFFET", dateOuNull(c.get("dissolutionDateEffet")));
        putSi(v, "DISSOLUTION_RC_INSCRIPTION_DATE", dateOuNull(c.get("dissolutionRcInscriptionDate")));
        putSi(v, "ANNONCE_LEGALE_JOURNAL", str(c.get("annonceJournal")));
        putSi(v, "ANNONCE_LEGALE_DATE_PUBLICATION", dateOuNull(c.get("annoncePublicationDate")));
        putSi(v, "LIQUIDATION_CLOTURE_DATE", dateOuNull(first0(c.get("dateClotureLiquidation"), c.get("dateCloture"))));

        Long actif = toLong(first0(c.get("actifRealise"), c.get("totalActif")));
        Long passif = toLong(first0(c.get("passifRegle"), c.get("totalPassif")));
        if (actif != null) {
            v.put("LIQUIDATION_ACTIF_REALISE_TOTAL_CHIFFRES", formatAmount(actif));
            v.put("LIQUIDATION_ACTIF_REALISE_TOTAL_LETTRES", lettres(actif));
        }
        if (passif != null) {
            v.put("LIQUIDATION_PASSIF_REGLE_TOTAL_CHIFFRES", formatAmount(passif));
            v.put("LIQUIDATION_PASSIF_REGLE_TOTAL_LETTRES", lettres(passif));
        }
        return v;
    }
}
