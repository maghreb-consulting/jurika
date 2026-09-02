package ma.jurika.ai.workflow.mapper;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires {@link LiquidationMapper} (sans Spring) — Phase C.
 *
 * <p>Vérifie le workflow {@code LIQUIDATION} : le PV directeur unifié à l'étape « clôture »
 * (délégué au noyau partagé) et le rapport de liquidation (document distinct), avec les deux
 * issues du résultat de liquidation — <b>boni</b> et <b>mali</b>.
 */
class LiquidationMapperTest {

    private final LiquidationMapper mapper = new LiquidationMapper();

    private static Map<String, Object> baseSociete() {
        return Map.of(
                "denomination", "PARACOSME",
                "capitalChiffres", 100_000,
                "siegeSocial", "12 RUE DES FOULES, CASABLANCA",
                "nombreParts", 1_000,
                "rcNumero", "123456",
                "villeGreffe", "CASABLANCA",
                "formeJuridique", "SARL");
    }

    @Test
    void workflowCodeAndSupportedTemplates() {
        assertEquals("LIQUIDATION", mapper.workflowCode());
        assertTrue(mapper.supportedTemplates().contains("PV_DISSOLUTION_LIQUIDATION_SARL"));
        assertTrue(mapper.supportedTemplates().contains("PV_DISSOLUTION_LIQUIDATION_SARL_AU"));
        assertTrue(mapper.supportedTemplates().contains("RAPPORT_LIQUIDATION_DIRECTEUR"));
        // 2026-08-13 — l'annonce légale de clôture de liquidation (SARL / SARL AU) rejoint le mapper.
        assertTrue(mapper.supportedTemplates().contains("ANNONCE_LEGALE_LIQUIDATION_SARL"));
        assertTrue(mapper.supportedTemplates().contains("ANNONCE_LEGALE_LIQUIDATION_SARL_AU"));
        assertEquals(5, mapper.supportedTemplates().size());
    }

    @Test
    void unsupportedTemplateThrows() {
        assertThrows(IllegalArgumentException.class, () -> mapper.map("PV_AGE_SARL", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> mapper.map(null, Map.of()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void pvClotureEtapeCloture() {
        Map<String, Object> payload = Map.of(
                "societe", baseSociete(),
                "seance", Map.of("type", "extraordinaire", "date", "2026-06-20", "heure", "10H00",
                        "lieu", "SIEGE", "presidentNom", "M. Ahmed Alaoui", "heureCloture", "12H00"),
                "associes", List.of(
                        Map.of("civilite", "M.", "prenom", "Ahmed", "nom", "Alaoui",
                                "nombreParts", 600, "presence", "présent"),
                        Map.of("civilite", "Mme", "prenom", "Fatima", "nom", "Bennani",
                                "nombreParts", 400, "presence", "présent")),
                "resolutions", List.of(
                        Map.of("objet", "Approbation des comptes de clôture",
                                "type", "approbation_comptes_cloture", "resultat", "à l'unanimité"),
                        Map.of("objet", "Quitus au liquidateur", "type", "quitus_liquidateur"),
                        Map.of("objet", "Répartition du boni", "type", "repartition_boni")),
                "liquidateur", Map.of("civilite", "M.", "prenom", "Ahmed", "nom", "Alaoui"),
                "cloture", Map.of("resultatSens", "boni", "resultatMontant", 50_000,
                        "boniMontant", 50_000, "boniParPart", 50));

        Map<String, Object> vars = mapper.map("PV_DISSOLUTION_LIQUIDATION_SARL", payload);
        assertEquals("clôture", vars.get("PV_ETAPE"));
        assertEquals("boni", vars.get("LIQ_RESULTAT_SENS"));
        assertEquals("oui", vars.get("BONI_EXISTE"));
        assertNotNull(vars.get("LIQ_RESULTAT_MONTANT"));
        List<Map<String, Object>> resolutions = (List<Map<String, Object>>) vars.get("RESOLUTIONS");
        assertEquals(3, resolutions.size());
        assertEquals("repartition_boni", resolutions.get(2).get("RESOLUTION_TYPE"));
    }

    @Test
    void rapportBoni() {
        Map<String, Object> payload = Map.of(
                "societe", baseSociete(),
                "liquidateur", Map.of("civilite", "M.", "prenom", "Ahmed", "nom", "Alaoui",
                        "adresse", "5 RUE D'ALGER", "pieceType", "CIN", "pieceNumero", "BE123456",
                        "genre", "masculin"),
                "dissolution", Map.of("date", "2026-05-15"),
                "cloture", Map.of("dateClotureLiquidation", "2026-06-20", "actifRealise", 300_000,
                        "passifRegle", 150_000, "resultatType", "boni", "boniMontant", 50_000),
                "associes", List.of(
                        Map.of("civilite", "M.", "prenom", "Ahmed", "nom", "Alaoui",
                                "nombreParts", 600, "boniChiffres", 30_000),
                        Map.of("civilite", "Mme", "prenom", "Fatima", "nom", "Bennani",
                                "nombreParts", 400, "boniChiffres", 20_000)),
                "signature", Map.of("lieu", "CASABLANCA", "date", "2026-06-25", "nombreOriginaux", "quatre"));

        Map<String, Object> vars = mapper.map("RAPPORT_LIQUIDATION_DIRECTEUR", payload);
        assertEquals("boni", vars.get("RESULTAT_LIQUIDATION_TYPE"));
        assertNotNull(vars.get("ACTIF_REALISE_CHIFFRES"));
        assertNotNull(vars.get("ACTIF_REALISE_LETTRES"));
        assertFalse(((String) vars.get("BONI_LIQUIDATION_LETTRES")).isBlank());
        // Mali vide en cas de boni (branche non rendue, aucun marqueur résiduel).
        assertEquals("", vars.get("MALI_LIQUIDATION_CHIFFRES"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> associes = (List<Map<String, Object>>) vars.get("ASSOCIES");
        assertEquals(2, associes.size());
        assertNotNull(associes.get(0).get("ASSOCIE_BONI_CHIFFRES"));
    }

    @Test
    void rapportMali() {
        Map<String, Object> payload = Map.of(
                "societe", baseSociete(),
                "liquidateur", Map.of("civilite", "Mme", "prenom", "Fatima", "nom", "Bennani",
                        "genre", "féminin"),
                "cloture", Map.of("dateClotureLiquidation", "2026-06-20", "actifRealise", 80_000,
                        "passifRegle", 120_000, "resultatType", "mali", "maliMontant", 40_000));

        Map<String, Object> vars = mapper.map("RAPPORT_LIQUIDATION_DIRECTEUR", payload);
        assertEquals("mali", vars.get("RESULTAT_LIQUIDATION_TYPE"));
        assertEquals("féminin", vars.get("LIQUIDATEUR_GENRE"));
        assertFalse(((String) vars.get("MALI_LIQUIDATION_LETTRES")).isBlank());
        // Boni vide en cas de mali.
        assertEquals("", vars.get("BONI_LIQUIDATION_CHIFFRES"));
    }
}
