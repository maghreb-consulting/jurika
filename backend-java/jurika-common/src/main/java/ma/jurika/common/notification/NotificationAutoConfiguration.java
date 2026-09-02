package ma.jurika.common.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration : si aucun {@link NotificationPublisher} n'est defini par
 * le service, on utilise {@link HttpNotificationPublisher} pointe sur le
 * realtime-service Node.js (defaut http://localhost:3000).
 */
@Configuration(proxyBeanMethods = false)
public class NotificationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public NotificationPublisher notificationPublisher(
            @Value("${jurika.realtime.base-url:http://localhost:3000}") String baseUrl,
            // Token partage serveur-a-serveur (cf .env.local INTERNAL_TOKEN). On lit
            // d'abord la var d'env brute INTERNAL_TOKEN (telle que definie cote Node),
            // sinon la property jurika.realtime.internal-token, sinon vide (= pas d'entete).
            @Value("${INTERNAL_TOKEN:${jurika.realtime.internal-token:}}") String internalToken) {
        return new HttpNotificationPublisher(baseUrl, internalToken);
    }
}
