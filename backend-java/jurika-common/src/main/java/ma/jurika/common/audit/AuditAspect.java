package ma.jurika.common.audit;

import ma.jurika.common.audit.AuditEventEmitter.AuditEvent;
import ma.jurika.common.security.AuthenticatedUser;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Aspect qui intercepte les methodes annotees {@link Auditable}.
 * <p>
 * Sprint 2 / TASK 6 :
 * <ul>
 *   <li>Succes : emet {@code action} avec metadata.</li>
 *   <li>Echec (any throwable) : emet {@code action_FAILED} avec error class+message.</li>
 *   <li>Enrichi avec {@code correlationId} (MDC) et {@code sourceService}
 *       ({@code spring.application.name}).</li>
 * </ul>
 * <p>
 * Order eleve pour s'executer APRES @Transactional (audit best-effort post-commit).
 */
@Aspect
@Order(100)
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);
    private static final SpelExpressionParser PARSER = new SpelExpressionParser();
    private static final String FAILED_SUFFIX = "_FAILED";

    private final AuditEventEmitter emitter;
    private final String sourceService;

    public AuditAspect(AuditEventEmitter emitter,
                       @Value("${spring.application.name:jurika}") String sourceService) {
        this.emitter = emitter;
        this.sourceService = sourceService;
    }

    @Around("@annotation(auditable)")
    public Object around(ProceedingJoinPoint pjp, Auditable auditable) throws Throwable {
        try {
            Object result = pjp.proceed();
            emitSuccess(pjp, auditable, result);
            return result;
        } catch (Throwable ex) {
            emitFailure(pjp, auditable, ex);
            throw ex;
        }
    }

    private void emitSuccess(ProceedingJoinPoint pjp, Auditable auditable, Object result) {
        try {
            UUID resourceId = resolveResourceId(pjp, auditable);
            Map<String, Object> metadata = buildMetadata(pjp, auditable, result, null);
            emit(auditable.action(), nullIfBlank(auditable.resourceType()), resourceId, metadata);
        } catch (Exception ex) {
            log.warn("audit.emit success.failed action={} : {}", auditable.action(), ex.getMessage());
        }
    }

    private void emitFailure(ProceedingJoinPoint pjp, Auditable auditable, Throwable thrown) {
        try {
            UUID resourceId = resolveResourceId(pjp, auditable);
            Map<String, Object> metadata = buildMetadata(pjp, auditable, null, thrown);
            emit(auditable.action() + FAILED_SUFFIX,
                    nullIfBlank(auditable.resourceType()), resourceId, metadata);
        } catch (Exception ex) {
            log.warn("audit.emit failure.failed action={} : {}", auditable.action(), ex.getMessage());
        }
    }

    private void emit(String action, String resourceType, UUID resourceId, Map<String, Object> metadata) {
        UUID workspaceId = currentWorkspaceId();
        UUID actorId = currentUserId();
        String correlationId = MDC.get("correlationId");
        AuditEvent event = new AuditEvent(workspaceId, actorId, action, resourceType,
                resourceId, metadata, correlationId, sourceService);
        if (emitter != null) {
            emitter.emit(event);
        } else {
            log.info("audit action={} resource={}#{} workspace={} actor={} correlationId={} meta={}",
                    action, resourceType, resourceId, workspaceId, actorId, correlationId, metadata);
        }
    }

    private UUID currentWorkspaceId() {
        AuthenticatedUser u = currentPrincipal();
        return u == null ? null : u.workspaceId();
    }

    private UUID currentUserId() {
        AuthenticatedUser u = currentPrincipal();
        return u == null ? null : u.userId();
    }

    private AuthenticatedUser currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        Object principal = auth.getPrincipal();
        return (principal instanceof AuthenticatedUser au) ? au : null;
    }

    private UUID resolveResourceId(ProceedingJoinPoint pjp, Auditable auditable) {
        String expr = auditable.resourceIdExpr();
        if (expr == null || expr.isBlank()) return null;
        try {
            MethodSignature sig = (MethodSignature) pjp.getSignature();
            String[] paramNames = sig.getParameterNames();
            Object[] args = pjp.getArgs();
            EvaluationContext ctx = new StandardEvaluationContext();
            if (paramNames != null) {
                for (int i = 0; i < paramNames.length && i < args.length; i++) {
                    ctx.setVariable(paramNames[i], args[i]);
                }
            }
            Object value = PARSER.parseExpression(expr).getValue(ctx);
            if (value instanceof UUID u) return u;
            if (value != null) return UUID.fromString(value.toString());
        } catch (Exception ex) {
            log.debug("audit.resolveResourceId failed expr={} : {}", expr, ex.getMessage());
        }
        return null;
    }

    private Map<String, Object> buildMetadata(ProceedingJoinPoint pjp, Auditable auditable,
                                              Object result, Throwable thrown) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("method", pjp.getSignature().toShortString());
        if (thrown != null) {
            m.put("error", thrown.getClass().getSimpleName());
            if (thrown.getMessage() != null) {
                m.put("errorMessage", safe(thrown.getMessage()));
            }
        }
        if (auditable.includeArgs()) {
            Map<String, Object> args = new HashMap<>();
            MethodSignature sig = (MethodSignature) pjp.getSignature();
            String[] names = sig.getParameterNames();
            Object[] vals = pjp.getArgs();
            if (names != null) {
                for (int i = 0; i < names.length && i < vals.length; i++) {
                    args.put(names[i], safe(vals[i] == null ? null : vals[i].toString()));
                }
            }
            m.put("args", args);
        }
        return m;
    }

    private static Object safe(Object v) {
        if (v == null) return null;
        String s = v.toString();
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }

    private String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
