package ma.jurika.workflow.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LA RÈGLE DE NOMMAGE DE L'ÉTAPE 7, ET SA RÉCIPROQUE.
 *
 * <p>Le catalogue des 180 champs est généré : sa clé d'écran est dérivée du nom
 * de la variable par une règle explicite du script de dérivation.
 *
 * <pre>const cleDe = (nom) =&gt; nom.toLowerCase().replace(/_([a-z0-9])/g, (_, c) =&gt; c.toUpperCase());</pre>
 *
 * <p>Le projecteur applique l'inverse. Ce n'est pas un rapprochement par
 * ressemblance — c'est la réciproque d'une fonction connue, et ces cas le
 * vérifient sur des variables réelles du corpus.
 */
class ProjecteurVariablesCreationTest {

    @Test
    @DisplayName("bailleurNom redonne BAILLEUR_NOM — et les cas du corpus avec")
    void laCleRedonneSaVariable() {
        assertThat(ProjecteurVariablesCreation.variableDeLaCle("bailleurNom"))
                .isEqualTo("BAILLEUR_NOM");
        assertThat(ProjecteurVariablesCreation.variableDeLaCle("domiciliationRedevanceChiffres"))
                .isEqualTo("DOMICILIATION_REDEVANCE_CHIFFRES");
        assertThat(ProjecteurVariablesCreation.variableDeLaCle("retraitDepotReference"))
                .isEqualTo("RETRAIT_DEPOT_REFERENCE");
        assertThat(ProjecteurVariablesCreation.variableDeLaCle("commissaireApportsDateDesignation"))
                .isEqualTo("COMMISSAIRE_APPORTS_DATE_DESIGNATION");
    }

    @Test
    @DisplayName("Un segment déjà en capitales ou un mot simple traversent sans dommage")
    void lesCasLimites() {
        assertThat(ProjecteurVariablesCreation.variableDeLaCle("subdivision")).isEqualTo("SUBDIVISION");
        assertThat(ProjecteurVariablesCreation.variableDeLaCle("fax")).isEqualTo("FAX");
        assertThat(ProjecteurVariablesCreation.variableDeLaCle("")).isEmpty();
        assertThat(ProjecteurVariablesCreation.variableDeLaCle(null)).isEmpty();
    }

    @Test
    @DisplayName("L3 (D6) : le type de piece n'est jamais suppose ; CIN seulement si un n° de CIN est saisi")
    void leTypeDePieceSuitLaDonneeSaisie() {
        assertThat(ProjecteurVariablesCreation.typePiece(java.util.Map.of("cin", "BK123456"), false)).isEqualTo("CIN");
        assertThat(ProjecteurVariablesCreation.typePiece(java.util.Map.of("cinNumero", "BK123456"), false)).isEqualTo("CIN");
        assertThat(ProjecteurVariablesCreation.typePiece(
                java.util.Map.of("pieceType", "passeport", "pieceNumero", "P1"), false)).isEqualTo("passeport");
        assertThat(ProjecteurVariablesCreation.typePiece(java.util.Map.of("nom", "ALAOUI"), false))
                .as("aucun numero saisi : rien a deriver").isEmpty();
        assertThat(ProjecteurVariablesCreation.typePiece(java.util.Map.of("rc", "12345"), true))
                .as("personne morale : pas de CIN").isEmpty();
    }

    @Test
    @DisplayName("L4a (E1c) : la cle d'ecran de CHAQUE champ du catalogue redonne sa variable (chiffres compris)")
    @SuppressWarnings("unchecked")
    void reciproqueExacteSurToutLeCatalogue() throws java.io.IOException {
        java.nio.file.Path catalogue = java.nio.file.Path.of(
                "../ai-service/src/main/resources/templates/v2/creation-champs.json");
        Map<String, Object> cat = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(catalogue.toFile(), Map.class);
        java.util.List<String> fausses = new java.util.ArrayList<>();
        for (Map<String, Object> c : (java.util.List<Map<String, Object>>) cat.get("champs")) {
            String cle = (String) c.get("cle");
            String variable = (String) c.get("variable");
            if (!variable.equals(ProjecteurVariablesCreation.variableDeLaCle(cle))) fausses.add(cle + " -> " + variable);
            // Le sens retour (variable -> cle d'ecran) ne vaut que pour la regle de nommage :
            // les champs herites passent par le bloc `formulaires`, sous leur propre table.
            if (!Boolean.TRUE.equals(c.get("heritee"))
                    && !cle.equals(ConstructeurChargeUtileCreation.cleDe(variable))) fausses.add(variable + " -> " + cle);
        }
        assertThat(fausses).isEmpty();
    }

    // L6b E14 : la date de debut du contrat (etape 2, obligatoire) n'etait lue par personne :
    // l'etape 7 la redemandait ("Bail date effet") et la taxe professionnelle, la declaration
    // d'immatriculation et le bail etaient refuses sans elle.
    private static Map<String, Object> lire(String justificatif, Map<String, Object> step7) {
        MagasinVariables magasin = new MagasinVariables(ChaineEcranMagasinCreationTest.depotEnMemoire());
        java.util.UUID ws = java.util.UUID.randomUUID();
        java.util.UUID ticket = java.util.UUID.randomUUID();
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("step2", Map.of("adresse", "12 rue Demo", "justificatifType", justificatif,
                "dateDebut", "2026-10-01", "dateFin", "2029-09-30"));
        if (step7 != null) data.put("step7", step7);
        new ProjecteurVariablesCreation(magasin).projeter(ws, ticket, data, java.util.UUID.randomUUID());
        return magasin.lirePourGeneration(ws, ticket);
    }

    @Test
    @DisplayName("L6b E14 : la date de debut du bail saisie a l'etape 2 alimente BAIL_DATE_EFFET")
    void dateDuBailDeLEtape2() {
        assertThat(lire("BAIL", null)).containsEntry("BAIL_DATE_EFFET", "2026-10-01")
                .doesNotContainKey("DOMICILIATION_DATE_EFFET");
    }

    @Test
    @DisplayName("L6b E14 : la date de debut de la domiciliation saisie a l'etape 2 alimente DOMICILIATION_DATE_EFFET")
    void dateDeLaDomiciliationDeLEtape2() {
        assertThat(lire("DOMICILIATION", null)).containsEntry("DOMICILIATION_DATE_EFFET", "2026-10-01")
                .doesNotContainKey("BAIL_DATE_EFFET");
    }

    @Test
    @DisplayName("L6b E14 : l'etape 7 ne recouvre pas la date de l'etape 2 (RG-VAR-03 : elle s'y affiche en lecture)")
    void lEtape7NeRecouvrePas() {
        assertThat(lire("BAIL", Map.of("complements", Map.of("bailDateEffet", "2026-10-15"))))
                .containsEntry("BAIL_DATE_EFFET", "2026-10-01");
    }
}
