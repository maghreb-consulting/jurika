package ma.jurika.dataroom.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import ma.jurika.common.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Lot L0, etape E15 (inventaire W3) : le cas d'usage ne pose plus lui-meme le
 * workspace courant. Pose dans le corps d'une methode transactionnelle, il
 * arrivait trop tard pour la RLS (G1) et restait sur le fil apres l'appel,
 * jamais nettoye.
 */
class DeleteDataroomUseCaseTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void ne_modifie_pas_le_workspace_courant() {
        EntityManager em = Mockito.mock(EntityManager.class);
        Query requete = Mockito.mock(Query.class, Mockito.RETURNS_SELF);
        when(requete.getResultList()).thenReturn(java.util.List.of());
        when(em.createNativeQuery(anyString())).thenReturn(requete);
        DeleteDataroomUseCase useCase = new DeleteDataroomUseCase();
        ReflectionTestUtils.setField(useCase, "em", em);

        UUID workspaceDeLaCommande = UUID.randomUUID();
        DeleteDataroomUseCase.Result r = useCase.execute(new DeleteDataroomUseCase.Command(
                workspaceDeLaCommande, UUID.randomUUID(), null, "ip", "ua"));

        assertThat(r.alreadyDeleted()).isTrue();
        assertThat(TenantContext.get()).isNull();
    }
}
