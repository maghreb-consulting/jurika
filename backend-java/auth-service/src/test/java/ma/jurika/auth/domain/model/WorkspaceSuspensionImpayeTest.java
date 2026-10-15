package ma.jurika.auth.domain.model;

import ma.jurika.common.security.Role;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L7 (E8), article 4.4 du contrat : un workspace suspendu pour impaye s'ouvre a son seul
 * superviseur (payer l'echeance) ; une suspension decidee par le super-admin ne s'ouvre a personne.
 */
class WorkspaceSuspensionImpayeTest {

    private static Workspace ws(WorkspaceStatus statut, String motif) {
        return new Workspace(UUID.randomUUID(), "JUR-SUSP1", "Cabinet", "c@x.invalid", null, statut,
                Instant.now(), Instant.now(), motif);
    }

    @Test
    void suspendu_pour_impaye_seul_le_superviseur_se_connecte() {
        Workspace w = ws(WorkspaceStatus.SUSPENDED, Workspace.SUSPENSION_IMPAYE);
        assertThat(w.isSuspenduImpaye()).isTrue();
        assertThat(w.ouvertALaConnexion()).isTrue();
        assertThat(w.accepteConnexion(Role.SUPERVISEUR)).isTrue();
        assertThat(w.accepteConnexion(Role.EMPLOYE)).isFalse();
        assertThat(w.accepteConnexion(Role.CLIENT)).isFalse();
        assertThat(w.isArchived()).isFalse();
    }

    @Test
    void suspendu_par_decision_personne_ne_se_connecte() {
        Workspace w = ws(WorkspaceStatus.SUSPENDED, null);
        assertThat(w.isSuspenduImpaye()).isFalse();
        assertThat(w.ouvertALaConnexion()).isFalse();
        assertThat(w.accepteConnexion(Role.SUPERVISEUR)).isFalse();
    }

    @Test
    void un_motif_sans_statut_suspendu_n_ouvre_rien_de_plus() {
        Workspace w = ws(WorkspaceStatus.ACTIVE, Workspace.SUSPENSION_IMPAYE);
        assertThat(w.isSuspenduImpaye()).isFalse();
        assertThat(w.accepteConnexion(Role.EMPLOYE)).isTrue();
    }
}
