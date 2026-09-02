package ma.jurika.ai.document;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Convertisseur DOCX -> PDF FIDELE via LibreOffice headless (soffice).
 *
 * <p>Sprint 2026-06-19 — Rendu PDF fidele aux styles Word.
 * Avant ce composant, le pipeline transitait par Mammoth.js (DOCX -> HTML)
 * puis iText (HTML -> PDF). Mammoth ignore les styles Word nommes
 * ({@code JurikaTitreArticle}, {@code JurikaSousTitre}) -> tout le PDF
 * sortait en Times New Roman 11pt sans hierarchie.
 *
 * <p>Cette implementation invoque {@code soffice --headless --convert-to pdf}
 * dans un sous-processus isole (timeout configurable, repertoire temporaire
 * dedie, suppression apres conversion). Le PDF resultant respecte 100 % les
 * styles definis dans le gabarit Word (police Calibri, hierarchie, couleurs,
 * marges, variables en rouge si manquantes via le moteur amont).
 *
 * <p><b>Pre-requis :</b> LibreOffice (>= 7.x) installe sur la machine qui
 * heberge le ai-service.
 * <ul>
 *   <li>Windows : <a href="https://www.libreoffice.org/download/">installeur officiel</a>
 *       puis verifier {@code C:\Program Files\LibreOffice\program\soffice.exe}.</li>
 *   <li>Linux serveur : {@code apt install libreoffice --no-install-recommends}.</li>
 *   <li>Override via property {@code jurika.docx-to-pdf.soffice-path}.</li>
 * </ul>
 *
 * <p>Si {@code soffice} n'est pas detecte au demarrage, le converter reste
 * disponible mais {@link #isAvailable()} retourne {@code false} ; le
 * controller REST renvoie alors 503 et le front bascule sur le telechargement
 * du .docx natif (fallback propre, pas d'erreur silencieuse).
 */
@Component
public class DocxToPdfConverter {

    private static final Logger log = LoggerFactory.getLogger(DocxToPdfConverter.class);

    /** Chemins par defaut a sonder si {@code jurika.docx-to-pdf.soffice-path} est vide. */
    private static final List<String> DEFAULT_WINDOWS_PATHS = List.of(
            "C:\\Program Files\\LibreOffice\\program\\soffice.exe",
            "C:\\Program Files (x86)\\LibreOffice\\program\\soffice.exe");

    private static final List<String> DEFAULT_UNIX_PATHS = List.of(
            "/usr/bin/soffice",
            "/usr/bin/libreoffice",
            "/usr/local/bin/soffice",
            "/opt/libreoffice/program/soffice");

    private final String configuredSofficePath;
    private final long timeoutSeconds;
    private final boolean enabled;

    private volatile String resolvedSofficePath; // null si indisponible
    private volatile String detectedVersion;     // pour /actuator/info eventuellement

    public DocxToPdfConverter(
            @Value("${jurika.docx-to-pdf.soffice-path:}") String configuredSofficePath,
            @Value("${jurika.docx-to-pdf.timeout-seconds:90}") long timeoutSeconds,
            @Value("${jurika.docx-to-pdf.enabled:true}") boolean enabled) {
        this.configuredSofficePath = configuredSofficePath == null ? "" : configuredSofficePath.trim();
        this.timeoutSeconds = timeoutSeconds <= 0 ? 90 : timeoutSeconds;
        this.enabled = enabled;
    }

    @PostConstruct
    void detectAtStartup() {
        if (!enabled) {
            log.warn("DocxToPdfConverter : desactive (jurika.docx-to-pdf.enabled=false). PDF fidele indisponible.");
            return;
        }
        this.resolvedSofficePath = resolveSofficePath();
        if (this.resolvedSofficePath == null) {
            log.warn("DocxToPdfConverter : LibreOffice (soffice) introuvable. Le rendu PDF fidele "
                    + "est INDISPONIBLE — installer LibreOffice ou definir jurika.docx-to-pdf.soffice-path. "
                    + "Le front basculera sur le telechargement .docx en attendant.");
        } else {
            this.detectedVersion = readSofficeVersion(resolvedSofficePath);
            log.info("DocxToPdfConverter : LibreOffice detecte = {} (version={})",
                    resolvedSofficePath, detectedVersion);
        }
    }

    public boolean isAvailable() {
        return resolvedSofficePath != null;
    }

    /** Chemin reel du binaire utilise (null si indisponible). Utile pour /actuator/info. */
    public String sofficePath() {
        return resolvedSofficePath;
    }

    public String detectedVersion() {
        return detectedVersion;
    }

    /**
     * Convertit le contenu binaire d'un .docx en PDF via LibreOffice headless.
     *
     * @param docxBytes contenu du .docx (jamais null/vide)
     * @param basename  nom de fichier sans extension (utilise pour le tmp file
     *                  et le nom de sortie ; le PDF resultant est independant)
     * @return contenu binaire du PDF
     * @throws LibreOfficeUnavailableException si soffice non detecte ou desactive
     * @throws DocxToPdfConversionException si l'execution echoue (timeout, code non-0, PDF non produit)
     */
    public byte[] convert(byte[] docxBytes, String basename) {
        return convert(docxBytes, basename, "docx");
    }

    /**
     * Variante generique : convertit un document bureautique (DOCX, DOC, XLSX,
     * XLS...) en PDF via LibreOffice headless. LibreOffice detecte le format
     * d'entree par l'extension du fichier temporaire : on ecrit donc l'input avec
     * {@code sourceExtension} (ex. {@code "xlsx"}) au lieu de forcer {@code .docx}.
     *
     * <p>Lot AB (2026-07-05) — sert l'apercu inline des Word/Excel dans la Data
     * Room (conversion serveur a la volee).
     *
     * @param sourceBytes     contenu binaire source (jamais null/vide)
     * @param basename        nom sans extension (fichier tmp + base du PDF)
     * @param sourceExtension extension du format d'entree ({@code docx}, {@code xlsx}...)
     */
    public byte[] convert(byte[] sourceBytes, String basename, String sourceExtension) {
        if (!isAvailable()) {
            throw new LibreOfficeUnavailableException(
                    "LibreOffice (soffice) non disponible sur ce serveur. "
                            + "Installer LibreOffice ou definir jurika.docx-to-pdf.soffice-path.");
        }
        if (sourceBytes == null || sourceBytes.length == 0) {
            throw new IllegalArgumentException("sourceBytes vide");
        }
        String safeBase = sanitizeBasename(basename);
        String ext = sanitizeExtension(sourceExtension);
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("jurika-docx2pdf-");
            Path input = workDir.resolve(safeBase + "." + ext);
            Files.write(input, sourceBytes);

            int exit = runSoffice(input, workDir);
            Path pdf = workDir.resolve(safeBase + ".pdf");
            if (exit != 0 || !Files.isRegularFile(pdf)) {
                throw new DocxToPdfConversionException(
                        "Conversion DOCX->PDF a echoue (exit=" + exit
                                + ", pdf_existe=" + Files.isRegularFile(pdf)
                                + ", base=" + safeBase + ")");
            }
            byte[] out = Files.readAllBytes(pdf);
            log.info("DocxToPdfConverter : {}.{} ({} bytes) -> {} bytes PDF",
                    safeBase, ext, sourceBytes.length, out.length);
            return out;
        } catch (LibreOfficeUnavailableException | DocxToPdfConversionException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DocxToPdfConversionException("Conversion interrompue : " + ex.getMessage(), ex);
        } catch (IOException ex) {
            throw new DocxToPdfConversionException("Erreur I/O pendant conversion : " + ex.getMessage(), ex);
        } finally {
            if (workDir != null) {
                deleteQuietly(workDir);
            }
        }
    }

    // ---------------------------------------------------------------------
    // internals
    // ---------------------------------------------------------------------

    private int runSoffice(Path input, Path workDir) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add(resolvedSofficePath);
        cmd.add("--headless");
        cmd.add("--norestore");
        cmd.add("--nologo");
        cmd.add("--nofirststartwizard");
        // Profil utilisateur isole par conversion -> evite les conflits si plusieurs
        // conversions tournent en parallele (soffice refuse un 2eme process sur le
        // meme profil par defaut).
        cmd.add("-env:UserInstallation=file:///" + workDir.toAbsolutePath().toString().replace('\\', '/') + "/uno");
        cmd.add("--convert-to");
        cmd.add("pdf");
        cmd.add("--outdir");
        cmd.add(workDir.toAbsolutePath().toString());
        cmd.add(input.toAbsolutePath().toString());

        Process p = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .directory(workDir.toFile())
                .start();

        // On lit stdout/stderr en best-effort (sans saturer la JVM).
        StringBuilder captured = new StringBuilder();
        try (var br = p.inputReader()) {
            char[] buf = new char[2048];
            int n;
            while ((n = br.read(buf)) != -1 && captured.length() < 8192) {
                captured.append(buf, 0, n);
            }
        }

        boolean finished = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            throw new DocxToPdfConversionException(
                    "Timeout LibreOffice apres " + timeoutSeconds + "s — sortie partielle : "
                            + captured.toString().strip());
        }
        int exit = p.exitValue();
        if (exit != 0) {
            log.warn("DocxToPdfConverter : soffice exit={}, sortie : {}", exit, captured.toString().strip());
        }
        return exit;
    }

    private String resolveSofficePath() {
        // 1. Property explicite.
        if (!configuredSofficePath.isEmpty()) {
            if (isExecutable(configuredSofficePath)) return configuredSofficePath;
            log.warn("DocxToPdfConverter : jurika.docx-to-pdf.soffice-path={} introuvable.", configuredSofficePath);
        }
        // 2. PATH.
        String pathBinary = locateOnPath(isWindows() ? "soffice.exe" : "soffice");
        if (pathBinary != null) return pathBinary;
        // 3. Chemins par defaut OS.
        List<String> defaults = isWindows() ? DEFAULT_WINDOWS_PATHS : DEFAULT_UNIX_PATHS;
        for (String candidate : defaults) {
            if (isExecutable(candidate)) return candidate;
        }
        return null;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static boolean isExecutable(String path) {
        if (path == null || path.isBlank()) return false;
        Path p = Path.of(path);
        return Files.isRegularFile(p) && Files.isExecutable(p);
    }

    private static String locateOnPath(String binary) {
        String envPath = System.getenv("PATH");
        if (envPath == null) return null;
        String sep = System.getProperty("path.separator", isWindows() ? ";" : ":");
        for (String dir : envPath.split(sep)) {
            if (dir.isBlank()) continue;
            Path candidate = Path.of(dir, binary);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return candidate.toString();
            }
        }
        return null;
    }

    private String readSofficeVersion(String binary) {
        try {
            Process p = new ProcessBuilder(binary, "--version")
                    .redirectErrorStream(true).start();
            StringBuilder sb = new StringBuilder();
            try (var br = p.inputReader()) {
                char[] buf = new char[256];
                int n;
                while ((n = br.read(buf)) != -1 && sb.length() < 512) {
                    sb.append(buf, 0, n);
                }
            }
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return "unknown (timeout)";
            }
            return sb.toString().strip();
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            return "unknown";
        }
    }

    /** Extension d'entree sure pour LibreOffice (defaut docx). Alphanumerique, minuscule. */
    private static String sanitizeExtension(String ext) {
        if (ext == null || ext.isBlank()) return "docx";
        String safe = ext.trim().toLowerCase().replaceAll("[^a-z0-9]", "");
        return safe.isBlank() ? "docx" : safe;
    }

    private static String sanitizeBasename(String basename) {
        if (basename == null || basename.isBlank()) return "document";
        String trimmed = basename.replaceAll("\\.[a-zA-Z0-9]{1,5}$", "");
        // Remove anything not safe for filesystem (Windows-strict).
        String safe = trimmed.replaceAll("[^A-Za-z0-9._\\-]", "_");
        if (safe.length() > 80) safe = safe.substring(0, 80);
        return safe.isBlank() ? "document" : safe;
    }

    private static void deleteQuietly(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {
        }
    }

    /** Levee quand soffice n'est pas disponible — le controller mappe en 503. */
    public static class LibreOfficeUnavailableException extends RuntimeException {
        public LibreOfficeUnavailableException(String message) { super(message); }
    }

    /** Levee quand la conversion echoue (timeout, exit non-0, PDF non produit). */
    public static class DocxToPdfConversionException extends RuntimeException {
        public DocxToPdfConversionException(String message) { super(message); }
        public DocxToPdfConversionException(String message, Throwable cause) { super(message, cause); }
    }
}
