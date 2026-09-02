package ma.jurika.auth.api.dto;

public record Setup2faResponse(String secret, String otpAuthUri, String qrCodePngBase64) {}
