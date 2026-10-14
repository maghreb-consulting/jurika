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
 * Lot L7 (E6) : version initiale du contrat (texte valide par la direction, du 12 octobre 2026)
 * chargee au demarrage si aucune version n'existe, et passage quotidien des echeances
 * (renouvellement, avis a 30 jours, RG-CTR-06) a 6 h, heure de Casablanca.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "jurika.contrat.planification.enabled", havingValue = "true", matchIfMissing = true)
public class ContratPlanification {

    private static final Logger log = LoggerFactory.getLogger(ContratPlanification.class);

    public static final String VERSION_INITIALE = "2026-10-12";
    public static final String MODELE_INITIAL = "contrat/modele-2026-10-12.md";

    private final ContratService service;

    public ContratPlanification(ContratService service) {
        this.service = service;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void chargerVersionInitiale() {
        service.initialiserSiVide(VERSION_INITIALE, modeleInitial());
    }

    @Scheduled(cron = "0 0 6 * * *", zone = "Africa/Casablanca")
    public void echeances() {
        ContratService.BilanEcheances b = service.executerEcheances();
        log.info("Echeances du contrat : {} renouvellement(s), {} avis", b.renouvellements(), b.avis());
    }

    public static String modeleInitial() {
        try (InputStream in = new ClassPathResource(MODELE_INITIAL).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Modele du contrat absent du classpath : " + MODELE_INITIAL, ex);
        }
    }
}
