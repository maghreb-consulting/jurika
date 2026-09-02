package ma.jurika.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Resolvers consumed by Spring Cloud Gateway's {@code RequestRateLimiter}.
 *
 * <ul>
 *   <li>{@code clientKeyResolver} — per-token (authenticated user) bucket. Falls back
 *       to per-IP when no Authorization header is present.</li>
 *   <li>{@code ipKeyResolver} — strict per-IP bucket for sensitive anonymous routes.</li>
 * </ul>
 *
 * <p><b>HIGH-11 (audit 2026-06-02)</b> : la resolution IP utilise maintenant
 * {@code X-Forwarded-For} quand le gateway est derriere un proxy de confiance
 * (Cloudflare, Traefik, Nginx, ALB). Avant ce fix, {@code request.getRemoteAddress()}
 * retournait l'IP du proxy -> tous les users partageaient le meme bucket,
 * rendant le rate-limit inutile en prod.
 *
 * <p>Configuration : {@code jurika.gateway.trusted-proxies} en CSV (defaut vide
 * = pas de XFF lu pour ne pas etre vulnerable au spoofing en dev local). En prod
 * derriere Traefik on-prem : {@code 10.0.0.0/8,172.16.0.0/12,192.168.0.0/16}.
 */
@Configuration
public class RateLimiterConfig {

    private final List<String> trustedProxyCidrs;

    public RateLimiterConfig(@Value("${jurika.gateway.trusted-proxies:}") String trustedProxiesCsv) {
        this.trustedProxyCidrs = trustedProxiesCsv == null || trustedProxiesCsv.isBlank()
                ? List.of()
                : Arrays.stream(trustedProxiesCsv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    @Bean("clientKeyResolver")
    @Primary
    public KeyResolver clientKeyResolver() {
        return exchange -> {
            String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                return Mono.just("token:" + auth.substring(7).hashCode());
            }
            return Mono.just("ip:" + resolveClientIp(exchange.getRequest()));
        };
    }

    @Bean("ipKeyResolver")
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just("ip:" + resolveClientIp(exchange.getRequest()));
    }

    /**
     * HIGH-11 : tente d'extraire la vraie IP client a partir de XFF/X-Real-IP
     * UNIQUEMENT si la connexion vient d'un proxy de confiance. Sinon retourne
     * l'IP directe (pas de spoofing possible par un attaquant externe).
     */
    private String resolveClientIp(ServerHttpRequest request) {
        String directIp = directRemoteIp(request.getRemoteAddress());

        // Pas de trusted proxies configures (dev local) -> on lit pas XFF, on prend l'IP directe.
        if (trustedProxyCidrs.isEmpty()) {
            return directIp;
        }
        // Connexion entrante NON dans la liste trusted -> XFF spoofable, on ignore.
        if (!isTrustedProxy(directIp)) {
            return directIp;
        }
        // XFF chain : on prend l'IP la plus a droite qui N'EST PAS un proxy de confiance
        // (= l'IP client reelle juste avant le 1er hop trusted).
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] hops = xff.split(",");
            for (int i = hops.length - 1; i >= 0; i--) {
                String hop = hops[i].trim();
                if (!hop.isEmpty() && !isTrustedProxy(hop)) {
                    return hop;
                }
            }
        }
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return directIp;
    }

    private static String directRemoteIp(InetSocketAddress address) {
        return Optional.ofNullable(address)
                .map(InetSocketAddress::getAddress)
                .map(InetAddress::getHostAddress)
                .orElse("anonymous");
    }

    /**
     * Match simplifie CIDR : supporte les prefixes exacts ("192.168.1.1") et les
     * /N classiques. Pour V1 on ne supporte que IPv4 (suffisant pour Cloudflare,
     * Traefik on-prem, ALB AWS).
     */
    private boolean isTrustedProxy(String ip) {
        if (ip == null) return false;
        for (String cidr : trustedProxyCidrs) {
            if (ipMatchesCidr(ip, cidr)) return true;
        }
        return false;
    }

    private static boolean ipMatchesCidr(String ip, String cidr) {
        try {
            if (!cidr.contains("/")) {
                return ip.equals(cidr);
            }
            String[] parts = cidr.split("/");
            int prefixLen = Integer.parseInt(parts[1]);
            byte[] cidrBytes = InetAddress.getByName(parts[0]).getAddress();
            byte[] ipBytes = InetAddress.getByName(ip).getAddress();
            if (cidrBytes.length != ipBytes.length) return false;
            int fullBytes = prefixLen / 8;
            int remainderBits = prefixLen % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (cidrBytes[i] != ipBytes[i]) return false;
            }
            if (remainderBits > 0 && fullBytes < cidrBytes.length) {
                int mask = (0xff << (8 - remainderBits)) & 0xff;
                if ((cidrBytes[fullBytes] & mask) != (ipBytes[fullBytes] & mask)) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
