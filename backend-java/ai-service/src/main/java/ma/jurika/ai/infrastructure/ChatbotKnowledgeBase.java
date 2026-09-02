package ma.jurika.ai.infrastructure;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Phase 6 -- Chatbot RAG (Knowledge Base sur le droit marocain des societes).
 *
 * Cette base de connaissances minimale tient lieu de pipeline RAG en attendant
 * l'integration complete Spring AI + pgvector + ingestion corpus juridique
 * (Loi 5-96, Code Commerce, OMPIC, DGI, CNSS).
 *
 * Chaque entry contient :
 *   - keywords : mots-cles a matcher (insensible casse / accents)
 *   - reponse  : texte de la reponse
 *   - sources  : citations precises avec article et reference
 *
 * Quand Spring AI sera branche, ce composant sera remplace par un RetrievalAugmentedChatClient
 * qui interroge pgvector pour les k meilleurs chunks puis appelle le LLM.
 */
@Component
public class ChatbotKnowledgeBase {

    private static final List<Entry> ENTRIES = List.of(
            new Entry(
                    List.of("creer", "creation", "sarl", "constituer"),
                    "Pour creer une SARL au Maroc, vous devez : (1) obtenir un certificat negatif aupres de l'OMPIC, "
                            + "(2) rediger les statuts, (3) liberer au moins 25% du capital social a la souscription, "
                            + "(4) publier une annonce au JAL, (5) immatriculer au Registre de Commerce dans les 30 jours.",
                    List.of(
                            new Source("Loi 5-96 sur les SARL", "Art. 47-49", "Constitution"),
                            new Source("Loi 5-96 sur les SARL", "Art. 51", "Liberation 25% capital"),
                            new Source("Code de Commerce", "Art. 37", "Immatriculation RC")
                    )),

            new Entry(
                    List.of("capital", "minimum", "liberer", "25", "souscription"),
                    "La SARL peut etre constituee avec un capital librement fixe (pas de minimum legal depuis 2006). "
                            + "Cependant, au moins 25% du capital DOIT etre libere a la souscription, "
                            + "et le solde dans un delai maximum de 5 ans.",
                    List.of(
                            new Source("Loi 5-96 sur les SARL", "Art. 51", "Liberation minimale"),
                            new Source("Loi 5-96 sur les SARL", "Art. 46", "Capital social")
                    )),

            new Entry(
                    List.of("dissolution", "dissoudre", "fin"),
                    "La dissolution d'une societe peut etre : volontaire (decision des associes en AGE), "
                            + "judiciaire, ou de plein droit (terme expire). Elle est suivie d'une phase de liquidation "
                            + "menee par un liquidateur. Un PV d'AGE doit etre etabli et la societe doit le mentionner "
                            + "dans tous ses actes (mention 'societe en liquidation').",
                    List.of(
                            new Source("Loi 5-96 sur les SARL", "Art. 89", "Dissolution"),
                            new Source("Code de Commerce", "Art. 217", "Dissolution societes commerciales")
                    )),

            new Entry(
                    List.of("liquidation", "liquidateur", "16 jours", "delai"),
                    "Apres la dissolution, la societe entre en liquidation. Un liquidateur est designe. "
                            + "Le PV d'AGE de dissolution doit etre depose au RC dans un delai legal recommande "
                            + "de 16 jours (non bloquant). Le liquidateur dresse l'inventaire, regle le passif et "
                            + "repartit le solde entre associes. La liquidation est cloturee par un PV d'AGE de cloture.",
                    List.of(
                            new Source("Loi 5-96 sur les SARL", "Art. 90-94", "Liquidation"),
                            new Source("Code de Commerce", "Art. 220", "Operations de liquidation")
                    )),

            new Entry(
                    List.of("modification", "statuts", "transfert", "siege"),
                    "Toute modification des statuts (transfert siege, augmentation capital, changement denomination...) "
                            + "necessite : (1) une decision en AGE prise a la majorite des 3/4 des parts sociales, "
                            + "(2) la modification des statuts, (3) la publication d'une annonce modificative au JAL, "
                            + "(4) l'inscription modificative au RC dans un delai de 1 mois.",
                    List.of(
                            new Source("Loi 5-96 sur les SARL", "Art. 75", "Modifications statutaires"),
                            new Source("Code de Commerce", "Art. 50", "Inscriptions modificatives")
                    )),

            new Entry(
                    List.of("succursale", "etranger", "etrangere", "apostille"),
                    "Une succursale d'une societe etrangere au Maroc requiert : (1) statuts apostilles + traduction "
                            + "certifiee FR/AR, (2) decision du conseil d'administration de la societe mere designant "
                            + "un directeur resident, (3) procuration apostillee, (4) inscription au RC du lieu de la "
                            + "succursale (le tribunal competent est celui du lieu de la succursale, pas celui de la "
                            + "societe mere).",
                    List.of(
                            new Source("Convention de La Haye 1961", "Apostille", "Legalisation documents"),
                            new Source("Code de Commerce", "Art. 37 bis", "Succursales de societes etrangeres")
                    )),

            new Entry(
                    List.of("ago", "assemblee generale", "annuelle", "ordinaire"),
                    "L'Assemblee Generale Ordinaire annuelle (AGO) doit etre tenue dans les 6 mois suivant la cloture "
                            + "de l'exercice. Elle approuve les comptes, decide de l'affectation du resultat (reserve "
                            + "legale, dividendes, report a nouveau) et donne quitus au gerant. Le PV est etabli et "
                            + "depose au greffe.",
                    List.of(
                            new Source("Loi 5-96 sur les SARL", "Art. 70-72", "AGO annuelle"),
                            new Source("Code Commerce", "Art. 19 bis", "Approbation des comptes")
                    ))
    );

    public Answer ask(String question) {
        String normalized = normalize(question);
        Entry best = null;
        int bestScore = 0;
        for (Entry e : ENTRIES) {
            int score = 0;
            for (String kw : e.keywords()) {
                if (normalized.contains(kw)) score++;
            }
            if (score > bestScore) {
                bestScore = score;
                best = e;
            }
        }
        if (best == null) {
            return new Answer(
                    "Je n'ai pas trouve de reponse precise dans mes sources. "
                            + "Reformulez votre question ou contactez un expert juridique du cabinet.",
                    List.of(),
                    0.0);
        }
        double confidence = Math.min(1.0, bestScore / 3.0);
        return new Answer(best.reponse(), best.sources(), confidence);
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT);
    }

    public record Source(String reference, String article, String topic) {
        public Map<String, String> asMap() {
            return Map.of("reference", reference, "article", article, "topic", topic);
        }
    }

    public record Answer(String reponse, List<Source> sources, double confidence) {
        public Map<String, Object> asMap() {
            List<Map<String, String>> sourcesList = new ArrayList<>();
            for (Source s : sources) sourcesList.add(s.asMap());
            return Map.of(
                    "reponse", reponse,
                    "sources", sourcesList,
                    "confidence", confidence);
        }
    }

    private record Entry(List<String> keywords, String reponse, List<Source> sources) {}
}
