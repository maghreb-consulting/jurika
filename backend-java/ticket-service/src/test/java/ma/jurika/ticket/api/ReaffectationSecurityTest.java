package ma.jurika.ticket.api;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static ma.jurika.ticket.api.GardesSuperviseurTicketTest.autorise;
import static ma.jurika.ticket.api.GardesSuperviseurTicketTest.methode;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L1, etape E4 : la reaffectation d'office est l'acte du SUPERVISEUR seul
 * (RG-DOS-03, CDC 3.2) ; l'historique des changements de responsable se lit par le
 * superviseur (observation) et par l'employe (responsable, controle par le service).
 */
class ReaffectationSecurityTest {

    @Test
    void reaffectation_reservee_au_superviseur() {
        Method m = methode(DossierTransferController.class, "reaffecter");
        assertThat(autorise("ROLE_SUPERVISEUR", m)).isTrue();
        assertThat(autorise("ROLE_EMPLOYE", m)).isFalse();
        assertThat(autorise("ROLE_CLIENT", m)).isFalse();
        assertThat(autorise("ROLE_SUPER_ADMIN", m)).isFalse();
    }

    @Test
    void historique_superviseur_et_employe_jamais_le_client() {
        Method m = methode(DossierTransferController.class, "historique");
        assertThat(autorise("ROLE_SUPERVISEUR", m)).isTrue();
        assertThat(autorise("ROLE_EMPLOYE", m)).isTrue();
        assertThat(autorise("ROLE_CLIENT", m)).isFalse();
    }

    @Test
    void rattrapages_et_verification_reserves_au_superviseur() {
        for (String nom : new String[]{"rattrapages", "verifierRattrapage"}) {
            Method m = methode(DossierTransferController.class, nom);
            assertThat(autorise("ROLE_SUPERVISEUR", m)).as(nom).isTrue();
            assertThat(autorise("ROLE_EMPLOYE", m)).as(nom).isFalse();
            assertThat(autorise("ROLE_CLIENT", m)).as(nom).isFalse();
            assertThat(autorise("ROLE_SUPER_ADMIN", m)).as(nom).isFalse();
        }
    }
}

