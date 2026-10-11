package ma.jurika.ai.workflow.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.ai.document.GenerationRefuseeException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L4d (E11e) : rapport de gestion -- modele du corpus choisi par la forme, variables
 * construites par le seul RapportGestionVarsBuilder (parcours ANNUAL_REPORT et PV_AGO),
 * donnees conditionnelles exigees seulement quand leur branche est imprimee.
 */
class AnnualReportMapperTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static Map<String, Map<String, Object>> fixtures;
    private static Map<String, Map<String, Object>> fixturesAgo;

    private final AnnualReportMapper mapper = new AnnualReportMapper();

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void loadFixture() throws Exception {
        try (InputStream is = AnnualReportMapperTest.class.getResourceAsStream("/workflow-fixtures/annual_report.json");
             InputStream ago = AnnualReportMapperTest.class.getResourceAsStream("/workflow-fixtures/approbation_comptes.json")) {
            fixtures = JSON.readValue(is, Map.class);
            fixturesAgo = JSON.readValue(ago, Map.class);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> copie(Map<String, Object> p) {
        return JSON.convertValue(p, LinkedHashMap.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> bloc(Map<String, Object> p, String cle) {
        return (Map<String, Object>) p.get(cle);
    }

    @Test
    @DisplayName("Contrat : workflow ANNUAL_REPORT + 1 document RAPPORT_GESTION")
    void contract() {
        assertThat(mapper.workflowCode()).isEqualTo("ANNUAL_REPORT");
        assertThat(mapper.supportedTemplates()).containsExactly("RAPPORT_GESTION");
    }

    @Test
    @DisplayName("Document inconnu : IllegalArgumentException")
    void rejectsUnknownTemplate() {
        assertThatThrownBy(() -> mapper.map("UNKNOWN", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Template non support");
    }

    @Test
    @DisplayName("gabarit() : modele du corpus selon la forme, la forme lue en base primant")
    void gabaritSelonLaForme() {
        assertThat(mapper.gabarit("RAPPORT_GESTION", Map.of("societe", Map.of("formeJuridique", "SARL"))))
                .isEqualTo("RAPPORT_GESTION_EXERCICE_SARL");
        assertThat(mapper.gabarit("RAPPORT_GESTION", Map.of("societe", Map.of("formeJuridique", "SARL AU"))))
                .isEqualTo("RAPPORT_GESTION_EXERCICE_SARL_AU");
        assertThat(mapper.gabarit("RAPPORT_GESTION", Map.of("formeJuridique", "SARL_AU")))
                .isEqualTo("RAPPORT_GESTION_EXERCICE_SARL_AU");
        assertThat(mapper.gabarit("RAPPORT_GESTION", Map.of("associeUnique", true)))
                .isEqualTo("RAPPORT_GESTION_EXERCICE_SARL_AU");
        // Base d'abord : la forme du dossier l'emporte sur celle de l'ecran.
        assertThat(mapper.gabarit("RAPPORT_GESTION", Map.of("formeJuridique", "SARL_AU",
                "societe", Map.of("formeJuridique", "SARL")))).isEqualTo("RAPPORT_GESTION_EXERCICE_SARL");
        // Lecture du modele sans donnee (emplacement de clause) : jamais d'exception.
        assertThat(mapper.gabarit("RAPPORT_GESTION", Map.of())).isEqualTo("RAPPORT_GESTION_EXERCICE_SARL");
        // Le parcours PV_AGO choisit le meme modele ; ses autres documents gardent leur code.
        ApprobationComptesMapper ago = new ApprobationComptesMapper(mapper);
        assertThat(ago.gabarit("RAPPORT_GESTION", Map.of("societe", Map.of("formeJuridique", "SARL_AU"))))
                .isEqualTo("RAPPORT_GESTION_EXERCICE_SARL_AU");
        assertThat(ago.gabarit("PV_APPROBATION_COMPTES_SARL", Map.of())).isEqualTo("PV_APPROBATION_COMPTES_SARL");
    }

    @Test
    @DisplayName("Forme inconnue : generation refusee, donnee nommee")
    void formeInconnueRefusee() {
        assertThatThrownBy(() -> mapper.map("RAPPORT_GESTION", Map.of()))
                .isInstanceOf(GenerationRefuseeException.class)
                .hasMessageContaining("Forme juridique");
    }

    @Test
    @DisplayName("Un seul constructeur : PV_AGO et ANNUAL_REPORT produisent les memes variables")
    void unSeulEndroit() {
        Map<String, Object> payload = fixturesAgo.get("RAPPORT_GESTION");
        assertThat(new ApprobationComptesMapper(mapper).map("RAPPORT_GESTION", payload))
                .isEqualTo(mapper.map("RAPPORT_GESTION", payload));
    }

    @Test
    @DisplayName("Fiche societe : valeur nominale = capital / nombre de parts ; exercice precedent = veille de l'ouverture")
    void derivees() {
        Map<String, Object> v = mapper.map("RAPPORT_GESTION", fixtures.get("RAPPORT_GESTION"));
        assertThat(v.get("NOMBRE_PARTS")).isEqualTo("1\u202f000");
        assertThat(v.get("VALEUR_NOMINALE_PART")).isEqualTo("100");
        assertThat(v.get("OBJET_SOCIAL")).isEqualTo("le conseil juridique et fiscal aux entreprises");
        assertThat(v.get("EXERCICE_DEBUT")).isEqualTo("01/01/2025");
        assertThat(v.get("EXERCICE_PRECEDENT_CLOS_DATE")).isEqualTo("31/12/2024");
        assertThat(v.get("ASSEMBLEE_DATE")).isEqualTo("15/06/2026");
        assertThat(v.get("CAPITAUX_PROPRES_SOUS_QUART")).isEqualTo("non");
        assertThat(v.get("RESULTAT_NET_LETTRES")).isEqualTo("CENT QUATRE-VINGT MILLE");
        assertThat((List<?>) v.get("GERANTS")).hasSize(1);

        Map<String, Object> p = copie(fixtures.get("RAPPORT_GESTION"));
        bloc(p, "societe").put("capitalChiffres", "10000");
        bloc(p, "societe").put("nombreParts", "400");
        assertThat(mapper.map("RAPPORT_GESTION", p).get("VALEUR_NOMINALE_PART")).isEqualTo("25");
        bloc(p, "societe").put("nombreParts", "3");
        bloc(p, "societe").put("valeurNominalePart", null);
        // Division sans quotient exact, sans valeur de la fiche : rien n'est arrondi, la donnee manque.
        assertThat(mapper.map("RAPPORT_GESTION", p).get("VALEUR_NOMINALE_PART")).isEqualTo("");
    }

    @Test
    @DisplayName("Article 86 : capitaux propres inferieurs au quart du capital calcules, jamais supposes")
    void capitauxPropresSousLeQuart() {
        Map<String, Object> p = copie(fixtures.get("RAPPORT_GESTION"));
        bloc(p, "rapportGestion").put("capitauxPropres", 24999);
        assertThat(mapper.map("RAPPORT_GESTION", p).get("CAPITAUX_PROPRES_SOUS_QUART")).isEqualTo("oui");
        bloc(p, "rapportGestion").put("capitauxPropres", 25000);
        assertThat(mapper.map("RAPPORT_GESTION", p).get("CAPITAUX_PROPRES_SOUS_QUART")).isEqualTo("non");
        bloc(p, "rapportGestion").remove("capitauxPropres");
        Map<String, Object> v = mapper.map("RAPPORT_GESTION", p);
        assertThat(v.get("CAPITAUX_PROPRES_SOUS_QUART")).isEqualTo("");
        assertThat(v.get("CAPITAUX_PROPRES_MONTANT")).isEqualTo("");
    }

    @Test
    @DisplayName("Benefice : reserve legale et dividende exiges ; pertes cumulees non exigees")
    void conditionsDuBenefice() {
        Map<String, Object> p = copie(fixtures.get("RAPPORT_GESTION"));
        Map<String, Object> v = mapper.map("RAPPORT_GESTION", p);
        assertThat(v).doesNotContainKey("PERTES_CUMULEES_MONTANT");
        bloc(p, "rapportGestion").remove("reserveLegaleDoteeIntegralement");
        assertThatThrownBy(() -> mapper.map("RAPPORT_GESTION", p))
                .isInstanceOf(GenerationRefuseeException.class).hasMessageContaining("R\u00e9serve l\u00e9gale");
    }

    @Test
    @DisplayName("Perte : pertes cumulees exigees (nommees si absentes) ; ni reserve legale ni dividende exiges")
    void conditionsDeLaPerte() {
        Map<String, Object> p = copie(fixtures.get("RAPPORT_GESTION"));
        bloc(p, "approbation").put("resultatType", "perte");
        bloc(p, "approbation").remove("dividendeDistribue");
        bloc(p, "rapportGestion").remove("reserveLegaleDoteeIntegralement");
        Map<String, Object> v = mapper.map("RAPPORT_GESTION", p);
        assertThat(v.get("RESULTAT_TYPE")).isEqualTo("perte");
        assertThat(v.get("PERTES_CUMULEES_MONTANT")).isEqualTo("");
        assertThat(v).doesNotContainKey("RESERVE_LEGALE_DOTEE_INTEGRALEMENT");
        bloc(p, "rapportGestion").put("pertesCumulees", "12 500,50");
        assertThat(mapper.map("RAPPORT_GESTION", p).get("PERTES_CUMULEES_MONTANT")).isEqualTo("12\u202f500,50");
    }

    @Test
    @DisplayName("Conditions oui / non du modele exigees ; listes requises seulement si \"oui\"")
    void conditionsOuiNon() {
        for (String cle : List.of("evenementsPostCloture", "rechercheDeveloppement")) {
            Map<String, Object> p = copie(fixtures.get("RAPPORT_GESTION"));
            bloc(p, "rapportGestion").remove(cle);
            assertThatThrownBy(() -> mapper.map("RAPPORT_GESTION", p)).isInstanceOf(GenerationRefuseeException.class);
        }
        Map<String, Object> p = copie(fixtures.get("RAPPORT_GESTION"));
        assertThat(mapper.map("RAPPORT_GESTION", p)).doesNotContainKeys("RECHERCHE_DEVELOPPEMENT_EXPOSE");
        bloc(p, "rapportGestion").put("rechercheDeveloppement", "oui");
        assertThat(mapper.map("RAPPORT_GESTION", p).get("RECHERCHE_DEVELOPPEMENT_EXPOSE")).isEqualTo("");
        // Evenements annonces sans aucun evenement saisi : refus nomme (le moteur effacerait la boucle).
        bloc(p, "rapportGestion").put("evenementsPostCloture", "oui");
        assertThatThrownBy(() -> mapper.map("RAPPORT_GESTION", p))
                .isInstanceOf(GenerationRefuseeException.class).hasMessageContaining("depuis la cl\u00f4ture");
        bloc(p, "rapportGestion").put("evenementsPostClotureListe",
                List.of(Map.of("date", "2026-02-10", "libelle", "perte d'un client", "incidence", "aucune sur les comptes")));
        assertThat((List<?>) mapper.map("RAPPORT_GESTION", p).get("EVENEMENTS_POST_CLOTURE")).hasSize(1);
        Map<String, Object> sansListe = copie(fixtures.get("RAPPORT_GESTION"));
        bloc(sansListe, "rapportGestion").put("faitsMarquants", List.of());
        assertThat(mapper.map("RAPPORT_GESTION", sansListe)).doesNotContainKey("FAITS_MARQUANTS_EXERCICE");
    }

    @Test
    @DisplayName("Aucun texte de rapport par defaut : saisies absentes = variables vides (nommees)")
    void aucunTexteParDefaut() {
        Map<String, Object> p = copie(fixtures.get("RAPPORT_GESTION"));
        Map<String, Object> rg = bloc(p, "rapportGestion");
        for (String k : List.of("activiteExpose", "evolutionCommentaire", "perspectives", "lieuSignature", "date", "exerciceDebut")) {
            rg.remove(k);
        }
        Map<String, Object> v = mapper.map("RAPPORT_GESTION", p);
        for (String k : List.of("ACTIVITE_EXERCICE_EXPOSE", "ACTIVITE_EVOLUTION_COMMENTAIRE", "PERSPECTIVES_EXPOSE",
                "LIEU_SIGNATURE", "RAPPORT_GESTION_DATE", "EXERCICE_DEBUT", "EXERCICE_PRECEDENT_CLOS_DATE")) {
            assertThat(v.get(k)).as(k).isEqualTo("");
        }
    }
}
