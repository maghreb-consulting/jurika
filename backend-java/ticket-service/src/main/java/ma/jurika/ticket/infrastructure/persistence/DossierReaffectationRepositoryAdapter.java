package ma.jurika.ticket.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ma.jurika.ticket.domain.model.DossierReaffectation;
import ma.jurika.ticket.domain.model.NatureReaffectation;
import ma.jurika.ticket.domain.model.ReaffectationVue;
import ma.jurika.ticket.domain.port.DossierReaffectationRepository;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Lot L1 : table {@code dossier_reaffectations} (V28), filtre workspace explicite en plus de la RLS. */
@Repository
public class DossierReaffectationRepositoryAdapter implements DossierReaffectationRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public DossierReaffectation enregistrer(DossierReaffectation r) {
        UUID id = UUID.randomUUID();
        Instant maintenant = Instant.now();
        em.createNativeQuery("""
                INSERT INTO dossier_reaffectations
                  (id, workspace_id, dossier_id, ancien_responsable_id, nouveau_responsable_id,
                   nature, auteur_id, transfert_id, motif, created_at)
                VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10)
                """)
                .setParameter(1, id)
                .setParameter(2, r.workspaceId())
                .setParameter(3, r.dossierId())
                .setParameter(4, r.ancienResponsableId())
                .setParameter(5, r.nouveauResponsableId())
                .setParameter(6, r.nature().name())
                .setParameter(7, r.auteurId())
                .setParameter(8, r.transfertId())
                .setParameter(9, r.motif())
                .setParameter(10, Timestamp.from(maintenant))
                .executeUpdate();
        return new DossierReaffectation(id, r.workspaceId(), r.dossierId(), r.ancienResponsableId(),
                r.nouveauResponsableId(), r.nature(), r.auteurId(), r.transfertId(), r.motif(), maintenant);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<DossierReaffectation> lister(UUID workspaceId, UUID dossierId) {
        List<Object[]> lignes = em.createNativeQuery("""
                SELECT id, workspace_id, dossier_id, ancien_responsable_id, nouveau_responsable_id,
                       nature, auteur_id, transfert_id, motif, created_at
                  FROM dossier_reaffectations
                 WHERE workspace_id = ?1 AND dossier_id = ?2
                 ORDER BY created_at, id
                """)
                .setParameter(1, workspaceId)
                .setParameter(2, dossierId)
                .getResultList();
        return lignes.stream().map(l -> new DossierReaffectation(uuid(l[0]), uuid(l[1]), uuid(l[2]), uuid(l[3]),
                uuid(l[4]), NatureReaffectation.valueOf((String) l[5]), uuid(l[6]), uuid(l[7]), (String) l[8],
                instant(l[9]))).toList();
    }

    private static final String VUE = """
            SELECT r.id, r.dossier_id, d.raison_sociale,
                   r.ancien_responsable_id, NULLIF(TRIM(CONCAT(ua.first_name, ' ', ua.last_name)), ''),
                   r.nouveau_responsable_id, NULLIF(TRIM(CONCAT(un.first_name, ' ', un.last_name)), ''),
                   r.nature, r.auteur_id, NULLIF(TRIM(CONCAT(uu.first_name, ' ', uu.last_name)), ''),
                   r.motif, r.created_at,
                   d.responsable_id, NULLIF(TRIM(CONCAT(ur.first_name, ' ', ur.last_name)), ''),
                   r.verifie_par, NULLIF(TRIM(CONCAT(uv.first_name, ' ', uv.last_name)), ''), r.verifie_le
              FROM dossier_reaffectations r
              JOIN entreprise_dossiers d ON d.id = r.dossier_id AND d.workspace_id = r.workspace_id
              LEFT JOIN users ua ON ua.id = r.ancien_responsable_id
              LEFT JOIN users un ON un.id = r.nouveau_responsable_id
              LEFT JOIN users uu ON uu.id = r.auteur_id
              LEFT JOIN users ur ON ur.id = d.responsable_id
              LEFT JOIN users uv ON uv.id = r.verifie_par
            """;

    @Override
    @SuppressWarnings("unchecked")
    public List<ReaffectationVue> listerVues(UUID workspaceId, UUID dossierId) {
        return ((List<Object[]>) em.createNativeQuery(VUE
                        + " WHERE r.workspace_id = ?1 AND r.dossier_id = ?2 ORDER BY r.created_at, r.id")
                .setParameter(1, workspaceId).setParameter(2, dossierId).getResultList())
                .stream().map(DossierReaffectationRepositoryAdapter::vue).toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ReaffectationVue> listerRattrapages(UUID workspaceId) {
        return ((List<Object[]>) em.createNativeQuery(VUE
                        + " WHERE r.workspace_id = ?1 AND r.nature = 'RATTRAPAGE'"
                        + " ORDER BY (r.verifie_le IS NOT NULL), d.raison_sociale, r.id")
                .setParameter(1, workspaceId).getResultList())
                .stream().map(DossierReaffectationRepositoryAdapter::vue).toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public java.util.Optional<ReaffectationVue> marquerVerifie(UUID workspaceId, UUID reaffectationId,
                                                              UUID superviseurId) {
        int n = em.createNativeQuery("""
                        UPDATE dossier_reaffectations SET verifie_par = ?3, verifie_le = NOW()
                         WHERE id = ?1 AND workspace_id = ?2 AND nature = 'RATTRAPAGE'
                        """)
                .setParameter(1, reaffectationId).setParameter(2, workspaceId).setParameter(3, superviseurId)
                .executeUpdate();
        if (n == 0) return java.util.Optional.empty();
        return ((List<Object[]>) em.createNativeQuery(VUE + " WHERE r.workspace_id = ?1 AND r.id = ?2")
                .setParameter(1, workspaceId).setParameter(2, reaffectationId).getResultList())
                .stream().map(DossierReaffectationRepositoryAdapter::vue).findFirst();
    }

    private static ReaffectationVue vue(Object[] l) {
        return new ReaffectationVue(uuid(l[0]), uuid(l[1]), (String) l[2], uuid(l[3]), (String) l[4],
                uuid(l[5]), (String) l[6], NatureReaffectation.valueOf((String) l[7]), uuid(l[8]), (String) l[9],
                (String) l[10], instant(l[11]), uuid(l[12]), (String) l[13], uuid(l[14]), (String) l[15],
                instant(l[16]));
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
