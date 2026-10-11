package ma.jurika.common.reference;

/**
 * Lot L4d (E6), decision du directeur du 11 octobre 2026 : references lisibles.
 *
 * <ul>
 *   <li>dossier : annee, numero, societe -- {@code 2026-0007 \u2014 PARACOSME} ; le numero est
 *       attribue par cabinet et par annee (ticket V38) ;</li>
 *   <li>document : type, societe, version -- {@code Statuts \u2014 PARACOSME \u2014 version 2}.</li>
 * </ul>
 * Une donnee absente donne une reference absente ({@code null}), jamais une reference de
 * repli : le document qui l'imprime nomme alors la donnee manquante.
 */
public final class ReferencesLisibles {

    static final String TIRET = " \u2014 ";

    private ReferencesLisibles() {}

    /** Reference du dossier, ou null si son numero ou sa societe manque. */
    public static String dossier(Number annee, Number ordre, String societe) {
        if (annee == null || ordre == null || societe == null || societe.isBlank()) return null;
        return String.format("%d-%04d", annee.intValue(), ordre.intValue()) + TIRET + societe.trim();
    }

    /** Reference d'un document, ou null si son type manque. */
    public static String document(String typeLibelle, String societe, int version) {
        if (typeLibelle == null || typeLibelle.isBlank()) return null;
        StringBuilder sb = new StringBuilder(typeLibelle.trim());
        if (societe != null && !societe.isBlank()) sb.append(TIRET).append(societe.trim());
        return sb.append(TIRET).append("version ").append(version).toString();
    }
}
