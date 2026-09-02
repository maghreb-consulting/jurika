package ma.jurika.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marque une methode dont l'execution doit emettre une entree d'audit.
 * <p>
 * Le {@link AuditAspect} intercepte la methode, capture l'utilisateur appelant
 * (depuis SecurityContextHolder), et delegue a {@link AuditEventEmitter}.
 * <p>
 * Reference : AGENT_BRIEF.md section 4.4 (Audit Log) + Killer Feature §4.7 (Activity Feed).
 * <p>
 * Exemple :
 * <pre>{@code
 * @Auditable(action = "TICKET_CREATED", resourceType = "ticket")
 * public Ticket create(...) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /** Code action (UPPER_SNAKE_CASE). Ex: "TICKET_CREATED", "TICKET_CANCELLED", "DOCUMENT_UPLOADED". */
    String action();

    /** Type de ressource cible. Ex: "ticket", "document", "workspace", "user". */
    String resourceType() default "";

    /**
     * Expression SpEL pour extraire l'UUID de la ressource ciblee depuis les arguments.
     * Ex: "#cmd.ticketId" ou "#id". Si vide, la ressource ID sera null.
     */
    String resourceIdExpr() default "";

    /** Si true, capture aussi les arguments (utile pour debug) -- attention donnees sensibles. */
    boolean includeArgs() default false;
}
