package ma.jurika.common.courriel;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Recette R3 : le logo des courriels est le logo Moucharabieh en PNG haute definition (les
 * messageries n'affichent pas le SVG), rendu en 3x de sa taille d'affichage, depuis le SVG source
 * du front par un script de regeneration du depot. Poids raisonnable pour un courriel.
 */
class LogoCourrielHauteDefinitionTest {

    @Test
    void le_logo_est_un_png_3x_de_sa_taille_d_affichage_et_leger() throws Exception {
        byte[] octets;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(CourrielJurika.LOGO_RESSOURCE)) {
            assertThat(in).as("ressource du logo des courriels").isNotNull();
            octets = in.readAllBytes();
        }
        assertThat(new String(octets, 1, 3, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("PNG");
        ByteBuffer ihdr = ByteBuffer.wrap(octets, 16, 8);
        int largeur = ihdr.getInt();
        int hauteur = ihdr.getInt();
        assertThat(largeur).as("largeur").isEqualTo(CourrielJurika.LOGO_COTE * 3);
        assertThat(hauteur).as("hauteur").isEqualTo(CourrielJurika.LOGO_COTE * 3);
        assertThat(octets.length).as("poids (octets)").isBetween(2_000, 60_000);
    }

    @Test
    void un_script_du_depot_regenere_le_logo_depuis_le_svg_source() throws Exception {
        Path script = Path.of("..", "..", "scripts", "courriels", "generer-logo-courriel.mjs");
        assertThat(script).exists();
        String texte = Files.readString(script);
        assertThat(texte).contains("embleme-ivoire.svg").contains(CourrielJurika.LOGO_RESSOURCE.replace("branding/", ""));
        assertThat(Path.of("..", "..", "frontend-react", "src", "assets", "brand", "moucharabieh", "embleme-ivoire.svg")).exists();
    }
}
