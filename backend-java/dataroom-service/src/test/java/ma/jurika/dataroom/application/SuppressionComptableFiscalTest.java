package ma.jurika.dataroom.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enonce, test 8 — apres la suppression des dossiers comptable et fiscal :
 * aucun ecran en erreur, aucune reference orpheline.
 *
 * <p><b>Pourquoi un test qui lit les sources.</b> Les cinq points d'appel
 * corriges en partie C (statistiques de stockage de l'admin, quota de depot,
 * suppression d'une Data Room, limites de plan, seed de demo) etaient tous du
 * SQL en chaine de caracteres : ils compilaient parfaitement et n'echouaient
 * qu'a l'execution, sur une table disparue. Aucun test de comportement ne les
 * voyait. Seule une relecture systematique des sources protege contre leur
 * reapparition — c'est exactement ce que fait ce test, et il ne pretend rien
 * verifier d'autre.
 *
 * <p>Le perimetre couvre les DEUX bases de code : un ecran React qui appellerait
 * un endpoint disparu serait tout aussi silencieux a la compilation.
 */
class SuppressionComptableFiscalTest {

    /** Tables supprimees par la migration dataroom V25. */
    private static final List<String> TABLES_SUPPRIMEES = List.of(
            "dataroom_comptable_documents",
            "dataroom_fiscal_documents",
            "dataroom_exercices_fiscaux",
            "dataroom_alertes_echeances");

    /** Endpoints REST disparus avec les deux dossiers. */
    private static final List<String> ENDPOINTS_SUPPRIMES = List.of(
            "/comptable/upload",
            "/comptable/documents",
            "/fiscal/upload",
            "/fiscal/documents",
            "/fiscal/sub-classifications",
            "/exercices",
            "/echeances");

    /** Classes et composants retires. */
    private static final List<String> SYMBOLES_SUPPRIMES = List.of(
            "DataroomComptableService",
            "DataroomFiscalService",
            "ExerciceFiscalService",
            "EcheancesGenerator",
            "AlertesEcheancesScheduler",
            "RetentionPurgeScheduler",
            "AccountantNotifier",
            "DossierComptableTab",
            "DossierFiscalTab",
            "FiscalUploadDrawer",
            "StepSuiviExercices",
            "StepImportFolder");

    /**
     * Racine du depot : le repertoire qui contient a la fois {@code backend-java}
     * et {@code frontend-react}. Recherchee en remontant depuis le repertoire du
     * module, pour ne dependre ni du repertoire de lancement ni de l'IDE.
     */
    private static Path racineDepot() {
        Path p = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && p != null; i++) {
            if (Files.isDirectory(p.resolve("backend-java"))
                    && Files.isDirectory(p.resolve("frontend-react"))) {
                return p;
            }
            p = p.getParent();
        }
        throw new IllegalStateException(
                "Racine du depot introuvable depuis " + System.getProperty("user.dir")
                        + " — ce test doit pouvoir relire les sources pour avoir un sens.");
    }

    /**
     * Fichiers de code a relire. Sont EXCLUS : les migrations (elles decrivent
     * l'histoire du schema, y compris la suppression elle-meme), les artefacts
     * de build, et ce fichier de test qui cite forcement les noms recherches.
     */
    private static List<Path> sources() throws IOException {
        Path racine = racineDepot();
        List<Path> out = new ArrayList<>();
        for (Path base : List.of(racine.resolve("backend-java"), racine.resolve("frontend-react/src"))) {
            if (!Files.isDirectory(base)) continue;
            try (Stream<Path> flux = Files.walk(base)) {
                flux.filter(Files::isRegularFile)
                    .filter(f -> {
                        String n = f.getFileName().toString();
                        return n.endsWith(".java") || n.endsWith(".ts") || n.endsWith(".tsx")
                                || n.endsWith(".sql") || n.endsWith(".yml");
                    })
                    .filter(f -> {
                        String chemin = f.toString().replace('\\', '/');
                        return !chemin.contains("/target/")
                                && !chemin.contains("/node_modules/")
                                && !chemin.contains("/db/migration/")
                                && !chemin.endsWith("SuppressionComptableFiscalTest.java");
                    })
                    .forEach(out::add);
            }
        }
        assertThat(out).as("aucune source relue : le test serait vide de sens").isNotEmpty();
        return out;
    }

    /** Occurrences d'un motif, rendues avec fichier et ligne pour etre corrigeables. */
    private static List<String> chercher(List<String> motifs) throws IOException {
        Path racine = racineDepot();
        List<String> trouvailles = new ArrayList<>();
        for (Path f : sources()) {
            List<String> lignes;
            try {
                lignes = Files.readAllLines(f);
            } catch (IOException | RuntimeException ex) {
                continue; // binaire ou encodage exotique : sans interet ici
            }
            for (int i = 0; i < lignes.size(); i++) {
                String ligne = lignes.get(i);
                for (String motif : motifs) {
                    if (ligne.contains(motif)) {
                        trouvailles.add(racine.relativize(f) + ":" + (i + 1) + " — " + motif);
                    }
                }
            }
        }
        return trouvailles;
    }

    @Test
    @DisplayName("Aucune source n'interroge plus les quatre tables supprimees")
    void aucuneReferenceAuxTablesSupprimees() throws IOException {
        assertThat(chercher(TABLES_SUPPRIMEES))
                .as("ces requetes compilent mais echouent a l'execution : "
                        + "elles doivent etre repointees, pas laissees en place")
                .isEmpty();
    }

    @Test
    @DisplayName("Aucun appel ne vise plus les endpoints comptables ou fiscaux")
    void aucunAppelAuxEndpointsSupprimes() throws IOException {
        assertThat(chercher(ENDPOINTS_SUPPRIMES))
                .as("un ecran appelant un endpoint disparu echoue silencieusement a l'execution")
                .isEmpty();
    }

    @Test
    @DisplayName("Aucune classe ni composant supprime n'est encore reference")
    void aucunSymboleOrphelin() throws IOException {
        assertThat(chercher(SYMBOLES_SUPPRIMES)).isEmpty();
    }

    @Test
    @DisplayName("Les cinq points d'appel corriges en partie C ne sont pas revenus")
    void lesCinqPointsDAppelRestentCorriges() throws IOException {
        // Chacun de ces fichiers interrogeait une table supprimee. On verifie
        // nommement qu'il ne le fait plus : une regression y serait invisible
        // autrement, chaque requete etant une simple chaine de caracteres.
        List<String> fichiers = List.of(
                "auth-service/src/main/java/ma/jurika/auth/api/AdminWorkspaceController.java",
                "dataroom-service/src/main/java/ma/jurika/dataroom/application/DataroomDepotService.java",
                "dataroom-service/src/main/java/ma/jurika/dataroom/application/DeleteDataroomUseCase.java",
                "jurika-common/src/main/java/ma/jurika/common/billing/PlanLimitsService.java",
                "ticket-service/src/main/java/ma/jurika/ticket/infrastructure/DemoDataSeeder.java");

        Path backend = racineDepot().resolve("backend-java");
        for (String rel : fichiers) {
            Path f = backend.resolve(rel);
            assertThat(f).as("fichier attendu : %s", rel).exists();
            String contenu = Files.readString(f);
            for (String table : TABLES_SUPPRIMEES) {
                assertThat(contenu)
                        .as("%s interroge de nouveau %s", rel, table)
                        .doesNotContain(table);
            }
        }
    }
}
