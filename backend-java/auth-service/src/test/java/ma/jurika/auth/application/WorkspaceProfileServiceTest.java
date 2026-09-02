package ma.jurika.auth.application;

import ma.jurika.auth.infrastructure.persistence.WorkspaceEntity;
import ma.jurika.auth.infrastructure.persistence.WorkspaceJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * En-tête PDF (2026-07-14) — champ « nom affiché sur les documents générés » :
 * mise à jour, effacement (repli), non-modification, valeur effective.
 */
class WorkspaceProfileServiceTest {

    private final WorkspaceJpaRepository repo = mock(WorkspaceJpaRepository.class);
    private WorkspaceProfileService service;
    private final UUID ws = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new WorkspaceProfileService(repo);
        when(repo.save(any(WorkspaceEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private WorkspaceEntity workspace(String nomAffiche) {
        WorkspaceEntity w = new WorkspaceEntity();
        w.setId(ws);
        w.setCode("JUR-ABCDE");
        w.setName("Maghreb Consulting");
        w.setContactEmail("contact@cabinet.ma");
        w.setStatus("ACTIVE");
        w.setNomAfficheDocuments(nomAffiche);
        return w;
    }

    @Test
    void definit_le_nom_affiche() {
        when(repo.findById(ws)).thenReturn(Optional.of(workspace(null)));
        var v = service.update(ws, new WorkspaceProfileService.UpdateCommand(
                null, null, "Cabinet Alaoui & Associés", null, null, null, null, null));
        assertThat(v.nomAfficheDocuments()).isEqualTo("Cabinet Alaoui & Associés");
        assertThat(v.documentDisplayName()).isEqualTo("Cabinet Alaoui & Associés");
    }

    @Test
    void vide_efface_et_repli_sur_denomination() {
        when(repo.findById(ws)).thenReturn(Optional.of(workspace("Ancien nom")));
        var v = service.update(ws, new WorkspaceProfileService.UpdateCommand(
                null, null, "", null, null, null, null, null));
        assertThat(v.nomAfficheDocuments()).isNull();
        assertThat(v.documentDisplayName()).isEqualTo("Maghreb Consulting");
    }

    @Test
    void null_laisse_le_nom_affiche_inchange() {
        when(repo.findById(ws)).thenReturn(Optional.of(workspace("Ancien nom")));
        var v = service.update(ws, new WorkspaceProfileService.UpdateCommand(
                "002345678000089", null, null, null, null, null, null, null));
        assertThat(v.nomAfficheDocuments()).isEqualTo("Ancien nom");
        assertThat(v.documentDisplayName()).isEqualTo("Ancien nom");
    }

    @Test
    void get_expose_la_valeur_effective_avec_repli() {
        when(repo.findById(ws)).thenReturn(Optional.of(workspace(null)));
        var v = service.get(ws);
        assertThat(v.nomAfficheDocuments()).isNull();
        assertThat(v.documentDisplayName()).isEqualTo("Maghreb Consulting");
    }
}
