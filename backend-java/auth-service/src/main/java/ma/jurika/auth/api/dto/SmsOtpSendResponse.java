package ma.jurika.auth.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Reponse de l'envoi d'un code SMS. Jamais le code lui-meme. Recette R2 : {@code simule} vrai en
 * environnement de test (aucun SMS envoye : code ecrit dans le journal d'auth, SMS-DEV-STUB).
 */
public record SmsOtpSendResponse(UUID otpId, String maskedPhone, Instant expiresAt, boolean simule) {}
