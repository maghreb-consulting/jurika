package ma.jurika.ticket.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ma.jurika.ticket.domain.model.TaxeProfessionnelleVersion;
import ma.jurika.ticket.domain.port.TaxeProfessionnelleVersionRepository;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Lot L1 : table {@code dossier_tp_versions} (V30), filtre workspace explicite en plus de la RLS. */
@Repository
public class TaxeProfessionnelleVersionRepositoryAdapter implements TaxeProfessionnelleVersionRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void ajouter(UUID workspaceId, UUID dossierId, String numero, LocalDate dateEffet, UUID auteurId) {
        em.createNativeQuery("""
                INSERT INTO dossier_tp_versions (workspace_id, dossier_id, numero, date_effet, saisi_par, origine)
                VALUES (?1, ?2, ?3, ?4, ?5, 'SAISIE')
                """)
                .setParameter(1, workspaceId)
                .setParameter(2, dossierId)
                .setParameter(3, numero)
                .setParameter(4, dateEffet == null ? null : Date.valueOf(dateEffet))
                .setParameter(5, auteurId)
                .executeUpdate();
    }

    @Override
    public boolean completerDateEffet(UUID workspaceId, UUID dossierId, String numero, LocalDate dateEffet) {
        return em.createNativeQuery("""
                UPDATE dossier_tp_versions SET date_effet = ?4
                 WHERE id = (SELECT id FROM dossier_tp_versions
                              WHERE workspace_id = ?1 AND dossier_id = ?2
                              ORDER BY saisi_le DESC, id DESC LIMIT 1)
                   AND numero = ?3 AND date_effet IS NULL
                """)
                .setParameter(1, workspaceId)
                .setParameter(2, dossierId)
                .setParameter(3, numero)
                .setParameter(4, Date.valueOf(dateEffet))
                .executeUpdate() > 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<TaxeProfessionnelleVersion> lister(UUID workspaceId, UUID dossierId) {
        List<Object[]> lignes = em.createNativeQuery("""
                SELECT id, numero, date_effet, saisi_par, saisi_le, origine FROM dossier_tp_versions
                 WHERE workspace_id = ?1 AND dossier_id = ?2
                 ORDER BY saisi_le, id
                """)
                .setParameter(1, workspaceId)
                .setParameter(2, dossierId)
                .getResultList();
        List<TaxeProfessionnelleVersion> out = new ArrayList<>();
        for (int i = 0; i < lignes.size(); i++) {
            Object[] l = lignes.get(i);
            out.add(new TaxeProfessionnelleVersion(uuid(l[0]), (String) l[1],
                    l[2] == null ? null : (l[2] instanceof Date d ? d.toLocalDate() : LocalDate.parse(l[2].toString())),
                    uuid(l[3]), instant(l[4]), (String) l[5], i == lignes.size() - 1));
        }
        return out;
    }

    private static UUID uuid(Object o) {
        return o == null ? null : (o instanceof UUID u ? u : UUID.fromString(o.toString()));
    }

    private static Instant instant(Object o) {
        if (o instanceof Timestamp t) return t.toInstant();
        if (o instanceof Instant i) return i;
        if (o instanceof java.time.OffsetDateTime d) return d.toInstant();
        return o == null ? null : Instant.parse(o.toString());
    }
}
