package ma.jurika.auth.api.dto;

import java.util.List;

/**
 * Reponse a la generation de codes de recuperation 2FA.
 * <p>
 * Les codes en clair ne seront JAMAIS renvoyes a nouveau apres cet appel.
 * Le user doit imperativement les sauvegarder.
 */
public record RecoveryCodesResponse(List<String> codes, String warning) {

    public static RecoveryCodesResponse of(List<String> codes) {
        return new RecoveryCodesResponse(codes,
                "Conservez ces codes en lieu sur. Ils ne seront plus jamais affiches. " +
                "Chaque code est utilisable une seule fois en cas de perte d'acces a votre 2FA.");
    }
}
