package ma.jurika.auth.infrastructure.security;

import ma.jurika.auth.domain.port.EncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuditingEncryptionDecorator implements EncryptionService {

    private static final Logger log = LoggerFactory.getLogger(AuditingEncryptionDecorator.class);

    private final EncryptionService delegate;

    public AuditingEncryptionDecorator(EncryptionService delegate) {
        this.delegate = delegate;
    }

    @Override
    public String encrypt(String plaintext) {
        log.trace("encrypt: {} bytes", plaintext == null ? 0 : plaintext.length());
        return delegate.encrypt(plaintext);
    }

    @Override
    public String decrypt(String ciphertext) {
        log.trace("decrypt: {} bytes", ciphertext == null ? 0 : ciphertext.length());
        return delegate.decrypt(ciphertext);
    }
}
