package ma.jurika.dataroom.application;

import java.text.Normalizer;

/**
 * Sprint 2026-06-23 (Prompt G) — convention de renommage UNIQUE des documents
 * importés / déposés dans la Data Room. Garantit qu'une Data Room a la même
 * architecture quel que soit le dossier.
 *
 * <p>Format :
 * <ul>
 *   <li>JURIDIQUE : {@code <DOCUMENT_TYPE>__<DENOMINATION_SLUG>__<YYYY-MM-DD|YYYY>.<ext>}</li>
 *   <li>COMPTABLE : {@code <ANNEE>/<CATEGORIE_COMPTABLE>__<DENOMINATION_SLUG>.<ext>}</li>
 *   <li>FISCAL    : {@code <ANNEE>/<CATEGORIE_FISCALE>__<DENOMINATION_SLUG>.<ext>}</li>
 * </ul>
 *
 * <p>{@code DENOMINATION_SLUG} = dénomination société normalisée :
 * MAJUSCULES, sans accents ni espaces (→ underscores), uniquement
 * {@code [A-Z0-9_]}. Multiples underscores compactés. Trimés au début/fin.
 *
 * <p>L'extension d'origine est conservée. La déduplication ({@code _2}, {@code _3} …)
 * est de la responsabilité de l'appelant : ce helper produit UN nom canonique.
 */
public final class DocumentNamingConvention {

    private DocumentNamingConvention() {
        // utility class
    }

    /** Normalise une dénomination en slug MAJUSCULES sans accents. */
    public static String slugDenomination(String denomination) {
        if (denomination == null) return "SOCIETE";
        String trimmed = denomination.trim();
        if (trimmed.isEmpty()) return "SOCIETE";
        // 1) Décomposer accents (é → e + ́) puis retirer les marques.
        String normalized = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        // 2) Majuscules.
        normalized = normalized.toUpperCase(java.util.Locale.ROOT);
        // 3) Tout caractère hors [A-Z0-9] → underscore. Compact + trim.
        normalized = normalized.replaceAll("[^A-Z0-9]+", "_");
        normalized = normalized.replaceAll("^_+|_+$", "");
        return normalized.isEmpty() ? "SOCIETE" : normalized;
    }

    /**
     * JURIDIQUE — {@code <TYPE>__<SLUG>__<DATE|YEAR>.<ext>}.
     * Si {@code dateOrYear} est null/blank, le suffixe est omis.
     */
    public static String forJuridique(String documentType, String denominationSlug,
                                       String dateOrYear, String extension) {
        String type = sanitizeTokenUpper(documentType, "AUTRE");
        String slug = (denominationSlug == null || denominationSlug.isBlank())
                ? "SOCIETE" : denominationSlug;
        String ext = normalizeExtension(extension);
        StringBuilder sb = new StringBuilder(type).append("__").append(slug);
        if (dateOrYear != null && !dateOrYear.isBlank()) {
            sb.append("__").append(dateOrYear.trim());
        }
        return sb.append('.').append(ext).toString();
    }

    /**
     * COMPTABLE — {@code <ANNEE>/<CATEGORIE>__<SLUG>.<ext>}.
     * {@code annee} obligatoire (≥ 1900). Si null, fallback "0000".
     */
    public static String forComptable(Integer annee, String categorie,
                                       String denominationSlug, String extension) {
        return yearedPath(annee, categorie, denominationSlug, extension);
    }

    /**
     * FISCAL — {@code <ANNEE>/<CATEGORIE>__<SLUG>.<ext>}.
     * Même format que comptable ; la distinction est dans l'arbre racine.
     */
    public static String forFiscal(Integer annee, String categorie,
                                    String denominationSlug, String extension) {
        return yearedPath(annee, categorie, denominationSlug, extension);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static String yearedPath(Integer annee, String categorie,
                                      String denominationSlug, String extension) {
        String year = (annee == null) ? "0000" : String.format(java.util.Locale.ROOT, "%04d", annee);
        String cat = sanitizeTokenUpper(categorie, "AUTRE");
        String slug = (denominationSlug == null || denominationSlug.isBlank())
                ? "SOCIETE" : denominationSlug;
        String ext = normalizeExtension(extension);
        return year + "/" + cat + "__" + slug + "." + ext;
    }

    private static String sanitizeTokenUpper(String s, String fallback) {
        if (s == null || s.isBlank()) return fallback;
        String t = s.trim().toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
        t = t.replaceAll("_+", "_").replaceAll("^_+|_+$", "");
        return t.isEmpty() ? fallback : t;
    }

    private static String normalizeExtension(String extension) {
        if (extension == null) return "bin";
        String e = extension.trim().toLowerCase(java.util.Locale.ROOT);
        if (e.startsWith(".")) e = e.substring(1);
        if (e.isEmpty()) return "bin";
        // Restreindre à alphanumérique (.tar.gz → "gz" en pratique, le caller doit gérer).
        return e.replaceAll("[^a-z0-9]", "");
    }

    /** Extrait l'extension d'un nom de fichier (sans le point), fallback "bin". */
    public static String extensionOf(String filename) {
        if (filename == null) return "bin";
        int dot = filename.lastIndexOf('.');
        if (dot <= 0 || dot >= filename.length() - 1) return "bin";
        return normalizeExtension(filename.substring(dot + 1));
    }
}
