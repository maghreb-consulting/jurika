package ma.jurika.common.persistence.rls;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import ma.jurika.common.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L0, etape E10a (G1) : le workspace du {@link TenantContext} doit atteindre
 * la transaction REELLE, connexion en role non superutilisateur (la RLS
 * s'applique). Avant L0, {@code RlsAspect} (supprime) posait {@code set_config(..., true)}
 * AVANT l'ouverture de la transaction (ordre AOP) : le reglage etait perdu et
 * toute lecture renvoyait zero ligne, sans erreur.
 *
 * <p>Exige aussi (reponse E1) qu'une requete sans workspace NE PASSE PAS EN
 * SILENCE : elle doit lever une erreur au lieu de renvoyer zero ligne.
 */
@Testcontainers
@SpringBootTest(
        classes = TenantTransactionIT.TestApp.class,
        properties = {
                "spring.application.name=jurika-common-rls-it",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration,"
                        + "ma.jurika.common.audit.AuditAutoConfiguration,"
                        + "ma.jurika.common.notification.NotificationAutoConfiguration,"
                        + "ma.jurika.common.observability.OpsActuatorSecurityAutoConfiguration,"
                        + "ma.jurika.common.observability.OpsActuatorReactiveSecurityAutoConfiguration,"
                        + "ma.jurika.common.security.JwtAutoConfiguration,"
                        + "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration",
                "spring.jpa.hibernate.ddl-auto=none",
                "spring.jpa.open-in-view=false"
        }
)
class TenantTransactionIT {

    static final UUID WS_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID WS_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_rls_it")
            .withInitScript("rls-it-init.sql");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        // Role d'execution NON superutilisateur : la RLS s'applique.
        registry.add("spring.datasource.username", () -> "rls_app");
        registry.add("spring.datasource.password", () -> "rls_app");
    }

    @Autowired SondeService sonde;
    @Autowired SondeExterne externe;
    @Autowired SondeRepository repository;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void nettoyer() {
        TenantContext.clear();
    }

    @Test
    void transaction_avec_contexte_lit_les_lignes_de_son_workspace() {
        TenantContext.set(WS_A);
        assertThat(sonde.libelles()).containsExactlyInAnyOrder("A1", "A2");
    }

    @Test
    void transaction_requires_new_dans_une_transaction_lit_son_workspace() {
        TenantContext.set(WS_B);
        assertThat(externe.depuisUneTransaction()).containsExactly("B1");
    }

    @Test
    void depot_appele_hors_service_lit_son_workspace() {
        TenantContext.set(WS_A);
        assertThat(repository.findAll()).extracting(Sonde::getLibelle)
                .containsExactlyInAnyOrder("A1", "A2");
    }

    @Test
    void ecriture_dans_son_workspace_passe_et_reste_invisible_aux_autres() {
        TenantContext.set(WS_B);
        sonde.ajouter(UUID.randomUUID(), WS_B, "B2");
        assertThat(sonde.libelles()).containsExactlyInAnyOrder("B1", "B2");
        TenantContext.set(WS_A);
        assertThat(sonde.libelles()).containsExactlyInAnyOrder("A1", "A2");
    }

    @Test
    void requete_hors_transaction_ne_passe_pas_en_silence() {
        TenantContext.set(WS_A);
        assertThatThrownBy(() -> jdbc.queryForList("SELECT libelle FROM rls_probe", String.class))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("invalid input syntax for type uuid")
                .hasMessageContaining("hors-transaction");
    }

    @Test
    void transaction_sans_contexte_ne_passe_pas_en_silence() {
        assertThatThrownBy(() -> sonde.libelles())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("invalid input syntax for type uuid")
                .hasMessageContaining("hors-transaction");
    }

    @Test
    void journal_d_audit_sans_contexte_reste_ecrivable() {
        // Les ecouteurs RabbitMQ d'audit ecrivent sans workspace courant : la
        // politique INSERT WITH CHECK (true) doit continuer a les accepter.
        jdbc.update("INSERT INTO audit_probe (workspace_id, action) VALUES (?, 'AVEC_WS')", WS_A);
        jdbc.update("INSERT INTO audit_probe (workspace_id, action) VALUES (NULL, 'SANS_WS')");
        TenantContext.set(WS_A);
        assertThat(sonde.actionsAudit()).contains("AVEC_WS", "SANS_WS");
    }

    // ------------------------------------------------------------------ app

    @SpringBootApplication
    @EnableAspectJAutoProxy
    @EntityScan(basePackageClasses = Sonde.class)
    @EnableJpaRepositories(basePackageClasses = SondeRepository.class, considerNestedRepositories = true)
    static class TestApp {
        @Bean SondeService sondeService(SondeRepository r, JdbcTemplate j) { return new SondeService(r, j); }
        @Bean SondeExterne sondeExterne(SondeService s) { return new SondeExterne(s); }
    }

    @Entity
    @Table(name = "rls_probe")
    public static class Sonde {
        @Id private UUID id;
        private UUID workspaceId;
        private String libelle;

        protected Sonde() { }

        Sonde(UUID id, UUID workspaceId, String libelle) {
            this.id = id;
            this.workspaceId = workspaceId;
            this.libelle = libelle;
        }

        public String getLibelle() { return libelle; }
    }

    public interface SondeRepository extends JpaRepository<Sonde, UUID> { }

    public static class SondeService {
        private final SondeRepository repository;
        private final JdbcTemplate jdbc;

        SondeService(SondeRepository repository, JdbcTemplate jdbc) {
            this.repository = repository;
            this.jdbc = jdbc;
        }

        @Transactional(readOnly = true)
        public List<String> libelles() {
            return jdbc.queryForList("SELECT libelle FROM rls_probe", String.class);
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
        public List<String> libellesNouvelleTransaction() {
            return jdbc.queryForList("SELECT libelle FROM rls_probe", String.class);
        }

        @Transactional
        public void ajouter(UUID id, UUID workspaceId, String libelle) {
            repository.saveAndFlush(new Sonde(id, workspaceId, libelle));
        }

        @Transactional(readOnly = true)
        public List<String> actionsAudit() {
            return jdbc.queryForList("SELECT action FROM audit_probe", String.class);
        }
    }

    public static class SondeExterne {
        private final SondeService sonde;

        SondeExterne(SondeService sonde) { this.sonde = sonde; }

        @Transactional(readOnly = true)
        public List<String> depuisUneTransaction() {
            return sonde.libellesNouvelleTransaction();
        }
    }
}
