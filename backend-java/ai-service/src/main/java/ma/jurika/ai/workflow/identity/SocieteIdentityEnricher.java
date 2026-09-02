package ma.jurika.ai.workflow.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Enrichit l'objet {@code societe} d'un payload de génération PV/acte avec l'identité
 * complète du dossier lue en BD (capital, siège, RC, ville du greffe, ICE, IF, nombre de
 * parts, valeur nominale). Point d'enrichissement <b>unique</b> : appelé une seule fois
 * par {@code WorkflowDocumentController} avant le dispatch vers les mappers, donc valable
 * pour <b>tous</b> les workflows PV (approbation, dissolution, liquidation, séance…) et
 * réutilisable pour les phases suivantes.
 *
 * <p><b>Priorité de valeur = BD d'abord</b> (RG du lot) : une valeur BD non vide écrase la
 * valeur homonyme du payload (ex. corrige une {@code villeGreffe} mal remplie côté front).
 * Le formulaire de séance ne sert qu'à <b>compléter</b> ce que la BD n'a pas (données
 * propres à la séance : présence, quorum, bureau…), jamais à remplacer une valeur BD.
 *
 * <p>Les listes {@code associes} / {@code gerants} de la BD ne sont utilisées que pour
 * <b>amorcer</b> la présence quand le formulaire n'a rien fourni (jamais pour écraser une
 * saisie de séance, qui porte l'état présent/absent/mandataire).
 *
 * <p>Best-effort : sans {@code dossierId} dans le payload, ou si l'identité BD est
 * indisponible, le payload est renvoyé inchangé (mode dégradé — 0 régression).
 */
@Component
public class SocieteIdentityEnricher {

    private static final Logger log = LoggerFactory.getLogger(SocieteIdentityEnricher.class);

    /** Clés de l'identité BD à fusionner dans {@code societe} (le reste = listes, traitées à part). */
    private static final Set<String> SOCIETE_KEYS = Set.of(
            "denomination", "formeJuridique", "ice", "ifNumero", "rcNumero",
            "villeGreffe", "rcVille", "capitalChiffres", "capitalSocial",
            "siegeSocial", "adresseSiege", "ville", "nombreParts", "valeurNominalePart");

    private final SocieteIdentityProvider identityProvider;

    public SocieteIdentityEnricher(SocieteIdentityProvider identityProvider) {
        this.identityProvider = identityProvider;
    }

    /**
     * Renvoie une <b>copie</b> du payload dont l'objet {@code societe} (et, si besoin, les
     * listes {@code associes}/{@code gerants}) est enrichi depuis la BD. Le payload d'entrée
     * n'est jamais muté.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> enrich(Map<String, Object> payload, UUID workspaceId) {
        if (payload == null || payload.isEmpty()) return payload;
        UUID dossierId = parseUuid(payload.get("dossierId"));
        if (dossierId == null || workspaceId == null) return payload;

        Map<String, Object> identity = identityProvider.loadIdentity(workspaceId, dossierId);
        if (identity == null || identity.isEmpty()) return payload;

        Map<String, Object> out = new LinkedHashMap<>(payload);

        // 1) societe : BD d'abord (la valeur BD non vide ecrase la valeur du payload).
        Map<String, Object> societe = new LinkedHashMap<>(asMap(payload.get("societe")));
        for (String key : SOCIETE_KEYS) {
            Object bd = identity.get(key);
            if (isPresent(bd)) societe.put(key, bd);
        }
        out.put("societe", societe);

        // 2) associes / gerants : amorcage uniquement si le formulaire n'a rien fourni.
        if (isEmptyList(payload.get("associes")) && isNonEmptyList(identity.get("associes"))) {
            out.put("associes", new ArrayList<>((List<Object>) identity.get("associes")));
        }
        if (isEmptyList(payload.get("gerants")) && isNonEmptyList(identity.get("gerants"))) {
            out.put("gerants", new ArrayList<>((List<Object>) identity.get("gerants")));
        }

        log.debug("societe enrichie depuis BD (dossier={}, {} champs identite)",
                dossierId, identity.size());
        return out;
    }

    private static boolean isPresent(Object v) {
        if (v == null) return false;
        return !(v instanceof String s && s.isBlank());
    }

    private static boolean isEmptyList(Object v) {
        return !(v instanceof List<?> l) || l.isEmpty();
    }

    private static boolean isNonEmptyList(Object v) {
        return v instanceof List<?> l && !l.isEmpty();
    }

    private static UUID parseUuid(Object raw) {
        if (raw == null) return null;
        try {
            String s = String.valueOf(raw).trim();
            return s.isBlank() ? null : UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : new LinkedHashMap<>();
    }
}
