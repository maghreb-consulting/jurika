package ma.jurika.ticket.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ma.jurika.ticket.domain.model.DossierReaffectation;
import ma.jurika.ticket.domain.model.NatureReaffectation;
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
