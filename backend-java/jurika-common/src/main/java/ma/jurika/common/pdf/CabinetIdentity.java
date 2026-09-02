package ma.jurika.common.pdf;

/**
 * Identité du cabinet pour le papier à en-tête des PDF (2026-07-14). Regroupe le
 * nom résolu, le logo et les coordonnées/mentions légales. Chaque champ est
 * optionnel SAUF {@code name} (toujours présent grâce au repli).
 *
 * <p>La résolution (valeurs + replis) est faite UNE SEULE FOIS via
 * {@link #resolve} ; les PDF ne construisent jamais leur en-tête eux-mêmes, ils
 * transmettent cet objet au thème.
 */
public record CabinetIdentity(
        String name,
        byte[] logo,
        String logoContentType,
        String adresse,
        String telephone,
        String email,
        String siteWeb,
        String ice,
        String rc,
        String iff) {

    /**
     * Construit une identité en résolvant le nom (nom affiché -> dénomination ->
     * « Cabinet ») et en normalisant les champs vides à null.
     */
    public static CabinetIdentity resolve(String nomAffiche, String workspaceName,
                                          byte[] logo, String logoContentType,
                                          String adresse, String telephone, String email,
                                          String siteWeb, String ice, String rc, String iff) {
        return new CabinetIdentity(
                JurikaPdfTheme.resolveCabinetName(nomAffiche, workspaceName),
                (logo != null && logo.length > 0) ? logo : null,
                blank(logoContentType),
                blank(adresse), blank(telephone), blank(email), blank(siteWeb),
                blank(ice), blank(rc), blank(iff));
    }

    /** Identité minimale (nom résolu seul), pour les appels sans coordonnées. */
    public static CabinetIdentity ofName(String nomAffiche, String workspaceName) {
        return resolve(nomAffiche, workspaceName, null, null, null, null, null, null, null, null, null);
    }

    public boolean hasLogo() {
        return logo != null && logo.length > 0;
    }

    private static String blank(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
