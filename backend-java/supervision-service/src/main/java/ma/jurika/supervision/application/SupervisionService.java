package ma.jurika.supervision.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ma.jurika.common.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * KPIs calcules par requete native sur les tables tickets / dossiers / debours / demandes.
 * <p>
 * Defense-in-depth multi-tenancy : chaque requete filtre EXPLICITEMENT par
 * {@code workspace_id = :ws} en plus de la RLS PostgreSQL. Raison : si le role
 * applicatif Postgres a {@code BYPASSRLS=true} (cas par defaut du conteneur
 * postgres officiel lorsque {@code POSTGRES_USER} cree un superuser), la RLS
 * est court-circuitee et les COUNT(*) cross-workspace fuitent les totaux
 * globaux dans le dashboard. Le filtre explicite garantit l'isolation meme
 * dans cette configuration sous-optimale (voir bug "dashboard montre 85
 * dossiers / 87 tickets" 2026-06-05).
 */
@Service
public class SupervisionService {

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public Map<String, Object> workspaceKpis() {
        UUID ws = TenantContext.get();
        if (ws == null) return Map.of();

        Number tickets = singleNumber(
                "SELECT COUNT(*) FROM tickets WHERE workspace_id = ?1", ws);
        Number ticketsEnCours = singleNumber(
                "SELECT COUNT(*) FROM tickets WHERE workspace_id = ?1 AND statut = 'EN_COURS'", ws);
        Number ticketsClotures = singleNumber(
                "SELECT COUNT(*) FROM tickets WHERE workspace_id = ?1 AND statut = 'CLOTURE'", ws);
        Number ticketsAnnules = singleNumber(
                "SELECT COUNT(*) FROM tickets WHERE workspace_id = ?1 AND statut = 'ANNULE'", ws);
        Number ticketsNouveaux = singleNumber(
                "SELECT COUNT(*) FROM tickets WHERE workspace_id = ?1 AND statut = 'NOUVEAU'", ws);
        Number dossiers = singleNumber(
                "SELECT COUNT(*) FROM entreprise_dossiers WHERE workspace_id = ?1", ws);
        Number dossiersActifs = singleNumber(
                "SELECT COUNT(*) FROM entreprise_dossiers WHERE workspace_id = ?1 AND statut = 'ACTIVE'", ws);
        Number deboursTotal = singleNumber(
                "SELECT COALESCE(SUM(montant_mad),0) FROM ticket_debours WHERE workspace_id = ?1", ws);
        Number demandesNonTraitees = singleNumber(
                "SELECT COUNT(*) FROM dataroom_demandes_client " +
                        "WHERE workspace_id = ?1 AND statut = 'NON_TRAITEE'", ws);

        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("tickets", n(tickets));
        out.put("ticketsNouveaux", n(ticketsNouveaux));
        out.put("ticketsEnCours", n(ticketsEnCours));
        out.put("ticketsClotures", n(ticketsClotures));
        out.put("ticketsAnnules", n(ticketsAnnules));
        out.put("dossiers", n(dossiers));
        out.put("dossiersActifs", n(dossiersActifs));
        out.put("deboursTotalMad",
                deboursTotal == null ? BigDecimal.ZERO : new BigDecimal(deboursTotal.toString()));
        out.put("demandesNonTraitees", n(demandesNonTraitees));
        out.put("computedAt", Instant.now().toString());
        return out;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> ticketsPerType() {
        UUID ws = TenantContext.get();
        if (ws == null) return Collections.emptyList();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT type, COUNT(*) FROM tickets WHERE workspace_id = ?1
                GROUP BY type ORDER BY type
                """).setParameter(1, ws).getResultList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] row : rows) {
            out.add(Map.of("type", row[0], "count", n((Number) row[1])));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> ticketsPerStatut() {
        UUID ws = TenantContext.get();
        if (ws == null) return Collections.emptyList();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT statut, COUNT(*) FROM tickets WHERE workspace_id = ?1 GROUP BY statut
                """).setParameter(1, ws).getResultList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] row : rows) {
            out.add(Map.of("statut", row[0], "count", n((Number) row[1])));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> ticketsPerEmploye() {
        UUID ws = TenantContext.get();
        if (ws == null) return Collections.emptyList();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT u.id, u.first_name, u.last_name,
                       COUNT(t.id) FILTER (WHERE t.statut = 'EN_COURS') AS en_cours,
                       COUNT(t.id) FILTER (WHERE t.statut = 'CLOTURE') AS clotures,
                       COUNT(t.id) FILTER (WHERE t.statut = 'ANNULE')  AS annules
                  FROM users u
                  LEFT JOIN tickets t
                       ON t.assigne_id = u.id AND t.workspace_id = ?1
                 WHERE u.workspace_id = ?1 AND u.role = 'EMPLOYE'
                 GROUP BY u.id, u.first_name, u.last_name
                 ORDER BY clotures DESC
                """).setParameter(1, ws).getResultList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : rows) {
            out.add(Map.of(
                    "userId", r[0],
                    "firstName", r[1] == null ? "" : r[1],
                    "lastName", r[2] == null ? "" : r[2],
                    "enCours", n((Number) r[3]),
                    "clotures", n((Number) r[4]),
                    "annules", n((Number) r[5])));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> ticketsLast30Days() {
        UUID ws = TenantContext.get();
        if (ws == null) return Collections.emptyList();
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT DATE_TRUNC('day', created_at) AS d, COUNT(*) AS c
                  FROM tickets
                 WHERE workspace_id = ?1
                   AND created_at >= NOW() - INTERVAL '30 days'
                 GROUP BY d
                 ORDER BY d
                """).setParameter(1, ws).getResultList();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] r : rows) {
            out.add(Map.of("date", r[0].toString(), "count", n((Number) r[1])));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> platformKpis() {
        // Cross-workspace (utilise par SUPER_ADMIN). TenantContext doit etre vide.
        Number workspaces = singleNumber(
                "SELECT COUNT(*) FROM workspaces WHERE status = 'ACTIVE'", null);
        Number users = singleNumber(
                "SELECT COUNT(*) FROM users WHERE is_active = TRUE", null);
        Number ticketsTotal = singleNumber("SELECT COUNT(*) FROM tickets", null);
        return Map.of(
                "workspacesActifs", n(workspaces),
                "utilisateursActifs", n(users),
                "ticketsTotal", n(ticketsTotal),
                "computedAt", Instant.now().toString());
    }

    private long n(Number num) {
        return num == null ? 0L : num.longValue();
    }

    /**
     * Execute une requete scalaire. Si {@code param} est non-null, il est lie a
     * {@code ?1}. Sinon la requete est executee sans parametre (cas
     * cross-workspace pour les KPIs plateforme).
     */
    private Number singleNumber(String sql, Object param) {
        var q = em.createNativeQuery(sql);
        if (param != null) q.setParameter(1, param);
        @SuppressWarnings("unchecked")
        List<Object> r = q.getResultList();
        if (r.isEmpty()) return 0;
        Object v = r.get(0);
        return v instanceof Number ? (Number) v : 0;
    }

    public Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }

    public List<UUID> emptyList() {
        return Collections.emptyList();
    }
}
