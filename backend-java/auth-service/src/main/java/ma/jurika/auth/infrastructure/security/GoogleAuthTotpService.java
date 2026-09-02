package ma.jurika.auth.infrastructure.security;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import ma.jurika.auth.domain.port.TotpService;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class GoogleAuthTotpService implements TotpService {

    private final GoogleAuthenticator authenticator = new GoogleAuthenticator();

    @Override
    public String generateSecret() {
        GoogleAuthenticatorKey key = authenticator.createCredentials();
        return key.getKey();
    }

    @Override
    public boolean verifyCode(String secret, int code) {
        return authenticator.authorize(secret, code);
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
