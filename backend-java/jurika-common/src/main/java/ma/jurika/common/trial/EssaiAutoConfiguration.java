package ma.jurika.common.trial;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Lot L7 (E4) : controles de l'essai gratuit dans tout service web servlet.
 *
 * <ul>
 *   <li>{@link TrialAccessChecker} distant (auth-service), sauf si le service
 *       declare le sien (auth-service : lecture locale de la table) ;</li>
 *   <li>{@link TrialSoftLockFilter}, a placer par chaque service dans sa
 *       chaine de securite apres JwtAuthFilter ;</li>
 *   <li>enregistrement automatique du filtre comme filtre de servlet
 *       DESACTIVE : hors chaine de securite, il tournerait sans workspace et
 *       serait marque deja passe (controle silencieusement inoperant).</li>
 * </ul>
 * {@code jurika.trial.remote.enabled=false} retire le verificateur distant
 * (tests sans auth-service) : avertissement au demarrage.
 */
@AutoConfiguration(after = org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class EssaiAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EssaiAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(TrialAccessChecker.class)
    @ConditionalOnProperty(prefix = "jurika.trial.remote", name = "enabled", havingValue = "true", matchIfMissing = true)
    public TrialAccessChecker remoteTrialAccessChecker(
            @Value("${jurika.auth.internal-url:http://auth-service:8081}") String urlAuth) {
        log.info("Essai gratuit : etat lu aupres d'auth-service ({})", urlAuth);
        return new RemoteTrialAccessChecker(urlAuth);
    }

    @Bean
    @ConditionalOnBean(TrialAccessChecker.class)
    @ConditionalOnMissingBean(TrialSoftLockFilter.class)
    public TrialSoftLockFilter trialSoftLockFilter(TrialAccessChecker checker, ObjectMapper objectMapper) {
        return new TrialSoftLockFilter(checker, objectMapper);
    }

    @Bean
    @ConditionalOnBean(TrialSoftLockFilter.class)
    public FilterRegistrationBean<TrialSoftLockFilter> enregistrementFiltreEssaiDesactive(TrialSoftLockFilter filtre) {
        FilterRegistrationBean<TrialSoftLockFilter> r = new FilterRegistrationBean<>(filtre);
        r.setEnabled(false);
        return r;
    }

    @Bean
    @ConditionalOnBean({TrialAccessChecker.class, JdbcTemplate.class})
    @ConditionalOnMissingBean(EssaiQuotas.class)
    public EssaiQuotas essaiQuotas(TrialAccessChecker checker, JdbcTemplate jdbc) {
        return new EssaiQuotas(checker, jdbc);
    }

    /** Signale au demarrage un service web sans controle de l'essai. */
    @Bean
    @ConditionalOnMissingBean(TrialAccessChecker.class)
    public Object avertissementEssaiSansControle() {
        log.warn("Essai gratuit : aucun verificateur d'etat (jurika.trial.remote.enabled=false) : "
                + "les controles de l'essai (telechargement, lecture seule, limites) sont INACTIFS dans ce service");
        return new Object();
    }
}
