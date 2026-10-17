package ma.jurika.common.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Libelles francais des codes d'action traces (lot L6b, E9).
 *
 * <p>Source unique cote serveur : la tracabilite du cabinet (dashboard), le journal
 * d'audit et la fiche d'un cabinet du super-admin (auth), le tableau de bord de la
 * plateforme (dashboard) et l'activite du client de la Data Room (dataroom) rendent
 * tous le libelle calcule ici ; le front l'affiche tel quel, sans table a lui.
 *
 * <p>Les libelles sont lus dans deux fichiers de ressources ASCII
 * ({@code libelles-actions-audit.properties}, {@code libelles-acces-client.properties}) :
 * la simulation visuelle du front les relit aussi, pour ne jamais imiter un libelle.
 *
 * <p>Un code sans libelle ne recoit plus un faux libelle fabrique a partir du code
 * (l'ancien repli "login success" masquait l'absence, motif 9) : il est affiche
 * comme "Action enregistr&eacute;e (code X)" et journalise en avertissement.
 * {@code AuditActionLabelsInventaireTest} relit les sources de tous les services et
 * echoue si un code emis n'a pas de libelle.
 */
public final class AuditActionLabels {

    private static final Logger log = LoggerFactory.getLogger(AuditActionLabels.class);

    /** Suffixe ajoute par {@link AuditAspect} quand une methode {@code @Auditable} echoue. */
    static final String SUFFIXE_ECHEC = "_FAILED";

    private static final Map<String, String> ACTIONS = charger("libelles-actions-audit.properties");
    private static final Map<String, String> ACCES_CLIENT = charger("libelles-acces-client.properties");

    /** Codes deja signales sans libelle : un avertissement par code, pas un par ligne lue. */
    private static final Set<String> SIGNALES = ConcurrentHashMap.newKeySet();

    private AuditActionLabels() {}

    /**
     * Libelle d'un code d'action du journal d'audit ({@code audit_log.action}).
     * Un echec d'une methode tracee ({@code CODE_FAILED} sans libelle propre) reprend
     * le libelle de {@code CODE}. Un code inconnu est nomme, jamais deguise.
     */
    public static String label(String action) {
        if (action == null || action.isBlank()) {
            return "";
        }
        String libelle = libelleConnu(action);
        return libelle != null ? libelle : inconnu("audit_log", action);
    }

    /** Libelle d'une action du journal d'acces du client ({@code dataroom_client_access_log.action}). */
    public static String libelleAccesClient(String action) {
        if (action == null || action.isBlank()) {
            return "";
        }
        String libelle = ACCES_CLIENT.get(action);
        return libelle != null ? libelle : inconnu("dataroom_client_access_log", action);
    }

    /** Vrai si le code a un libelle (propre ou derive d'un echec). */
    public static boolean estConnu(String action) {
        return action != null && libelleConnu(action) != null;
    }

    /** Vrai si l'action du journal d'acces du client a un libelle. */
    public static boolean estConnueAccesClient(String action) {
        return action != null && ACCES_CLIENT.containsKey(action);
    }

    /** Codes du journal d'audit qui ont un libelle propre (sans les echecs derives). */
    public static Set<String> codesAvecLibelle() {
        return ACTIONS.keySet();
    }

    /** Actions du journal d'acces du client qui ont un libelle. */
    public static Set<String> actionsAccesClientAvecLibelle() {
        return ACCES_CLIENT.keySet();
    }

    private static String libelleConnu(String action) {
        String libelle = ACTIONS.get(action);
        if (libelle != null) {
            return libelle;
        }
        if (action.endsWith(SUFFIXE_ECHEC)) {
            String base = ACTIONS.get(action.substring(0, action.length() - SUFFIXE_ECHEC.length()));
            if (base != null) {
                return "\u00c9chec de l\u2019action \u00ab\u00a0" + base + "\u00a0\u00bb";
            }
        }
        return null;
    }

    private static String inconnu(String journal, String action) {
        if (SIGNALES.add(journal + ":" + action)) {
            log.warn("audit.libelle.absent journal={} action={} : aucun libelle, code affiche tel quel",
                    journal, action);
        }
        return "Action enregistr\u00e9e (code " + action + ")";
    }

    private static Map<String, String> charger(String ressource) {
        Properties p = new Properties();
        try (InputStream in = AuditActionLabels.class.getResourceAsStream(ressource)) {
            if (in == null) {
                throw new IllegalStateException("Ressource des libelles d'audit absente : " + ressource);
            }
            p.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Ressource des libelles d'audit illisible : " + ressource, e);
        }
        Map<String, String> m = new LinkedHashMap<>();
        for (String cle : p.stringPropertyNames()) {
            String valeur = p.getProperty(cle).trim();
            if (valeur.isEmpty()) {
                throw new IllegalStateException("Libelle vide pour " + cle + " dans " + ressource);
            }
            m.put(cle, valeur);
        }
        return Collections.unmodifiableMap(m);
    }
}
