package ma.jurika.auth.infrastructure.security;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import ma.jurika.auth.domain.port.TotpService;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.OptionalLong;

@Component
public class GoogleAuthTotpService implements TotpService {

    /**
     * Configuration PAR DEFAUT de googleauth (pas de 30 s, fenetre de 3, soit
     * -1/0/+1 pas) : la tolerance n'est pas modifiee par le lot L0, elle est
     * seulement rendue explicite pour calculer le pas du code accepte.
     */
    private final GoogleAuthenticatorConfig config =
            new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder().build();
    private final GoogleAuthenticator authenticator = new GoogleAuthenticator(config);
    private final Clock clock;

    public GoogleAuthTotpService(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String generateSecret() {
        GoogleAuthenticatorKey key = authenticator.createCredentials();
        return key.getKey();
    }

    /**
     * Reproduit la fenetre de {@code GoogleAuthenticator#checkCode} (googleauth
     * 1.5.0, verifie par javap) : pas de reference {@code t / tailleDuPas},
     * decalages de {@code -(fenetre-1)/2} a {@code fenetre/2}. Renvoie le pas du
     * code reconnu, celui que l'anti-rejeu enregistre.
     */
    @Override
    public OptionalLong pasDuCode(String secret, int code) {
        long tailleDuPas = config.getTimeStepSizeInMillis();
        int fenetre = config.getWindowSize();
        long pasDeReference = clock.millis() / tailleDuPas;
        for (int decalage = -((fenetre - 1) / 2); decalage <= fenetre / 2; decalage++) {
            long pas = pasDeReference + decalage;
            if (authenticator.getTotpPassword(secret, pas * tailleDuPas) == code) {
                return OptionalLong.of(pas);
            }
        }
        return OptionalLong.empty();
    }

    /** Verification de la bibliotheque, a l'heure de l'horloge injectee (tests de coherence). */
    boolean autoriseParLaBibliotheque(String secret, int code) {
        return authenticator.authorize(secret, code, clock.millis());
    }

    @Override
    public String buildOtpAuthUri(String issuer, String accountLabel, String secret) {
        String label = URLEncoder.encode(accountLabel, StandardCharsets.UTF_8);
        String iss = URLEncoder.encode(issuer, StandardCharsets.UTF_8);
        return String.format("otpauth://totp/%s?secret=%s&issuer=%s&algorithm=SHA1&digits=6&period=30",
                label, secret, iss);
    }

    @Override
    public byte[] renderQrCodePng(String otpAuthUri, int sizePx) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            var matrix = writer.encode(otpAuthUri, BarcodeFormat.QR_CODE, sizePx, sizePx);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de generer le QR code TOTP", e);
        }
    }
}
