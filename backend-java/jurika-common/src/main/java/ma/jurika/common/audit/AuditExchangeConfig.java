package ma.jurika.common.audit;

/**
 * Topology RabbitMQ partagee pour l'audit log universel (RG-SAAS-02).
 *
 * <p>Exchange topic {@code audit.events}. Routing key par message :
 * {@code audit.{sourceService}.{action}}. Chaque service expose une queue
 * {@code audit.{sourceService}} liee via {@code audit.{sourceService}.#}
 * (cf. {@code AuditQueueConfig} cote service).
 */
public final class AuditExchangeConfig {

    public static final String EXCHANGE = "audit.events";
    public static final String ROUTING_KEY_PREFIX = "audit.";

    private AuditExchangeConfig() {
    }

    public static String routingKey(String sourceService, String action) {
        String svc = (sourceService == null || sourceService.isBlank()) ? "unknown" : sourceService;
        String act = (action == null || action.isBlank()) ? "unknown" : action.toLowerCase();
        return ROUTING_KEY_PREFIX + svc + "." + act;
    }
}
