package ma.jurika.auth.application.essai;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Lot L7 (E4), RG-ESS-02 : les adresses jetables sont refusees a l'inscription
 * a l'essai. Liste des domaines dans la ressource {@code essai/domaines-jetables.txt}
 * (un domaine par ligne, commentaires en {@code #}). Un sous-domaine d'un
 * domaine liste est refuse aussi. Ressource absente, vide ou mal formee : le
 * service ne demarre pas (aucun controle silencieusement inoperant).
 */
@Component
public class DomainesJetables {

    static final String RESSOURCE = "essai/domaines-jetables.txt";
    private static final Pattern DOMAINE = Pattern.compile("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$");

    private final Set<String> domaines;

    public DomainesJetables() {
        this(lire(RESSOURCE));
    }

    DomainesJetables(Set<String> domaines) {
        if (domaines.isEmpty()) {
            throw new IllegalStateException("Liste des domaines jetables vide : " + RESSOURCE);
        }
        this.domaines = Set.copyOf(domaines);
    }

    public Set<String> domaines() {
        return domaines;
    }

    /** {@code true} si le domaine de l'adresse (ou l'un de ses parents) est liste. */
    public boolean estJetable(String email) {
        if (email == null) return false;
        int at = email.lastIndexOf('@');
        if (at < 0 || at == email.length() - 1) return false;
        String domaine = email.substring(at + 1).trim().toLowerCase(Locale.ROOT);
        while (true) {
            if (domaines.contains(domaine)) return true;
            int point = domaine.indexOf('.');
            if (point < 0 || domaine.indexOf('.', point + 1) < 0) return false;
            domaine = domaine.substring(point + 1);
        }
    }

    static Set<String> lire(String chemin) {
        ClassPathResource res = new ClassPathResource(chemin);
        try (InputStream in = res.getInputStream()) {
            String texte = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Set<String> lignes = texte.lines()
                    .map(String::trim)
                    .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .collect(Collectors.toUnmodifiableSet());
            for (String l : lignes) {
                if (!DOMAINE.matcher(l).matches()) {
                    throw new IllegalStateException("Domaine jetable mal forme dans " + chemin + " : " + l);
                }
            }
            return lignes;
        } catch (IOException e) {
            throw new UncheckedIOException("Liste des domaines jetables illisible : " + chemin, e);
        }
    }
}
