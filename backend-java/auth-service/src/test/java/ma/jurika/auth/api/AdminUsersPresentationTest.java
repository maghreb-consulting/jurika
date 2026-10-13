package ma.jurika.auth.api;

import ma.jurika.auth.api.AdminUsersController.UserRow;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Lot L6 (E6, CDC 3.1) : le compte d'administration de la plateforme s'affiche "JURIKA". */
class AdminUsersPresentationTest {

    private static UserRow ligne(String role) {
        return new UserRow(UUID.randomUUID(), "Super", "Admin", "admin@jurika.ma", "admin@jurika.ma", role,
                "ACTIVE", true, UUID.randomUUID(), "JUR-DEMO1", "Maghreb Consulting (DEMO)", Instant.now(), null);
    }

    @Test
    void le_super_admin_est_presente_sous_le_nom_jurika_sans_son_cabinet_d_hebergement() {
        UserRow vu = ligne("SUPER_ADMIN").pourAffichage();
        assertThat(vu.firstName()).isEqualTo("JURIKA");
        assertThat(vu.lastName()).isEmpty();
        assertThat(vu.workspaceCode()).isEqualTo("JURIKA");
        assertThat(vu.workspaceName()).isEqualTo("Administration de la plateforme");
        assertThat(vu.loginEmail()).isEqualTo("admin@jurika.ma");
    }

    @Test
    void les_autres_comptes_sont_inchanges() {
        UserRow employe = ligne("EMPLOYE");
        assertThat(employe.pourAffichage()).isSameAs(employe);
    }
}
