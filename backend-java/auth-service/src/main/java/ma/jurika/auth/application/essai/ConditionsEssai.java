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
 * <p>Texte : piece D du projet de contrat d'abonnement du 12 octobre 2026
 * ({@code ~/docs/CONTRAT_ABONNEMENT_JURIKA_PROJET.md}), reprise mot pour mot.
 * Ce projet n'est PAS encore valide par la direction (A_DECIDER : validation
 * du texte, point 11 "interdiction des vrais dossiers" a confirmer) : la
 * version porte la mention PROJET. Une nouvelle version = nouveau fichier et
 * nouvelle constante ; l'empreinte est calculee sur les octets du texte.
 */
@Component
public class ConditionsEssai {

    public static final String VERSION = "D-PROJET-2026-10-12";
    static final String RESSOURCE = "essai/conditions-essai.txt";

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
