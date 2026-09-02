package ma.jurika.auth.domain.port;

public interface EncryptionService {

    String encrypt(String plaintext);

    String decrypt(String ciphertext);
}
