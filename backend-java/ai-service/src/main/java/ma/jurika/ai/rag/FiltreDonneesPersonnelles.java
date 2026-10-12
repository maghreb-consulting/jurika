package ma.jurika.ai.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * RG-COM-03 / RG-COM-04 (lot L5 E8) : avant tout envoi au modele, la question est analysee ;
 * si elle contient une donnee personnelle reconnaissable, l'envoi est bloque et l'employe est
 * invite a reformuler.
 *
 * <p>Les motifs sont volontairement precis (une question juridique normale ne doit pas etre
 * bloquee : "article 86", "loi 5-96", "10 000 DH", "2026"). Les memes motifs sont recopies
 * cote ecran ({@code frontend-react/src/lib/donneesPersonnelles.ts}) et les deux cotes sont
 * eprouves par le meme jeu de cas : {@code tests/chatbot/cas-donnees-personnelles.json}.
 * Toute modification se fait des deux cotes.
 *
 * <p>Le resultat ne contient que la NATURE des donnees detectees, jamais leur valeur : rien de
 * la question n'est renvoye ni journalise.
 */
public final class FiltreDonneesPersonnelles {

    /** Nature de donnee personnelle detectee, avec son libelle affiche a l'utilisateur. */
    public enum Nature {
        CIN("numéro de carte nationale (CIN)"),
        ICE("ICE"),
        RC("numéro de registre du commerce (RC)"),
        IDENTIFIANT_FISCAL("identifiant fiscal (IF)"),
        TELEPHONE("numéro de téléphone"),
        EMAIL("adresse électronique"),
        RIB("RIB"),
        DATE_NAISSANCE("date de naissance");

        private final String libelle;

        Nature(String libelle) {
            this.libelle = libelle;
        }

        public String libelle() {
            return libelle;
        }
    }

    private record Motif(Nature nature, Pattern pattern) {}

    private static final String MARQUE_NUMERO = "(?:n\u00b0|no\\.?|num[e\u00e9]ro)?\\s*:?\\s*";
    private static final String MOIS =
            "(?:janvier|f[e\u00e9]vrier|mars|avril|mai|juin|juillet|ao[u\u00fb]t|septembre|octobre|novembre|d[e\u00e9]cembre)";
    private static final int I = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private static final List<Motif> MOTIFS = List.of(
            // CIN : 1 ou 2 lettres capitales collees a 5 ou 6 chiffres (AB123456), ou toute
            // forme precedee de la mention CIN / carte nationale.
            new Motif(Nature.CIN, Pattern.compile("(?<![\\p{L}\\d])[A-Z]{1,2}\\d{5,6}(?!\\d)")),
            new Motif(Nature.CIN, Pattern.compile(
                    "(?<!\\p{L})(?:CIN|C\\.I\\.N\\.?|carte\\s+nationale(?:\\s+d'identit[e\u00e9])?)\\s*"
                            + MARQUE_NUMERO + "[A-Z]{1,2}\\s?\\d{3,6}(?!\\d)", I)),
            // ICE : 15 chiffres d'un seul tenant, ou chiffres groupes apres la mention ICE.
            new Motif(Nature.ICE, Pattern.compile("(?<!\\d)\\d{15}(?!\\d)")),
            new Motif(Nature.ICE, Pattern.compile(
                    "(?<!\\p{L})ICE\\s*" + MARQUE_NUMERO + "\\d(?:[\\s.]?\\d){7,14}(?!\\d)", I)),
            // RC : mention RC (en capitales), ville facultative, puis un numero d'au moins 3 chiffres.
            new Motif(Nature.RC, Pattern.compile(
                    "(?<!\\p{L})R\\.?C\\.?\\s*(?:de\\s+)?(?:\\p{Lu}[\\p{L}\\-]+\\s*)?"
                            + MARQUE_NUMERO + "\\d{3,}(?!\\d)")),
            new Motif(Nature.RC, Pattern.compile(
                    "registre\\s+(?:du|de)\\s+commerce\\s*(?:de\\s+\\p{L}[\\p{L}\\-]*\\s*)?"
                            + "(?:n\u00b0|no\\.?|num[e\u00e9]ro)\\s*:?\\s*\\d{3,}(?!\\d)", I)),
            // Identifiant fiscal : mention IF (en capitales) ou "identifiant fiscal", puis 6 a 9 chiffres.
            new Motif(Nature.IDENTIFIANT_FISCAL, Pattern.compile(
                    "(?<!\\p{L})I\\.?F\\.?\\s*" + MARQUE_NUMERO + "\\d{6,9}(?!\\d)")),
            new Motif(Nature.IDENTIFIANT_FISCAL, Pattern.compile(
                    "identifiant\\s+fiscal\\s*" + MARQUE_NUMERO + "\\d{6,9}(?!\\d)", I)),
            // RIB : 24 chiffres (groupes ou non), ou IBAN marocain (MA + 26 chiffres).
            new Motif(Nature.RIB, Pattern.compile("(?<!\\d)\\d(?:[\\s.\\-]?\\d){23}(?!\\d)")),
            new Motif(Nature.RIB, Pattern.compile("(?<!\\p{L})MA\\d{2}(?:\\s?\\d){24}(?!\\d)", I)),
            // Telephone : formats marocains (0 / +212 / 00212 puis 5 a 8 et 8 chiffres) et
            // internationaux (+ ou 00, indicatif, 8 a 15 chiffres au total).
            new Motif(Nature.TELEPHONE, Pattern.compile(
                    "(?<![\\d+])(?:\\+212|00212|0)\\s?(?:\\(0\\)\\s?)?[5-8](?:[\\s.\\-]?\\d){8}(?!\\d)")),
            new Motif(Nature.TELEPHONE, Pattern.compile(
                    "(?<![\\d\\p{L}])(?:\\+|00)[1-9](?:[\\s.\\-]?\\d){7,14}(?!\\d)")),
            // Adresse electronique.
            new Motif(Nature.EMAIL, Pattern.compile(
                    "[\\p{L}0-9._%+\\-]+@[\\p{L}0-9.\\-]+\\.\\p{L}{2,}")),
            // Date de naissance : une date precedee de "ne(e) le", "date de naissance" ou "naissance :".
            new Motif(Nature.DATE_NAISSANCE, Pattern.compile(
                    "(?<!\\p{L})(?:n[e\u00e9]e?(?:\\(e\\))?\\s+le|date\\s+de\\s+naissance\\s*:?|naissance\\s*:)\\s*(?:le\\s+)?"
                            + "(?:\\d{1,2}[/.\\-]\\d{1,2}[/.\\-]\\d{2,4}|\\d{1,2}(?:er)?\\s+" + MOIS + "\\s+\\d{4})", I)));

    private FiltreDonneesPersonnelles() {
    }

    /** Natures detectees dans le texte, dans l'ordre de l'enumeration, sans doublon. */
    public static List<Nature> detecter(String texte) {
        List<Nature> out = new ArrayList<>();
        if (texte == null || texte.isBlank()) return out;
        for (Nature nature : Nature.values()) {
            for (Motif motif : MOTIFS) {
                if (motif.nature() == nature && motif.pattern().matcher(texte).find()) {
                    out.add(nature);
                    break;
                }
            }
        }
        return out;
    }
}
