package ma.jurika.auth.infrastructure.email;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L6 (E5) : aucun gabarit de courriel n'invite a ecrire a une boite @jurika.ma. Le domaine
 * n'est pas deploye avant la mise en production : un utilisateur qui recoit une alerte de
 * securite ecrivait a une adresse inexistante. Le contact vient de ${supportEmail}
 * (JURIKA_SUPPORT_EMAIL, a defaut l'adresse d'expediteur verifiee).
 */
class GabaritsSansDomaineNonDeployeTest {

    private static final Path GABARITS = Path.of("src/main/resources/templates/email");

    @Test
    void aucun_gabarit_ne_cite_une_boite_du_domaine_non_deploye() throws IOException {
        List<String> ecarts = new ArrayList<>();
        try (Stream<Path> s = Files.list(GABARITS)) {
            for (Path p : s.filter(f -> f.toString().endsWith(".html")).sorted().toList()) {
                String html = Files.readString(p, StandardCharsets.UTF_8);
                for (String boite : List.of("support@jurika.ma", "abuse@jurika.ma", "contact@jurika.ma", "noreply@jurika.ma")) {
                    if (html.contains(boite)) ecarts.add(p.getFileName() + " : " + boite);
                }
            }
        }
        assertThat(ecarts).isEmpty();
    }
}
