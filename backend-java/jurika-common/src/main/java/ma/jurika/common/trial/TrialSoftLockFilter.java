package ma.jurika.common.trial;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ma.jurika.common.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Sprint 11 TASK 3 — Filter HTTP qui renvoie 402 Payment Required quand
 * le workspace courant est en TRIAL_EXPIRED, sauf pour les endpoints
 * whitelistes (billing/auth/public/trial-status).
 *
 * <p>Active dans auth-service Sprint 11 (cf. {@code TrialFilterConfig}).
 * Importable dans n'importe quel autre microservice qui injecte un bean
 * {@link TrialAccessChecker}.
 *
 * <p>Pattern : {@code OncePerRequestFilter} declenche apres JwtAuthFilter
 * (qui pose le {@link TenantContext}). Le filter lit le workspaceId via
 * TenantContext et delegue au TrialAccessChecker. En cas de
 * {@link Optional#empty()} (workspace sans trial = legacy / SuperAdmin
 * global / endpoints non-tenant), passe le filter (fail-open).
 *
 * <p>RG-SU06 : Trial soft-lock apres expiration : acces lecture admin
 * + billing uniquement (le reste = 402).
 */
public class TrialSoftLockFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TrialSoftLockFilter.class);

    /**
     * Paths whitelistes : pas de check trial. Permet a l'utilisateur
     * en TRIAL_EXPIRED de se logger, voir son statut et upgrader son plan.
     */
    private static final List<String> WHITELIST_PATTERNS = List.of(
            "/api/v1/auth/**",            // login, logout, refresh, password-reset, etc.
            "/api/v1/public/**",          // landing endpoints (leads, pricing)
            "/api/v1/billing/**",         // Sprint 12 placeholder + page upgrade
            "/api/v1/trial/status",       // self-introspection
            "/api/v1/admin/trial/**",     // SuperAdmin extend
            "/actuator/**",
            "/v3/api-docs/**",
            "/swagger-ui/**"
    );

    private final TrialAccessChecker checker;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final ObjectMapper objectMapper;

    public TrialSoftLockFilter(TrialAccessChecker checker, ObjectMapper objectMapper) {
        this.checker = checker;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String pattern : WHITELIST_PATTERNS) {
            if (pathMatcher.match(pattern, path)) return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        UUID workspaceId = TenantContext.get();
        if (workspaceId == null) {
            // pas de contexte tenant (endpoint global, ou auth pas encore propagee)
            chain.doFilter(request, response);
            return;
        }

        Optional<TrialAccessChecker.TrialState> stateOpt;
        try {
            stateOpt = checker.getState(workspaceId);
        } catch (RuntimeException ex) {
            log.warn("TrialAccessChecker.getState({}) a lance {} — fail-open", workspaceId, ex.toString());
            chain.doFilter(request, response);
            return;
        }

        if (stateOpt.isEmpty()) {
            // legacy workspace (created_via_sprint11_wizard=FALSE) → pas de trial → pass
            chain.doFilter(request, response);
            return;
        }

        TrialAccessChecker.TrialState state = stateOpt.get();
        if (!state.isExpired()) {
            chain.doFilter(request, response);
            return;
        }

        // Soft-lock 402
        log.info("Trial expired (workspace={} status={} endsAt={}) → 402 sur {}",
                workspaceId, state.status(), state.endsAt(), request.getRequestURI());

        response.setStatus(HttpStatus.PAYMENT_REQUIRED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Map<String, Object> body = Map.of(
                "error", "TRIAL_EXPIRED",
                "message", "Votre essai gratuit a expire. Activez votre abonnement pour continuer.",
                "upgradeUrl", "/app/billing",
                "trial", Map.of(
                        "status", state.status(),
                        "endsAt", state.endsAt() == null ? null : state.endsAt().toString(),
                        "selectedPlan", state.selectedPlan() == null ? "" : state.selectedPlan()
                )
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
