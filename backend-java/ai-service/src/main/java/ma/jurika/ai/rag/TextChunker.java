package ma.jurika.ai.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Decoupe un texte source en passages ("chunks") indexables par le RAG.
 *
 * <p>Strategie deterministe et sans dependance externe :
 * <ol>
 *   <li>normalisation des fins de ligne + suppression des lignes vides multiples ;</li>
 *   <li>regroupement des paragraphes consecutifs jusqu'a atteindre {@code maxChars} ;</li>
 *   <li>un paragraphe plus long que {@code maxChars} est coupe sur des frontieres de
 *       phrase (point/;/saut de ligne) puis, en dernier recours, en tranches dures.</li>
 * </ol>
 *
 * <p>Deux garde-fous ajoutes le 2026-08-29, apres qu'une reponse du chatbot se soit
 * revelee tronquee en pleine clause :
 * <ul>
 *   <li>une frontiere de passage est forcee devant chaque en-tete juridique
 *       (« ARTICLE 7 », « TITRE II »…), pour qu'un article ne soit pas coupe en son
 *       milieu quand le texte arrive d'un PDF en un seul bloc ;</li>
 *   <li>chaque passage reprend en tete la fin du precedent
 *       ({@value #DEFAULT_OVERLAP_RATIO} de sa taille), pour qu'une clause a cheval
 *       sur deux passages reste retrouvable entiere.</li>
 * </ul>
 */
@Component
public class TextChunker {

    /** Taille cible d'un chunk (caracteres). En-dessous on fusionne, au-dessus on coupe. */
    public static final int DEFAULT_MAX_CHARS = 1200;
    /** Un chunk plus court que ce seuil est fusionne avec le suivant si possible. */
    public static final int DEFAULT_MIN_CHARS = 200;
    /** Part du passage precedent reprise en tete du suivant (15 % = ~180 car. a 1200). */
    public static final float DEFAULT_OVERLAP_RATIO = 0.15f;

    /**
     * En-tetes juridiques devant lesquels on force une frontiere de passage.
     *
     * <p>Volontairement limite aux formes MAJUSCULES suivies d'un numero : « ARTICLE 7 »,
     * « TITRE II », « CHAPITRE 3 ». Une reference en minuscules dans le corps du texte
     * (« conformement a l'article 56 de la loi ») ne doit surtout pas declencher de
     * coupure.
     */
    private static final Pattern EN_TETE_JURIDIQUE = Pattern.compile(
            "(?<!\\n)\\s*((?:ARTICLE|ART\\.|TITRE|CHAPITRE|SECTION|PREAMBULE)"
                    + "\\s+(?:[0-9]+|[IVXLC]+|PREMIER|PRELIMINAIRE)\\b)");

    public List<String> chunk(String text) {
        return chunk(text, DEFAULT_MAX_CHARS, DEFAULT_MIN_CHARS);
    }

    public List<String> chunk(String text, int maxChars, int minChars) {
        return chunk(text, maxChars, minChars, Math.round(maxChars * DEFAULT_OVERLAP_RATIO));
    }

    /**
     * Variante avec recouvrement explicite. Le recouvrement est ajouté APRÈS le
     * découpage : chaque passage (sauf le premier) est préfixé par la fin du
     * précédent, coupée sur une frontière de mot.
     *
     * @param overlapChars nombre de caractères repris du passage précédent ; 0 pour
     *                     l'ancien comportement sans chevauchement.
     */
    public List<String> chunk(String text, int maxChars, int minChars, int overlapChars) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) return out;

        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        // Un texte extrait d'un PDF arrive en un seul bloc, sans ligne vide : les
        // titres d'articles sont alors noyés au milieu du flux et le découpage
        // tombait en plein milieu d'une phrase. On force donc une frontière de
        // paragraphe devant chaque en-tête juridique.
        normalized = EN_TETE_JURIDIQUE.matcher(normalized).replaceAll("\n\n$1");
        String[] paragraphs = normalized.split("\n\\s*\n");

        StringBuilder current = new StringBuilder();
        for (String rawParagraph : paragraphs) {
            String paragraph = rawParagraph.strip();
            if (paragraph.isEmpty()) continue;

            // Un paragraphe trop long est decoupe a part (sans le fusionner).
            if (paragraph.length() > maxChars) {
                flush(out, current);
                for (String piece : splitLongParagraph(paragraph, maxChars)) {
                    out.add(piece.strip());
                }
                continue;
            }

            if (current.length() + paragraph.length() + 1 > maxChars) {
                flush(out, current);
            }
            if (current.length() > 0) current.append("\n\n");
            current.append(paragraph);

            if (current.length() >= minChars) {
                // Un chunk "plein" : on le ferme pour eviter d'accumuler indefiniment.
                if (current.length() >= maxChars - minChars) {
                    flush(out, current);
                }
            }
        }
        flush(out, current);
        return overlapChars > 0 ? avecRecouvrement(out, overlapChars) : out;
    }

    /**
     * Reprend la fin de chaque passage au début du suivant.
     *
     * <p>Sans cela, une clause à cheval sur deux passages n'est jamais retrouvée
     * entière : le modèle répond en signalant lui-même que son contexte est tronqué
     * (« la phrase est tronquée dans le contexte fourni »). Le recouvrement est fixé
     * à {@value #DEFAULT_OVERLAP_RATIO} de la taille du passage, soit ~180 caractères
     * pour un passage de 1200 — l'ordre de grandeur d'une à deux phrases de prose
     * juridique française, assez pour rattacher un début de clause à sa suite sans
     * gonfler le corpus de plus de 15 %.
     */
    private List<String> avecRecouvrement(List<String> chunks, int overlapChars) {
        if (chunks.size() < 2) return chunks;
        List<String> out = new ArrayList<>(chunks.size());
        out.add(chunks.get(0));
        for (int i = 1; i < chunks.size(); i++) {
            String queue = queueSurFrontiereDeMot(chunks.get(i - 1), overlapChars);
            out.add(queue.isEmpty() ? chunks.get(i) : queue + " […] " + chunks.get(i));
        }
        return out;
    }

    /** Derniers {@code n} caracteres d'un passage, recadres sur un debut de mot. */
    private String queueSurFrontiereDeMot(String texte, int n) {
        if (texte == null || texte.isBlank() || n <= 0) return "";
        if (texte.length() <= n) return texte.strip();
        String queue = texte.substring(texte.length() - n);
        int espace = queue.indexOf(' ');
        return espace >= 0 ? queue.substring(espace + 1).strip() : queue.strip();
    }

    private void flush(List<String> out, StringBuilder current) {
        if (current.length() > 0) {
            out.add(current.toString().strip());
            current.setLength(0);
        }
    }

    /** Coupe un paragraphe surdimensionne sur des frontieres de phrase puis en dur. */
    private List<String> splitLongParagraph(String paragraph, int maxChars) {
        List<String> pieces = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        // Frontieres de phrase : point, point-virgule, deux-points, saut de ligne.
        String[] sentences = paragraph.split("(?<=[.;:])\\s+|\\n");
        for (String sentence : sentences) {
            String s = sentence.strip();
            if (s.isEmpty()) continue;
            if (s.length() > maxChars) {
                if (buf.length() > 0) { pieces.add(buf.toString()); buf.setLength(0); }
                // Tranches dures pour une phrase aberrante (texte sans ponctuation).
                for (int i = 0; i < s.length(); i += maxChars) {
                    pieces.add(s.substring(i, Math.min(s.length(), i + maxChars)));
                }
                continue;
            }
            if (buf.length() + s.length() + 1 > maxChars) {
                pieces.add(buf.toString());
                buf.setLength(0);
            }
            if (buf.length() > 0) buf.append(' ');
            buf.append(s);
        }
        if (buf.length() > 0) pieces.add(buf.toString());
        return pieces;
    }
}
