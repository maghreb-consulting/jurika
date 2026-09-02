package ma.jurika.auth.domain.port;

import ma.jurika.auth.domain.model.AuthTokens;
import ma.jurika.auth.domain.model.User;

public interface TokenIssuer {

    AuthTokens issue(User user);

    String hashRefreshToken(String rawRefreshToken);

    ParsedRefreshToken parseRefreshToken(String rawRefreshToken);

    record ParsedRefreshToken(String tokenId, String hash) {}
}
