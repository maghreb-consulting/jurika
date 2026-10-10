package ma.jurika.auth.api;

import ma.jurika.auth.application.SetDroitSuppressionDataroomUseCase;
import ma.jurika.auth.domain.port.DroitSuppressionDataroomRepository;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.Role;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Lot L1, page Equipe : l'ecran affiche quels employes ont le droit de suppression. */
class DroitSuppressionDataroomControllerTest {

    @Test
    void la_liste_reprend_les_employes_du_workspace_du_superviseur() {
        UUID ws = UUID.randomUUID();
        UUID employe = UUID.randomUUID();
        DroitSuppressionDataroomRepository droits = mock(DroitSuppressionDataroomRepository.class);
        when(droits.employesAvecLeDroit(ws)).thenReturn(List.of(employe));
        var controleur = new DroitSuppressionDataroomController(mock(SetDroitSuppressionDataroomUseCase.class), droits);

        var reponse = controleur.lister(new AuthenticatedUser(UUID.randomUUID(), ws, "s@x.ma", Role.SUPERVISEUR));

        assertThat(reponse.employesAvecLeDroit()).containsExactly(employe);
    }
}
