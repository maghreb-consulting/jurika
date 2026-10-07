package ma.jurika.ticket.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Seed idempotent de donnees demo (dossiers + tickets) pour le workspace JUR-DEMO1.
 * Execute au demarrage seulement si {@code jurika.demo-seed=true} (default true en dev).
 */
@Component
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final String WORKSPACE_ID = "11111111-1111-1111-1111-111111111111";
    private static final String EMPLOYE_ID = "33333333-3333-3333-3333-333333333333";

    @PersistenceContext
    private EntityManager em;

    @Value("${jurika.demo-seed:true}")
    private boolean enabled;

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            log.info("Demo seeder disabled (jurika.demo-seed=false)");
            return;
        }
        try {
            // Lot L0 (E14, inventaire T1) : le workspace de demonstration est pose
            // AVANT toute lecture. Sous jurika_app, la RLS cache tout sans lui, y
            // compris workspaces (politique workspace_self_access : id = workspace
            // courant) ; le comptage ci-dessous echouait, en silence (non-blocking).
            em.createNativeQuery("SELECT set_config('app.current_workspace_id', ?1, true)")
                    .setParameter(1, WORKSPACE_ID)
                    .getSingleResult();

            // Workspace existe ?
            Number wsCount = (Number) em.createNativeQuery(
                    "SELECT COUNT(*) FROM workspaces WHERE id = ?1")
                    .setParameter(1, java.util.UUID.fromString(WORKSPACE_ID))
                    .getSingleResult();
            if (wsCount.longValue() == 0) {
                log.info("Workspace demo JUR-DEMO1 absent, seed skipped");
                return;
            }

            Number dossierCount = (Number) em.createNativeQuery(
                    "SELECT COUNT(*) FROM entreprise_dossiers WHERE workspace_id = ?1")
                    .setParameter(1, java.util.UUID.fromString(WORKSPACE_ID))
                    .getSingleResult();

            if (dossierCount.longValue() > 0) {
                log.info("Demo data already present (dossiers={}). Base seed skipped, " +
                        "running enrich pass only.", dossierCount);
                enrichDemoDashboardInline();
                return;
            }

            log.info("Seeding demo dossiers and tickets...");

            // 3 dossiers
            // Lot L0 (E14) : CAST(?n AS type) et non ?n::type, que Hibernate 6 refuse
            // (ParameterLabelException) : sur une base vierge, le seed echouait en silence.
            em.createNativeQuery("""
                INSERT INTO entreprise_dossiers
                    (id, workspace_id, raison_sociale, forme_juridique, ice, rc_numero, rc_tribunal,
                     adresse_siege, ville, capital_social_mad, date_constitution, statut)
                VALUES
                    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1'::uuid, CAST(?1 AS uuid),
                     'ATLAS TRADING SARL', 'SARL', '002345678000089', 'RC-CASA-12345', 'Casablanca',
                     '12 Avenue Hassan II, Casablanca', 'Casablanca', 100000.00, '2026-01-14', 'ACTIVE'),
                    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2'::uuid, CAST(?1 AS uuid),
                     'CASA SERVICES SARL AU', 'SARL_AU', '002345678000090', 'RC-CASA-12346', 'Casablanca',
                     '45 Boulevard Mohammed V, Casablanca', 'Casablanca', 50000.00, '2026-01-05', 'ACTIVE'),
                    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3'::uuid, CAST(?1 AS uuid),
                     'MAROC EXPORT PLUS SARL', 'SARL', '002345678000091', 'RC-RABAT-7891', 'Rabat',
                     '8 Rue Allal Ben Abdellah, Rabat', 'Rabat', 200000.00, '2026-03-08', 'ACTIVE')
                """)
                    .setParameter(1, WORKSPACE_ID)
                    .executeUpdate();

            // 5 tickets (refs uniques timestamp)
            long ts = System.currentTimeMillis() % 100000;
            em.createNativeQuery("""
                INSERT INTO tickets
                    (id, workspace_id, reference, titre, type, statut, priorite, dossier_id,
                     assigne_id, cree_par_id, description, deadline, cloture_at, created_at, updated_at)
                VALUES
                    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1'::uuid, CAST(?1 AS uuid),
                     'T-DEMO-' || ?2 || '-1', 'Creation SARL Atlas Trading', 'CREATION', 'GENERATION_DOCUMENTS', 'NORMALE',
                     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1'::uuid,
                     CAST(?3 AS uuid), CAST(?3 AS uuid),
                     'Constitution societe ATLAS TRADING SARL', '2026-06-18'::timestamptz, NULL,
                     NOW() - INTERVAL '10 days', NOW()),
                    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2'::uuid, CAST(?1 AS uuid),
                     'T-DEMO-' || ?2 || '-2', 'Transfert siege CASA Services', 'MODIFICATION', 'DEROULEMENT_DEMARCHE', 'HAUTE',
                     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2'::uuid,
                     CAST(?3 AS uuid), CAST(?3 AS uuid),
                     'Transfert siege social vers Rabat', '2026-06-20'::timestamptz, NULL,
                     NOW() - INTERVAL '5 days', NOW()),
                    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb3'::uuid, CAST(?1 AS uuid),
                     'T-DEMO-' || ?2 || '-3', 'Augmentation capital Atlas', 'MODIFICATION', 'CLOTURE_DOSSIER', 'NORMALE',
                     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1'::uuid,
                     CAST(?3 AS uuid), CAST(?3 AS uuid),
                     'Augmentation capital de 100k a 150k MAD',
                     NOW() - INTERVAL '30 days', NOW() - INTERVAL '30 days',
                     NOW() - INTERVAL '45 days', NOW() - INTERVAL '30 days'),
                    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb4'::uuid, CAST(?1 AS uuid),
                     'T-DEMO-' || ?2 || '-4', 'Creation Maroc Export Plus', 'CREATION', 'CLOTURE_DOSSIER', 'NORMALE',
                     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3'::uuid,
                     CAST(?3 AS uuid), CAST(?3 AS uuid),
                     'Constitution MAROC EXPORT PLUS SARL',
                     NOW() - INTERVAL '60 days', NOW() - INTERVAL '70 days',
                     NOW() - INTERVAL '80 days', NOW() - INTERVAL '70 days'),
                    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb5'::uuid, CAST(?1 AS uuid),
                     'T-DEMO-' || ?2 || '-5', 'Question juridique Atlas', 'MODIFICATION', 'CREATION_TICKET', 'BASSE',
                     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1'::uuid,
                     NULL, CAST(?3 AS uuid),
                     'Demande de conseil sur cession de parts', NULL, NULL,
                     NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days')
                """)
                    .setParameter(1, WORKSPACE_ID)
                    .setParameter(2, String.valueOf(ts))
                    .setParameter(3, EMPLOYE_ID)
                    .executeUpdate();

            log.info("Demo seed complete : 3 dossiers + 5 tickets created");
            enrichDemoDashboardInline();
        } catch (Exception ex) {
            // Lot L0 (E14) : reste non bloquant au demarrage, mais visible : niveau
            // ERROR et exception complete (un seed casse ne doit plus passer inapercu).
            log.error("Demo seed failed (non-blocking)", ex);
        }
    }

    /**
     * Enrichissement idempotent du workspace demo pour rendre le dashboard
     * EMPLOYE non-vide :
     *   1. assigne karim sur les tickets orphelins du workspace (assigne_id IS NULL)
     *   2. seed 5 echeances DGI (TVA mensuelle + IS annuelle) si la table est vide
     *      sur ce workspace -> alimente "Echeances a venir"
     *   3. seed 3 entrees audit_log JURIDIQUE_UPLOADED si aucun upload trace
     *      pour karim -> alimente "Activite Data Room recente"
     * <p>
     * Tout est WHERE workspace_id = ? + ON CONFLICT/IF NOT EXISTS, donc safe
     * a re-executer. Defense-in-depth contre les fuites cross-tenant : aucun
     * filtre RLS implicite, on filtre tout explicitement.
     * <p>
     * Methode {@code @Transactional} portee par l'appelant ({@link #run}). Pas
     * de self-invocation : on inline directement les writes via l'EM existant.
     */
    private void enrichDemoDashboardInline() {
        try {
            UUID wsId = UUID.fromString(WORKSPACE_ID);
            UUID employeId = UUID.fromString(EMPLOYE_ID);

            // ─── 1. Assigner karim sur les tickets orphelins du workspace ─
            int assigned = em.createNativeQuery("""
                    UPDATE tickets SET assigne_id = ?1, updated_at = NOW()
                    WHERE workspace_id = ?2
                      AND assigne_id IS NULL
                      AND statut IN ('CREATION_TICKET','GENERATION_DOCUMENTS','DEROULEMENT_DEMARCHE')
                    """)
                    .setParameter(1, employeId)
                    .setParameter(2, wsId)
                    .executeUpdate();
            if (assigned > 0) {
                log.info("Demo enrich : {} tickets ouverts reassignes a karim", assigned);
            }

            // Lot 1 (2026-09-04) — le seed des echeances DGI (TVA, IS, IR) est
            // retire : ces echeances recurrentes sont abandonnees avec le dossier
            // fiscal. Les echeances affichees viennent desormais des delais legaux
            // portes par les demarches du parcours (referentiel ticket V20/V21).
            // ─── 3. Audit log dataroom pour karim (activite recente) ──────
            Number datroomEvents = (Number) em.createNativeQuery("""
                    SELECT COUNT(*) FROM audit_log
                    WHERE workspace_id = ?1 AND user_id = ?2
                      AND action IN ('JURIDIQUE_UPLOADED','COMPTABLE_UPLOADED',
                                     'FISCAL_UPLOADED','DOCUMENT_PREVIEWED')
                    """)
                    .setParameter(1, wsId)
                    .setParameter(2, employeId)
                    .getSingleResult();
            if (datroomEvents.longValue() == 0) {
                em.createNativeQuery("""
                        INSERT INTO audit_log
                            (workspace_id, user_id, action, entity_type, entity_id,
                             ip_address, user_agent, metadata, created_at, source_service)
                        VALUES
                            (?1, ?2, 'JURIDIQUE_UPLOADED', 'dataroom_document',
                             gen_random_uuid(), '127.0.0.1', 'demo-seeder', '{}'::jsonb,
                             NOW() - INTERVAL '1 day', 'ticket-service'),
                            (?1, ?2, 'DOCUMENT_PREVIEWED', 'dataroom_document',
                             gen_random_uuid(), '127.0.0.1', 'demo-seeder', '{}'::jsonb,
                             NOW() - INTERVAL '2 day', 'ticket-service'),
                            (?1, ?2, 'COMPTABLE_UPLOADED', 'dataroom_document',
                             gen_random_uuid(), '127.0.0.1', 'demo-seeder', '{}'::jsonb,
                             NOW() - INTERVAL '4 day', 'ticket-service')
                        """)
                        .setParameter(1, wsId)
                        .setParameter(2, employeId)
                        .executeUpdate();
                log.info("Demo enrich : 3 audit_log dataroom seedes pour karim");
            }
        } catch (Exception ex) {
            log.error("Demo enrich dashboard failed (non-blocking)", ex);
        }
    }
}
