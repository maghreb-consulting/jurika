package ma.jurika.ai.document;

import org.apache.poi.xwpf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Outil d'extraction de templates depuis des EXEMPLES de documents juridiques.
 *
 * <p>Strategy :
 * <ol>
 *   <li>Recoit un .docx d'exemple (avec donnees reelles)</li>
 *   <li>Detecte automatiquement les parties variables via patterns heuristiques :
 *       montants MAD, dates JJ/MM/AAAA, numeros ICE (15 chiffres), RC, dates
 *       de naissance, denominations sociales, etc.</li>
 *   <li>Remplace par {@code {{nom_variable}}} dans une copie</li>
 *   <li>Genere un schema JSON avec liste des variables detectees + leur position</li>
 *   <li>Renvoie le template + schema -- l'utilisateur valide manuellement</li>
 * </ol>
 *
 * <p>Le boilerplate juridique (clauses de la Loi 5-96, articles obligatoires)
 * reste intact -- seules les donnees specifiques au dossier sont parametrees.
 */
@Component
public class TemplateExtractor {

    private static final Logger log = LoggerFactory.getLogger(TemplateExtractor.class);

    /** Patterns de detection (ordre = priorite). */
    private static final List<DetectionRule> RULES = List.of(
            new DetectionRule("ice", "\\b\\d{15}\\b", "Numero ICE (15 chiffres)"),
            new DetectionRule("rc_numero", "\\b(?:RC|R\\.?C\\.?)[\\s-:]?[A-Z]{2,}[\\s-]?\\d{4,}\\b", "Numero RC"),
            new DetectionRule("cnss_numero", "\\b\\d{7,10}\\b(?=.*CNSS)", "Numero CNSS"),
            new DetectionRule("if_numero", "\\b\\d{8}\\b(?=.*(?:fiscal|IF))", "Identifiant fiscal"),
            new DetectionRule("cin_numero", "\\b[A-Z]{1,2}\\d{4,8}\\b", "CIN"),
            new DetectionRule("date_jma", "\\b\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}\\b", "Date JJ/MM/AAAA"),
            new DetectionRule("date_text", "\\b\\d{1,2}\\s+(?:janvier|fevrier|mars|avril|mai|juin|juillet|aout|septembre|octobre|novembre|decembre|janv|fevr|mars|avr|juin|juil|sept|oct|nov|dec)\\.?\\s+\\d{4}\\b", "Date texte"),
            new DetectionRule("montant_mad", "\\b\\d{1,3}(?:[\\s.,]?\\d{3})*(?:[.,]\\d{1,2})?\\s*(?:MAD|DH|dirhams?)\\b", "Montant en MAD"),
            new DetectionRule("pourcentage", "\\b\\d{1,3}(?:[.,]\\d+)?\\s*%\\b", "Pourcentage"),
            new DetectionRule("denomination_sarl", "\\b[A-Z][A-Z\\s&'-]{2,}\\s+(?:SARL\\s*AU|SARL|SA|SAS|SCS)\\b", "Denomination + forme"),
            new DetectionRule("telephone_ma", "\\b(?:\\+212|0)\\s?[5-7]\\d{2}[\\s.-]?\\d{2}[\\s.-]?\\d{2}[\\s.-]?\\d{2}\\b", "Telephone marocain"),
            new DetectionRule("email", "\\b[\\w.+-]+@[\\w-]+\\.[\\w.-]+\\b", "Email")
    );

    /**
     * Convertit un exemple .docx en template avec variables {{}}.
     *
     * @param exampleBytes contenu du .docx exemple
     * @return resultat avec template_bytes + schema (variables detectees)
     */
    public ExtractionResult extract(byte[] exampleBytes, String templateName) {
        try (var in = new ByteArrayInputStream(exampleBytes);
             var doc = new XWPFDocument(in);
             var out = new ByteArrayOutputStream()) {

            Map<String, DetectedVariable> variables = new LinkedHashMap<>();

            for (XWPFParagraph p : doc.getParagraphs()) {
                processRuns(p, variables);
            }
            for (XWPFTable t : doc.getTables()) {
                for (XWPFTableRow r : t.getRows()) {
                    for (XWPFTableCell c : r.getTableCells()) {
                        for (XWPFParagraph p : c.getParagraphs()) {
                            processRuns(p, variables);
                        }
                    }
                }
            }

            doc.write(out);

            log.info("Template '{}' : {} variables detectees", templateName, variables.size());

            return new ExtractionResult(
                    out.toByteArray(),
                    new ArrayList<>(variables.values()),
                    templateName,
                    LocalDate.now().toString());
        } catch (Exception ex) {
            throw new RuntimeException("Echec extraction template : " + ex.getMessage(), ex);
        }
    }

    private void processRuns(XWPFParagraph paragraph, Map<String, DetectedVariable> variables) {
        List<XWPFRun> runs = paragraph.getRuns();
        if (runs.isEmpty()) return;

        StringBuilder full = new StringBuilder();
        for (XWPFRun r : runs) {
            String t = r.text();
            if (t != null) full.append(t);
        }
        String original = full.toString();
        if (original.isBlank()) return;

        String transformed = original;
        for (DetectionRule rule : RULES) {
            Pattern p = Pattern.compile(rule.regex);
            Matcher m = p.matcher(transformed);
            StringBuilder sb = new StringBuilder();
            int idx = 0;
            while (m.find()) {
                String match = m.group();
                String key = uniqueKey(rule.name, idx, variables);
                variables.put(key, new DetectedVariable(
                        key, rule.description, match,
                        original.substring(0, Math.min(80, original.length())) + "..."));
                m.appendReplacement(sb, Matcher.quoteReplacement("{{" + key + "}}"));
                idx++;
            }
            m.appendTail(sb);
            transformed = sb.toString();
        }

        if (!transformed.equals(original)) {
            for (int i = runs.size() - 1; i > 0; i--) {
                paragraph.removeRun(i);
            }
            runs.get(0).setText(transformed, 0);
        }
    }

    private String uniqueKey(String base, int idx, Map<String, DetectedVariable> existing) {
        String key = idx == 0 ? base : base + "_" + (idx + 1);
        while (existing.containsKey(key)) {
            idx++;
            key = base + "_" + (idx + 1);
        }
        return key;
    }

    private record DetectionRule(String name, String regex, String description) {}

    public record DetectedVariable(String name, String description,
                                    String exampleValue, String contextSnippet) {}

    public record ExtractionResult(byte[] templateBytes,
                                    List<DetectedVariable> detectedVariables,
                                    String templateName, String extractedAt) {}
}
