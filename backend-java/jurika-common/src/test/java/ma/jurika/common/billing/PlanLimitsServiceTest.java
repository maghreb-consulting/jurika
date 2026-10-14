package ma.jurika.common.billing;

import ma.jurika.common.exception.FonctionHorsForfaitException;
import ma.jurika.common.exception.PlanLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lot L7 (E5), RG-PLN-01, RG-PLN-03, RG-PLN-04, CDC 4.5 : quotas par mois d'abonnement, blocage a
 * 100 %, fonctions par forfait.
 */
class PlanLimitsServiceTest {

    static final UUID WS = UUID.randomUUID();
    static final UUID DOSSIER = UUID.randomUUID();
    static final long GO = 1024L * 1024L * 1024L;

    JdbcTemplate jdbc;
    PlanLimitsService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        // 2026-10-14 a midi UTC ; abonnement commence le 15/09 : mois du 15/09 au 15/10.
        service = new PlanLimitsService(jdbc, true, Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC));
    }

    private void forfait(String code, String essai) {
        Map<String, Object> ligne = new HashMap<>();
        ligne.put("selected_plan", code);
        ligne.put("abonnement_debut", Date.valueOf(LocalDate.of(2026, 9, 15)));
        ligne.put("trial_status", essai);
        when(jdbc.queryForList(contains("FROM workspaces"), eq(WS))).thenReturn(List.of(ligne));
    }

    private void dossiersDuMois(int n) {
        when(jdbc.queryForObject(contains("COUNT(DISTINCT dossier_id)"), eq(Integer.class), eq(WS), any(), any()))
                .thenReturn(n);
    }

    @Test
    void les_dossiers_sont_comptes_dans_le_mois_d_abonnement() {
        forfait("essentiel", null);
        dossiersDuMois(3);
        when(jdbc.queryForObject(contains("FROM users"), eq(Integer.class), eq(WS))).thenReturn(1);
        when(jdbc.queryForObject(contains("to_regclass"), eq(Boolean.class))).thenReturn(true);
        when(jdbc.queryForObject(contains("FROM dataroom_documents"), eq(Long.class), eq(WS))).thenReturn(0L);
        when(jdbc.queryForObject(contains("FROM dataroom_depots"), eq(Long.class), eq(WS))).thenReturn(0L);

        PlanLimitsService.UsageSnapshot u = service.snapshot(WS);

        assertThat(u.dossiers()).isEqualTo(3);
        assertThat(u.maxDossiers()).isEqualTo(25);
        assertThat(u.periodeDebut()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(u.periodeFin()).isEqualTo(LocalDate.of(2026, 10, 15));
        ArgumentCaptor<Object> debut = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> fin = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).queryForObject(contains("COUNT(DISTINCT dossier_id) FROM tickets"), eq(Integer.class), eq(WS),
                debut.capture(), fin.capture());
        // Minuit a Casablanca (UTC+1 en octobre 2026) : 14/09 a 23:00 UTC.
        assertThat(((Timestamp) debut.getValue()).toInstant()).isEqualTo(Instant.parse("2026-09-14T23:00:00Z"));
        assertThat(((Timestamp) fin.getValue()).toInstant()).isEqualTo(Instant.parse("2026-10-14T23:00:00Z"));
    }

    @Test
    void a_100_pourcent_un_dossier_de_plus_est_bloque_avec_le_changement_de_forfait_propose() {
        forfait("essentiel", null);
        dossiersDuMois(25);

        assertThatThrownBy(() -> service.enforceDossierLimit(WS, null))
                .isInstanceOf(PlanLimitException.class)
                .hasMessageContaining("25 dossiers ouverts sur 25")
                .hasMessageContaining("changez de forfait");
        verify(jdbc).queryForObject(contains("pg_advisory_xact_lock"), eq(Object.class), anyString());
    }

    @Test
    void un_dossier_deja_compte_ce_mois_ci_reste_utilisable() {
        forfait("essentiel", null);
        when(jdbc.queryForObject(contains("SELECT EXISTS"), eq(Boolean.class), eq(WS), eq(DOSSIER), any(), any()))
                .thenReturn(true);

        assertThatCode(() -> service.enforceDossierLimit(WS, DOSSIER)).doesNotThrowAnyException();
    }

    @Test
    void sous_le_quota_l_ouverture_passe() {
        forfait("professionnel", null);
        dossiersDuMois(49);
        assertThatCode(() -> service.enforceDossierLimit(WS, null)).doesNotThrowAnyException();
    }

    @Test
    void stockage_plein_ajout_bloque() {
        forfait("essentiel", null);
        when(jdbc.queryForObject(contains("to_regclass"), eq(Boolean.class))).thenReturn(true);
        when(jdbc.queryForObject(contains("FROM dataroom_documents"), eq(Long.class), eq(WS))).thenReturn(5 * GO - 10);
        when(jdbc.queryForObject(contains("FROM dataroom_depots"), eq(Long.class), eq(WS))).thenReturn(0L);

        assertThatThrownBy(() -> service.enforceStorageLimit(WS, 11))
                .isInstanceOf(PlanLimitException.class)
                .hasMessageContaining("consultation et le t\u00e9l\u00e9chargement restent possibles");
        assertThatCode(() -> service.enforceStorageLimit(WS, 10)).doesNotThrowAnyException();
    }

    /** La taille des documents est la colonne size_bytes (l'ancien compteur lisait une colonne absente). */
    @Test
    void stockage_lu_dans_la_colonne_reelle() {
        forfait("essentiel", null);
        when(jdbc.queryForObject(contains("to_regclass"), eq(Boolean.class))).thenReturn(true);
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(WS))).thenReturn(0L);
        service.enforceStorageLimit(WS, 1);
        verify(jdbc).queryForObject(eq("SELECT COALESCE(SUM(size_bytes), 0) FROM dataroom_documents WHERE workspace_id = ?"),
                eq(Long.class), eq(WS));
    }

    @Test
    void stockage_non_mesurable_n_est_pas_un_zero() {
        forfait("essentiel", null);
        when(jdbc.queryForObject(contains("to_regclass"), eq(Boolean.class))).thenReturn(false);
        assertThatThrownBy(() -> service.enforceStorageLimit(WS, 1)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fonction_hors_forfait_refusee_par_le_serveur() {
        forfait("essentiel", null);
        assertThatThrownBy(() -> service.exigerFonction(WS, FonctionForfait.MESSAGERIE_INTERNE))
                .isInstanceOf(FonctionHorsForfaitException.class)
                .hasMessageContaining("forfait Professionnel");
    }

    @Test
    void essai_en_cours_fonctions_du_professionnel() {
        forfait("essentiel", "TRIAL_ACTIVE");
        assertThat(service.fonctionIncluse(WS, FonctionForfait.ALERTES_DELAIS)).isTrue();
        assertThat(service.fonctionIncluse(WS, FonctionForfait.EDITION_INTEGREE_ACTES)).isFalse();
    }

    @Test
    void forfait_inconnu_en_base_refuse_au_lieu_d_etre_rabattu() {
        forfait("business", null);
        assertThatThrownBy(() -> service.enforceDossierLimit(WS, null)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("business");
    }

    @Test
    void forfait_absent_signale() {
        forfait(null, null);
        when(jdbc.queryForObject(contains("to_regclass"), eq(Boolean.class))).thenReturn(false);
        assertThat(service.snapshot(WS).forfaitDefini()).isFalse();
        assertThat(service.snapshot(WS).storageMesure()).isFalse();
    }
}
