package ma.jurika.auth.application.essai;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L7 (E9), RG-CTR-10 : conditions de l'essai = piece D de la version 1 du contrat
 * (validee le 14 octobre 2026). Les acceptations deja enregistrees sous la version
 * "D-PROJET-2026-10-12" gardent leur texte : le fichier de cette version reste inchange.
 */
class ConditionsEssaiTest {

    /** Empreinte du texte de l'essai (version D-V1 comme D-PROJET-2026-10-12 : la piece D n'a pas change). */
    private static final String EMPREINTE_D = "f196315b802231b48df943c78e596230e838e377f05b1b0150157440f597fc92";

    private static byte[] lire(String chemin) throws Exception {
        try (InputStream in = new ClassPathResource(chemin).getInputStream()) {
            return in.readAllBytes();
        }
    }

    @Test
    void version_1_validee_et_texte_de_la_piece_d() {
        ConditionsEssai c = new ConditionsEssai();
        assertThat(c.version()).isEqualTo("D-V1").doesNotContain("PROJET");
        assertThat(c.empreinte()).isEqualTo(EMPREINTE_D);
        assertThat(c.texte()).startsWith("Conditions d'utilisation de l'essai gratuit JURIKA\n\n1. L'essai est gratuit")
                .contains("4. L'essai ne doit pas \u00eatre utilis\u00e9 pour traiter de vrais dossiers de clients.",
                        "8. Les articles 9, 13, 14, 15 et 18 des conditions g\u00e9n\u00e9rales et l'accord de traitement "
                                + "des donn\u00e9es s'appliquent \u00e0 l'essai.")
                .doesNotContain("**", "[");
    }

    @Test
    void le_texte_de_la_version_anterieure_reste_disponible_et_inchange() throws Exception {
        byte[] ancien = lire("essai/conditions-essai-D-PROJET-2026-10-12.txt");
        assertThat(ConditionsEssai.sha256(ancien)).isEqualTo(EMPREINTE_D);
        assertThat(new String(ancien, StandardCharsets.UTF_8)).startsWith("Conditions d'utilisation de l'essai gratuit JURIKA");
    }
}
