package ma.jurika.billing.infrastructure.stripe;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Sprint 12 — generateur de cles d'idempotence stables pour les appels Stripe
 * mutables (createCustomer / createCheckoutSession / etc.).
 *
 * <p>RG-BL18 : Idempotency-Key obligatoire sur toutes les mutations.
 * Risque archi : user qui clique 2x rapidement sur "Souscrire" = 2 sessions
 * checkout. La cle est construite a partir de {@code (workspaceId, operation,
 * scope)} de sorte que toute retry dans la fenetre vise la meme cle = pas de
 * doublon cote Stripe (24h cote Stripe API).
 *
 * <p>Format : {@code jurika_<op>_<sha256_first_16>} (lisible cote Stripe
 * dashboard).
 */
public final class IdempotencyKeys {

    private static final ZoneId CASABLANCA = ZoneId.of("Africa/Casablanca");

    private IdempotencyKeys() {}

    /**
     * Cle pour la creation d'un customer Stripe. Stable par workspace
     * (un workspace = 1 customer Stripe a vie).
     */
    public static String forCreateCustomer(UUID workspaceId) {
        return build("customer", workspaceId.toString());
    }

    /**
     * Cle pour la creation d'une checkout session. Inclut la date du jour
     * cote Casablanca pour qu'une retry le lendemain ne soit pas bloquee
     * (cas : user revient le lendemain finir son checkout abandonne).
     */
    public static String forCheckoutSession(UUID workspaceId, String planCode) {
        String scope = workspaceId + ":" + planCode + ":" + LocalDate.now(CASABLANCA);
        return build("checkout", scope);
    }

    /**
     * Cle pour l'ouverture du Customer Portal. Stable par workspace + heure
     * (regenerable rapidement, mais antispam dans la meme heure).
     */
    public static String forCustomerPortal(UUID workspaceId) {
        long hourBucket = System.currentTimeMillis() / (60L * 60L * 1000L);
        return build("portal", workspaceId + ":" + hourBucket);
    }

    /**
     * Cle libre + manuel pour les autres appels.
     */
    public static String forOperation(String operation, String scope) {
        return build(operation, scope);
    }

    /**
     * BUG 8 (2026-06-07) — cle pour le changement de plan d'une subscription
     * existante. Inclut la date du jour + targetPlan pour autoriser
     * "downgrade puis upgrade" le meme jour si besoin. Stripe deduplique sur
     * 24h donc un retry double-clic = meme operation.
     */
    public static String forChangePlan(UUID workspaceId, String targetPlanCode) {
        String scope = workspaceId + ":" + targetPlanCode + ":" + LocalDate.now(CASABLANCA);
        return build("change-plan", scope);
    }

    private static String build(String operation, String scope) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha.digest(scope.getBytes());
            String hex = HexFormat.of().formatHex(digest).substring(0, 16);
            return "jurika_" + operation + "_" + hex;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
