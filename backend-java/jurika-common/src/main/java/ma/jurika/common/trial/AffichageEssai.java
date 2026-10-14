package ma.jurika.common.trial;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Decision du 2026-10-14 (RG-ESS-05) : pendant l'essai, seules les reponses
 * d'AFFICHAGE parviennent au navigateur. Une image produite par le serveur
 * pour l'affichage (code QR de la double authentification, logo du cabinet
 * reencode par le serveur) se declare ici, par un attribut de la REQUETE :
 * il est pose par le code du serveur, jamais par le client (un en-tete serait
 * falsifiable), et il ne vaut que pour une image ({@code image/png},
 * {@code image/jpeg}), jamais pour un PDF, un document Office ou une archive.
 */
public final class AffichageEssai {

    /** Attribut de requete lu par {@link TrialSoftLockFilter}. */
    public static final String ATTRIBUT = AffichageEssai.class.getName() + ".IMAGE_SERVEUR";

    private AffichageEssai() {}

    /** Declare que la reponse de cette requete est une image d'affichage produite par le serveur. */
    public static void declarer(HttpServletRequest request) {
        if (request != null) request.setAttribute(ATTRIBUT, Boolean.TRUE);
    }

    static boolean estDeclaree(HttpServletRequest request) {
        return request != null && Boolean.TRUE.equals(request.getAttribute(ATTRIBUT));
    }
}
