package ma.jurika.auth.domain.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Hashe les tokens de verification (email, SMS) pour stockage en DB.
 * <p>
 * On stocke le SHA-256 du token (pas BCrypt) car :
 *   - le token est deja imprevisible (UUID v4 ou random 6 chiffres)
 *   - on a besoin de lookup rapide en DB (BCrypt scan lineaire serait inacceptable)
 *   - le token a une TTL courte (24h email, 5min SMS)
 * <p>
 * En cas de fuite DB, l'attaquant ne peut pas inverser le hash (SHA-256 du UUID v4
 * a 128 bits d'entropie). Pour les codes SMS 6 chiffres, on rate-limite les tentatives.
 */
@Component
public class VerificationTokenHasher {

    private final SecureRandom random = new SecureRandom();

    public String hash(String rawToken) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    /**
     * Genere un token UUID v4 (cas email).
     */
    public String generateUuidToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * Genere un code numerique aleatoire de N chiffres (cas SMS OTP).
     * <p>
     * Ex: pour digits=6 → "042195". Le zero de tete est preserve (padding).
     */
    public String generateNumericCode(int digits) {
        if (digits < 4 || digits > 10) {
            throw new IllegalArgumentException("digits doit etre entre 4 et 10");
        }
        int max = (int) Math.pow(10, digits);
        int value = random.nextInt(max);
        return String.format("%0" + digits + "d", value);
    }
}
