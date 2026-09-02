package ma.jurika.auth.domain.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Genere des codes de recuperation 2FA :
 * <ul>
 *     <li>10 codes par defaut</li>
 *     <li>Format : 4 groupes de 4 caracteres alphanumeriques, ex: {@code A7K2-9XQM-3FBL-7TPN}</li>
 *     <li>Aleatoire cryptographique (SecureRandom)</li>
 *     <li>Pas d'ambiguites visuelles (ni 0/O, ni 1/I/l)</li>
 * </ul>
 */
@Component
public class RecoveryCodeGenerator {

    private static final String CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // 31 chars
    private static final int GROUP_SIZE = 4;
    private static final int GROUPS = 4;

    private final SecureRandom random = new SecureRandom();

    public String generateOne() {
        StringBuilder sb = new StringBuilder(GROUPS * (GROUP_SIZE + 1) - 1);
        for (int g = 0; g < GROUPS; g++) {
            if (g > 0) sb.append('-');
            for (int i = 0; i < GROUP_SIZE; i++) {
                sb.append(CHARSET.charAt(random.nextInt(CHARSET.length())));
            }
        }
        return sb.toString();
    }
}
