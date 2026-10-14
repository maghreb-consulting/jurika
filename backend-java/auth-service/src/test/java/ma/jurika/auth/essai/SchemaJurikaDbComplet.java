package ma.jurika.auth.essai;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Lot L7 (E10) : schema COMPLET de la base partagee jurika_db, construit par les
 * VRAIES migrations de tous les services qui y migrent, dans l'ordre reel :
 * auth (proprietaire du schema racine), ticket jusqu'a V20, dataroom (V5 cree
 * dataroom_documents en referencant tickets), fin de ticket (V21 reference
 * dataroom_documents), workflow, ai, dashboard, supervision. billing migre dans
 * une autre base (jurika_billing) : hors de ce schema.
 *
 * <p>Acces aux migrations des autres services : chemins du depot
 * ({@code filesystem:../<service>/src/main/resources/db/migration}), relatifs au
 * dossier du module (repertoire de travail de failsafe, en local comme en CI ou
 * le depot entier est extrait). Meme choix que les IT de dataroom et d'ai ;
 * aucune dependance de test vers les jars des autres services (ils ne sont pas
 * construits avant auth dans le reacteur). Un dossier absent fait ECHOUER le
 * test (jamais un schema partiel pris pour complet).
 *
 * <p>baselineVersion 0, comme les IT existants : la V1 de chaque service
 * s'applique toujours (piege du baseline sur base partagee, CLAUDE.md).
 */
final class SchemaJurikaDbComplet {

    /** Services qui migrent dans jurika_db, dans l'ordre d'application. */
    static final String[] SERVICES = {"auth", "ticket", "dataroom", "workflow", "ai", "dashboard", "supervision"};

    private SchemaJurikaDbComplet() { }

    /** Conteneur pgvector (ai V10 cree l'extension vector), role jurika_app. */
    static PostgreSQLContainer<?> conteneur(String base) {
        return new PostgreSQLContainer<>(
                DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
                .withDatabaseName(base)
                .withUsername("jurika_it")
                .withPassword("jurika_it_pwd")
                .withInitScript("jurika-db-complet-init.sql");
    }

    static void migrer(PostgreSQLContainer<?> pg) {
        passe(pg, "auth", null);
        passe(pg, "ticket", "20");
        passe(pg, "dataroom", null);
        passe(pg, "ticket", null);
        passe(pg, "workflow", null);
        passe(pg, "ai", null);
        passe(pg, "dashboard", null);
        passe(pg, "supervision", null);
    }

    static Path dossierMigrations(String service) {
        Path dossier = Path.of("..", service + "-service", "src", "main", "resources", "db", "migration")
                .toAbsolutePath().normalize();
        if (!Files.isDirectory(dossier)) {
            throw new IllegalStateException("Migrations de " + service + "-service introuvables : " + dossier
                    + " (le test lit les migrations de tous les services de jurika_db dans le depot)");
        }
        return dossier;
    }

    private static void passe(PostgreSQLContainer<?> pg, String service, String cible) {
        var config = Flyway.configure()
                .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                .locations("filesystem:" + dossierMigrations(service))
                .table("flyway_history_" + service)
                .baselineOnMigrate(true).baselineVersion("0");
        if (cible != null) config.target(cible);
        config.load().migrate();
    }

    static JdbcTemplate proprietaire(PostgreSQLContainer<?> pg) {
        return new JdbcTemplate(new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword()));
    }
}
