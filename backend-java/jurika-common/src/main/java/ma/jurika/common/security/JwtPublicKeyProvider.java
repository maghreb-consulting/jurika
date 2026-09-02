package ma.jurika.common.security;

import java.security.Key;

public interface JwtPublicKeyProvider {
    Key verificationKey();
}
