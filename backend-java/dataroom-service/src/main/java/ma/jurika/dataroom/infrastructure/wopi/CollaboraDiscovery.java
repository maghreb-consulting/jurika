package ma.jurika.dataroom.infrastructure.wopi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * L'URL de l'éditeur, lue dans le document de découverte de Collabora.
 *
 * <p>Lot 3 (2026-09-07). Elle ne se devine pas : le chemin porte une empreinte
 * de version — {@code /browser/cc5614c67e/cool.html} — qui change à chaque
 * publication de l'image. Or CODE est publié en continu. Une URL codée en dur
 * fonctionnerait aujourd'hui et tomberait à la prochaine mise à jour du
 * conteneur, avec un éditeur qui refuse simplement de s'ouvrir.
 *
 * <p>Le protocole WOPI prévoit exactement cela : l'hôte interroge
 * {@code /hosting/discovery} et y lit l'URL d'édition pour l'extension voulue.
 * On la relit périodiquement plutôt qu'à chaque ouverture de document — c'est
 * un document de ~40 Ko qui ne change qu'aux mises à jour.
 *
 * <p>Deux adresses distinctes, et c'est important : la découverte est
 * interrogée depuis le RÉSEAU DOCKER ({@code collabora:9980}), tandis que l'URL
 * rendue au navigateur doit être publique ({@code localhost:9980}). Confondre
 * les deux est la cause d'échec la plus fréquente de cette intégration.
 */
@Component
public class CollaboraDiscovery {

    private static final Logger log = LoggerFactory.getLogger(CollaboraDiscovery.class);

    /** {@code <action ... ext="docx" ... name="edit" ... urlsrc="..."/>}, dans n'importe quel ordre. */
    private static final Pattern ACTION_EDIT_DOCX = Pattern.compile(
            "<action(?=[^>]*\\bext=\"docx\")(?=[^>]*\\bname=\"edit\")[^>]*\\burlsrc=\"([^\"]+)\"");

    private final RestClient http = RestClient.create();

    /** Adresse interne, pour interroger la découverte. */
    private final String urlInterne;
    /** Adresse publique, celle que le navigateur chargera. */
    private final String urlPublique;
    private final Duration ttl;

    private volatile String urlEditeur;
    private volatile Instant relueLe = Instant.EPOCH;

    public CollaboraDiscovery(
            @Value("${jurika.wopi.discovery-url:http://collabora:9980}") String urlInterne,
            @Value("${jurika.wopi.editor-url:http://localhost:9980}") String urlPublique,
            @Value("${jurika.wopi.discovery-ttl:PT30M}") Duration ttl) {
        this.urlInterne = urlInterne.replaceAll("/+$", "");
        this.urlPublique = urlPublique.replaceAll("/+$", "");
        this.ttl = ttl;
    }

    /**
     * URL de l'éditeur pour un {@code .docx}, prête à recevoir les paramètres
     * {@code WOPISrc} et {@code access_token}. Elle se termine déjà par
     * {@code ?} ou {@code &}, comme le veut la découverte.
     *
     * @return {@code null} si la découverte est injoignable — l'appelant doit
     *         alors le dire à l'employé plutôt que d'ouvrir un cadre vide.
     */
    public String urlEditeurDocx() {
        String cache = urlEditeur;
        if (cache != null && Instant.now().isBefore(relueLe.plus(ttl))) {
            return cache;
        }
        try {
            String xml = http.get()
                    .uri(urlInterne + "/hosting/discovery")
                    .retrieve()
                    .body(String.class);
            if (xml == null || xml.isBlank()) {
                log.warn("Decouverte Collabora vide ({})", urlInterne);
                return cache;
            }
            Matcher m = ACTION_EDIT_DOCX.matcher(xml);
            if (!m.find()) {
                log.warn("Aucune action d'edition .docx dans la decouverte Collabora");
                return cache;
            }
            // La découverte annonce l'URL avec l'hôte tel que Collabora se voit.
            // On ne garde que le CHEMIN et on le recolle sur l'adresse publique :
            // le navigateur ne sait pas résoudre un nom de service Docker.
            String annoncee = m.group(1);
            String chemin = annoncee.replaceFirst("^[a-zA-Z]+://[^/]+", "");
            String resolue = urlPublique + chemin;

            urlEditeur = resolue;
            relueLe = Instant.now();
            log.info("URL editeur Collabora resolue : {}", resolue);
            return resolue;
        } catch (Exception ex) {
            log.warn("Decouverte Collabora injoignable ({}) : {}", urlInterne, ex.getMessage());
            return cache;
        }
    }
}
