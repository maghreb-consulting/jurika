package ma.jurika.dataroom.application;

import ma.jurika.common.audit.AuditEventEmitter;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.infrastructure.persistence.SettingsEntity;
import ma.jurika.dataroom.infrastructure.persistence.SettingsJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lot L1, etape E10 (RG-CLI-01) : les permissions du client sont reglees par dossier
 * (consultation, telechargement, depot, envoi de demandes) et chaque modification est
 * tracee avec l'acteur, l'ancienne et la nouvelle valeur. Un champ absent de la requete
 * (ancien ecran) laisse la permission inchangee.
 */
class DataroomSettingsServiceTest {

    private static final UUID WS = UUID.randomUUID();
    private static final UUID DOSSIER = UUID.randomUUID();
    private static final UUID ACTEUR = UUID.randomUUID();

    private final SettingsJpaRepository repo = mock(SettingsJpaRepository.class);
    private final AuditEventEmitter audit = mock(AuditEventEmitter.class);
    private final DataroomSettingsService service = new DataroomSettingsService(repo, audit);
    // Lot L1 (V35) : l'historique de l'acces client s'ecrit par requete native.
    private final jakarta.persistence.EntityManager em = mock(jakarta.persistence.EntityManager.class);
    private final jakarta.persistence.Query insert = mock(jakarta.persistence.Query.class);

    @BeforeEach
    void setUp() {
        TenantContext.set(WS);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "em", em);
        when(em.createNativeQuery(org.mockito.ArgumentMatchers.startsWith("INSERT INTO dataroom_acces_client_historique")))
                .thenReturn(insert);
        when(insert.setParameter(org.mockito.ArgumentMatchers.anyInt(), any())).thenReturn(insert);
        SettingsEntity s = new SettingsEntity();
        s.setDossierId(DOSSIER);
        s.setWorkspaceId(WS);
        s.setAccessStatus("ACTIVE");
        s.setPermDownload(true);
        when(repo.findByDossierIdAndWorkspaceId(DOSSIER, WS)).thenReturn(Optional.of(s));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @SuppressWarnings("unchecked")
    void modification_tracee_avec_ancienne_et_nouvelle_valeur() {
        SettingsEntity r = service.updatePermissions(DOSSIER, ACTEUR, false, null, null, false, null);

        assertThat(r.isPermConsultation()).isFalse();
        assertThat(r.isPermDownload()).isFalse();
        assertThat(r.isPermDemandes()).as("champ absent : inchange").isTrue();
        ArgumentCaptor<Map<String, Object>> meta = ArgumentCaptor.forClass(Map.class);
        verify(audit).emit(eq(WS), eq(ACTEUR), eq("PERMISSIONS_CLIENT_MODIFIEES"), eq("dossier"), eq(DOSSIER),
                meta.capture());
        assertThat((Map<String, Object>) meta.getValue().get("avant"))
                .containsEntry("consultation", true).containsEntry("telechargement", true);
        assertThat((Map<String, Object>) meta.getValue().get("apres"))
                .containsEntry("consultation", false).containsEntry("telechargement", false)
                .containsEntry("demandes", true);
        // Historique de l'ecran (V35) : une ligne PERMISSIONS, ecrite dans la transaction.
        verify(insert).setParameter(4, "PERMISSIONS");
        verify(insert).executeUpdate();
    }

    @Test
    void sans_changement_reel_ni_trace_ni_historique() {
        service.updatePermissions(DOSSIER, ACTEUR, true, null, null, null, null);
        org.mockito.Mockito.verifyNoInteractions(audit, em);
    }

    @Test
    void la_suspension_est_tracee_dans_l_historique() {
        service.toggleSuspension(DOSSIER, ACTEUR, true);
        verify(audit).emit(eq(WS), eq(ACTEUR), eq("ACCES_CLIENT_SUSPENDU"), eq("dossier"), eq(DOSSIER), any());
        verify(insert).setParameter(4, "SUSPENSION");
        verify(insert).executeUpdate();
    }
}
