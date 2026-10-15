package ma.jurika.auth.application.essai;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Lot L7 (E4), RG-CTR-10 : conditions d'utilisation de l'essai gratuit,
 * acceptees a l'inscription selon RG-CTR-01 (case jamais pre-cochee) et
 * RG-CTR-02 (version exacte, texte et empreinte, date, IP).
 *
 * <p>Texte : piece D de la version 1 du contrat d'abonnement, validee par la
 * direction le 14 octobre 2026 ({@code ~/docs/CONTRAT_ABONNEMENT_JURIKA_V1.md}),
 * reprise mot pour mot en texte simple (titre, puis les huit points, sans les
 * marques de gras ni les retours a la ligne de mise en page). Une nouvelle
 * version = nouveau fichier et nouvelle constante ; l'empreinte est calculee sur
 * les octets du texte.
 *
 * <p>Versions anterieures : les acceptations deja enregistrees gardent leur
 * version et leur empreinte ; leur texte reste au classpath, inchange
 * ({@code essai/conditions-essai-D-PROJET-2026-10-12.txt}, version
 * "D-PROJET-2026-10-12", meme texte que la piece D validee).
 */
@Component
public class ConditionsEssai {

    public static final String VERSION = "D-V1";
    static final String RESSOURCE = "essai/conditions-essai-D-V1.txt";

    private final String texte;
    private final String empreinte;

    public ConditionsEssai() {
        ClassPathResource res = new ClassPathResource(RESSOURCE);
        try (InputStream in = res.getInputStream()) {
            byte[] octets = in.readAllBytes();
            this.texte = new String(octets, StandardCharsets.UTF_8);
            if (texte.isBlank()) {
                throw new IllegalStateException("Conditions de l'essai vides : " + RESSOURCE);
            }
            this.empreinte = sha256(octets);
        } catch (IOException e) {
            throw new UncheckedIOException("Conditions de l'essai illisibles : " + RESSOURCE, e);
        }
    }

    public String version() { return VERSION; }
    public String texte() { return texte; }
    public String empreinte() { return empreinte; }

    public static String sha256(byte[] octets) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(octets));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sha256(String texte) {
        return sha256(texte.getBytes(StandardCharsets.UTF_8));
    }
}
