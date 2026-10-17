package ma.jurika.auth.infrastructure.sms;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recette R2 (2026-10-17) : dernier code SMS SIMULE de la double authentification, par numero,
 * en memoire, pour les parcours de bout en bout qui n'ont pas acces aux journaux (route
 * CodeSmsDeTestController). N'existe qu'en environnement de test : jurika.sms.simulation=true ET
 * hors profil prod (meme double verrou que DisponibiliteSms2fa). Rien n'est ecrit en base ; les
 * codes expires ne sont plus rendus ; la boite est bornee.
 */
@Component
@Profile("!prod")
@ConditionalOnProperty(name = "jurika.sms.simulation", havingValue = "true", matchIfMissing = false)
public class BoiteSmsDeTest {

    private static final int TAILLE_MAX = 1000;

    public record CodeDepose(String telephone, String code, Instant expireLe) {}

    private final Map<String, CodeDepose> parNumero = new ConcurrentHashMap<>();

    public void deposer(String telephone, String code, Instant expireLe) {
        if (parNumero.size() >= TAILLE_MAX) {
            Instant maintenant = Instant.now();
            parNumero.values().removeIf(c -> c.expireLe().isBefore(maintenant));
            if (parNumero.size() >= TAILLE_MAX) parNumero.clear();
        }
        parNumero.put(telephone, new CodeDepose(telephone, code, expireLe));
    }

    public Optional<CodeDepose> dernier(String telephone) {
        CodeDepose c = parNumero.get(telephone);
        if (c == null || c.expireLe().isBefore(Instant.now())) return Optional.empty();
        return Optional.of(c);
    }
}
