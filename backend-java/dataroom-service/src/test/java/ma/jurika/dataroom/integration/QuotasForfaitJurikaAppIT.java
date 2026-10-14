package ma.jurika.dataroom.integration;

import ma.jurika.common.billing.PlanLimitsService;
import ma.jurika.common.exception.FonctionHorsForfaitException;
import ma.jurika.common.exception.PlanLimitException;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.domain.port.ObjectStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lot L7 (E5), RG-PLN-01, RG-PLN-03, RG-PLN-04 et CDC 4.5, sur le VRAI schema (migrations auth, ticket,
 * dataroom) et en role d'execution jurika_app (RLS active) :
 * <ul>
 *   <li>dossiers comptes dans le mois d'abonnement, une fois par dossier quel que soit le nombre de
 *       tickets ; les tickets du mois precedent ne comptent pas ;</li>
 *   <li>a 100 %, un dossier de plus est bloque, un dossier deja compte passe ;</li>
 *   <li>stockage lu dans la colonne reelle des documents (l'ancien compteur lisait une colonne
 *       absente et rendait 0 en silence) ; ajout bloque au-dela du forfait ;</li>
 *   <li>fonction hors forfait refusee.</li>
 * </ul>
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"})
@ActiveProfiles("it")
class QuotasForfaitJurikaAppIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jurika_it_quotas")
            .withUsername("jurika_it")
            .withPassword("jurika_it_pwd")
            .withInitScript("testcontainers-init.sql");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        SchemaJurikaDb.migrer(POSTGRES);
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", () -> "jurika_app");
        r.add("spring.datasource.password", () -> "jurika_app_it");
        r.add("spring.flyway.user", POSTGRES::getUsername);
        r.add("spring.flyway.password", POSTGRES::getPassword);
    }

    static final long GO = 1024L * 1024L * 1024L;

    private final JdbcTemplate jdbc = SchemaJurikaDb.proprietaire(POSTGRES);
    @Autowired PlanLimitsService limites;
    @Autowired PlatformTransactionManager tm;
    @MockBean ObjectStorage storage;

    UUID ws;
    LocalDate debutMois;

    @BeforeEach
    void seed() {
        ws = UUID.randomUUID();
        SchemaJurikaDb.workspace(jdbc, ws, "Cabinet Quotas", "JUR-Q" + ws.toString().substring(0, 4).toUpperCase());
        LocalDate aujourdhui = LocalDate.now(ZoneId.of("Africa/Casablanca"));
        // Abonnement commence il y a quatre mois, au jour d'aujourd'hui : le mois en cours commence aujourd'hui.
        debutMois = aujourdhui;
        jdbc.update("UPDATE workspaces SET selected_plan = 'essentiel', abonnement_debut = ?, trial_status = NULL WHERE id = ?",
                aujourdhui.minusMonths(4), ws);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private <T> T dansLeCabinet(Supplier<T> f) {
        TenantContext.set(ws);
        try {
            return new TransactionTemplate(tm).execute(st -> f.get());
        } finally {
            TenantContext.clear();
        }
    }

    private UUID dossier(String nom) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO entreprise_dossiers(id, workspace_id, raison_sociale, forme_juridique, responsable_id) "
                + "VALUES (?, ?, ?, 'SARL', '33333333-3333-3333-3333-333333333333')", id, ws, nom);
        return id;
    }

    private void ticket(UUID dossier, OffsetDateTime quand) {
        jdbc.update("""
                INSERT INTO tickets(id, workspace_id, reference, titre, type, statut, dossier_id, created_at, cree_par_id)
                VALUES (?, ?, ?, 'T', 'CREATION', 'DEROULEMENT_DEMARCHE', ?, ?, '33333333-3333-3333-3333-333333333333')
                """, UUID.randomUUID(), ws, "T-" + UUID.randomUUID().toString().substring(0, 8), dossier, quand);
    }

    @Test
    void un_dossier_compte_une_fois_dans_le_mois_d_abonnement() {
        OffsetDateTime maintenant = OffsetDateTime.now();
        UUID a = dossier("ALPHA");
        UUID b = dossier("BETA");
        UUID ancien = dossier("GAMMA");
        ticket(a, maintenant);
        ticket(a, maintenant);
        ticket(b, maintenant);
        ticket(ancien, debutMois.minusDays(3).atStartOfDay(ZoneId.of("Africa/Casablanca")).toOffsetDateTime());

        PlanLimitsService.UsageSnapshot u = dansLeCabinet(() -> limites.snapshot(ws));

        assertThat(u.dossiers()).isEqualTo(2);
        assertThat(u.maxDossiers()).isEqualTo(25);
        assertThat(u.periodeDebut()).isEqualTo(debutMois);
        assertThat(u.forfaitDefini()).isTrue();
    }

    @Test
    void a_100_pourcent_un_dossier_de_plus_est_bloque_un_dossier_compte_passe() {
        OffsetDateTime maintenant = OffsetDateTime.now();
        UUID premier = null;
        for (int i = 0; i < 25; i++) {
            UUID d = dossier("D" + i);
            if (premier == null) premier = d;
            ticket(d, maintenant);
        }
        UUID dejaCompte = premier;
        UUID nouveau = dossier("NOUVEAU");

        assertThatThrownBy(() -> dansLeCabinet(() -> { limites.enforceDossierLimit(ws, nouveau); return null; }))
                .isInstanceOf(PlanLimitException.class).hasMessageContaining("25 dossiers ouverts sur 25");
        assertThatThrownBy(() -> dansLeCabinet(() -> { limites.enforceDossierLimit(ws, null); return null; }))
                .isInstanceOf(PlanLimitException.class);
        assertThatCode(() -> dansLeCabinet(() -> { limites.enforceDossierLimit(ws, dejaCompte); return null; }))
                .doesNotThrowAnyException();
    }

    @Test
    void stockage_lu_dans_la_colonne_reelle_et_ajout_bloque_au_dela_du_forfait() {
        UUID d = dossier("STOCK");
        jdbc.update("""
                INSERT INTO dataroom_documents(id, workspace_id, dossier_id, document_type, title, object_key, filename, size_bytes)
                VALUES (?, ?, ?, 'AUTRE', 'Piece', 'k', 'f.pdf', ?)
                """, UUID.randomUUID(), ws, d, 5 * GO - 100);

        PlanLimitsService.UsageSnapshot u = dansLeCabinet(() -> limites.snapshot(ws));
        assertThat(u.storageMesure()).isTrue();
        assertThat(u.storageBytes()).isEqualTo(5 * GO - 100);

        assertThatThrownBy(() -> dansLeCabinet(() -> { limites.enforceStorageLimit(ws, 101); return null; }))
                .isInstanceOf(PlanLimitException.class);
        assertThatCode(() -> dansLeCabinet(() -> { limites.enforceStorageLimit(ws, 100); return null; }))
                .doesNotThrowAnyException();
    }

    @Test
    void fonction_hors_forfait_refusee() {
        assertThatThrownBy(() -> dansLeCabinet(() -> {
            limites.exigerFonction(ws, ma.jurika.common.billing.FonctionForfait.EDITION_INTEGREE_ACTES);
            return null;
        })).isInstanceOf(FonctionHorsForfaitException.class).hasMessageContaining("forfait Max");
    }
}
