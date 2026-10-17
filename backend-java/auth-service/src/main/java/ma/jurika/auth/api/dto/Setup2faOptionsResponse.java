package ma.jurika.auth.api.dto;

/**
 * Reponse de {@code GET /api/v1/auth/2fa/setup-options}.
 *
 * <p>Permet au frontend de savoir, au moment ou l'utilisateur arrive sur la page
 * de choix 2FA (RG-AU30 : choix bloquant au 1er login), quelles methodes lui
 * sont effectivement disponibles. TOTP est toujours dispo (rien a installer cote
 * compte) ; SMS necessite qu'un numero de telephone ait ete renseigne pendant
 * l'inscription.
 *
 * @param totpAvailable toujours {@code true} (rien ne bloque l'install Google
 *                      Authenticator) — garde present pour symetrie + future
 *                      extension (ex: desactivation par admin).
 * @param smsAvailable  recette R2 (2026-10-17) : {@code true} si la plateforme ouvre le SMS
 *                      (fournisseur configure, ou simulation en environnement de test).
 *                      Le numero se saisit a l'activation. Si {@code false}, l'option SMS est
 *                      affichee indisponible et le serveur la refuse.
 * @param smsSimule     {@code true} si le SMS est simule (environnement de test : aucun SMS
 *                      envoye, code ecrit dans le journal d'auth, SMS-DEV-STUB).
 * @param maskedPhone   numero du compte masque (ex: "+212 ****1234"), ou {@code null}.
 * @param methodeActive methode reellement active ("SMS", "TOTP") ou {@code null}.
 */
public record Setup2faOptionsResponse(
        boolean totpAvailable,
        boolean smsAvailable,
        boolean smsSimule,
        String maskedPhone,
        String methodeActive
) {}
