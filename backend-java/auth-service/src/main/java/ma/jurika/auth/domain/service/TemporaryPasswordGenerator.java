package ma.jurika.auth.domain.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Genere un mot de passe temporaire envoye par email a l'inscription.
 * <p>
 * Garanties :
 *   - au moins 1 majuscule, 1 minuscule, 1 chiffre, 1 caractere special
 *   - longueur configurable (defaut 12)
 *   - aleatoire cryptographique (SecureRandom)
 *   - pas d'ambiguites visuelles (O/0, l/I/1) pour faciliter la lecture dans l'email
 */
@Component
public class TemporaryPasswordGenerator {

    private static final String UPPER   = "ABCDEFGHJKLMNPQRSTUVWXYZ"; // sans I, O
    private static final String LOWER   = "abcdefghjkmnpqrstuvwxyz"; // sans i, l, o
    private static final String DIGIT   = "23456789";                 // sans 0, 1
    private static final String SPECIAL = "!@#$%^&*-_=+?";

    private final SecureRandom random = new SecureRandom();
    private final int length;

    public TemporaryPasswordGenerator(@Value("${jurika.auth.temp-password-length:12}") int length) {
        if (length < 8) throw new IllegalArgumentException("Longueur MDP temp minimum 8");
        this.length = length;
    }

    public String generate() {
        char[] buf = new char[length];
        // Garantir au moins 1 char de chaque categorie
        buf[0] = UPPER.charAt(random.nextInt(UPPER.length()));
        buf[1] = LOWER.charAt(random.nextInt(LOWER.length()));
        buf[2] = DIGIT.charAt(random.nextInt(DIGIT.length()));
        buf[3] = SPECIAL.charAt(random.nextInt(SPECIAL.length()));
        // Remplir le reste depuis l'union
        String all = UPPER + LOWER + DIGIT + SPECIAL;
        for (int i = 4; i < length; i++) {
            buf[i] = all.charAt(random.nextInt(all.length()));
        }
        // Shuffle Fisher-Yates pour ne pas avoir un pattern fixe
        for (int i = length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = buf[i]; buf[i] = buf[j]; buf[j] = tmp;
        }
        return new String(buf);
    }
}
