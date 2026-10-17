package ma.jurika.common.courriel;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recette R3 : version texte brut d'un courriel, derivee du HTML rendu (partie text/plain de la
 * multipart/alternative). Lisible sans HTML : un bloc par ligne, liens ecrits en clair
 * ("libelle : adresse"), entites decodees ; ni en-tete HTML, ni styles, ni commentaires, ni
 * images, ni les glyphes du decor (frise de zellige).
 */
public final class TexteBrut {

    /** Entites nommees decodees (celles des gabarits des courriels, et les usuelles). */
    public static final Map<String, String> ENTITES = Map.ofEntries(
            Map.entry("amp", "&"), Map.entry("lt", "<"), Map.entry("gt", ">"), Map.entry("quot", "\""),
            Map.entry("apos", "'"), Map.entry("nbsp", "\u00a0"), Map.entry("thinsp", "\u2009"),
            Map.entry("rsquo", "\u2019"), Map.entry("lsquo", "\u2018"), Map.entry("ldquo", "\u201c"),
            Map.entry("rdquo", "\u201d"), Map.entry("laquo", "\u00ab"), Map.entry("raquo", "\u00bb"),
            Map.entry("mdash", "\u2014"), Map.entry("ndash", "\u2013"), Map.entry("middot", "\u00b7"),
            Map.entry("hellip", "\u2026"), Map.entry("euro", "\u20ac"), Map.entry("bull", "\u2022"),
            Map.entry("eacute", "\u00e9"), Map.entry("Eacute", "\u00c9"), Map.entry("egrave", "\u00e8"),
            Map.entry("Egrave", "\u00c8"), Map.entry("ecirc", "\u00ea"), Map.entry("Ecirc", "\u00ca"),
            Map.entry("euml", "\u00eb"), Map.entry("agrave", "\u00e0"), Map.entry("Agrave", "\u00c0"),
            Map.entry("acirc", "\u00e2"), Map.entry("Acirc", "\u00c2"), Map.entry("ccedil", "\u00e7"),
            Map.entry("Ccedil", "\u00c7"), Map.entry("icirc", "\u00ee"), Map.entry("iuml", "\u00ef"),
            Map.entry("ocirc", "\u00f4"), Map.entry("Ocirc", "\u00d4"), Map.entry("oelig", "\u0153"),
            Map.entry("ucirc", "\u00fb"), Map.entry("ugrave", "\u00f9"), Map.entry("uuml", "\u00fc"),
            Map.entry("copy", "\u00a9"), Map.entry("deg", "\u00b0"), Map.entry("times", "\u00d7"));

    /** Glyphes du decor (frise de zellige de la mise en page) : absents du texte brut. */
    private static final String GLYPHES_DECOR = "[\u25c6\u25c7\u2726\u2727]";

    private static final Pattern LIEN = Pattern.compile(
            "<a\\b[^>]*?href\\s*=\\s*\"([^\"]*)\"[^>]*>(.*?)</a\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ENTITE = Pattern.compile("&(#[0-9]+|#[xX][0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]*);");

    private TexteBrut() {}

    public static String depuisHtml(String html) {
        if (html == null) return "";
        String s = html;
        s = s.replaceAll("(?is)<!--.*?-->", "");
        s = s.replaceAll("(?is)<head\\b.*?</head\\s*>", "");
        s = s.replaceAll("(?is)<(style|script|title)\\b.*?</\\1\\s*>", "");
        s = s.replaceAll("(?is)<!DOCTYPE[^>]*>", "");
        s = s.replaceAll("[\\r\\n\\t]+", " ");
        s = remplacerLiens(s);
        s = s.replaceAll("(?i)<img\\b[^>]*>", "");
        s = s.replaceAll("(?i)<br\\s*/?>", "\n");
        s = s.replaceAll("(?i)</(p|div|tr|table|h[1-6]|li|ul|ol)\\s*>", "\n");
        s = s.replaceAll("(?i)<(p|div|table|h[1-6]|ul|ol)\\b[^>]*>", "\n");
        s = s.replaceAll("(?i)<li\\b[^>]*>", "\n- ");
        s = s.replaceAll("(?i)</t[dh]\\s*>", " ");
        s = s.replaceAll("<[^>]+>", "");
        s = decoder(s);
        s = s.replaceAll(GLYPHES_DECOR, "");
        StringBuilder out = new StringBuilder();
        for (String ligne : s.split("\n", -1)) {
            String l = ligne.replaceAll("[ \\t\\x0B\\f]+", " ")
                    .replaceAll("^[\\s\u00a0]+", "").replaceAll("[\\s\u00a0]+$", "");
            out.append(l).append('\n');
        }
        return out.toString().replaceAll("\n{3,}", "\n\n").strip();
    }

    private static String remplacerLiens(String s) {
        Matcher m = LIEN.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String href = m.group(1).trim();
            String libelle = m.group(2).replaceAll("<[^>]+>", "").trim();
            String libelleDecode = decoder(libelle).trim();
            String hrefDecode = decoder(href);
            String rendu;
            if (href.isEmpty() || href.regionMatches(true, 0, "mailto:", 0, 7) || hrefDecode.equals(libelleDecode)) {
                rendu = libelle;
            } else if (libelleDecode.isEmpty()) {
                rendu = href;
            } else {
                rendu = libelle + " : " + href;
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(rendu));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    static String decoder(String s) {
        Matcher m = ENTITE.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String nom = m.group(1);
            String r;
            if (nom.startsWith("#x") || nom.startsWith("#X")) {
                r = new String(Character.toChars(Integer.parseInt(nom.substring(2), 16)));
            } else if (nom.startsWith("#")) {
                r = new String(Character.toChars(Integer.parseInt(nom.substring(1))));
            } else {
                r = ENTITES.getOrDefault(nom, m.group(0));
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(r));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
