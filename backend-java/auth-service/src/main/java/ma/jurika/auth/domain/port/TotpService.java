package ma.jurika.auth.domain.port;

import java.util.OptionalLong;

public interface TotpService {

    String generateSecret();

    /**
     * Pas de temps TOTP (temps Unix / taille du pas) auquel correspond le code,
     * dans la fenetre de tolerance du verificateur ; vide si le code n'est pas
     * valide. Lot L0 (E10d) : sert a l'anti-rejeu, le pas accepte etant
     * enregistre de facon atomique (UserRepository#consommerPasTotp).
     */
    OptionalLong pasDuCode(String secret, int code);

    String buildOtpAuthUri(String issuer, String accountLabel, String secret);

    byte[] renderQrCodePng(String otpAuthUri, int sizePx);
}
