package ma.jurika.ai.workflow.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ma.jurika.ai.document.GenerationRefuseeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L4d (E11b) : choix du modele du corpus des documents de seance partages et refus
 * (SARL AU, donnee de condition absente), sans corpus ni moteur.
 */
class SeanceDocumentMapperTest {

    private static final ObjectMapper OM = new ObjectMapper();
    private final SeanceDocumentMapper mapper = new SeanceDocumentMapper();

    static Map<String, Object> fixture(String code) {
        try (InputStream in = SeanceDocumentMapperTest.class.getResourceAsStream("/workflow-fixtures/seance.json")) {
            Map<String, Object> all = OM.readValue(in, new TypeReference<Map<String, Object>>() {});
            // Copie profonde : chaque test modifie la sienne.
            return OM.readValue(OM.writeValueAsBytes(all.get(code)), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> sous(Map<String, Object> p, String cle) {
        return (Map<String, Object>) p.get(cle);
    }

    private static Map<String, Object> avecType(String code, String type) {
        Map<String, Object> p = fixture(code);
        sous(p, "seance").put("type", type);
        return p;
    }

    @Test
    @DisplayName("convocation : ordinaire -> AGO, extraordinaire et mixte -> AGE, seconde -> seconde convocation")
    void choix_du_modele_de_convocation() {
        assertThat(mapper.gabarit("CONVOCATION_AG", avecType("CONVOCATION_AG", "ordinaire")))
                .isEqualTo("CONVOCATION_AGO_SARL");
        assertThat(mapper.gabarit("CONVOCATION_AG", avecType("CONVOCATION_AG", "extraordinaire")))
                .isEqualTo("CONVOCATION_AGE_SARL");
        assertThat(mapper.gabarit("CONVOCATION_AG", avecType("CONVOCATION_AG", "mixte")))
                .isEqualTo("CONVOCATION_AGE_SARL");
        for (String type : List.of("ordinaire", "extraordinaire", "mixte")) {
            Map<String, Object> p = avecType("CONVOCATION_AG", type);
            sous(p, "convocation").put("rang", "deuxi\u00e8me");
            assertThat(mapper.gabarit("CONVOCATION_AG", p)).isEqualTo("SECONDE_CONVOCATION_ASSEMBLEE_SARL");
        }
    }

    @Test
    @DisplayName("feuille de presence : ordinaire -> AGO, extraordinaire et mixte -> AGE")
    void choix_du_modele_de_feuille() {
        assertThat(mapper.gabarit("FEUILLE_PRESENCE_AG", avecType("FEUILLE_PRESENCE_AG", "ordinaire")))
                .isEqualTo("FEUILLE_PRESENCE_AGO_SARL");
        assertThat(mapper.gabarit("FEUILLE_PRESENCE_AG", avecType("FEUILLE_PRESENCE_AG", "extraordinaire")))
                .isEqualTo("FEUILLE_PRESENCE_AGE_SARL");
        assertThat(mapper.gabarit("FEUILLE_PRESENCE_AG", avecType("FEUILLE_PRESENCE_AG", "mixte")))
                .isEqualTo("FEUILLE_PRESENCE_AGE_SARL");
    }

    @Test
    @DisplayName("SARL AU : ni convocation ni feuille de presence (article 76), refus motive")
    void sarl_au_refusee() {
        for (String code : List.of("CONVOCATION_AG", "FEUILLE_PRESENCE_AG")) {
            Map<String, Object> p = fixture(code);
            sous(p, "societe").put("formeJuridique", "SARL_AU");
            assertThatThrownBy(() -> mapper.map(code, p))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("associ\u00e9 unique")
                    .hasMessageContaining("article 76");
            assertThat(mapper.gabarit(code, p)).isEqualTo(code);
        }
    }

    @Test
    @DisplayName("type d'assemblee non indique : jamais suppose, la donnee est nommee")
    void type_absent_nomme() {
        Map<String, Object> p = fixture("CONVOCATION_AG");
        sous(p, "seance").remove("type");
        assertThatThrownBy(() -> mapper.map("CONVOCATION_AG", p))
                .isInstanceOf(GenerationRefuseeException.class)
                .satisfies(e -> assertThat(((GenerationRefuseeException) e).donnees())
                        .extracting(GenerationRefuseeException.DonneeManquante::variable)
                        .containsExactly("ASSEMBLEE_TYPE"));
    }

    @Test
    @DisplayName("une condition du modele sans valeur (oui / non) est nommee, pas supposee")
    void condition_absente_nommee() {
        Map<String, Object> p = fixture("CONVOCATION_AG");
        sous(p, "convocation").remove("pouvoirJoint");
        sous(p, "comptes").remove("cacDesigne");
        sous(p, "convocation").remove("auteurType");
        assertThatThrownBy(() -> mapper.map("CONVOCATION_AG", p))
                .isInstanceOf(GenerationRefuseeException.class)
                .satisfies(e -> assertThat(((GenerationRefuseeException) e).donnees())
                        .extracting(GenerationRefuseeException.DonneeManquante::variable)
                        .contains("CONVOCATION_POUVOIR_JOINT", "CAC_DESIGNE", "CONVOCATION_AUTEUR_TYPE"));
    }

    @Test
    @DisplayName("feuille : presence inconnue nommee (jamais supposee)")
    void presence_inconnue_nommee() {
        Map<String, Object> p = fixture("FEUILLE_PRESENCE_AG");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> associes = (List<Map<String, Object>>) p.get("associes");
        associes.get(1).put("presence", "");
        assertThatThrownBy(() -> mapper.map("FEUILLE_PRESENCE_AG", p))
                .isInstanceOf(GenerationRefuseeException.class)
                .hasMessageContaining("ALAOUI");
    }

    @Test
    @DisplayName("calculs : valeur nominale, quotites, date limite des six mois, majorites")
    @SuppressWarnings("unchecked")
    void valeurs_calculees() {
        Map<String, Object> p = fixture("FEUILLE_PRESENCE_AG");
        sous(p, "societe").remove("valeurNominalePart");
        Map<String, Object> v = mapper.map("FEUILLE_PRESENCE_AG", p);
        assertThat(v.get("VALEUR_NOMINALE_PART")).isEqualTo("100");
        List<Map<String, Object>> rows = (List<Map<String, Object>>) v.get("ASSOCIES");
        assertThat(rows).extracting(r -> r.get("ASSOCIE_PARTS_QUOTITE")).containsExactly("60\u00a0%", "40\u00a0%");
        assertThat(v.get("PARTS_PRESENTES_CHIFFRES")).isEqualTo("600");
        assertThat(v.get("PARTS_PRESENTES_QUOTITE")).isEqualTo("60\u00a0%");
        assertThat(v.get("ASSOCIES_TOTAL_NOMBRE")).isEqualTo("2");
        assertThat(v.get("ASSOCIES_PRESENTS_NOMBRE")).isEqualTo("1");
        assertThat(v.get("ASSOCIES_REPRESENTES_NOMBRE")).isEqualTo("0");
        assertThat(v.get("MAJORITE_MOITIE_ATTEINTE")).isEqualTo("oui");
        assertThat(v.get("QUORUM_ATTEINT")).isEqualTo("non");

        Map<String, Object> c = mapper.map("CONVOCATION_AG", fixture("CONVOCATION_AG"));
        assertThat(c.get("ASSEMBLEE_DELAI_SIX_MOIS_DATE_LIMITE")).isEqualTo("30/06/2026");
        assertThat(c.get("FORME_JURIDIQUE")).isEqualTo("SARL");
        assertThat(((List<Map<String, Object>>) c.get("DOCUMENTS_COMMUNIQUES")))
                .extracting(d -> d.get("DOCUMENT_COMMUNIQUE_LIBELLE"))
                .containsExactly("le tableau d'affectation du r\u00e9sultat propos\u00e9");
    }

    @Test
    @DisplayName("valeur nominale non entiere ou capital indivisible : rien d'invente")
    void valeur_nominale_indivisible() {
        Map<String, Object> p = fixture("FEUILLE_PRESENCE_AG");
        sous(p, "societe").remove("valeurNominalePart");
        sous(p, "societe").put("nombreParts", 3);
        assertThat(mapper.map("FEUILLE_PRESENCE_AG", p).get("VALEUR_NOMINALE_PART")).isEqualTo("");
    }
}
