package ma.jurika.ai.document.manifest;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Charge le manifest L3 ({@code templates/v2/manifest.json}) + le dictionnaire des
 * variables ({@code templates/v2/dictionary.json}) au démarrage Spring, puis offre
 * un lookup case-sensitive sur le code, avec résolution des alias.
 *
 * <p><b>Politique d'erreur</b> :
 * <ul>
 *   <li>Fail-fast au startup si un {@code aliasOf} pointe vers un code inexistant.</li>
 *   <li>Fail-fast si la lecture des fichiers échoue.</li>
 *   <li>Cycle de résolution alias → {@link IllegalStateException} (max 5 hops).</li>
 *   <li>Cross-ref variables ↔ dictionnaire : WARN log uniquement (non bloquant).</li>
 * </ul>
 */
@Component
public class TemplateManifestLoader {

    private static final Logger log = LoggerFactory.getLogger(TemplateManifestLoader.class);

    /** Profondeur maximum de résolution alias avant détection cycle. */
    static final int MAX_ALIAS_HOPS = 5;

    private static final String DEFAULT_MANIFEST_RESOURCE = "templates/v2/manifest.json";
    private static final String DEFAULT_DICTIONARY_RESOURCE = "templates/v2/dictionary.json";

    private final ObjectMapper objectMapper;
    private final String manifestResource;
    private final String dictionaryResource;

    private TemplateManifest manifest;
    private DictionaryManifest dictionary;
    /** Map code → entry. Préserve l'ordre d'insertion (utile pour les logs). */
    private Map<String, TemplateManifest.TemplateEntry> byCode;

    /** Constructeur de production (Spring). */
    @Autowired
    public TemplateManifestLoader(ObjectMapper objectMapper) {
        this(objectMapper, DEFAULT_MANIFEST_RESOURCE, DEFAULT_DICTIONARY_RESOURCE);
    }

    /**
     * Constructeur secondaire (package-private) pour testabilité : permet de pointer
     * vers une ressource classpath alternative (manifest de test cycliques, etc.).
     */
    TemplateManifestLoader(ObjectMapper objectMapper,
                          String manifestResource,
                          String dictionaryResource) {
        this.objectMapper = objectMapper;
        this.manifestResource = manifestResource;
        this.dictionaryResource = dictionaryResource;
    }

    @PostConstruct
    public void load() {
        this.manifest = readJson(manifestResource, TemplateManifest.class);
        this.dictionary = readJson(dictionaryResource, DictionaryManifest.class);

        if (manifest.templates() == null || manifest.templates().isEmpty()) {
            throw new IllegalStateException("Manifest vide : " + manifestResource);
        }

        Map<String, TemplateManifest.TemplateEntry> map = new LinkedHashMap<>();
        for (TemplateManifest.TemplateEntry entry : manifest.templates()) {
            if (entry.code() == null || entry.code().isBlank()) {
                throw new IllegalStateException(
                        "Manifest " + manifestResource + " : entrée sans code détectée");
            }
            TemplateManifest.TemplateEntry previous = map.put(entry.code(), entry);
            if (previous != null) {
                throw new IllegalStateException(
                        "Manifest " + manifestResource + " : code dupliqué = " + entry.code());
            }
        }
        this.byCode = Collections.unmodifiableMap(map);

        validateAliases();
        validateDictionaryCrossRef();

        int total = byCode.size();
        int aliases = 0;
        int directeurs = 0;
        int internes = 0;
        for (TemplateManifest.TemplateEntry e : byCode.values()) {
            if (e.aliasOf() != null) {
                aliases++;
            } else if ("interne".equals(e.origin())) {
                internes++;
            } else if ("directeur".equals(e.origin())) {
                directeurs++;
            }
        }
        log.info("Loaded {} templates ({} directeurs, {} internes, {} aliases)",
                total, directeurs, internes, aliases);
    }

    private <T> T readJson(String resourcePath, Class<T> type) {
        Resource res = new ClassPathResource(resourcePath);
        if (!res.exists()) {
            throw new IllegalStateException(
                    "Ressource introuvable sur classpath : " + resourcePath);
        }
        try (InputStream in = res.getInputStream()) {
            return objectMapper.readValue(in, type);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Lecture impossible : " + resourcePath + " — " + ex.getMessage(), ex);
        }
    }

    /** Fail-fast : tout aliasOf doit cibler un code existant dans le manifest. */
    private void validateAliases() {
        for (TemplateManifest.TemplateEntry entry : byCode.values()) {
            if (entry.aliasOf() == null) continue;
            if (!byCode.containsKey(entry.aliasOf())) {
                throw new IllegalStateException(
                        "Manifest " + manifestResource + " : alias " + entry.code()
                                + " pointe vers un code inexistant : " + entry.aliasOf());
            }
        }
    }

    /** WARN log si une variable d'un template n'a pas d'entrée dictionnaire. */
    private void validateDictionaryCrossRef() {
        if (dictionary == null || dictionary.variables() == null) return;
        Set<String> known = new HashSet<>();
        for (DictionaryManifest.VariableDef v : dictionary.variables()) {
            if (v != null && v.name() != null) {
                known.add(v.name());
            }
        }
        for (TemplateManifest.TemplateEntry entry : byCode.values()) {
            if (entry.variables() == null) continue;
            for (String var : entry.variables()) {
                if (var == null) continue;
                // Variables MAJUSCULES uniquement (style uppercase_dollar) : les variantes
                // legacy (lowercase) ne sont pas couvertes par le dictionnaire L3.
                if (var.equals(var.toUpperCase()) && !known.contains(var)) {
                    log.warn("Manifest {} : variable {} (template {}) absente du dictionnaire",
                            manifestResource, var, entry.code());
                }
            }
        }
    }

    /**
     * Résout le code (suivant les alias jusqu'à l'entrée concrète). Retourne
     * {@link Optional#empty()} si le code n'existe pas.
     *
     * @throws IllegalStateException si un cycle d'alias est détecté ou si la chaîne
     *                               dépasse {@value #MAX_ALIAS_HOPS} sauts.
     */
    public Optional<TemplateManifest.TemplateEntry> resolve(String code) {
        if (code == null) return Optional.empty();
        TemplateManifest.TemplateEntry current = byCode.get(code);
        if (current == null) return Optional.empty();

        Set<String> visited = new HashSet<>();
        visited.add(code);
        int hops = 0;
        while (current.aliasOf() != null) {
            if (hops++ >= MAX_ALIAS_HOPS) {
                throw new IllegalStateException(
                        "Chaîne d'alias trop longue (> " + MAX_ALIAS_HOPS
                                + ") au départ du code : " + code);
            }
            String target = current.aliasOf();
            if (!visited.add(target)) {
                throw new IllegalStateException(
                        "Cycle d'alias détecté au départ du code : " + code
                                + " (boucle sur " + target + ")");
            }
            TemplateManifest.TemplateEntry next = byCode.get(target);
            if (next == null) {
                // Devrait être bloqué par validateAliases ; garde-fou défensif.
                throw new IllegalStateException(
                        "Alias " + current.code() + " → " + target + " : cible introuvable");
            }
            current = next;
        }
        return Optional.of(current);
    }

    /** Tous les codes du manifest (directs + aliases). */
    public Set<String> allCodes() {
        return byCode.keySet();
    }

    /**
     * Toutes les entrées brutes du manifest (directes + alias), dans l'ordre
     * d'insertion. Les alias conservent {@code aliasOf} non-null (à différence de
     * {@link #resolve(String)} qui suit la chaîne).
     */
    public List<TemplateManifest.TemplateEntry> allEntries() {
        return List.copyOf(byCode.values());
    }

    /** Codes directs uniquement (pas d'alias). */
    public Set<String> allDirectCodes() {
        Set<String> result = new HashSet<>();
        for (TemplateManifest.TemplateEntry e : byCode.values()) {
            if (e.aliasOf() == null) {
                result.add(e.code());
            }
        }
        return result;
    }

    public DictionaryManifest dictionary() {
        return dictionary;
    }
}
