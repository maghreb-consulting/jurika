package ma.jurika.ai.workflow.mapper;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires {@link DissolutionMapper} (sans Spring) — Phase C.
 *
 * <p>Vérifie le contrat du bean mince (workflow {@code DISSOLUTION}, PV directeur unifié à
 * l'étape « dissolution ») et la délégation au noyau partagé {@link DissolutionLiquidationMapper}
 * qui réutilise la séance : noyau séance (identité société, quorum, résolutions) + variables
 * propres à la dissolution / nomination du liquidateur.
 */
class DissolutionMapperTest {

    private final DissolutionMapper mapper = new DissolutionMapper();

    private static Map<String, Object> payload() {
        return Map.of(
                "societe", Map.of(
                        "denomination", "PARACOSME",
                        "capitalChiffres", 100_000,
                        "siegeSocial", "12 RUE DES FOULES, CASABLANCA",
                        "nombreParts", 1_000,
                        "rcNumero", "123456",
                        "villeGreffe", "CASABLANCA",
                        "formeJuridique", "SARL"),
                "seance", Map.of(
                        "type", "extraordinaire",
                        "date", "2026-05-15",
                        "heure", "10H00",
                        "lieu", "SIEGE SOCIAL",
                        "presidentNom", "M. Ahmed Alaoui",
                        "presidentQualite", "gérant",
                        "heureCloture", "12H00"),
                "convocation", Map.of("mode", "lettre recommandée", "date", "2026-04-30"),
                "associes", List.of(
                        Map.of("civilite", "M.", "prenom", "Ahmed", "nom", "Alaoui",
                                "nombreParts", 600, "presence", "présent"),
                        Map.of("civilite", "Mme", "prenom", "Fatima", "nom", "Bennani",
                                "nombreParts", 400, "presence", "présent")),
                "ordreDuJour", List.of("Dissolution anticipée", "Nomination du liquidateur"),
                "resolutions", List.of(
                        Map.of("objet", "Dissolution anticipée", "type", "dissolution_anticipee",
                                "resultat", "à l'unanimité"),
                        Map.of("objet", "Nomination du liquidateur", "type", "nomination_liquidateur")),
                "dissolution", Map.of("motif", "volontaire", "dateEffet", "2026-05-15",
                        "exerciceClosDate", "2025-12-31"),
                "liquidateur", Map.of("civilite", "M.", "prenom", "Ahmed", "nom", "Alaoui",
                        "adresse", "5 RUE D'ALGER, CASABLANCA", "remuneration", "exercées à titre gratuit",
                        "siege", "12 RUE DES FOULES, CASABLANCA"));
    }

    @Test
    void workflowCodeAndSupportedTemplates() {
        assertEquals("DISSOLUTION", mapper.workflowCode());
        assertTrue(mapper.supportedTemplates().contains("PV_DISSOLUTION_LIQUIDATION_SARL"));
        assertTrue(mapper.supportedTemplates().contains("PV_DISSOLUTION_LIQUIDATION_SARL_AU"));
        // 2026-08-12 — l'annonce légale de dissolution (SARL / SARL AU) rejoint le mapper.
        assertTrue(mapper.supportedTemplates().contains("ANNONCE_LEGALE_DISSOLUTION_SARL"));
        assertTrue(mapper.supportedTemplates().contains("ANNONCE_LEGALE_DISSOLUTION_SARL_AU"));
        assertEquals(4, mapper.supportedTemplates().size());
    }

    @Test
    void unsupportedTemplateThrows() {
        assertThrows(IllegalArgumentException.class, () -> mapper.map("RAPPORT_LIQUIDATION_DIRECTEUR", payload()));
        assertThrows(IllegalArgumentException.class, () -> mapper.map(null, payload()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void mapSarlPluri() {
        Map<String, Object> vars = mapper.map("PV_DISSOLUTION_LIQUIDATION_SARL", payload());
        assertNotNull(vars);

        // Étape fixée par le workflow (jamais saisie).
        assertEquals("dissolution", vars.get("PV_ETAPE"));

        // Noyau séance (identité société + quorum + double nommage).
        assertEquals("PARACOSME", vars.get("DENOMINATION"));
        assertEquals("non", vars.get("ASSOCIE_UNIQUE"));
        assertEquals("15/05/2026", vars.get("DATE_AG"));
        assertNotNull(vars.get("ANNEE_LETTRES"));
        assertFalse(((String) vars.get("ANNEE_LETTRES")).isBlank());
        assertEquals("M. Ahmed Alaoui", vars.get("PRESIDENT_NOM"));

        // Variables propres à la dissolution / liquidateur.
        assertEquals("volontaire", vars.get("DISSOLUTION_MOTIF"));
        assertEquals("M. Ahmed Alaoui", vars.get("LIQUIDATEUR_NOM"));
        assertEquals("5 RUE D'ALGER, CASABLANCA", vars.get("LIQUIDATEUR_ADRESSE"));
        assertEquals("exercées à titre gratuit", vars.get("LIQUIDATEUR_REMUNERATION"));
        assertEquals("12 RUE DES FOULES, CASABLANCA", vars.get("LIQUIDATION_SIEGE"));

        // Bloc ASSOCIES (présence / quorum) — liste de lignes.
        List<Map<String, Object>> associes = (List<Map<String, Object>>) vars.get("ASSOCIES");
        assertEquals(2, associes.size());
        assertEquals("M. Ahmed Alaoui", associes.get(0).get("ASSOCIE_NOM"));

        // Bloc RESOLUTIONS — type piloté par la saisie.
        List<Map<String, Object>> resolutions = (List<Map<String, Object>>) vars.get("RESOLUTIONS");
        assertEquals(2, resolutions.size());
        assertEquals("dissolution_anticipee", resolutions.get(0).get("RESOLUTION_TYPE"));
        assertEquals("nomination_liquidateur", resolutions.get(1).get("RESOLUTION_TYPE"));
    }

    @Test
    void mapSarlAu() {
        Map<String, Object> p = new java.util.HashMap<>(payload());
        p.put("societe", Map.of("denomination", "PARACOSME AU", "capitalChiffres", 100_000,
                "siegeSocial", "12 RUE DES FOULES, CASABLANCA", "rcNumero", "123456",
                "villeGreffe", "CASABLANCA", "formeJuridique", "SARL_AU"));
        p.put("associes", List.of(Map.of("civilite", "M.", "prenom", "Ahmed", "nom", "Alaoui",
                "nationalite", "marocaine", "adresse", "5 RUE D'ALGER", "pieceType", "CIN",
                "pieceNumero", "BE123456")));

        Map<String, Object> vars = mapper.map("PV_DISSOLUTION_LIQUIDATION_SARL_AU", p);
        assertEquals("dissolution", vars.get("PV_ETAPE"));
        assertEquals("oui", vars.get("ASSOCIE_UNIQUE"));
        // Associé unique décrit hors boucle (scalaires $ASSOCIE_*).
        assertEquals("personne physique", vars.get("ASSOCIE_TYPE"));
        // Fix M5 (2026-08-16) — ATTENTE CORRIGÉE. Ce test exigeait auparavant le
        // libellé COMPLET (« M. Ahmed Alaoui ») dans $ASSOCIE_NOM. C'était l'erreur
        // elle-même : la comparution du modèle SARL AU s'écrit
        // « $ASSOCIE_CIVILITE $ASSOCIE_PRENOM $ASSOCIE_NOM », si bien que la civilité
        // et le prénom sortaient DEUX fois — « M. Salma M. Salma BENJELLOUN » en
        // simulation. En scalaire, $ASSOCIE_NOM porte donc le SEUL nom de famille ;
        // le libellé complet reste l'usage de la BOUCLE (PV SARL, feuille de présence).
        assertEquals("Alaoui", vars.get("ASSOCIE_NOM"));
        assertEquals("M.", vars.get("ASSOCIE_CIVILITE"));
        assertEquals("Ahmed", vars.get("ASSOCIE_PRENOM"));
        assertEquals("volontaire", vars.get("DISSOLUTION_MOTIF"));
    }
}
