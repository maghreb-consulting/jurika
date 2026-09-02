package ma.jurika.ai.infrastructure.ocr;

import ma.jurika.ai.domain.ocr.OcrDocumentType;
import ma.jurika.ai.domain.ocr.OcrExtractionResult;
import ma.jurika.ai.domain.ocr.OcrService;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adapter OCR Tesseract via Tess4J (JNA wrapper).
 * <p>
 * Activation conditionnelle stricte : ne se déploie que si {@code jurika.ocr.provider=tesseract}
 * est positionné dans la configuration. Marqué {@link Primary} pour primer sur le fallback
 * lorsque les deux beans sont présents.
 * <p>
 * Pré-requis runtime :
 * <ul>
 *   <li>Binaire Tesseract installé sur l'hôte (apt-get install tesseract-ocr).</li>
 *   <li>Packs de langue {@code fra}, {@code ara} (apt-get install tesseract-ocr-fra tesseract-ocr-ara).</li>
 *   <li>Variable d'environnement {@code TESSDATA_PREFIX} OU propriété {@code jurika.ocr.tesseract.datapath} pointant vers le dossier {@code tessdata/}.</li>
 * </ul>
 * En cas d'échec à l'invocation (Tesseract indisponible, image illisible, parsing insuffisant),
 * retourne {@link OcrExtractionResult#manualFallback(String)} — jamais d'exception remontée au caller.
 */
@Service
@Primary
@ConditionalOnProperty(name = "jurika.ocr.provider", havingValue = "tesseract")
public class TesseractOcrService implements OcrService {

    private static final Logger log = LoggerFactory.getLogger(TesseractOcrService.class);

    /** Pattern CIN marocaine : 1-2 lettres MAJUSCULES + 4-7 chiffres (ex : AB123456, K987654). */
    private static final Pattern CIN_PATTERN = Pattern.compile("\\b([A-Z]{1,2}\\d{4,7})\\b");

    /** Pattern date : jj.mm.aaaa | jj/mm/aaaa | jj-mm-aaaa | jj mm aaaa. */
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "\\b(\\d{1,2})[./\\- ](\\d{1,2})[./\\- ](\\d{2,4})\\b"
    );

    /** Pattern ICE marocain : 15 chiffres consécutifs. */
    private static final Pattern ICE_PATTERN = Pattern.compile("\\b(\\d{15})\\b");

    /** Nombre de champs attendus par défaut pour une CIN (pour calcul de confiance). */
    private static final int EXPECTED_CIN_FIELDS = 5; // NOM, PRENOM, CIN, DATE_NAISSANCE, NATIONALITE

    private final String datapath;
    private final String languages;

    public TesseractOcrService(
            @Value("${jurika.ocr.tesseract.datapath:}") String datapath,
            @Value("${jurika.ocr.tesseract.languages:fra+ara+eng}") String languages
    ) {
        // Fallback sur variable d'environnement TESSDATA_PREFIX si la property n'est pas définie
        String resolvedDatapath = (datapath == null || datapath.isBlank()) ? System.getenv("TESSDATA_PREFIX") : datapath;
        this.datapath = resolvedDatapath == null ? "" : resolvedDatapath;
        this.languages = languages;
        log.info("TesseractOcrService initialisé : datapath='{}', languages='{}'", this.datapath, this.languages);
    }

    @Override
    public OcrExtractionResult extract(byte[] image, String filename, OcrDocumentType type) {
        log.info("OCR Tesseract — début extraction filename='{}' type={} bytes={}",
                filename, type, image == null ? 0 : image.length);

        if (image == null || image.length == 0) {
            log.warn("OCR Tesseract — input vide, fallback manuel");
            return OcrExtractionResult.manualFallback("Document vide ou non transmis");
        }

        if (datapath == null || datapath.isBlank()) {
            log.warn("OCR Tesseract — datapath non configuré (TESSDATA_PREFIX absent), fallback manuel");
            return OcrExtractionResult.manualFallback(
                    "OCR Tesseract non configuré (TESSDATA_PREFIX absent) — saisie manuelle requise"
            );
        }

        // Fix 2026-06-04 — supporter les PDF (Certificat Negatif OMPIC) :
        //  1) Tenter d'extraire la couche texte via PDFBox (PDF numerique).
        //  2) Si vide / illisible, rendre la 1ere page en BufferedImage 300 dpi et OCR Tesseract.
        // Detection robuste via magic bytes `%PDF-` (resiste a un mauvais filename).
        boolean isPdf = looksLikePdf(image, filename);

        String rawText = null;
        String extractionMode = null;
        if (isPdf) {
            log.info("OCR Tesseract — PDF detecte filename='{}', tentative couche texte PDFBox", filename);
            String textLayer = extractPdfTextLayer(image);
            if (textLayer != null && textLayer.trim().length() >= MIN_PDF_TEXT_LAYER_CHARS) {
                rawText = textLayer;
                extractionMode = "PDFBOX_TEXT";
                log.info("OCR Tesseract — PDF texte natif extrait ({} chars), bypass Tesseract", textLayer.length());
            } else {
                log.info("OCR Tesseract — PDF sans couche texte exploitable ({} chars), rendu image + OCR",
                        textLayer == null ? 0 : textLayer.length());
                BufferedImage rendered = renderFirstPagePdfToImage(image);
                if (rendered == null) {
                    return OcrExtractionResult.manualFallback(
                            "PDF illisible (ni couche texte ni rendu image) — saisie manuelle requise");
                }
                rawText = runTesseractSafely(rendered);
                extractionMode = "PDFBOX_RENDER_TESSERACT";
                if (rawText == null) {
                    return OcrExtractionResult.manualFallback(
                            "Echec OCR sur PDF rendu — saisie manuelle requise");
                }
            }
        } else {
            // Chemin image classique (PNG/JPEG/TIFF).
            BufferedImage img;
            try {
                img = ImageIO.read(new ByteArrayInputStream(image));
            } catch (IOException e) {
                log.warn("OCR Tesseract — lecture image impossible : {}", e.getMessage());
                return OcrExtractionResult.manualFallback("Format d'image non supporté : " + e.getMessage());
            }
            if (img == null) {
                log.warn("OCR Tesseract — ImageIO.read=null (format inconnu, ni image ni PDF)");
                return OcrExtractionResult.manualFallback(
                        "Format non supporté (ni image ni PDF) — saisie manuelle requise"
                );
            }
            rawText = runTesseractSafely(img);
            extractionMode = "TESSERACT_IMAGE";
            if (rawText == null) {
                return OcrExtractionResult.manualFallback("Echec OCR Tesseract sur image");
            }
        }

        if (rawText.isBlank()) {
            log.warn("OCR Tesseract — texte vide après extraction (mode={})", extractionMode);
            return OcrExtractionResult.manualFallback("Aucun texte détecté — saisie manuelle requise");
        }

        Map<String, String> fields = new HashMap<>();
        List<String> warnings = new ArrayList<>();
        // Le Certificat Negatif a son propre parseur (cles lowercase alignees contrat front).
        if (type == OcrDocumentType.CERTIFICAT_NEGATIF) {
            parseCnFields(rawText, fields, warnings);
        } else {
            parseFields(rawText, type, fields, warnings);
        }

        int expected;
        int minOk;
        if (type == OcrDocumentType.CIN_RECTO || type == OcrDocumentType.CIN_VERSO) {
            expected = EXPECTED_CIN_FIELDS;
            minOk = 3;
        } else if (type == OcrDocumentType.CERTIFICAT_NEGATIF) {
            expected = EXPECTED_CN_FIELDS;
            // CN = on accepte des qu'on a un ICE OU une denomination + 1 autre champ.
            minOk = 2;
        } else {
            expected = 3;
            minOk = 3;
        }
        double confidence = Math.min(1.0d, (double) fields.size() / (double) expected);
        boolean manual = fields.size() < minOk;
        if (manual) {
            warnings.add("Extraction insuffisante (" + fields.size() + " champs détectés sur " + expected + " attendus)");
        }

        log.info("OCR Tesseract — fin : {} champs extraits, mode={}, confidence={}, manual={}",
                fields.size(), extractionMode, confidence, manual);

        return new OcrExtractionResult(
                fields,
                confidence,
                manual,
                warnings,
                rawText,
                OcrExtractionResult.PROVIDER_TESSERACT
        );
    }

    /**
     * Override de la methode legacy {@code extractCertificatNegatif} pour utiliser
     * le type {@link OcrDocumentType#CERTIFICAT_NEGATIF} qui declenche le parseur dedie
     * (cles lowercase : ice / denomination / cnNumero / cnDate / activiteCn / beneficiaire)
     * + le traitement PDF (couche texte ou rendu).
     * <p>
     * Reponse enrichie 2026-06-04 (PARTIE B nav-and-ocr) :
     * <ul>
     *   <li>{@code source} = identifiant fournisseur OCR ("OCR_TESSERACT" / "OCR_MANUAL_FALLBACK")</li>
     *   <li>{@code extractionMode} = chemin technique utilise ("PDFBOX_TEXT" / "PDFBOX_RENDER_TESSERACT"
     *       / "TESSERACT_IMAGE" / "FALLBACK") -- utile pour afficher au user la fiabilite attendue</li>
     *   <li>{@code confidence}, {@code requiresManualEntry}, {@code warnings} : inchanges</li>
     * </ul>
     * Extension LLM (futur) : si {@code OPENAI_API_KEY} est configure, ce point serait
     * l'endroit ideal pour invoquer un Chat completion sur {@code r.rawText()} et
     * fusionner les champs LLM dans le retour. Non cable aujourd'hui (Spring AI absent
     * du runtime) -- fallback deterministe regex est utilise. Cf. doc/v2 RAG roadmap.
     */
    @Override
    public Map<String, Object> extractCertificatNegatif(byte[] pdfBytes) {
        OcrExtractionResult r = extract(pdfBytes, "certificat-negatif.pdf",
                OcrDocumentType.CERTIFICAT_NEGATIF);
        java.util.Map<String, Object> out = new java.util.HashMap<>();
        out.putAll(r.fields());
        out.put("source", "OCR_" + r.provider().toUpperCase(Locale.ROOT).replace('-', '_'));
        out.put("extractionMode", inferExtractionMode(r, pdfBytes));
        out.put("confidence", r.confidence());
        out.put("requiresManualEntry", r.requiresManualEntry());
        out.put("warnings", r.warnings());
        out.put("filenameSize", pdfBytes == null ? 0 : pdfBytes.length);
        return out;
    }

    /**
     * Reconstruit le mode d'extraction effectif a partir du resultat + bytes.
     * (Le mode est trace en log dans {@link #extract} mais pas porte dans
     * {@link OcrExtractionResult} pour ne pas casser le contrat public.)
     */
    private static String inferExtractionMode(OcrExtractionResult r, byte[] bytes) {
        if (OcrExtractionResult.PROVIDER_MANUAL_FALLBACK.equals(r.provider())) {
            return "FALLBACK";
        }
        boolean isPdf = looksLikePdf(bytes, "certificat-negatif.pdf");
        if (!isPdf) return "TESSERACT_IMAGE";
        // Si le rawText est significativement plus long que ce que Tesseract produit
        // sur une page rendue (typiquement < 4000 chars), c'est la couche texte
        // PDFBox qui a parle. Heuristique conservative.
        int len = r.rawText() == null ? 0 : r.rawText().length();
        return len >= 2000 ? "PDFBOX_TEXT" : "PDFBOX_RENDER_TESSERACT";
    }

    // -----------------------------------------------------------------------
    //  PDF helpers (PDFBox 3.x — fourni transitivement par tess4j 5.13).
    // -----------------------------------------------------------------------

    /** Seuil minimum de chars dans la couche texte avant de la considerer exploitable. */
    private static final int MIN_PDF_TEXT_LAYER_CHARS = 60;
    /** DPI pour le rendu de page PDF avant OCR Tesseract — equilibre fidelite / perf. */
    private static final float PDF_RENDER_DPI = 300f;
    /** Champs attendus dans un Certificat Negatif (ICE, denomination, cnNumero, cnDate, activiteCn, beneficiaire). */
    private static final int EXPECTED_CN_FIELDS = 6;

    static boolean looksLikePdf(byte[] data, String filename) {
        if (data != null && data.length >= 4
                && data[0] == 0x25 && data[1] == 0x50 && data[2] == 0x44 && data[3] == 0x46) {
            return true;
        }
        return filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    private String extractPdfTextLayer(byte[] pdfBytes) {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        } catch (IOException | RuntimeException e) {
            log.warn("OCR Tesseract — extraction couche texte PDFBox echec : {}", e.getMessage());
            return null;
        }
    }

    private BufferedImage renderFirstPagePdfToImage(byte[] pdfBytes) {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            if (doc.getNumberOfPages() == 0) return null;
            PDFRenderer renderer = new PDFRenderer(doc);
            return renderer.renderImageWithDPI(0, PDF_RENDER_DPI, ImageType.GRAY);
        } catch (IOException | RuntimeException e) {
            log.warn("OCR Tesseract — rendu page PDF echec : {}", e.getMessage());
            return null;
        }
    }

    private String runTesseractSafely(BufferedImage img) {
        try {
            Tesseract tess = new Tesseract();
            tess.setDatapath(datapath);
            tess.setLanguage(languages);
            return tess.doOCR(img);
        } catch (TesseractException e) {
            log.warn("OCR Tesseract — échec doOCR : {}", e.getMessage());
            return null;
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            log.warn("OCR Tesseract — binaire natif Tesseract introuvable : {}", e.getMessage());
            return null;
        } catch (RuntimeException e) {
            log.warn("OCR Tesseract — erreur inattendue : {}", e.getMessage());
            return null;
        }
    }

    // -----------------------------------------------------------------------
    //  Parsing dedie Certificat Negatif (OMPIC).
    //  Ecrit les cles attendues par frontend-react/.../Step1Denomination.tsx :
    //    ice, denomination, cnNumero, cnDate, activiteCn, beneficiaire.
    //  Best-effort : tout champ non detecte = laisse vide -> saisie manuelle.
    // -----------------------------------------------------------------------

    /** N° CN typique : "CN-2026-12345" ou "N° CN: 12345" ou "Numero : XXXX". */
    private static final Pattern CN_NUMERO_PATTERN = Pattern.compile(
            "(?i)(?:CN[\\s-]*(?:N(?:°|um[ée]ro)?)?[:\\s-]*|N(?:°|um[ée]ro)\\s+(?:du\\s+)?(?:certificat|CN)[:\\s-]*)([A-Z]?\\d{3,}[-/]?\\d*)"
    );
    /** "Dénomination : XXX" / "Raison sociale : XXX" — capture jusqu'a fin de ligne. */
    private static final Pattern DENOMINATION_PATTERN = Pattern.compile(
            "(?i)(?:d[ée]nomination|raison\\s+sociale)\\s*[:\\-]?\\s*([^\\r\\n]{2,120})"
    );
    /** "Bénéficiaire : XXX". */
    private static final Pattern BENEFICIAIRE_PATTERN = Pattern.compile(
            "(?i)b[ée]n[ée]ficiaire\\s*[:\\-]?\\s*([^\\r\\n]{2,120})"
    );
    /** "Activité(s) : XXX". */
    private static final Pattern ACTIVITE_PATTERN = Pattern.compile(
            "(?i)activit[ée]s?\\s*[:\\-]?\\s*([^\\r\\n]{2,200})"
    );

    void parseCnFields(String rawText, Map<String, String> fields, List<String> warnings) {
        // ICE — 15 chiffres consecutifs (RG-C13).
        Matcher iceM = ICE_PATTERN.matcher(rawText);
        if (iceM.find()) {
            fields.put("ice", iceM.group(1));
        } else {
            warnings.add("ICE non detecte — saisie manuelle requise");
        }

        // N° Certificat Negatif.
        Matcher cnNumM = CN_NUMERO_PATTERN.matcher(rawText);
        if (cnNumM.find()) {
            String n = cnNumM.group(1).trim();
            if (!n.isEmpty()) fields.put("cnNumero", n);
        }

        // Date — on cherche la 1ere date plausible apres "delivr" / "date" (heuristique simple
        // : on prend la 1ere date trouvee dans le doc, generalement la date de delivrance en haut).
        Matcher dateM = DATE_PATTERN.matcher(rawText);
        if (dateM.find()) {
            try {
                String day = String.format("%02d", Integer.parseInt(dateM.group(1)));
                String month = String.format("%02d", Integer.parseInt(dateM.group(2)));
                String year = dateM.group(3);
                if (year.length() == 2) {
                    int y = Integer.parseInt(year);
                    year = (y > 30 ? "19" : "20") + year;
                }
                fields.put("cnDate", year + "-" + month + "-" + day);
            } catch (NumberFormatException ignored) { /* skip date invalide */ }
        }

        // Denomination / Raison sociale.
        Matcher denM = DENOMINATION_PATTERN.matcher(rawText);
        if (denM.find()) {
            String d = denM.group(1).trim().replaceAll("\\s{2,}", " ");
            if (!d.isEmpty()) fields.put("denomination", d);
        }

        // Beneficiaire.
        Matcher benM = BENEFICIAIRE_PATTERN.matcher(rawText);
        if (benM.find()) {
            String b = benM.group(1).trim().replaceAll("\\s{2,}", " ");
            if (!b.isEmpty()) fields.put("beneficiaire", b);
        }

        // Activite.
        Matcher actM = ACTIVITE_PATTERN.matcher(rawText);
        if (actM.find()) {
            String a = actM.group(1).trim().replaceAll("\\s{2,}", " ");
            if (!a.isEmpty()) fields.put("activiteCn", a);
        }
    }

    /**
     * Parsing heuristique des champs métier à partir du texte brut Tesseract.
     * Implémentation best-effort : régex pour CIN/dates/ICE, lookup mot-clé pour NOM/PRENOM.
     */
    private void parseFields(String rawText, OcrDocumentType type, Map<String, String> fields, List<String> warnings) {
        // CIN
        Matcher cinMatcher = CIN_PATTERN.matcher(rawText);
        if (cinMatcher.find()) {
            fields.put("CIN", cinMatcher.group(1));
        }

        // Date de naissance — première date trouvée
        Matcher dateMatcher = DATE_PATTERN.matcher(rawText);
        if (dateMatcher.find()) {
            String day = String.format("%02d", Integer.parseInt(dateMatcher.group(1)));
            String month = String.format("%02d", Integer.parseInt(dateMatcher.group(2)));
            String year = dateMatcher.group(3);
            if (year.length() == 2) {
                int y = Integer.parseInt(year);
                year = (y > 30 ? "19" : "20") + year;
            }
            fields.put("DATE_NAISSANCE", year + "-" + month + "-" + day);
        } else if (type == OcrDocumentType.CIN_RECTO) {
            warnings.add("DATE_NAISSANCE non détectée — vérifier manuellement");
        }

        // ICE (Certificat Négatif / RC)
        Matcher iceMatcher = ICE_PATTERN.matcher(rawText);
        if (iceMatcher.find()) {
            fields.put("ICE", iceMatcher.group(1));
        }

        // Nationalité — heuristique mot-clé
        String upper = rawText.toUpperCase(Locale.ROOT);
        if (upper.contains("MAROCAIN") || upper.contains("MAROCAINE") || upper.contains("ROYAUME DU MAROC")) {
            fields.put("NATIONALITE", "Marocaine");
        }

        // NOM / PRENOM — lookup après mots-clés (FR)
        String[] lines = rawText.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            String lineUpper = line.toUpperCase(Locale.ROOT);
            if (lineUpper.startsWith("NOM") && i + 1 < lines.length) {
                String candidate = extractAfterKeyword(line, "NOM");
                if (candidate.isBlank() && i + 1 < lines.length) {
                    candidate = lines[i + 1].trim();
                }
                if (!candidate.isBlank()) {
                    fields.put("NOM", candidate.toUpperCase(Locale.ROOT));
                }
            } else if ((lineUpper.startsWith("PRENOM") || lineUpper.startsWith("PRÉNOM")) && i + 1 < lines.length) {
                String candidate = extractAfterKeyword(line, "PRENOM");
                if (candidate.isBlank()) {
                    candidate = extractAfterKeyword(line, "PRÉNOM");
                }
                if (candidate.isBlank() && i + 1 < lines.length) {
                    candidate = lines[i + 1].trim();
                }
                if (!candidate.isBlank()) {
                    fields.put("PRENOM", candidate);
                }
            } else if (lineUpper.startsWith("ADRESSE") || lineUpper.startsWith("ADDRESS")) {
                String candidate = extractAfterKeyword(line, "ADRESSE");
                if (candidate.isBlank()) {
                    candidate = extractAfterKeyword(line, "ADDRESS");
                }
                if (candidate.isBlank() && i + 1 < lines.length) {
                    candidate = lines[i + 1].trim();
                }
                if (!candidate.isBlank()) {
                    fields.put("ADRESSE", candidate);
                }
            }
        }

        // Fallback NOM/PRENOM : lignes en majuscules consécutives sans mot-clé trouvé
        if (!fields.containsKey("NOM") && (type == OcrDocumentType.CIN_RECTO || type == OcrDocumentType.PASSPORT)) {
            Arrays.stream(lines)
                    .map(String::trim)
                    .filter(l -> l.length() >= 3 && l.length() <= 30)
                    .filter(l -> l.matches("[A-Z][A-Z\\- ]{2,}"))
                    .findFirst()
                    .ifPresent(l -> fields.put("NOM", l));
        }
    }

    private String extractAfterKeyword(String line, String keyword) {
        int idx = line.toUpperCase(Locale.ROOT).indexOf(keyword);
        if (idx < 0) {
            return "";
        }
        String rest = line.substring(idx + keyword.length()).trim();
        // Strip ponctuation de séparation : ":", "/", "-"
        while (!rest.isEmpty() && (rest.charAt(0) == ':' || rest.charAt(0) == '/' || rest.charAt(0) == '-')) {
            rest = rest.substring(1).trim();
        }
        return rest;
    }
}
