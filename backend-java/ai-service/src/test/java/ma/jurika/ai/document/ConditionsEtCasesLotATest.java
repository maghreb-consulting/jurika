package ma.jurika.ai.document;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

/**
 * Lot A (2026-09-10) — les deux défauts trouvés sur le corpus CRÉATION du
 * 9 septembre, et la non-régression des neuf autres workflows.
 *
 * <p>Le premier — une condition coupée par le « et » de son propre libellé —
 * se teste ici, au niveau du découpage. Le second — les options de case à
 * cocher qui ne portent plus de ☐ — se teste sur le document rendu, dans
 * {@code ma.jurika.ai.lotA.CorpusCreation0909RenduTest} : c'est le seul endroit
 * où l'on voit ce que le lecteur verra.
 */
class ConditionsEtCasesLotATest {

    // ─────────────────────────────────────────────────────────────────
    //  Le défaut : « Télédéclaration ET télépaiement » coupé en deux
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("le « et » d'un libellé entre guillemets ne coupe plus la condition")
    void et_dans_un_litteral_ne_coupe_pas() {
        String cond = "$CNSS_MODE_DECLARATION = « Télédéclaration et télépaiement (DAMANCOM) »";
        assertIterableEquals(List.of(cond), DocxTemplateEngine.decouperHorsLitteral(cond, "ET"));
    }

    @Test
    @DisplayName("le « ou » d'un libellé entre guillemets ne coupe plus la condition")
    void ou_dans_un_litteral_ne_coupe_pas() {
        String cond = "$TP_OBJET = « Personne morale ou assimilée »";
        assertIterableEquals(List.of(cond), DocxTemplateEngine.decouperHorsLitteral(cond, "OU"));
    }

    // ─────────────────────────────────────────────────────────────────
    //  Non-régression : les conjonctions RÉELLES coupent toujours
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("une vraie conjonction ET coupe toujours")
    void et_hors_litteral_coupe() {
        List<String> parts = DocxTemplateEngine.decouperHorsLitteral(
                "$SIGLE existe ET $ENSEIGNE existe", "ET");
        assertIterableEquals(List.of("$SIGLE existe", "$ENSEIGNE existe"), parts);
    }

    @Test
    @DisplayName("une vraie conjonction OU coupe toujours, quelle que soit la casse")
    void ou_hors_litteral_coupe() {
        List<String> parts = DocxTemplateEngine.decouperHorsLitteral(
                "$RC_NUMERO existe ou $ICE existe", "OU");
        assertIterableEquals(List.of("$RC_NUMERO existe", "$ICE existe"), parts);
    }

    @Test
    @DisplayName("conjonction réelle ET libellé porteur du mot-clé : seule la vraie coupe")
    void melange_litteral_et_conjonction() {
        List<String> parts = DocxTemplateEngine.decouperHorsLitteral(
                "$CNSS_MODE_DECLARATION = « Télédéclaration et télépaiement (DAMANCOM) »"
                        + " ET $CNSS_CONTACT_NOM existe", "ET");
        assertEquals(2, parts.size());
        assertEquals("$CNSS_MODE_DECLARATION = « Télédéclaration et télépaiement (DAMANCOM) »",
                parts.get(0).trim());
        assertEquals("$CNSS_CONTACT_NOM existe", parts.get(1).trim());
    }

    @Test
    @DisplayName("« et » collé à un mot n'est pas une conjonction")
    void mot_contenant_le_motcle_ne_coupe_pas() {
        String cond = "$ETABLISSEMENT_ADRESSE existe";
        assertIterableEquals(List.of(cond), DocxTemplateEngine.decouperHorsLitteral(cond, "ET"));
    }

    @Test
    @DisplayName("une condition sans conjonction reste d'un seul tenant")
    void atome_seul() {
        String cond = "$SIGLE existe";
        assertIterableEquals(List.of(cond), DocxTemplateEngine.decouperHorsLitteral(cond, "ET"));
        assertIterableEquals(List.of(cond), DocxTemplateEngine.decouperHorsLitteral(cond, "OU"));
    }

    @Test
    @DisplayName("expression vide ou nulle : aucune casse")
    void vide() {
        assertEquals(List.of(), DocxTemplateEngine.decouperHorsLitteral(null, "ET"));
        assertIterableEquals(List.of(""), DocxTemplateEngine.decouperHorsLitteral("", "ET"));
    }
}
