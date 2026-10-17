package ma.jurika.ai.workflow.mapper;

import ma.jurika.ai.document.GenerationRefuseeException;
import ma.jurika.ai.document.GenerationRefuseeException.DonneeManquante;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static ma.jurika.ai.workflow.mapper.LiquidationCorpusCommun.*;

/**
 * Lot L6b (E19, decision du 2026-10-17) : resultat de la liquidation, regle UNIQUE.
 *
 * <p>Seule source : le rapport final du liquidateur (bloc {@code rapportLiquidateur}) :
 * actif realise = somme des prix des realisations ; passif regle = somme des montants regles ;
 * frais = somme du tableau des frais (remuneration du liquidateur comprise) ; impot de la
 * liquidation ({@code cloture.isMontant}, facultatif). Tresorerie disponible = actif - passif -
 * frais - impot ; le capital est rembourse a concurrence du disponible ; le solde est un boni,
 * un mali ou nul. Le PV de cloture et le rapport final l'appliquent tous deux par
 * {@link #depuisRapport} ; les totaux de l'etape 1 et les frais du PV ne sont que controles
 * (refus nomme s'ils different). Aucun sens ni montant transmis par l'ecran n'est lu.
 */
final class ResultatLiquidation {

    static final String BONI = "boni";
    static final String MALI = "mali";
    static final String NUL = "nul";

    private ResultatLiquidation() {
    }

    /** Compte de la liquidation et son resultat. */
    record Compte(BigDecimal actif, BigDecimal passif, BigDecimal frais, BigDecimal impot, BigDecimal capital,
                  BigDecimal disponible) {

        /** Capital rembourse : a concurrence du disponible. */
        BigDecimal rembourse() {
            return disponible.min(capital);
        }

        /** "boni", "mali" ou "nul". */
        String sens() {
            int s = disponible.compareTo(capital);
            return s > 0 ? BONI : s < 0 ? MALI : NUL;
        }

        /** Montant du boni ou du mali (0 si nul). */
        BigDecimal montant() {
            return disponible.subtract(capital).abs();
        }
    }

    /** La regle : disponible = actif - passif - frais - impot, compare au capital. */
    static Compte calculer(BigDecimal actif, BigDecimal passif, BigDecimal frais, BigDecimal impot,
                           BigDecimal capital) {
        BigDecimal i = impot == null ? BigDecimal.ZERO : impot;
        return new Compte(actif, passif, frais, i, capital, actif.subtract(passif).subtract(frais).subtract(i));
    }

    /**
     * Compte tire du rapport final, controle contre les totaux de l'etape 1 et les frais du PV.
     *
     * @param remuneration remuneration du liquidateur (0 s'il n'est pas remunere), comprise
     *                     dans le tableau des frais du rapport
     */
    static Compte depuisRapport(String templateCode, Map<String, Object> payload, BigDecimal remuneration) {
        Map<String, Object> safe = payload == null ? Map.of() : payload;
        Map<String, Object> r = asMap(safe.get("rapportLiquidateur"));
        Map<String, Object> c = asMap(safe.get("cloture"));
        Map<String, Object> societe = asMap(safe.get("societe"));
        // Aucun tableau du rapport saisi (PV demande avant le rapport, ou rapport absent) : le
        // resultat ne se calcule pas, la donnee est nommee.
        if (RapportLiquidateurCorpusVarsBuilder.lignesSaisies(r.get("realisations")).isEmpty()
                && RapportLiquidateurCorpusVarsBuilder.lignesSaisies(r.get("apurements")).isEmpty()
                && RapportLiquidateurCorpusVarsBuilder.lignesSaisies(r.get("frais")).isEmpty()) {
            throw new GenerationRefuseeException(templateCode,
                    "R\u00e9sultat de la liquidation : il se calcule au rapport final du liquidateur, non renseign\u00e9",
                    List.of(new DonneeManquante("RAPPORT_LIQUIDATEUR",
                            "Rapport final du liquidateur (r\u00e9alisation de l'actif, apurement du passif, frais)", null)));
        }
        List<DonneeManquante> manquantes = new ArrayList<>();
        BigDecimal actif = total(r.get("realisations"), "prix");
        BigDecimal passif = total(r.get("apurements"), "montantRegle");
        BigDecimal frais = total(r.get("frais"), "montant");
        if (actif == null) manque(manquantes, "LIQUIDATION_ACTIF_REALISE_TOTAL_CHIFFRES",
                "Total des r\u00e9alisations de l'actif (un prix est illisible)");
        if (passif == null) manque(manquantes, "LIQUIDATION_PASSIF_REGLE_TOTAL_CHIFFRES",
                "Total du passif r\u00e9gl\u00e9 (un montant r\u00e9gl\u00e9 est illisible)");
        if (frais == null) manque(manquantes, "LIQUIDATION_FRAIS_TOTAL_CHIFFRES",
                "Total des frais et honoraires de liquidation (un montant est illisible)");
        Object impotSaisi = c.get("isMontant");
        BigDecimal impot = null;
        if (impotSaisi != null && !String.valueOf(impotSaisi).isBlank()) {
            impot = RapportLiquidateurCorpusVarsBuilder.montantOuNull(impotSaisi);
            if (impot == null) manque(manquantes, "LIQUIDATION_IS_MONTANT_CHIFFRES",
                    "Imp\u00f4t sur les soci\u00e9t\u00e9s de la liquidation (montant illisible : dirhams, au plus deux "
                            + "d\u00e9cimales, positif)");
        }
        Long capital = toLong(first0(societe.get("capitalChiffres"), societe.get("capitalSocial")));
        if (capital == null) manque(manquantes, "CAPITAL_CHIFFRES", "Capital social");
        refuser(templateCode, "Compte de la liquidation incomplet", manquantes);

        BigDecimal rem = remuneration == null ? BigDecimal.ZERO : remuneration;
        concorde(manquantes, c.get("actifRealise"), actif, "LIQUIDATION_ACTIF_REALISE_TOTAL_CHIFFRES",
                "Total des r\u00e9alisations de l'actif", "total de l'actif des comptes finaux (\u00e9tape 1)");
        concorde(manquantes, c.get("passifRegle"), passif, "LIQUIDATION_PASSIF_REGLE_TOTAL_CHIFFRES",
                "Total du passif r\u00e9gl\u00e9", "total du passif des comptes finaux (\u00e9tape 1)");
        BigDecimal fraisPv = RapportLiquidateurCorpusVarsBuilder.montantOuNull(c.get("frais"));
        if (fraisPv != null) {
            concorde(manquantes, fraisPv.add(rem), frais, "LIQUIDATION_FRAIS_TOTAL_CHIFFRES",
                    "Total des frais et honoraires de liquidation",
                    "frais du proc\u00e8s-verbal de cl\u00f4ture augment\u00e9s de la r\u00e9mun\u00e9ration du liquidateur");
        }
        if (frais.compareTo(rem) < 0) {
            manque(manquantes, "LIQUIDATION_FRAIS_TOTAL_CHIFFRES", "Frais et honoraires de liquidation : la "
                    + "r\u00e9mun\u00e9ration du liquidateur (" + RapportLiquidateurCorpusVarsBuilder.decimal(rem)
                    + " dirhams) doit figurer au tableau des frais, dont le total la comprend");
        }
        refuser(templateCode, "Le rapport ne concorde pas avec le proc\u00e8s-verbal de cl\u00f4ture", manquantes);
        return calculer(actif, passif, frais, impot, BigDecimal.valueOf(capital));
    }

    /** Somme d'une colonne des lignes saisies ; null si un montant est illisible. */
    private static BigDecimal total(Object lignes, String cle) {
        BigDecimal t = BigDecimal.ZERO;
        for (Map<String, Object> l : RapportLiquidateurCorpusVarsBuilder.lignesSaisies(lignes)) {
            BigDecimal m = RapportLiquidateurCorpusVarsBuilder.montantOuNull(l.get(cle));
            if (m == null) return null;
            t = t.add(m);
        }
        return t;
    }

    /** Ecart entre un total saisi ailleurs et le total du rapport : donnee nommee. */
    private static void concorde(List<DonneeManquante> manquantes, Object attendu, BigDecimal calcule,
                                 String variable, String libelle, String source) {
        BigDecimal a = attendu instanceof BigDecimal b ? b : RapportLiquidateurCorpusVarsBuilder.montantOuNull(attendu);
        if (a == null || a.compareTo(calcule) == 0) return;
        manque(manquantes, variable, libelle + " (" + RapportLiquidateurCorpusVarsBuilder.decimal(calcule)
                + " dirhams) diff\u00e9rent du " + source + " (" + RapportLiquidateurCorpusVarsBuilder.decimal(a) + " dirhams)");
    }

    private static void manque(List<DonneeManquante> manquantes, String variable, String libelle) {
        manquantes.add(new DonneeManquante(variable, libelle, null));
    }

    private static void refuser(String templateCode, String titre, List<DonneeManquante> manquantes) {
        if (manquantes.isEmpty()) return;
        List<DonneeManquante> d = List.copyOf(manquantes);
        manquantes.clear();
        throw new GenerationRefuseeException(templateCode, titre + " : "
                + String.join(" ; ", d.stream().map(DonneeManquante::libelle).toList()), d);
    }
}
