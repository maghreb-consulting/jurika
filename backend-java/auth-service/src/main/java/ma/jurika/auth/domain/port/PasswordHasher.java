package ma.jurika.auth.domain.port;

public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String hashed);
}
