package ma.jurika.auth.infrastructure.security;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L0 (E10d) : le pas de temps calcule pour l'anti-rejeu doit correspondre
 * EXACTEMENT a la fenetre de tolerance de googleauth, qui n'est pas modifiee :
 * un code est reconnu par {@code pasDuCode} si et seulement si la bibliotheque
 * l'autorise, et le pas renvoye est celui ou le code a ete calcule.
 */
class GoogleAuthTotpServiceTest {

    private static final long PAS_MS = 30_000L;
    // Milieu d'un pas : evite toute ambiguite de frontiere dans le test.
    private static final Instant MAINTENANT = Instant.ofEpochMilli(1_760_000_000_000L / PAS_MS * PAS_MS + 15_000L);

    private final GoogleAuthTotpService service =
            new GoogleAuthTotpService(Clock.fixed(MAINTENANT, ZoneOffset.UTC));
    private final GoogleAuthenticator generateur = new GoogleAuthenticator();
    private final String secret = generateur.createCredentials().getKey();

    @Test
    void pas_du_code_coherent_avec_la_bibliotheque_de_moins_trois_a_plus_trois_pas() {
        long pasCourant = MAINTENANT.toEpochMilli() / PAS_MS;
        for (int decalage = -3; decalage <= 3; decalage++) {
            long pas = pasCourant + decalage;
            int code = generateur.getTotpPassword(secret, pas * PAS_MS);
            OptionalLong calcule = service.pasDuCode(secret, code);
            assertThat(calcule.isPresent())
                    .as("decalage %d", decalage)
                    .isEqualTo(service.autoriseParLaBibliotheque(secret, code));
            if (calcule.isPresent() && Math.abs(decalage) <= 1) {
                assertThat(calcule.getAsLong()).as("decalage %d", decalage).isEqualTo(pas);
            }
        }
    }

    @Test
    void fenetre_par_defaut_inchangee_moins_un_a_plus_un_pas() {
        long pasCourant = MAINTENANT.toEpochMilli() / PAS_MS;
        for (int decalage = -1; decalage <= 1; decalage++) {
            int code = generateur.getTotpPassword(secret, (pasCourant + decalage) * PAS_MS);
            assertThat(service.pasDuCode(secret, code)).as("decalage %d", decalage).isPresent();
        }
    }
}
