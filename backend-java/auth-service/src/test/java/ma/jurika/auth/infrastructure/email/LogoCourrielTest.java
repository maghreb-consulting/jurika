package ma.jurika.auth.infrastructure.email;

import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L6 (E6) : l'embleme embarque dans les courriels (cid:jurika-logo) est
 * l'embleme Moucharabieh, variante ivoire, a un poids raisonnable pour un
 * courriel. Le fichier vient de jurika-common (branding/jurika-email-logo.png).
 */
class LogoCourrielTest {

    @Test
    void l_embleme_des_courriels_est_un_png_carre_de_128_pixels_et_leger() throws Exception {
        byte[] octets;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("branding/jurika-email-logo.png")) {
            assertThat(in).as("ressource du logo des courriels").isNotNull();
            octets = new DataInputStream(in).readAllBytes();
        }
        assertThat(octets.length).as("poids du logo (octets)").isBetween(1_000, 30_000);
        assertThat(octets[1]).isEqualTo((byte) 'P');
        assertThat(octets[2]).isEqualTo((byte) 'N');
        assertThat(octets[3]).isEqualTo((byte) 'G');
        ByteBuffer ihdr = ByteBuffer.wrap(octets, 16, 8);
        assertThat(ihdr.getInt()).as("largeur").isEqualTo(128);
        assertThat(ihdr.getInt()).as("hauteur").isEqualTo(128);
    }
}
