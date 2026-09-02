package ma.jurika.ai.workflow.identity;

import ma.jurika.ai.workflow.mapper.SeancePvVarsBuilder;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prouve le lot « identité société depuis la BD » : à partir d'un payload de séance
 * <b>sans</b> capital / siège / RC / ville du greffe / parts (cas d'un vrai dossier où
 * le front ne porte que la dénomination), l'enrichissement BD (simulé par un stub qui
 * renvoie ce que {@code DossierIdentityQueryService} lit en base) remplit l'en-tête PV :
 * 0 champ vide.
 *
 * <p>Le {@link SeancePvVarsBuilder} n'est PAS modifié : on vérifie que l'enrichissement
 * en amont suffit à ce que ses variables d'en-tête soient toutes renseignées.
 */
class SocieteIdentityEnricherTest {

    private static final UUID WS = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DOSSIER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    /** Identité renvoyée par la BD (clés alignées sur le builder, cf. DossierIdentityQueryService). */
    private static Map<String, Object> bdIdentity() {
        Map<String, Object> id = new HashMap<>();
        id.put("denomination", "ACME MAROC SARL");
        id.put("formeJuridique", "SARL");
        id.put("ice", "001234567000089");
        id.put("ifNumero", "40012345");
        id.put("rcNumero", "RC 123456");
        id.put("villeGreffe", "Casablanca");
        id.put("rcVille", "Casablanca");
        id.put("capitalChiffres", 100000L);
        id.put("capitalSocial", 100000L);
        id.put("siegeSocial", "12 rue de la Liberté, Casablanca");
        id.put("adresseSiege", "12 rue de la Liberté, Casablanca");
        id.put("nombreParts", 1000L);
        id.put("valeurNominalePart", 100L);
        return id;
    }

    /** Payload de séance « pauvre » : seule la dénomination est portée par le front. */
    private static Map<String, Object> thinPayload() {
        Map<String, Object> societe = new HashMap<>();
        societe.put("denomination", "ACME MAROC SARL");
        Map<String, Object> payload = new HashMap<>();
        payload.put("dossierId", DOSSIER.toString());
        payload.put("formeJuridique", "SARL");
        payload.put("societe", societe);
        payload.put("seance", Map.of("type", "extraordinaire", "date", "2026-08-01"));
        return payload;
    }

    @Test
    void enrichit_len_tete_pv_depuis_la_bd_zero_champ_vide() {
        SocieteIdentityEnricher enricher = new SocieteIdentityEnricher((ws, id) -> bdIdentity());

        Map<String, Object> enriched = enricher.enrich(thinPayload(), WS);
        Map<String, Object> vars =
                SeancePvVarsBuilder.build("PV_DISSOLUTION_LIQUIDATION_SARL", enriched);

        // En-tête société : toutes les variables du modèle doivent être renseignées.
        for (String key : List.of("DENOMINATION", "CAPITAL_CHIFFRES", "CAPITAL_LETTRES",
                "SIEGE_SOCIAL", "NOMBRE_PARTS", "RC_NUMERO", "VILLE_GREFFE")) {
            Object v = vars.get(key);
            assertFalse(v == null || v.toString().isBlank(),
                    "Variable d'en-tête vide après enrichissement BD : " + key);
        }
        assertEquals("ACME MAROC SARL", vars.get("DENOMINATION"));
        assertEquals("RC 123456", vars.get("RC_NUMERO"));
        assertEquals("Casablanca", vars.get("VILLE_GREFFE"));
        assertEquals("1000", vars.get("NOMBRE_PARTS"));
    }

    @Test
    void bd_prioritaire_ecrase_une_valeur_front_perimee() {
        // Le front a mal rempli villeGreffe (a mis la ville du siège au lieu du greffe).
        Map<String, Object> payload = thinPayload();
        @SuppressWarnings("unchecked")
        Map<String, Object> societe = (Map<String, Object>) payload.get("societe");
        societe.put("villeGreffe", "PÉRIMÉ");

        SocieteIdentityEnricher enricher = new SocieteIdentityEnricher((ws, id) -> bdIdentity());
        Map<String, Object> vars = SeancePvVarsBuilder.build(
                "PV_DISSOLUTION_LIQUIDATION_SARL", enricher.enrich(payload, WS));

        assertEquals("Casablanca", vars.get("VILLE_GREFFE"), "La valeur BD doit gagner sur le front");
    }

    @Test
    void sans_dossierId_le_payload_reste_inchange() {
        Map<String, Object> payload = thinPayload();
        payload.remove("dossierId");

        SocieteIdentityEnricher enricher = new SocieteIdentityEnricher((ws, id) -> bdIdentity());
        Map<String, Object> out = enricher.enrich(payload, WS);

        assertSame(payload, out, "Sans dossierId, aucun enrichissement (mode dégradé)");
        // Et l'en-tête reste vide (preuve que c'est bien l'enrichissement qui remplit).
        Map<String, Object> vars = SeancePvVarsBuilder.build("PV_DISSOLUTION_LIQUIDATION_SARL", out);
        assertTrue(vars.get("CAPITAL_CHIFFRES").toString().isBlank());
        assertTrue(vars.get("RC_NUMERO").toString().isBlank());
    }

    @Test
    void identite_bd_indisponible_ne_casse_pas_la_generation() {
        // Provider best-effort renvoyant vide (workflow-service KO) → payload inchangé.
        SocieteIdentityEnricher enricher = new SocieteIdentityEnricher((ws, id) -> Map.of());
        Map<String, Object> payload = thinPayload();
        Map<String, Object> out = enricher.enrich(payload, WS);
        assertSame(payload, out);
    }

    @Test
    void amorce_associes_seulement_si_le_formulaire_na_rien_saisi() {
        SocieteIdentityEnricher enricher = new SocieteIdentityEnricher((ws, id) -> {
            Map<String, Object> m = new HashMap<>(bdIdentity());
            m.put("associes", List.of(Map.of("nom", "BENALI", "nombreParts", 1000)));
            return m;
        });

        // Cas 1 : le formulaire n'a pas d'associés → on amorce depuis la BD.
        Map<String, Object> enriched = enricher.enrich(thinPayload(), WS);
        @SuppressWarnings("unchecked")
        List<Object> seeded = (List<Object>) enriched.get("associes");
        assertEquals(1, seeded.size());

        // Cas 2 : le formulaire a saisi la présence → on NE remplace PAS.
        Map<String, Object> withForm = thinPayload();
        withForm.put("associes", List.of(Map.of("nom", "SAISI_SEANCE", "presence", "présent")));
        Map<String, Object> enriched2 = enricher.enrich(withForm, WS);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> kept = (List<Map<String, Object>>) enriched2.get("associes");
        assertEquals("SAISI_SEANCE", kept.get(0).get("nom"));
    }
}
