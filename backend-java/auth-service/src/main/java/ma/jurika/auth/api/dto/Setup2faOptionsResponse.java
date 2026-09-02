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
 * @param smsAvailable  {@code true} ssi l'utilisateur a un {@code user.phone} non
 *                      vide en base. Si {@code false}, le bouton SMS doit etre
 *                      masque dans l'UI.
 * @param maskedPhone   numero masque (ex: "+212 ****1234") affichable a cote du
 *                      bouton SMS. {@code null} si {@code !smsAvailable}.
 */
public record Setup2faOptionsResponse(
        boolean totpAvailable,
        boolean smsAvailable,
        String maskedPhone
) {}
