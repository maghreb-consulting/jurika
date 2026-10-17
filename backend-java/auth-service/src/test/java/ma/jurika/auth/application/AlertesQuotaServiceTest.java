package ma.jurika.auth.application;

import ma.jurika.auth.domain.port.AlertesQuotaRegistre;
import ma.jurika.common.billing.PlanLimitsService;
import ma.jurika.common.billing.PlanLimitsService.UsageSnapshot;
import ma.jurika.common.notification.NotificationPublisher;
import ma.jurika.common.security.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lot L5 (E9, RG-COM-08 et RG-PLN-02) : alerte quota approchant au superviseur. */
@ExtendWith(MockitoExtension.class)
class AlertesQuotaServiceTest {

    static final UUID WS = UUID.randomUUID();
    static final UUID SUP = UUID.randomUUID();
    static final long GO = 1024L * 1024L * 1024L;

    @Mock AlertesQuotaRegistre registre;
    @Mock PlanLimitsService limites;
    @Mock NotificationPublisher notifier;

    AlertesQuotaService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
        lenient().when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        ObjectProvider<PlanLimitsService> provider = mock(ObjectProvider.class);
        lenient().when(provider.getIfAvailable()).thenReturn(limites);
        // 2026-10-12 a 23:30 UTC = 2026-10-13 a 00:30 a Casablanca (UTC+1) : le mois reste octobre.
        Clock horloge = Clock.fixed(Instant.parse("2026-10-12T23:30:00Z"), ZoneOffset.UTC);
        service = new AlertesQuotaService(registre, provider, notifier, tm, horloge);
        lenient().when(registre.cabinetsActifs()).thenReturn(List.of(WS));
        lenient().when(registre.superviseursActifs(WS)).thenReturn(List.of(SUP));
        lenient().when(registre.retenirSiNouvelle(any(), anyString(), anyString())).thenReturn(true);
        lenient().when(registre.superAdminsActifs()).thenReturn(List.of(new AlertesQuotaRegistre.SuperAdmin(SA, WS_SA)));
        lenient().when(registre.denominationCabinet(WS)).thenReturn("Cabinet Alpha");
    }

    static final UUID SA = UUID.randomUUID();
    static final UUID WS_SA = UUID.randomUUID();

    /** Lot L7 (E5), RG-PLN-02 : a 80 %, le super-admin est alerte aussi, dans son workspace. */
    @Test
    void a_80_pourcent_le_super_admin_est_alerte_dans_son_workspace() {
        when(limites.snapshot(WS)).thenReturn(usage(1, 6, 40, 50, 0, 20));

        AlertesQuotaService.Bilan bilan = service.detecter();

        assertThat(bilan.notifications()).isEqualTo(2);
        verify(notifier).notifyUser(eq(SA), eq(WS_SA), eq("QUOTA_CABINET_APPROCHANT"),
                contains("Cabinet Alpha"), contains("40 dossiers sur 50"), eq("/admin/workspaces/" + WS), anyMap());
    }

    /** Lot L7 (E5), RG-PLN-01 : la cle de l'alerte est le mois d'abonnement, pas le mois civil. */
    @Test
    void la_periode_est_le_mois_d_abonnement() {
        when(limites.snapshot(WS)).thenReturn(new UsageSnapshot("professionnel", "Professionnel", 0, 2, false,
                45, 50, false, 0, 10, false, true,
                java.time.LocalDate.of(2026, 9, 15), java.time.LocalDate.of(2026, 10, 15), true,
                false, 10L * 1024 * 1024 * 1024));
        service.detecter();
        verify(registre).retenirSiNouvelle(WS, "DOSSIERS", "2026-09");
    }

    private static UsageSnapshot usage(int users, int maxUsers, int dossiers, int maxDossiers,
                                       long octets, int maxGo) {
        return new UsageSnapshot("professionnel", "Professionnel", users, maxUsers, maxUsers < 0,
                dossiers, maxDossiers, maxDossiers < 0, octets, maxGo, maxGo < 0,
                true, java.time.LocalDate.of(2026, 10, 1), java.time.LocalDate.of(2026, 11, 1), true,
                false, maxGo < 0 ? -1L : (long) maxGo * 1024 * 1024 * 1024);
    }

    /** Recette R1 (point 3), RG-ESS-07 : pendant l'essai, l'alerte nomme l'essai, jamais un forfait. */
    @Test
    void pendant_l_essai_l_alerte_nomme_l_essai() {
        UsageSnapshot essai = new UsageSnapshot("essai", "Essai gratuit (fonctions du forfait Professionnel)",
                1, 1, false, 3, 3, false, 450L * 1024 * 1024, 0, false, false,
                java.time.LocalDate.of(2026, 10, 1), java.time.LocalDate.of(2026, 11, 1), true,
                true, 500L * 1024 * 1024);
        List<AlertesQuotaService.Depassement> d = AlertesQuotaService.depassements(essai);
        assertThat(d).extracting(AlertesQuotaService.Depassement::message)
                .allMatch(m -> m.contains("(Essai gratuit (fonctions du forfait Professionnel))"))
                .noneMatch(m -> m.contains("forfait Essentiel"));
        assertThat(d).filteredOn(x -> x.quota().equals("STOCKAGE")).singleElement()
                .extracting(AlertesQuotaService.Depassement::message).asString().contains("450 Mo utilis\u00e9s sur 500 Mo");
    }

    @Test
    void le_seuil_est_celui_du_cahier_des_charges() {
        assertThat(AlertesQuotaService.SEUIL_ALERTE_POURCENT).isEqualTo(80);
    }

    @Test
    void a_80_pourcent_des_dossiers_le_superviseur_est_alerte_dans_l_application() {
        when(limites.snapshot(WS)).thenReturn(usage(1, 6, 80, 100, 0, 20));

        AlertesQuotaService.Bilan bilan = service.detecter();

        assertThat(bilan.notifications()).isEqualTo(2);
        verify(registre).retenirSiNouvelle(WS, "DOSSIERS", "2026-10");
        verify(notifier).notifyUser(eq(SUP), eq(WS), eq("QUOTA_APPROCHANT"), eq("Quota de dossiers presque atteint"),
                contains("80 dossiers sur 100 (forfait Professionnel), soit 80 %"), eq("/app/billing/change-plan"), anyMap());
    }

    @Test
    void sous_le_seuil_aucune_alerte() {
        when(limites.snapshot(WS)).thenReturn(usage(4, 6, 79, 100, 15 * GO, 20));
        service.detecter();
        verify(notifier, never()).notifyUser(any(), any(), any(), any(), any(), any(), any());
        verify(registre, never()).retenirSiNouvelle(any(), any(), any());
    }

    @Test
    void utilisateurs_et_stockage_sont_aussi_surveilles() {
        when(limites.snapshot(WS)).thenReturn(usage(5, 6, 10, 100, 17 * GO, 20));
        service.detecter();
        verify(registre).retenirSiNouvelle(WS, "UTILISATEURS", "2026-10");
        verify(registre).retenirSiNouvelle(WS, "STOCKAGE", "2026-10");
        verify(notifier).notifyUser(eq(SUP), eq(WS), eq("QUOTA_APPROCHANT"), eq("Stockage presque plein"),
                contains("17,0 Go utilisés sur 20 Go"), any(), anyMap());
    }

    @Test
    void un_quota_illimite_n_alerte_jamais() {
        when(limites.snapshot(WS)).thenReturn(usage(500, -1, 9999, -1, 999 * GO, -1));
        service.detecter();
        verify(notifier, never()).notifyUser(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void une_alerte_deja_emise_ce_mois_ci_ne_se_repete_pas() {
        when(limites.snapshot(WS)).thenReturn(usage(1, 6, 90, 100, 0, 20));
        when(registre.retenirSiNouvelle(WS, "DOSSIERS", "2026-10")).thenReturn(false);
        service.detecter();
        verify(notifier, never()).notifyUser(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void l_usage_est_lu_dans_le_cabinet_puis_le_contexte_est_libere() {
        AtomicReference<UUID> vu = new AtomicReference<>();
        when(limites.snapshot(WS)).thenAnswer(inv -> {
            vu.set(TenantContext.get());
            return usage(1, 6, 1, 100, 0, 20);
        });
        service.detecter();
        assertThat(vu.get()).isEqualTo(WS);
        assertThat(TenantContext.get()).isNull();
    }
}
