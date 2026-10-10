package ma.jurika.ticket.api;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static ma.jurika.ticket.api.GardesSuperviseurTicketTest.autorise;
import static ma.jurika.ticket.api.GardesSuperviseurTicketTest.methode;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L1, etape E6 : la note de ticket est strictement interne (RG-TKT-07) :
 * jamais le client ; ecriture par l'employe seul ; lecture du superviseur en
 * observation ; l'equipe JURIKA (SUPER_ADMIN) n'y a pas acces.
 */
class TicketNoteSecurityTest {

    @Test
    void lecture() {
        Method m = methode(TicketNoteController.class, "lire");
        assertThat(autorise("ROLE_EMPLOYE", m)).isTrue();
        assertThat(autorise("ROLE_SUPERVISEUR", m)).isTrue();
        assertThat(autorise("ROLE_CLIENT", m)).isFalse();
        assertThat(autorise("ROLE_SUPER_ADMIN", m)).isFalse();
    }

    @Test
    void ecriture() {
        Method m = methode(TicketNoteController.class, "enregistrer");
        assertThat(autorise("ROLE_EMPLOYE", m)).isTrue();
        assertThat(autorise("ROLE_SUPERVISEUR", m)).isFalse();
        assertThat(autorise("ROLE_CLIENT", m)).isFalse();
        assertThat(autorise("ROLE_SUPER_ADMIN", m)).isFalse();
    }
}
