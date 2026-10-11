package ma.jurika.ticket.infrastructure.persistence;

import ma.jurika.ticket.domain.port.FicheCloture;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lot L4c (E2) : identifiants du ticket (magasin des variables) reportes sur la fiche du
 * dossier a la cloture, colonne vide seulement. La taxe professionnelle, versionnee
 * (RG-FIC-02), se saisit dans la fiche : elle n'est pas reprise ici.
 */
@Repository
public class FicheClotureJdbc implements FicheCloture {

    /** Variable du corpus -> colonne de entreprise_dossiers. */
    static final Map<String, String> COLONNES = new LinkedHashMap<>();

    static {
        COLONNES.put("RC_NUMERO", "rc_numero");
        COLONNES.put("ICE", "ice");
        COLONNES.put("IDENTIFIANT_FISCAL", "identifiant_fiscal");
        COLONNES.put("CNSS_NUMERO", "cnss");
    }

    private final JdbcTemplate jdbc;

    public FicheClotureJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Resultat mettreAJour(UUID workspaceId, UUID ticketId) {
        List<String> repris = new ArrayList<>();
        List<String> ecarts = new ArrayList<>();
        List<UUID> dossier = jdbc.queryForList("SELECT dossier_id FROM tickets WHERE id = ? AND workspace_id = ?",
                UUID.class, ticketId, workspaceId);
        if (dossier.isEmpty() || dossier.get(0) == null) return new Resultat(repris, ecarts);
        UUID dossierId = dossier.get(0);
        Map<String, String> donnees = new LinkedHashMap<>();
        for (Map<String, Object> v : jdbc.queryForList("""
                SELECT variable, valeur FROM dossier_variables
                WHERE workspace_id = ? AND ticket_id = ? AND boucle IS NULL AND variable = ANY (?)
                """, workspaceId, ticketId, COLONNES.keySet().toArray(new String[0]))) {
            Object val = v.get("valeur");
            if (val != null && !String.valueOf(val).isBlank()) donnees.put((String) v.get("variable"), String.valueOf(val).trim());
        }
        for (Map.Entry<String, String> e : donnees.entrySet()) {
            String colonne = COLONNES.get(e.getKey());
            String actuelle = jdbc.queryForObject("SELECT " + colonne + " FROM entreprise_dossiers WHERE id = ? AND workspace_id = ?",
                    String.class, dossierId, workspaceId);
            if (actuelle == null || actuelle.isBlank()) {
                jdbc.update("UPDATE entreprise_dossiers SET " + colonne + " = ?, updated_at = now() WHERE id = ? AND workspace_id = ?",
                        e.getValue(), dossierId, workspaceId);
                repris.add(e.getKey());
            } else if (!actuelle.trim().equals(e.getValue())) {
                ecarts.add(e.getKey() + " : fiche \u00ab " + actuelle + " \u00bb, ticket \u00ab " + e.getValue() + " \u00bb");
            }
        }
        return new Resultat(repris, ecarts);
    }
}
