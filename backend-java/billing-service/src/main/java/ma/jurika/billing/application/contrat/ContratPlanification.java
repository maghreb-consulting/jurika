package ma.jurika.billing.application.contrat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Lot L7 (E6, E9) : version 1 du contrat (texte valide par la direction le 14 octobre 2026,
 * {@code contrat/CONTRAT_ABONNEMENT_JURIKA_V1.md}, copie exacte du document) chargee au demarrage
 * si elle manque, et passage quotidien des echeances (renouvellement, avis a 30 jours, RG-CTR-06)
 * a 6 h, heure de Casablanca.
 *
 * <p>Le projet du 12 octobre 2026 (code "2026-10-12"), charge sur une base vivante avant la
 * validation, y reste (versions inalterables) mais est retire (billing V16) : il n'est jamais
 * presente a un cabinet.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "jurika.contrat.planification.enabled", havingValue = "true", matchIfMissing = true)
public class ContratPlanification {

    private static final Logger log = LoggerFactory.getLogger(ContratPlanification.class);

    public static final String VERSION_INITIALE = "1";
    public static final String SOURCE_INITIALE = "contrat/CONTRAT_ABONNEMENT_JURIKA_V1.md";

    private final ContratService service;

    public ContratPlanification(ContratService service) {
        this.service = service;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void chargerVersionInitiale() {
        service.initialiserVersionInitiale(VERSION_INITIALE, sourceInitiale());
    }

    @Scheduled(cron = "0 0 6 * * *", zone = "Africa/Casablanca")
    public void echeances() {
        ContratService.BilanEcheances b = service.executerEcheances();
        log.info("Echeances du contrat : {} renouvellement(s), {} avis", b.renouvellements(), b.avis());
    }

    /** Document source de la version 1, octet pour octet. */
    public static String sourceInitiale() {
        try (InputStream in = new ClassPathResource(SOURCE_INITIALE).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Texte du contrat absent du classpath : " + SOURCE_INITIALE, ex);
        }
    }

    /** Modele de la version 1 (sans la ligne de publication ni la piece D). */
    public static String modeleInitial() {
        return ContratTexte.modeleDepuisSource(sourceInitiale());
    }
}
