package ma.jurika.ai.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.ai.document.DocxTemplateEngine;
import ma.jurika.ai.document.DocxTemplateEngine.DocumentResult;
import ma.jurika.ai.document.manifest.TemplateManifestLoader;
import ma.jurika.ai.workflow.mapper.ModificationMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase E2 (2026-08-09) — {@code STATUTS_REFONDUS_*} : prouve que la Modification
 * produit un STATUT COMPLET refondu par la voie <b>DIRECTEUR</b>
 * ({@code STATUTS_SARL_DIRECTEUR}) depuis la FICHE STRUCTURÉE (jamais le scan), avec
 * superposition (overlay) des nouvelles valeurs des modifications cochées.
 *
 * <p>Remplace l'ancienne preuve via la voie LEGACY {@code STATUTS_CONSTITUTIFS_*}.
 */
class StatutsRefondusTest {

    private final ModificationMapper mapper = new ModificationMapper();
    private final DocxTemplateEngine engine = buildEngine();

    private static DocxTemplateEngine buildEngine() {
        TemplateManifestLoader loader = new TemplateManifestLoader(new ObjectMapper());
        loader.load();
        return new DocxTemplateEngine(loader);
    }

    /** Fiche structurée d'une société (état COURANT), telle que persistée en base. */
    private static Map<String, Object> ficheBaseline() {
        return Map.of(
                "denomination", "ACME ORIGINAL SARL",
                "formeJuridique", "SARL",
                "adresseSiege", "12 Rue des Hôpitaux, Casablanca",
                "ice", "001234567000089",
                "objetSocial", "Conseil juridique et fiscal",
                "capitalSocial", 100_000L,
                "valeurNominale", 100L,
                "dureeAnnees", 99L,
                "gerants", List.of(Map.of(
                        "nom", "BENNANI", "prenom", "Karim", "cinNumero", "BK12345",
                        "nationalite", "marocaine", "isStatutaire", Boolean.TRUE)),
                "associes", List.of(Map.of(
                        "typePersonne", "PHYSIQUE", "nom", "BENNANI", "prenom", "Karim",
                        "cin", "BK12345", "nombreParts", 1000L)));
    }

    private static Map<String, Object> payload(Map<String, Object> fiche,
                                               List<Map<String, Object>> modifications) {
        return Map.of(
                "ficheStructuree", fiche,
                "societe", Map.of("denomination", fiche.get("denomination"),
                        "formeJuridique", fiche.get("formeJuridique")),
                "modifications", modifications);
    }

    private static String text(byte[] bytes) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder sb = new StringBuilder();
            doc.getParagraphs().forEach(p -> sb.append(p.getText()).append('\n'));
            doc.getTables().forEach(t -> t.getRows().forEach(r -> r.getTableCells().forEach(c ->
                    c.getParagraphs().forEach(p -> sb.append(p.getText()).append('\n')))));
            return sb.toString();
        }
    }

    @Test
    @DisplayName("Refonte directeur SARL : la NOUVELLE dénomination remplace l'ancienne, statut COMPLET rendu")
    void refonte_changement_denomination() throws IOException {
        Map<String, Object> p = payload(ficheBaseline(), List.of(
                Map.of("typeId", "CHANGEMENT_DENOMINATION",
                        "details", Map.of("nouvelleDenomination", "ATLAS PARTNERS SARL"))));

        Map<String, Object> vars = mapper.map("STATUTS_REFONDUS_SARL", p);
        // Voie directeur : variable $UPPER. Overlay gagne : nouvelle dénomination.
        assertThat(vars).containsEntry("DENOMINATION", "ATLAS PARTNERS SARL");

        DocumentResult res = engine.generate("STATUTS_REFONDUS_SARL", vars);
        assertThat(res.templateFound()).isTrue();
        String t = text(res.bytes());
        // Statut COMPLET (gabarit directeur) effectivement rendu + nouvelle valeur.
        assertThat(t).contains("ATLAS PARTNERS SARL");
        assertThat(t).doesNotContain("ACME ORIGINAL SARL");
        assertThat(res.bytes().length).isGreaterThan(2000);
        // Aucun marqueur résiduel de variable/boucle/condition directeur.
        assertThat(t).doesNotContain("$");
        assertThat(t).doesNotContain("▼").doesNotContain("▲").doesNotContain("◇").doesNotContain("◆");
        assertThat(t).doesNotContain("VALEUR MANQUANTE");
    }

    @Test
    @DisplayName("Refonte directeur SARL : augmentation de capital -> nouveau capital dans le statut refondu")
    void refonte_augmentation_capital() throws IOException {
        Map<String, Object> p = payload(ficheBaseline(), List.of(
                Map.of("typeId", "AUGMENTATION_CAPITAL",
                        "details", Map.of("nouveauCapital", 500_000L, "nouvellesParts", 4000L))));

        Map<String, Object> vars = mapper.map("STATUTS_REFONDUS_SARL", p);
        // CAPITAL_CHIFFRES ($UPPER) dérivé de capitalChiffres=500000 par le builder directeur.
        assertThat(String.valueOf(vars.get("CAPITAL_CHIFFRES"))).contains("500");

        DocumentResult res = engine.generate("STATUTS_REFONDUS_SARL", vars);
        assertThat(res.templateFound()).isTrue();
        assertThat(text(res.bytes())).contains("500");
    }

    @Test
    @DisplayName("Refonte directeur SARL : cession -> le cessionnaire apparaît dans la répartition des parts")
    @SuppressWarnings("unchecked")
    void refonte_cession_credite_le_cessionnaire() throws IOException {
        Map<String, Object> p = payload(ficheBaseline(), List.of(
                Map.of("typeId", "CESSION_PARTIELLE",
                        "details", Map.of("cessionnaire", "IDRISSI", "nombreParts", 300L, "prix", 30000L))));

        Map<String, Object> vars = mapper.map("STATUTS_REFONDUS_SARL", p);
        // Le cessionnaire (best-effort : ajouté aux associés avec ses parts acquises)
        // apparaît dans la boucle ASSOCIES.
        List<Map<String, Object>> associes = (List<Map<String, Object>>) vars.get("ASSOCIES");
        assertThat(associes).anySatisfy(a ->
                assertThat(String.valueOf(a.get("ASSOCIE_NOM"))).contains("IDRISSI"));

        DocumentResult res = engine.generate("STATUTS_REFONDUS_SARL", vars);
        assertThat(res.templateFound()).isTrue();
        assertThat(text(res.bytes())).contains("IDRISSI");
    }
}
