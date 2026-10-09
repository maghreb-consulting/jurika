package ma.jurika.ticket.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L0, etape E16c (reponse E1 n° 6) : le seeder de demonstration de ticket
 * est INACTIF par defaut et actif seulement en profil {@code dev}. La valeur est
 * lue telle que Spring Boot la resout depuis application.yml (documents de
 * profil compris), sans autre source.
 */
class SeedDemonstrationProfilTest {

    @Configuration
    static class Vide { }

    private static String valeur(String... profils) {
        SpringApplication app = new SpringApplication(Vide.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setAdditionalProfiles(profils);
        try (ConfigurableApplicationContext ctx = app.run()) {
            return ctx.getEnvironment().getProperty("jurika.demo-seed");
        }
    }

    @Test
    void inactif_sans_profil() {
        assertThat(valeur()).isEqualTo("false");
    }

    @Test
    void actif_en_profil_dev() {
        assertThat(valeur("dev")).isEqualTo("true");
    }

    @Test
    void inactif_en_profil_prod() {
        assertThat(valeur("prod")).isEqualTo("false");
    }
}
