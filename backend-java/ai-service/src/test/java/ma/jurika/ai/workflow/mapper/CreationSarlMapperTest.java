package ma.jurika.ai.workflow.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot B — LE MAPPER, RECONSTRUIT SUR LE CORPUS DU 9 SEPTEMBRE.
 *
 * <p>Le lot A l'avait débranché : les 23 gabarits du cabinet emploient
 * 404 variables, dont 253 que la plateforme ne résolvait pas. L'étape de
 * génération affichait une liste vide.
 *
 * <p>Les assertions portent sur des <b>valeurs produites</b>, jamais sur
 * l'absence d'erreur : c'est la leçon des quatre défauts moteur du lot A, tous
 * trouvés par lecture du document et aucun par un compteur.
 */
class CreationSarlMapperTest {

    private final CreationSarlMapper mapper = new CreationSarlMapper();

    // -----------------------------------------------------------------
    //  Un dossier de référence, aussi proche que possible d'un vrai
    // -----------------------------------------------------------------

    private static Map<String, Object> payload() {
        Map<String, Object> societe = new LinkedHashMap<>();
        societe.put("denomination", "PARACOSME");
        societe.put("formeJuridique", "SARL");
        societe.put("siegeSocial", "101 boulevard Zerktouni, Casablanca");
        societe.put("ville", "Casablanca");
        societe.put("capitalChiffres", "100000");
        societe.put("certificatNegatifNumero", "CN2026/114532");
        societe.put("certificatNegatifDate", "02/09/2026");
        societe.put("valeurNominalePart", "100");

        Map<String, Object> premier = new LinkedHashMap<>();
        premier.put("prenom", "Yassine");
        premier.put("nom", "BENANI");
        premier.put("nombreParts", "600");
        premier.put("pieceType", "CIN");
        premier.put("pieceNumero", "BK188421");

        Map<String, Object> second = new LinkedHashMap<>();
        second.put("prenom", "Nour");
        second.put("nom", "ALAMI");
        second.put("nombreParts", "400");
        second.put("pieceType", "CIN");
        second.put("pieceNumero", "BK992014");

        Map<String, Object> gerant = new LinkedHashMap<>();
        gerant.put("prenom", "Yassine");
        gerant.put("nom", "BENANI");
        gerant.put("pieceType", "CIN");
        gerant.put("pieceNumero", "BK188421");

        Map<String, Object> dossier = new LinkedHashMap<>();
        dossier.put("numero", "D-2026-00841");
        dossier.put("dateOuverture", "2026-09-01");
        dossier.put("charge", "Salma IDRISSI");

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("societe", societe);
        p.put("associes", List.of(premier, second));
        p.put("gerants", List.of(gerant));
        p.put("dossier", dossier);
        p.put("valeurNominalePart", "100");
        return p;
    }

    // =================================================================

    @Test
    @DisplayName("Les 23 modèles du corpus sont supportés, et la liste vient du catalogue")
    void vingtTroisModeles() {
        assertThat(mapper.workflowCode()).isEqualTo("CREATION_SARL");
        assertThat(mapper.supportedTemplates())
                .as("le corpus livré le 9 septembre compte 23 gabarits")
                .hasSize(23)
                .contains("STATUTS_SARL", "STATUTS_SARL_AU", "CONTRAT_BAIL",
                        "CONTRAT_DOMICILIATION", "DECLARATION_BENEFICIAIRES_EFFECTIFS",
                        "DECLARATION_CNDP", "LETTRE_RETRAIT_DEPOT", "NOTE_ANNULATION_DOSSIER",
                        "ANNONCE_LEGALE_CONSTITUTION");
    }

    @Test
    @DisplayName("Un modèle inconnu est refusé, et le message dit lesquels sont supportés")
    void modeleInconnuRefuse() {
        assertThatThrownBy(() -> mapper.map("PV_AGO", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PV_AGO")
                .hasMessageContaining("supportés");
    }

    @Test
    @DisplayName("Les variables déjà résolues le restent : aucune régression du lot A")
    void variablesDejaResoluesConservees() {
        Map<String, Object> v = mapper.map("STATUTS_SARL", payload());

        assertThat(v.get("DENOMINATION")).isEqualTo("PARACOSME");
        assertThat(v.get("CERTIFICAT_NEGATIF_NUMERO")).isEqualTo("CN2026/114532");
        assertThat(v).containsKeys("ASSOCIES", "GERANTS", "APPORTS_PAR_ASSOCIE");
    }

    @Test
    @DisplayName("Une saisie du parcours alimente SA variable, sur le document qui la consomme")
    void saisieDuParcoursAlimenteLaVariable() {
        Map<String, Object> p = payload();
        // `$TP_COMMUNE` est l'une des 160 : elle n'existait nulle part avant ce lot.
        p.put("creation", Map.of("tpCommune", "Casablanca-Anfa",
                "effectifPrevisionnel", "4"));

        Map<String, Object> v = mapper.map("DEMANDE_TAXE_PROFESSIONNELLE", p);

        assertThat(v.get("TP_COMMUNE")).isEqualTo("Casablanca-Anfa");
        assertThat(v.get("EFFECTIF_PREVISIONNEL")).isEqualTo("4");
    }

    @Test
    @DisplayName("Une saisie n'alimente QUE les documents qui la consomment")
    void saisieCantonneeAuxDocumentsConsommateurs() {
        Map<String, Object> p = payload();
        p.put("creation", Map.of("tpCommune", "Casablanca-Anfa"));

        // `$TP_COMMUNE` n'est employée que par la demande de taxe professionnelle.
        // La poser sur les statuts n'aurait pas d'effet visible, mais la résolution
        // doit rester honnête : on ne publie pas ce que le modèle ne demande pas.
        Map<String, Object> statuts = mapper.map("STATUTS_SARL", p);

        assertThat(statuts).doesNotContainKey("TP_COMMUNE");
    }

    @Test
    @DisplayName("Une clé vide n'écrit RIEN : le blanc reste un blanc, pas une chaîne vide")
    void cleVideNEcritRien() {
        Map<String, Object> p = payload();
        Map<String, Object> saisies = new LinkedHashMap<>();
        saisies.put("tpCommune", "   ");
        p.put("creation", saisies);

        Map<String, Object> v = mapper.map("DEMANDE_TAXE_PROFESSIONNELLE", p);

        assertThat(v)
                .as("une variable absente disparaît du rendu et remonte au contrôle de "
                        + "complétude ; une chaîne vide, elle, passerait inaperçue")
                .doesNotContainKey("TP_COMMUNE");
    }

    @Test
    @DisplayName("Une boucle du parcours devient une boucle du moteur, numérotée toute seule")
    void boucleNumeroteeAutomatiquement() {
        Map<String, Object> p = payload();
        p.put("creation", Map.of("beneficiairesEffectifs", List.of(
                Map.of("beNom", "BENANI", "bePrenom", "Yassine", "beCritere", "…"),
                Map.of("beNom", "ALAMI", "bePrenom", "Nour"))));

        Map<String, Object> v = mapper.map("DECLARATION_BENEFICIAIRES_EFFECTIFS", p);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) v.get("BENEFICIAIRES_EFFECTIFS");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).get("BE_NOM")).isEqualTo("BENANI");
        assertThat(items.get(0).get("BE_NUMERO"))
                .as("le rang est DÉRIVÉ : le demander reviendrait à faire compter l'employé")
                .isEqualTo("1");
        assertThat(items.get(1).get("BE_NUMERO")).isEqualTo("2");
        assertThat(v.get("RBE_NOMBRE_BENEFICIAIRES"))
                .as("le compte aussi se dérive")
                .isEqualTo("2");
    }

    @Test
    @DisplayName("L'étendue du contrôle d'un bénéficiaire se lit dans la répartition du capital")
    void etendueDuControleDeriveeDesAssocies() {
        Map<String, Object> p = payload();
        p.put("creation", Map.of("beneficiairesEffectifs", List.of(
                Map.of("beNom", "BENANI", "bePrenom", "Yassine"))));

        Map<String, Object> v = mapper.map("DECLARATION_BENEFICIAIRES_EFFECTIFS", p);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) v.get("BENEFICIAIRES_EFFECTIFS");
        assertThat(items.get(0).get("BE_NOMBRE_PARTS"))
                .as("600 parts sur 1000 — lues chez l'associé du même nom")
                .isEqualTo("600");
        assertThat(items.get(0).get("BE_POURCENTAGE")).isEqualTo("60");
    }

    @Test
    @DisplayName("Les souscriptions se déduisent des associés : aucun chiffre n'est ressaisi")
    void souscriptionsDerivees() {
        Map<String, Object> v = mapper.map("ATTESTATION_SOUSCRIPTION_LIBERATION", payload());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> souscriptions =
                (List<Map<String, Object>>) v.get("SOUSCRIPTIONS");
        assertThat(souscriptions).hasSize(2);
        assertThat(souscriptions.get(0).get("SOUSCRIPTEUR_LIBELLE")).isEqualTo("Yassine BENANI");
        assertThat(souscriptions.get(0).get("SOUSCRIPTEUR_MONTANT_SOUSCRIT"))
                .as("600 parts × 100 DH")
                .isEqualTo("60000");
        assertThat(v.get("SOUSCRIPTIONS_TOTAL_SOUSCRIT")).isEqualTo("100000");
        assertThat(v.get("SOUSCRIPTIONS_TOTAL_VERSE")).isEqualTo("100000");
        assertThat(String.valueOf(v.get("SOUSCRIPTIONS_TOTAL_VERSE_LETTRES")))
                .as("le montant en toutes lettres se calcule, il ne se saisit pas")
                .containsIgnoringCase("cent mille");
    }

    @Test
    @DisplayName("L'échéance d'un bail se calcule en années CALENDAIRES, pas en 365 jours")
    void echeanceDuBailCalculee() {
        Map<String, Object> p = payload();
        // 29 février 2028 — une date qui n'existe pas l'année suivante. C'est la
        // même règle qu'au lot 1 pour les mois : l'addition se fait en calendrier,
        // pas en durée fixe. Un calcul en 365 jours donnerait le 28/02/2029 par
        // accident ; en années calendaires, il le donne par construction.
        p.put("creation", Map.of("bailDateEffet", "2028-02-29",
                "bailDuree", "1", "bailLoyerChiffres", "8500"));

        Map<String, Object> v = mapper.map("CONTRAT_BAIL", p);

        assertThat(v.get("BAIL_DATE_FIN")).isEqualTo("28/02/2029");
        assertThat(String.valueOf(v.get("BAIL_LOYER_LETTRES")))
                .as("le montant en toutes lettres se calcule, il ne se saisit pas")
                .containsIgnoringCase("huit mille cinq cent");
    }

    @Test
    @DisplayName("Le numéro de dossier vient du ticket : il n'est jamais redemandé")
    void dossierRepris() {
        Map<String, Object> v = mapper.map("BORDEREAU_REMISE_DOSSIER", payload());

        assertThat(v.get("DOSSIER_NUMERO")).isEqualTo("D-2026-00841");
        assertThat(v.get("DOSSIER_DATE_OUVERTURE")).isEqualTo("01/09/2026");
        assertThat(v.get("DOSSIER_CHARGE")).isEqualTo("Salma IDRISSI");
    }

    @Test
    @DisplayName("Les 55 variables sans source ne reçoivent AUCUNE valeur, pas même vide")
    void variablesSansSourceNonInventees() {
        Map<String, Object> v = mapper.map("CONTRAT_BAIL", payload());

        // Le bailleur est une donnée d'un tiers : aucun écran ne la produit.
        // Lui poser une chaîne vide ferait passer le document pour complet.
        assertThat(v).doesNotContainKeys("BAILLEUR_NOM", "BAILLEUR_ADRESSE",
                "BAILLEUR_DENOMINATION", "BAIL_TITRE_FONCIER");
    }

    @Test
    @DisplayName("$SIEGE_VILLE et $VILLE coexistent : l'arbitrage n'est pas tranché ici")
    void arbitrageDeNommageNonTranche() {
        Map<String, Object> v = mapper.map("DECLARATION_EXISTENCE", payload());

        // Le corpus emploie les DEUX noms, parfois dans le même document. La
        // plateforme ne résout que `$VILLE`. On aligne dans la RÉSOLUTION —
        // jamais dans le .docx — de sorte qu'aucun des deux ne soit préjugé.
        assertThat(v.get("VILLE")).isEqualTo("Casablanca");
        assertThat(v.get("SIEGE_VILLE"))
                .as("alias de résolution, retirable le jour où le cabinet tranche")
                .isEqualTo("Casablanca");
    }

    @Test
    @DisplayName("$ICE est tranché : $ICE_NUMERO n'est jamais produit côté création")
    void iceTranche() {
        Map<String, Object> p = payload();
        @SuppressWarnings("unchecked")
        Map<String, Object> societe = (Map<String, Object>) p.get("societe");
        societe.put("ice", "001234567000089");

        Map<String, Object> v = mapper.map("DECLARATION_EXISTENCE", p);

        assertThat(v).doesNotContainKey("ICE_NUMERO");
    }
}
