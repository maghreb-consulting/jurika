package ma.jurika.common.audit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Lot L6b (E9) : relecture des sources de TOUS les services Java du depot pour en tirer
 * les codes d'action traces, sous toutes leurs formes :
 * <ul>
 *   <li>{@code @Auditable(action = "X")} (et l'echec {@code X_FAILED} que pose {@link AuditAspect}) ;</li>
 *   <li>{@code <AuditLogger>.log(ws, user, "X", ...)} (auth) et
 *       {@code <AuditEventEmitter>.emit(ws, acteur, "X", ...)} (jurika-common) : le 3e argument,
 *       litteral, ternaire, constante, variable locale ou parametre d'une methode du fichier ;</li>
 *   <li>{@code INSERT INTO audit_log} ecrit en SQL (blocs de texte Java, migrations) ;</li>
 *   <li>les codes LUS par une requete ({@code action IN (...)}, {@code action = '...'}) : un code
 *       lu qui n'existe pas est un filtre silencieusement inoperant ;</li>
 *   <li>le journal d'acces du client : {@code accessLogger.log(dossier, document, "X", ...)},
 *       {@code @ClientAccess(action = "X")} et la contrainte CHECK de la derniere migration.</li>
 * </ul>
 * Les commentaires sont retires avant la lecture (un exemple de javadoc n'est pas une emission).
 * Un site d'emission dont le code ne se resout pas est rapporte, jamais ignore.
 */
final class SourcesActionsAudit {

    private static final Pattern LITTERAL = Pattern.compile("\"([A-Z0-9][A-Z0-9_]*)\"");
    private static final Pattern LITTERAL_SQL = Pattern.compile("'([A-Z0-9][A-Z0-9_]*)'");
    private static final Pattern AUDITABLE = Pattern.compile("@Auditable\\s*\\(([^)]*)\\)");
    private static final Pattern CLIENT_ACCESS = Pattern.compile("@ClientAccess\\s*\\(([^)]*)\\)");
    private static final Pattern ACTION_ATTR = Pattern.compile("action\\s*=\\s*\"([A-Z0-9_]+)\"");
    private static final Pattern RECEPTEUR =
            Pattern.compile("\\b(?:AuditLogger|AuditEventEmitter)\\s+(\\w+)\\s*[;,)=]");
    private static final Pattern INSERT_AUDIT =
            Pattern.compile("(?is)INSERT\\s+INTO\\s+(?:public\\.)?audit_log\\b(.*?)(?:\"\"\"|;)");
    private static final Pattern LECTURE_IN = Pattern.compile("(?i)\\baction\\s+IN\\s*\\(([^)]*)\\)");
    private static final Pattern LECTURE_EGAL = Pattern.compile("(?i)\\baction\\s*=\\s*'([A-Z0-9_]+)'");
    private static final Pattern CHECK_ACCES =
            Pattern.compile("(?is)CHECK\\s*\\(\\s*action\\s+IN\\s*\\(([^)]*)\\)\\s*\\)");
    private static final Pattern VERSION_MIGRATION = Pattern.compile("^V(\\d+)(?:_(\\d+))?__.*\\.sql$");

    final Path racine;
    /** Code emis -> fichiers emetteurs. */
    final Map<String, Set<String>> emis = new TreeMap<>();
    /** Codes poses par @Auditable (leur echec X_FAILED est derive). */
    final Map<String, Set<String>> auditables = new TreeMap<>();
    /** Codes lus par une requete SQL d'un service. */
    final Map<String, Set<String>> lus = new TreeMap<>();
    /** Actions du journal d'acces du client emises. */
    final Map<String, Set<String>> accesEmis = new TreeMap<>();
    /** Actions autorisees par la derniere contrainte CHECK du journal d'acces. */
    final Set<String> accesCheck = new TreeSet<>();
    String migrationCheck;
    /** Sites d'emission dont le code n'a pas pu etre resolu. */
    final List<String> nonResolus = new ArrayList<>();
    int fichiersJava;
    int sitesAuditable;
    int sitesAuditLogger;
    int sitesAuditEmitter;
    int sitesAccesClient;

    private SourcesActionsAudit(Path racine) {
        this.racine = racine;
    }

    /** Racine {@code backend-java} vue depuis le module de test (jurika-common). */
    static SourcesActionsAudit lire() {
        Path racine = Path.of("..").toAbsolutePath().normalize();
        for (String module : List.of("jurika-common", "auth-service", "ticket-service", "dataroom-service",
                "workflow-service", "ai-service", "billing-service", "dashboard-service", "supervision-service")) {
            if (!Files.isDirectory(racine.resolve(module).resolve("src/main/java"))) {
                throw new IllegalStateException("Module introuvable sous " + racine + " : " + module);
            }
        }
        SourcesActionsAudit s = new SourcesActionsAudit(racine);
        s.lireJava();
        s.lireMigrations();
        return s;
    }

    private void lireJava() {
        try (Stream<Path> f = Files.walk(racine)) {
            List<Path> fichiers = f.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> racine.relativize(p).toString().replace('\\', '/').matches("[^/]+/src/main/java/.*"))
                    .sorted().toList();
            for (Path p : fichiers) {
                fichiersJava++;
                lireJava(nom(p), sansCommentaires(Files.readString(p, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void lireJava(String fichier, String t) {
        Matcher m = AUDITABLE.matcher(t);
        while (m.find()) {
            sitesAuditable++;
            Matcher a = ACTION_ATTR.matcher(m.group(1));
            if (!a.find()) {
                nonResolus.add(fichier + " : @Auditable(" + m.group(1).trim() + ")");
                continue;
            }
            ajouter(auditables, a.group(1), fichier);
            ajouter(emis, a.group(1), fichier);
            ajouter(emis, a.group(1) + AuditActionLabels.SUFFIXE_ECHEC, fichier + " (AuditAspect)");
        }
        Set<String> recepteurs = new TreeSet<>();
        Matcher r = RECEPTEUR.matcher(t);
        while (r.find()) recepteurs.add(r.group(1));
        boolean declareAuditLogger = t.contains("AuditLogger");
        for (String recepteur : recepteurs) {
            Matcher appel = Pattern.compile("\\b" + recepteur + "\\s*\\.\\s*(log|emit)\\s*\\(").matcher(t);
            while (appel.find()) {
                List<String> args = arguments(t, appel.end());
                if (args.size() == 1 && args.get(0).startsWith("new AuditEvent(")) {
                    args = arguments(args.get(0), args.get(0).indexOf('(') + 1);
                } else if (args.size() == 1) {
                    // Delegation interne d'un emetteur (emit(event)) : le code vient de l'appelant.
                    continue;
                }
                if ("log".equals(appel.group(1)) && declareAuditLogger) sitesAuditLogger++;
                else sitesAuditEmitter++;
                resoudreSite(fichier, t, args, emis);
            }
        }
        Matcher acces = Pattern.compile("\\baccessLogger\\s*\\.\\s*log\\s*\\(").matcher(t);
        while (acces.find()) {
            sitesAccesClient++;
            resoudreSite(fichier, t, arguments(t, acces.end()), accesEmis);
        }
        Matcher ca = CLIENT_ACCESS.matcher(t);
        while (ca.find()) {
            Matcher a = ACTION_ATTR.matcher(ca.group(1));
            if (a.find()) ajouter(accesEmis, a.group(1), fichier);
        }
        lireSql(fichier, t);
    }

    private void lireSql(String fichier, String t) {
        Matcher ins = INSERT_AUDIT.matcher(t);
        while (ins.find()) {
            Matcher l = LITTERAL_SQL.matcher(ins.group(1));
            while (l.find()) ajouter(emis, l.group(1), fichier + " (SQL)");
        }
        if (!t.contains("audit_log")) return;
        Matcher in = LECTURE_IN.matcher(t);
        while (in.find()) {
            Matcher l = LITTERAL_SQL.matcher(in.group(1));
            while (l.find()) ajouter(lus, l.group(1), fichier);
        }
        Matcher eg = LECTURE_EGAL.matcher(t);
        while (eg.find()) ajouter(lus, eg.group(1), fichier);
    }

    private void lireMigrations() {
        try (Stream<Path> f = Files.walk(racine)) {
            List<Path> migrations = f.filter(p -> p.toString().replace('\\', '/').contains("/src/main/resources/db/migration/"))
                    .filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .toList();
            for (Path p : migrations) {
                String t = Files.readString(p, StandardCharsets.UTF_8).replaceAll("(?m)--.*$", "");
                Matcher ins = INSERT_AUDIT.matcher(t);
                while (ins.find()) {
                    Matcher l = LITTERAL_SQL.matcher(ins.group(1));
                    while (l.find()) ajouter(emis, l.group(1), nom(p) + " (SQL)");
                }
            }
            List<Path> dataroom = migrations.stream()
                    .filter(p -> p.startsWith(racine.resolve("dataroom-service")))
                    .sorted(Comparator.comparingLong(SourcesActionsAudit::version))
                    .toList();
            for (Path p : dataroom) {
                String t = Files.readString(p, StandardCharsets.UTF_8).replaceAll("(?m)--.*$", "");
                if (!t.contains("dataroom_client_access_log")) continue;
                Matcher c = CHECK_ACCES.matcher(t);
                String dernier = null;
                while (c.find()) dernier = c.group(1);
                if (dernier != null) {
                    accesCheck.clear();
                    Matcher l = LITTERAL_SQL.matcher(dernier);
                    while (l.find()) accesCheck.add(l.group(1));
                    migrationCheck = p.getFileName().toString();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static long version(Path p) {
        Matcher m = VERSION_MIGRATION.matcher(p.getFileName().toString());
        if (!m.matches()) throw new IllegalStateException("Nom de migration inattendu : " + p);
        return Long.parseLong(m.group(1)) * 1000 + (m.group(2) == null ? 0 : Long.parseLong(m.group(2)));
    }

    /** Le code est le 3e argument (ws, acteur, CODE, ...) pour les trois journaux. */
    private void resoudreSite(String fichier, String t, List<String> args, Map<String, Set<String>> cible) {
        if (args.size() < 3) {
            nonResolus.add(fichier + " : appel a " + args.size() + " argument(s) : " + args);
            return;
        }
        Set<String> codes = new TreeSet<>();
        resoudre(t, args.get(2), codes, 0);
        if (codes.isEmpty()) {
            nonResolus.add(fichier + " : code non resolu : " + args.get(2));
        }
        for (String c : codes) ajouter(cible, c, fichier);
    }

    /** Litteraux d'une expression ; sinon identifiant : affectations, puis parametre d'une methode. */
    private static void resoudre(String t, String expr, Set<String> codes, int profondeur) {
        if (profondeur > 4) return;
        Matcher l = LITTERAL.matcher(expr);
        boolean trouve = false;
        while (l.find()) { codes.add(l.group(1)); trouve = true; }
        if (trouve) return;
        String id = expr.trim();
        if (!id.matches("[A-Za-z_]\\w*")) return;
        Matcher aff = Pattern.compile("\\b" + id + "\\s*=\\s*([^;=][^;]*);").matcher(t);
        while (aff.find()) {
            Matcher v = LITTERAL.matcher(aff.group(1));
            while (v.find()) { codes.add(v.group(1)); trouve = true; }
        }
        if (trouve) return;
        // Parametre "String id" d'une methode du fichier : on suit les appels de cette methode.
        Matcher decl = Pattern.compile("\\b(\\w+)\\s*\\(([^()]*\\bString\\s+" + id + "\\b[^()]*)\\)\\s*(?:throws[^{]*)?\\{")
                .matcher(t);
        while (decl.find()) {
            String methode = decl.group(1);
            List<String> params = arguments(decl.group(2) + ")", 0);
            int index = -1;
            for (int i = 0; i < params.size(); i++) {
                if (params.get(i).matches("(?s).*\\bString\\s+" + id)) index = i;
            }
            if (index < 0) continue;
            Matcher appel = Pattern.compile("\\b" + methode + "\\s*\\(").matcher(t);
            while (appel.find()) {
                if (appel.start() == decl.start()) continue;
                List<String> args = arguments(t, appel.end());
                if (args.size() > index) resoudre(t, args.get(index), codes, profondeur + 1);
            }
        }
    }

    /** Arguments de premier niveau d'un appel, a partir de l'indice qui suit la parenthese ouvrante. */
    static List<String> arguments(String t, int debut) {
        List<String> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        int prof = 1;
        boolean chaine = false;
        for (int i = debut; i < t.length(); i++) {
            char c = t.charAt(i);
            if (chaine) {
                buf.append(c);
                if (c == '\\' && i + 1 < t.length()) { buf.append(t.charAt(++i)); continue; }
                if (c == '"') chaine = false;
                continue;
            }
            if (c == '"') { chaine = true; buf.append(c); continue; }
            if (c == '(' || c == '[' || c == '{') prof++;
            if (c == ')' || c == ']' || c == '}') {
                prof--;
                if (prof == 0) break;
            }
            if (c == ',' && prof == 1) { out.add(buf.toString().trim()); buf.setLength(0); continue; }
            buf.append(c);
        }
        out.add(buf.toString().trim());
        return out;
    }

    /** Retire commentaires de ligne et de bloc, en respectant chaines, caracteres et blocs de texte. */
    static String sansCommentaires(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (s.startsWith("\"\"\"", i)) {
                int fin = s.indexOf("\"\"\"", i + 3);
                fin = fin < 0 ? n : fin + 3;
                out.append(s, i, fin);
                i = fin;
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && s.charAt(j) != c) {
                    if (s.charAt(j) == '\\') j++;
                    j++;
                }
                j = Math.min(j + 1, n);
                out.append(s, i, j);
                i = j;
            } else if (s.startsWith("//", i)) {
                int fin = s.indexOf('\n', i);
                i = fin < 0 ? n : fin;
            } else if (s.startsWith("/*", i)) {
                int fin = s.indexOf("*/", i + 2);
                i = fin < 0 ? n : fin + 2;
                out.append(' ');
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private String nom(Path p) {
        String rel = racine.relativize(p).toString().replace('\\', '/');
        return rel.substring(0, rel.indexOf('/')) + ":" + p.getFileName();
    }

    private static void ajouter(Map<String, Set<String>> m, String code, String fichier) {
        m.computeIfAbsent(code, k -> new TreeSet<>()).add(fichier);
    }
}
