package ma.jurika.dataroom.application;

import ma.jurika.common.exception.ConflictException;
import ma.jurika.common.notification.NotificationPublisher;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.api.dto.DataroomDtos.ComplementRequest;
import ma.jurika.dataroom.api.dto.DataroomDtos.CreateRequeteRequest;
import ma.jurika.dataroom.api.dto.DataroomDtos.DemandeSummary;
import ma.jurika.dataroom.api.dto.DataroomDtos.RepondreRequest;
import ma.jurika.dataroom.application.access.ClientAccessLogger;
import ma.jurika.dataroom.infrastructure.persistence.DemandeEntity;
import ma.jurika.dataroom.infrastructure.persistence.DemandeJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewEntity;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Lot AG — machine a etats des requetes EMPLOYE_TO_CLIENT (cloture 2 etapes) :
 *   OUVERTE --(client repond)--> REPONDUE --(employe valide)--> CLOTUREE
 *                                 REPONDUE --(employe +)--> A_COMPLETER --(client)--> REPONDUE
 * Verifie les transitions valides/invalides + les roles (client repond, employe
 * responsable valide/complement).
 */
@ExtendWith(MockitoExtension.class)
class RequetesEmployeClientTest {

    private static final UUID WS = UUID.randomUUID();
    private static final UUID DOSSIER = UUID.randomUUID();
    private static final UUID RESP = UUID.randomUUID();     // employe responsable
    private static final UUID OTHER_EMP = UUID.randomUUID();
    private static final UUID CLIENT = UUID.randomUUID();

    @Mock DemandeJpaRepository repo;
    @Mock DossierViewJpaRepository dossiers;
    @Mock ma.jurika.dataroom.infrastructure.persistence.UserViewJpaRepository users;
    @Mock ClientAccessLogger accessLogger;
    @Mock NotificationPublisher notifier;
    @Mock DossierViewEntity dossier;

    DemandesClientService service;

    @BeforeEach
    void setUp() {
        service = new DemandesClientService(repo, dossiers, users, accessLogger, notifier,
                org.mockito.Mockito.mock(DossierArchiveGuard.class));
        TenantContext.set(WS);
        lenient().when(dossiers.findByWorkspaceIdAndId(WS, DOSSIER)).thenReturn(Optional.of(dossier));
        lenient().when(dossier.getClientId()).thenReturn(CLIENT);
        lenient().when(dossier.getResponsableId()).thenReturn(RESP);
        lenient().when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private DemandeEntity requete(String statut) {
        DemandeEntity e = new DemandeEntity();
        e.setId(UUID.randomUUID());
        e.setWorkspaceId(WS);
        e.setDossierId(DOSSIER);
        e.setDirection("EMPLOYE_TO_CLIENT");
        e.setTypeRequete("PIECE");
        e.setStatut(statut);
        when(repo.findByWorkspaceIdAndId(WS, e.getId())).thenReturn(Optional.of(e));
        return e;
    }

    @Test
    void createRequete_parResponsable_ouvre() {
        DemandeSummary s = service.createRequete(
                new CreateRequeteRequest("Fournir le RC", "merci", DOSSIER, "PIECE"), RESP, Role.EMPLOYE);
        assertThat(s.statut()).isEqualTo("OUVERTE");
        assertThat(s.direction()).isEqualTo("EMPLOYE_TO_CLIENT");
        assertThat(s.typeRequete()).isEqualTo("PIECE");
    }

    @Test
    void createRequete_parEmployeNonResponsable_refuse() {
        assertThatThrownBy(() -> service.createRequete(
                new CreateRequeteRequest("X", "y", DOSSIER, "INFO"), OTHER_EMP, Role.EMPLOYE))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void repondre_parClient_ouverteVersRepondue() {
        DemandeEntity e = requete("OUVERTE");
        DemandeSummary s = service.repondre(e.getId(), new RepondreRequest("voici la piece"), CLIENT, Role.CLIENT);
        assertThat(s.statut()).isEqualTo("REPONDUE");
        assertThat(s.reponduAt()).isNotNull();
        assertThat(s.noteClient()).isEqualTo("voici la piece");
    }

    @Test
    void repondre_aComplererVersRepondue() {
        DemandeEntity e = requete("A_COMPLETER");
        DemandeSummary s = service.repondre(e.getId(), new RepondreRequest("complement"), CLIENT, Role.CLIENT);
        assertThat(s.statut()).isEqualTo("REPONDUE");
    }

    @Test
    void repondre_parNonClient_refuse() {
        DemandeEntity e = requete("OUVERTE");
        assertThatThrownBy(() -> service.repondre(e.getId(), new RepondreRequest("x"), RESP, Role.EMPLOYE))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void repondre_depuisEtatNonActionnable_conflit() {
        DemandeEntity e = requete("CLOTUREE");
        assertThatThrownBy(() -> service.repondre(e.getId(), new RepondreRequest("x"), CLIENT, Role.CLIENT))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void valider_parResponsable_repondueVersCloturee() {
        DemandeEntity e = requete("REPONDUE");
        DemandeSummary s = service.valider(e.getId(), RESP, Role.EMPLOYE);
        assertThat(s.statut()).isEqualTo("CLOTUREE");
        assertThat(s.clotureAt()).isNotNull();
    }

    @Test
    void valider_parClient_refuse() {
        DemandeEntity e = requete("REPONDUE");
        assertThatThrownBy(() -> service.valider(e.getId(), CLIENT, Role.CLIENT))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void valider_depuisOuverte_conflit() {
        DemandeEntity e = requete("OUVERTE");
        assertThatThrownBy(() -> service.valider(e.getId(), RESP, Role.EMPLOYE))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void complement_parResponsable_repondueVersAComplere() {
        DemandeEntity e = requete("REPONDUE");
        DemandeSummary s = service.complement(e.getId(), new ComplementRequest("il manque la page 2"), RESP, Role.EMPLOYE);
        assertThat(s.statut()).isEqualTo("A_COMPLETER");
        assertThat(s.noteInterne()).isEqualTo("il manque la page 2");
    }

    @Test
    void complement_depuisOuverte_conflit() {
        DemandeEntity e = requete("OUVERTE");
        assertThatThrownBy(() -> service.complement(e.getId(), new ComplementRequest("x"), RESP, Role.EMPLOYE))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void requeteOperations_surUneDemandeClientToEmploye_conflit() {
        // Une demande CLIENT_TO_EMPLOYE ne peut pas etre validee comme une requete.
        DemandeEntity e = new DemandeEntity();
        e.setId(UUID.randomUUID());
        e.setWorkspaceId(WS);
        e.setDossierId(DOSSIER);
        e.setDirection("CLIENT_TO_EMPLOYE");
        e.setStatut("NON_TRAITEE");
        when(repo.findByWorkspaceIdAndId(WS, e.getId())).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> service.valider(e.getId(), RESP, Role.EMPLOYE))
                .isInstanceOf(ConflictException.class);
    }
}
