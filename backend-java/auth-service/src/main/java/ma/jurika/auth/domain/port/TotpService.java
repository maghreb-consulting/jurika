package ma.jurika.auth.domain.port;

public interface TotpService {

    String generateSecret();

    boolean verifyCode(String secret, int code);

    String buildOtpAuthUri(String issuer, String accountLabel, String secret);

    byte[] renderQrCodePng(String otpAuthUri, int sizePx);
}
