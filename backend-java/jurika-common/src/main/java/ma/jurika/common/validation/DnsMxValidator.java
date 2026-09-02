package ma.jurika.common.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;
import java.util.regex.Pattern;

/**
 * Validation reelle d'un email via DNS MX lookup.
 * <p>
 * Etapes :
 * <ol>
 *     <li>Verification syntaxique (RFC-5322 simplifie)</li>
 *     <li>Verification DNS MX du domaine (au moins 1 MX record valide)</li>
 *     <li>Fallback : verification A record du domaine si pas de MX (RFC-5321 §5)</li>
 * </ol>
 * <p>
 * Cette validation peut etre lente (lookup DNS) -- utiliser un timeout strict.
 * En cas d'echec reseau, le validator est <b>permissif</b> (retourne valid)
 * pour ne pas bloquer une inscription legitime.
 */
@Component
public class DnsMxValidator {

    private static final Logger log = LoggerFactory.getLogger(DnsMxValidator.class);

    private static final Pattern EMAIL_REGEX = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@([A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,}$"
    );

    private final boolean enabled;
    private final int dnsTimeoutMs;

    public DnsMxValidator(@Value("${jurika.validation.dns-mx-enabled:true}") boolean enabled,
                          @Value("${jurika.validation.dns-timeout-ms:3000}") int dnsTimeoutMs) {
        this.enabled = enabled;
        this.dnsTimeoutMs = dnsTimeoutMs;
    }

    /**
     * Resultat du check : {@code valid()} indique si l'email est utilisable.
     * {@code reason()} donne un code machine ({@code FORMAT_INVALID}, {@code DOMAIN_NOT_FOUND}, {@code OK}).
     */
    public record Result(boolean valid, String reason) {
        public static Result ok()                       { return new Result(true,  "OK"); }
        public static Result formatInvalid()            { return new Result(false, "EMAIL_FORMAT_INVALID"); }
        public static Result domainNotFound(String d)   { return new Result(false, "EMAIL_DOMAIN_NOT_FOUND:" + d); }
    }

    public Result validate(String email) {
        if (email == null || email.isBlank()) return Result.formatInvalid();
        if (!EMAIL_REGEX.matcher(email).matches()) return Result.formatInvalid();
        if (!enabled) return Result.ok();

        String domain = email.substring(email.indexOf('@') + 1).toLowerCase();
        return hasMxOrARecord(domain) ? Result.ok() : Result.domainNotFound(domain);
    }

    private boolean hasMxOrARecord(String domain) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", String.valueOf(dnsTimeoutMs));
        env.put("com.sun.jndi.dns.timeout.retries", "1");

        DirContext ctx = null;
        try {
            ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes(domain, new String[]{"MX", "A"});
            Attribute mx = attrs.get("MX");
            if (mx != null && mx.size() > 0) return true;
            Attribute a = attrs.get("A");
            return a != null && a.size() > 0;
        } catch (NamingException e) {
            log.warn("DNS lookup echec pour '{}' : {}. Permissif : on autorise l'inscription.", domain, e.getMessage());
            // permissif : si DNS down, on n'aimerait pas bloquer une inscription legitime
            return true;
        } finally {
            if (ctx != null) {
                try { ctx.close(); } catch (NamingException ignore) {}
            }
        }
    }
}
