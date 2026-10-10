package ma.jurika.workflow.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import ma.jurika.workflow.infrastructure.persistence.DossierVariableEntity;
import ma.jurika.workflow.infrastructure.persistence.DossierVariableJpaRepository;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L4a (E1c) : LA CHAINE ECRAN -> MAGASIN -> CHARGE UTILE de la creation.
 *
 * <p>Avant, chaque maillon etait teste seul : le constructeur contre un magasin "complet du
 * corpus", le mapper contre une charge ecrite a la main. Personne ne confrontait ce que les
 * ECRANS ecrivent a ce que le projecteur lit : des saisies se perdaient (apports par associe,
 * pouvoirs du gerant...) et le projecteur lisait des cles que l'ecran n'ecrit pas (regle 3).
 *
 * <p>Entree : `chaine/saisie-creation-complete.json`, au format exact des ecrans (stepData).
 * Sortie : la charge utile reellement produite, comparee a la fixture de
 * DocumentVerificationIT (ai-service) -- la fixture n'est plus ecrite a la main, elle EST la
 * sortie de la chaine. Regeneration : {@code -Dchaine.regenerer=true}.
 */
class ChaineEcranMagasinCreationTest {

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    static final Path FIXTURE_AI = Path.of("../ai-service/src/test/resources/workflow-fixtures/creation_sarl.json");
    private static final UUID WS = UUID.randomUUID();
    private static final UUID TICKET = UUID.randomUUID();

    /** Un depot en memoire : la vraie logique de MagasinVariables, sans base. */
    static DossierVariableJpaRepository depotEnMemoire() {
        List<DossierVariableEntity> lignes = new ArrayList<>();
        return (DossierVariableJpaRepository) Proxy.newProxyInstance(
                ChaineEcranMagasinCreationTest.class.getClassLoader(),
                new Class<?>[]{DossierVariableJpaRepository.class},
                (proxy, m, a) -> switch (m.getName()) {
                    case "findByWorkspaceIdAndTicketId" -> new ArrayList<>(lignes);
                    case "findSimple" -> lignes.stream().filter(e -> e.getBoucle() == null
                            && e.getVariable().equals(a[2])).findFirst();
                    case "findEnBoucle" -> lignes.stream().filter(e -> a[2].equals(e.getBoucle())
                            && e.getRang() == (short) a[3] && e.getVariable().equals(a[4])).findFirst();
                    case "save" -> {
                        if (!lignes.contains(a[0])) lignes.add((DossierVariableEntity) a[0]);
                        yield a[0];
                    }
                    case "delete" -> lignes.remove(a[0]);
                    case "purgerBoucleAuDela" -> {
                        int avant = lignes.size();
                        lignes.removeIf(e -> a[2].equals(e.getBoucle()) && e.getRang() >= (short) a[3]);
                        yield avant - lignes.size();
                    }
                    case "rattacherAuDossier" -> 0;
                    case "toString" -> "depotEnMemoire";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == a[0];
                    default -> throw new UnsupportedOperationException(m.getName());
                });
    }

    static Map<String, Object> saisie() throws IOException {
        try (var in = ChaineEcranMagasinCreationTest.class.getResourceAsStream("/chaine/saisie-creation-complete.json")) {
            Map<String, Object> s = JSON.readValue(in, new TypeReference<>() { });
            s.remove("_commentaire");
            return s;
        }
    }

    /** La charge utile que le serveur construit pour cette saisie. */
    static Map<String, Object> chargeProduite() throws IOException {
        MagasinVariables magasin = new MagasinVariables(depotEnMemoire());
        new ProjecteurVariablesCreation(magasin).projeter(WS, TICKET, saisie(), UUID.randomUUID());
        // Faits du ticket, tels que FaitsDuTicket les lit en base (ticket ouvert, non annule).
        FaitsDuTicket.Faits faits = new FaitsDuTicket.Faits("D-2026-00841", "2026-09-01", "Salma IDRISSI", null, null);
        return ConstructeurChargeUtileCreation.construire(
                ConstructeurChargeUtileCreation.avecFaitsDuTicket(magasin.lirePourGeneration(WS, TICKET), faits));
    }

    @Test
    void la_charge_produite_est_la_fixture_de_verification_des_documents() throws IOException {
        Map<String, Object> charge = new LinkedHashMap<>();
        charge.put("_commentaire", "GENERE par ChaineEcranMagasinCreationTest (workflow-service) depuis "
                + "chaine/saisie-creation-complete.json : charge utile reelle de la creation. Ne pas editer.");
        charge.putAll(chargeProduite());
        String attendu = JSON.writeValueAsString(charge) + "\n";
        if (Boolean.getBoolean("chaine.regenerer")) {
            Files.writeString(FIXTURE_AI, attendu, StandardCharsets.UTF_8);
        }
        assertThat(Files.readString(FIXTURE_AI, StandardCharsets.UTF_8))
                .as("fixture ai-service desynchronisee : relancer avec -Dchaine.regenerer=true et relire la diff")
                .isEqualTo(attendu);
    }

    @Test
    @SuppressWarnings("unchecked")
    void les_saisies_de_l_ecran_atteignent_la_charge() throws IOException {
        Map<String, Object> charge = chargeProduite();
        List<Map<String, Object>> associes = (List<Map<String, Object>>) charge.get("associes");
        assertThat(associes).hasSize(2);
        // Etape 6 : piece de l'associe (l'ecran ecrit `cin`), montant d'apport par associe.
        assertThat(associes.get(0)).containsEntry("pieceNumero", "BK188421").containsEntry("pieceType", "CIN");
        assertThat(associes.get(0)).containsEntry("apportNumeraire", "60000");
        assertThat(associes.get(1)).containsEntry("apportNature", "40000");
        // Etape 5 : piece du gerant (l'ecran ecrit `cinNumero`).
        List<Map<String, Object>> gerants = (List<Map<String, Object>>) charge.get("gerants");
        assertThat(gerants.get(0)).containsEntry("cin", "BK188421");
        // Etape 3 : depot du capital et commissaire aux apports, saisis et jamais lus avant L4a.
        Map<String, Object> societe = (Map<String, Object>) charge.get("societe");
        assertThat(societe).containsEntry("commissaireApportsNom", "Cabinet AUDIT CONSEIL")
                .containsEntry("compteBancaireNumero", "190 780 2111 2233 4455 6677 01");
        // Etape 5 : pouvoirs de la gerance.
        assertThat(societe).containsEntry("limitationPouvoirs",
                "Les actes de disposition exc\u00e9dant 100 000 dirhams requi\u00e8rent l'accord des associ\u00e9s.");
        // Etape 7 : une saisie du catalogue atteint la charge (bloc creation).
        assertThat((Map<String, Object>) charge.get("creation")).containsEntry("bailleurDenomination", "IMMOBILIERE ANFA")
                .containsEntry("cnProposition1", "PARACOSME");
    }
}
