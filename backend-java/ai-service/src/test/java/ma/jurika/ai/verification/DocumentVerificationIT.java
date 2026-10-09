package ma.jurika.ai.verification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import ma.jurika.ai.document.DocxTemplateEngine;
import ma.jurika.ai.document.manifest.TemplateManifestLoader;
import ma.jurika.ai.workflow.WorkflowDocumentMapper;
import ma.jurika.ai.workflow.mapper.AnnualReportMapper;
import ma.jurika.ai.workflow.mapper.CreationSarlMapper;
import ma.jurika.ai.workflow.mapper.DissolutionMapper;
import ma.jurika.ai.workflow.mapper.FermetureSuccursaleMapper;
import ma.jurika.ai.workflow.mapper.LiquidationMapper;
import ma.jurika.ai.workflow.mapper.ApprobationComptesMapper;
import ma.jurika.ai.workflow.mapper.ModificationMapper;
import ma.jurika.ai.workflow.mapper.SuccursaleEtrMapper;
import ma.jurika.ai.workflow.mapper.SuccursaleMaMapper;
import org.apache.poi.xwpf.usermodel.IBody;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test L6 de vérification structurelle des documents générés par les 9 mappers.
 *
 * <p>Pour chaque mapper enregistré (9 au total : ApprobationComptes, CreationSarl, Dissolution,
 * Liquidation, Modification, FermetureSuccursale, SuccursaleMa, SuccursaleEtr,
 * AnnualReport), pour chaque templateCode supporté :
 * <ol>
 *   <li>Charge la fixture JSON (workflow-fixtures/&lt;workflow&gt;.json)</li>
 *   <li>mapper.map(templateCode, payload) → Map&lt;String,Object&gt;</li>
 *   <li>DocxTemplateEngine.generate(templateCode, variables) → bytes</li>
 *   <li>Sauvegarde dans target/sample_outputs/&lt;CODE&gt;.docx</li>
 *   <li>Re-parse via XWPFDocument, vérifie absence ${UPPER_VAR} et ▶ NOM résiduels</li>
 *   <li>Émet un rapport JSON dans target/document-verification-report.json</li>
 * </ol>
 *
 * <p>Le test ne fait JAMAIS échouer le build sur des résiduels (WARN).
 * Il ne fail que sur exception levée ou bytes=0 ou .docx illisible.
 */
class DocumentVerificationIT {

    private static final Pattern RESIDUAL_UPPER_VAR =
            Pattern.compile("\\$\\{[A-Z][A-Z0-9_]*\\}");
    private static final Pattern RESIDUAL_BLOCK_MARKER =
            Pattern.compile("\\u25B6\\s*[A-Z]+");

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    // Mappers à instancier (constructeurs sans args sauf ModificationMapper).
    record MapperBinding(String name, WorkflowDocumentMapper mapper, String fixtureFile,
                         boolean fixtureKeyedByTemplate) {}

    List<MapperBinding> buildBindings() {
        return List.of(
                new MapperBinding("ApprobationComptesMapper",
                        new ApprobationComptesMapper(new AnnualReportMapper()),
                        "/workflow-fixtures/approbation_comptes.json", true),
                new MapperBinding("CreationSarlMapper", new CreationSarlMapper(),
                        "/workflow-fixtures/creation_sarl.json", false),
                new MapperBinding("DissolutionMapper", new DissolutionMapper(),
                        "/workflow-fixtures/dissolution.json", false),
                new MapperBinding("LiquidationMapper", new LiquidationMapper(),
                        "/workflow-fixtures/liquidation.json", false),
                new MapperBinding("ModificationMapper", new ModificationMapper(),
                        "/workflow-fixtures/modification.json", true),
                new MapperBinding("FermetureSuccursaleMapper", new FermetureSuccursaleMapper(),
                        "/workflow-fixtures/fermeture_succursale.json", true),
                new MapperBinding("SuccursaleMaMapper", new SuccursaleMaMapper(),
                        "/workflow-fixtures/succursale_ma.json", true),
                new MapperBinding("SuccursaleEtrMapper", new SuccursaleEtrMapper(),
                        "/workflow-fixtures/succursale_etr.json", true),
                new MapperBinding("AnnualReportMapper", new AnnualReportMapper(),
                        "/workflow-fixtures/annual_report.json", true)
        );
    }

    @Test
    void generateSampleOutputsAndVerifyStructure() throws Exception {
        // ── 1. Wire engine + manifest loader manuellement ──
        ObjectMapper jacksonForLoader = new ObjectMapper();
        TemplateManifestLoader loader = new TemplateManifestLoader(jacksonForLoader);
        // load() est @PostConstruct → on l'invoque explicitement (public method).
        invokeLoad(loader);
        DocxTemplateEngine engine = new DocxTemplateEngine(loader);

        // ── 2. Préparer le dossier sample_outputs ──
        Path sampleOutputs = Paths.get("target/sample_outputs");
        Files.createDirectories(sampleOutputs);

        // ── 3. Itérer sur les bindings ──
        List<Map<String, Object>> report = new ArrayList<>();
        List<MapperBinding> bindings = buildBindings();

        for (MapperBinding b : bindings) {
            Map<String, Object> fixtureRoot = loadFixture(b.fixtureFile());
            Set<String> supported = b.mapper().supportedTemplates();
            for (String templateCode : new TreeSet<>(supported)) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("template", templateCode);
                entry.put("mapper", b.name());
                entry.put("workflow", b.mapper().workflowCode());

                try {
                    Map<String, Object> payload;
                    if (b.fixtureKeyedByTemplate()) {
                        Object raw = resoudreFixture(fixtureRoot, templateCode);
                        if (raw instanceof Map<?, ?> m) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> casted = (Map<String, Object>) m;
                            payload = casted;
                        } else {
                            entry.put("ok", false);
                            entry.put("status", "FAIL");
                            entry.put("notes",
                                    "Fixture key " + templateCode + " absente ou non-map dans "
                                            + b.fixtureFile());
                            entry.put("size_bytes", 0);
                            entry.put("residual_uppercase_vars", List.of());
                            entry.put("residual_block_markers", List.of());
                            report.add(entry);
                            continue;
                        }
                    } else {
                        payload = fixtureRoot;
                    }

                    Map<String, Object> variables = b.mapper().map(templateCode, payload);
                    DocxTemplateEngine.DocumentResult result = engine.generate(templateCode, variables);

                    byte[] bytes = result.bytes();
                    if (bytes == null || bytes.length == 0) {
                        entry.put("ok", false);
                        entry.put("status", "FAIL");
                        entry.put("size_bytes", 0);
                        entry.put("residual_uppercase_vars", List.of());
                        entry.put("residual_block_markers", List.of());
                        entry.put("notes", "Engine returned 0 bytes");
                        report.add(entry);
                        continue;
                    }

                    Path outFile = sampleOutputs.resolve(templateCode + ".docx");
                    Files.write(outFile, bytes);

                    // Re-parser le .docx
                    String fullText;
                    try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
                        fullText = extractAllText(doc);
                    }

                    Set<String> residualUpperVars = findAll(RESIDUAL_UPPER_VAR, fullText);
                    Set<String> residualBlocks = findAll(RESIDUAL_BLOCK_MARKER, fullText);

                    boolean templateFound = result.templateFound();
                    boolean hasResiduals = !residualUpperVars.isEmpty() || !residualBlocks.isEmpty();

                    entry.put("ok", true);
                    entry.put("status", hasResiduals ? "WARN" : "OK");
                    entry.put("template_found", templateFound);
                    entry.put("size_bytes", bytes.length);
                    entry.put("residual_uppercase_vars", new ArrayList<>(residualUpperVars));
                    entry.put("residual_block_markers", new ArrayList<>(residualBlocks));
                    entry.put("output_path", outFile.toAbsolutePath().toString());
                    if (!templateFound) {
                        entry.put("notes",
                                "Template introuvable : placeholder docx généré");
                    }
                } catch (Throwable t) {
                    entry.put("ok", false);
                    entry.put("status", "FAIL");
                    entry.put("size_bytes", 0);
                    entry.put("residual_uppercase_vars", List.of());
                    entry.put("residual_block_markers", List.of());
                    entry.put("notes",
                            t.getClass().getSimpleName() + ": " + t.getMessage());
                }
                report.add(entry);
            }
        }

        // ── 4. Compter et écrire le rapport ──
        int total = report.size();
        long ok = report.stream().filter(e -> Boolean.TRUE.equals(e.get("ok"))).count();
        long fail = report.stream().filter(e -> "FAIL".equals(e.get("status"))).count();
        long warn = report.stream().filter(e -> "WARN".equals(e.get("status"))).count();

        Map<String, Object> rootReport = new LinkedHashMap<>();
        rootReport.put("generated_at", java.time.OffsetDateTime.now().toString());
        rootReport.put("total_templates", total);
        rootReport.put("ok_count", ok);
        rootReport.put("warn_count", warn);
        rootReport.put("fail_count", fail);
        rootReport.put("sample_outputs_dir", sampleOutputs.toAbsolutePath().toString());
        rootReport.put("results", report);

        Path reportFile = Paths.get("target/document-verification-report.json");
        Files.createDirectories(reportFile.getParent());
        Files.writeString(reportFile, JSON.writeValueAsString(rootReport));

        System.out.println("=== Document verification report ===");
        System.out.println("Total: " + total + " | OK: " + ok + " | WARN: " + warn + " | FAIL: " + fail);
        System.out.println("Report: " + reportFile.toAbsolutePath());
        System.out.println("Samples: " + sampleOutputs.toAbsolutePath());

        // Échec dur uniquement si exception (FAIL).
        if (fail > 0) {
            StringBuilder sb = new StringBuilder("FAIL count > 0:\n");
            for (Map<String, Object> e : report) {
                if ("FAIL".equals(e.get("status"))) {
                    sb.append("  - ").append(e.get("template"))
                            .append(" (").append(e.get("mapper")).append(") : ")
                            .append(e.get("notes")).append('\n');
                }
            }
            throw new AssertionError(sb.toString());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────

    void invokeLoad(TemplateManifestLoader loader) throws Exception {
        Method m = TemplateManifestLoader.class.getMethod("load");
        m.invoke(loader);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> loadFixture(String classpathRes) throws Exception {
        try (InputStream is = getClass().getResourceAsStream(classpathRes)) {
            if (is == null) {
                throw new IllegalStateException("Fixture introuvable : " + classpathRes);
            }
            return JSON.readValue(is, new TypeReference<Map<String, Object>>() {});
        }
    }

    private String extractAllText(XWPFDocument doc) {
        StringBuilder sb = new StringBuilder();
        for (XWPFParagraph p : doc.getParagraphs()) {
            sb.append(p.getText()).append('\n');
        }
        for (XWPFTable t : doc.getTables()) {
            appendTableText(t, sb);
        }
        for (XWPFHeader h : doc.getHeaderList()) {
            appendBodyText(h, sb);
        }
        for (XWPFFooter f : doc.getFooterList()) {
            appendBodyText(f, sb);
        }
        return sb.toString();
    }

    private void appendBodyText(IBody body, StringBuilder sb) {
        for (XWPFParagraph p : body.getParagraphs()) {
            sb.append(p.getText()).append('\n');
        }
        for (XWPFTable t : body.getTables()) {
            appendTableText(t, sb);
        }
    }

    private void appendTableText(XWPFTable t, StringBuilder sb) {
        for (XWPFTableRow row : t.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph p : cell.getParagraphs()) {
                    sb.append(p.getText()).append('\n');
                }
                for (XWPFTable nested : cell.getTables()) {
                    appendTableText(nested, sb);
                }
            }
        }
    }

    private Set<String> findAll(Pattern pattern, String text) {
        Set<String> out = new TreeSet<>();
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    /**
     * Résout la fixture d'un modèle (2026-08-17).
     *
     * <p><b>Pourquoi ce n'est pas une simple lecture par clé.</b> Cette classe ne
     * s'exécutait pas (surefire ignore les {@code *IT}, aucun failsafe n'était
     * configuré) ; elle a donc dérivé du code pendant que les mappers gagnaient de
     * nouveaux modèles. Neuf entrées manquaient, pour deux raisons distinctes :
     * <ul>
     *   <li>le PV de modification a été <b>renommé</b> ({@code PV_AGE_*} →
     *       {@code PV_MODIFICATION_*}) sans que la fixture suive ;</li>
     *   <li>les <b>annonces légales</b> ont été ajoutées après coup. Or elles n'ont
     *       jamais eu de payload propre <i>par conception</i> : une annonce est
     *       produite à partir des MÊMES données que le PV de la même séance — c'est
     *       la garantie qu'un avis ne peut pas diverger de l'acte qu'il publie, et
     *       les tests de mapper l'exercent déjà ainsi
     *       ({@code mapper.map(PV, payload)} puis {@code mapper.map(ANNONCE, payload)}).</li>
     * </ul>
     *
     * <p>La résolution reflète donc le contrat réel : clé exacte, sinon alias de
     * renommage, sinon le PV frère de la même forme juridique.
     */
    static Object resoudreFixture(Map<String, Object> fixtureRoot, String templateCode) {
        Object exact = fixtureRoot.get(templateCode);
        if (exact instanceof Map<?, ?>) return exact;

        // (a) Renommage historique du PV de modification.
        Object alias = fixtureRoot.get(templateCode.replace("PV_MODIFICATION_", "PV_AGE_"));
        if (alias instanceof Map<?, ?>) return alias;

        // (b) Annonce -> PV frère de la MÊME forme juridique (SARL_AU avant SARL :
        //     « _SARL » est un suffixe de « _SARL_AU », l'ordre du test importe).
        String forme = templateCode.endsWith("_SARL_AU") ? "_SARL_AU"
                : templateCode.endsWith("_SARL") ? "_SARL" : null;
        if (forme != null) {
            for (Map.Entry<String, Object> e : fixtureRoot.entrySet()) {
                String k = e.getKey();
                if (!k.startsWith("PV_") || !(e.getValue() instanceof Map<?, ?>)) continue;
                boolean memeForme = "_SARL_AU".equals(forme)
                        ? k.endsWith("_SARL_AU")
                        : k.endsWith("_SARL");
                if (memeForme) return e.getValue();
            }
        }
        return null;
    }
}
