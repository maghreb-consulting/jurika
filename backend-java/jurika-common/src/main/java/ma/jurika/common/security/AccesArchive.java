package ma.jurika.common.security;

import java.util.List;

/**
 * Lot L7 (E5), RG-TAR-07 et RG-TAR-09 : chemins ouverts au superviseur d'un workspace ARCHIVE.
 *
 * <p>Pendant l'archivage, les acces sont fermes ; le superviseur se connecte seulement pour
 * souscrire de nouveau (facturation, paiement, contrat), consulter ses factures, exporter ses donnees
 * ou demander leur suppression definitive (chemins de l'archive, lot L8). Tout autre chemin est
 * refuse par {@link JwtAuthFilter} (403 {@code WORKSPACE_ARCHIVE}), dans chaque service.
 *
 * <p>Lot L7 (E8), article 4.4 du contrat : un workspace SUSPENDU pour impaye ouvre les memes
 * chemins a son superviseur (payer l'echeance), refus 403 {@code WORKSPACE_SUSPENDU}.
 */
public final class AccesArchive {

    /** Prefixes permis (la session, la facturation et le contrat, l'archive, l'usage du forfait). */
    public static final List<String> PREFIXES_PERMIS = List.of(
            "/api/v1/auth/",
            "/api/v1/billing/",
            "/api/v1/archive/",
            "/api/v1/workspace/usage",
            "/actuator/");

    /**
     * Lot L6b (E2) : chemins EXACTS permis, en plus des prefixes. Les preferences d'affichage du
     * COMPTE (theme, assistant masque) : le superviseur qui se connecte pour payer ou exporter
     * garde son affichage ; aucune donnee du cabinet n'y est lue ni ecrite.
     */
    public static final List<String> CHEMINS_PERMIS = List.of(
            "/api/v1/users/me/preferences");

    private AccesArchive() {}

    /** Le chemin est-il ouvert pendant l'archivage ? */
    public static boolean cheminPermis(String uri) {
        if (uri == null) return false;
        if (CHEMINS_PERMIS.contains(uri)) return true;
        for (String p : PREFIXES_PERMIS) {
            if (uri.startsWith(p)) return true;
        }
        return false;
    }

    /** Corps JSON de la reponse 403 (francais affiche). */
    public static final String CORPS_REFUS = "{\"code\":\"WORKSPACE_ARCHIVE\",\"message\":\"Ce cabinet est archiv\u00e9 : "
            + "son forfait est arr\u00eat\u00e9. Souscrivez de nouveau un forfait pour retrouver l\u2019acc\u00e8s ; rien n\u2019a "
            + "\u00e9t\u00e9 supprim\u00e9.\"}";

    /**
     * Lot L7 (E8), article 4.4 du contrat : corps de la reponse 403 d'un workspace SUSPENDU pour
     * impaye (memes chemins permis que l'archive : le superviseur se connecte pour payer).
     */
    public static final String CORPS_REFUS_SUSPENDU = "{\"code\":\"WORKSPACE_SUSPENDU\",\"message\":\"L\u2019acc\u00e8s "
            + "\u00e0 ce cabinet est suspendu : une \u00e9ch\u00e9ance de l\u2019abonnement n\u2019est pas r\u00e9gl\u00e9e. "
            + "Le superviseur peut se connecter pour la payer ; l\u2019acc\u00e8s est r\u00e9tabli d\u00e8s le paiement "
            + "constat\u00e9. Rien n\u2019a \u00e9t\u00e9 supprim\u00e9.\"}";
}
