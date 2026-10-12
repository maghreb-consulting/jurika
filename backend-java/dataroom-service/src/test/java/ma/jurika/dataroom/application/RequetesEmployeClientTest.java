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
    @Mock ma.jurika.dataroom.domain.port.CourrielsClient courriels;

    DemandesClientService service;

    @BeforeEach
    void setUp() {
        service = new DemandesClientService(repo, dossiers, users, accessLogger, notifier,
                org.mockito.Mockito.mock(DossierArchiveGuard.class), courriels);
        TenantContext.set(WS);
        lenient().when(dossiers.findByWorkspaceIdAndId(WS, DOSSIER)).thenReturn(Optional.of(dossier));
        lenient().when(dossier.getClientId()).thenReturn(CLIENT);
        lenient().when(dossier.getResponsableId()).thenReturn(RESP);
        lenient().when(dossier.getId()).thenReturn(DOSSIER);
        lenient().when(dossier.getRaisonSociale()).thenReturn("ATLAS SARL");
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
        assertThat(s.etatSuivi()).isEqualTo("NON_TRAITEE");
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
        // Lot L5 (E9, RG-COM-07) : reponse recue, a verifier par le cabinet -> en cours.
        assertThat(s.etatSuivi()).isEqualTo("EN_COURS");
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

    // ------------------------------------------------------------------
    // Lot L5 (E9) : RG-COM-08 (employe notifie dans l'application) et
    // RG-COM-09 (client averti par courriel, jamais dans l'application).
    // ------------------------------------------------------------------

    private ma.jurika.common.courriel.CourrielClientEvenement courrielEnvoye() {
        org.mockito.ArgumentCaptor<ma.jurika.common.courriel.CourrielClientEvenement> c =
                org.mockito.ArgumentCaptor.forClass(ma.jurika.common.courriel.CourrielClientEvenement.class);
        org.mockito.Mockito.verify(courriels).demander(c.capture());
        return c.getValue();
    }

    @Test
    void requete_du_cabinet_le_client_est_averti_par_courriel_pas_dans_l_application() {
        service.createRequete(new CreateRequeteRequest("Fournir le RC", "merci", DOSSIER, "PIECE"), RESP, Role.EMPLOYE);
        var c = courrielEnvoye();
        assertThat(c.type()).isEqualTo("REQUETE_CABINET");
        assertThat(c.clientId()).isEqualTo(CLIENT);
        assertThat(c.workspaceId()).isEqualTo(WS);
        assertThat(c.objet()).isEqualTo("Fournir le RC");
        assertThat(c.raisonSociale()).isEqualTo("ATLAS SARL");
        org.mockito.Mockito.verify(notifier, org.mockito.Mockito.never())
                .notifyUser(org.mockito.ArgumentMatchers.eq(CLIENT), any(), any(), any(), any(), any(), any());
    }

    @Test
    void complement_le_client_est_averti_par_courriel() {
        DemandeEntity e = requete("REPONDUE");
        service.complement(e.getId(), new ComplementRequest("page 2"), RESP, Role.EMPLOYE);
        assertThat(courrielEnvoye().type()).isEqualTo("COMPLEMENT_REQUETE");
        org.mockito.Mockito.verifyNoInteractions(notifier);
    }

    @Test
    void cloture_aucune_notification_au_client() {
        DemandeEntity e = requete("REPONDUE");
        service.valider(e.getId(), RESP, Role.EMPLOYE);
        org.mockito.Mockito.verifyNoInteractions(notifier);
        org.mockito.Mockito.verifyNoInteractions(courriels);
    }

    @Test
    void reponse_du_client_le_responsable_est_notifie_dans_l_application() {
        DemandeEntity e = requete("OUVERTE");
        service.repondre(e.getId(), new RepondreRequest("voici"), CLIENT, Role.CLIENT);
        org.mockito.Mockito.verify(notifier).notifyUser(org.mockito.ArgumentMatchers.eq(RESP),
                org.mockito.ArgumentMatchers.eq(WS), org.mockito.ArgumentMatchers.eq("DEMANDE_CLIENT"),
                org.mockito.ArgumentMatchers.eq("Réponse du client à une requête"),
                org.mockito.ArgumentMatchers.contains("ATLAS SARL"), any(), any());
        org.mockito.Mockito.verifyNoInteractions(courriels);
    }

    @Test
    void nouvelle_demande_du_client_le_responsable_est_notifie() {
        service.create(new ma.jurika.dataroom.api.dto.DataroomDtos.CreateDemandeRequest("Attestation", null, DOSSIER),
                CLIENT);
        org.mockito.Mockito.verify(notifier).notifyUser(org.mockito.ArgumentMatchers.eq(RESP),
                org.mockito.ArgumentMatchers.eq(WS), org.mockito.ArgumentMatchers.eq("DEMANDE_CLIENT"),
                org.mockito.ArgumentMatchers.eq("Nouvelle demande du client"),
                org.mockito.ArgumentMatchers.contains("Attestation"), any(), any());
    }

    @Test
    void demande_traitee_le_client_est_averti_par_courriel_une_seule_fois() {
        DemandeEntity e = new DemandeEntity();
        e.setId(UUID.randomUUID());
        e.setWorkspaceId(WS);
        e.setDossierId(DOSSIER);
        e.setDirection("CLIENT_TO_EMPLOYE");
        e.setSujet("Attestation");
        e.setStatut("EN_COURS");
        when(repo.findByWorkspaceIdAndId(WS, e.getId())).thenReturn(Optional.of(e));
        var req = new ma.jurika.dataroom.api.dto.DataroomDtos.UpdateDemandeStatusRequest("TRAITEE", null, null);

        service.updateStatus(e.getId(), req, RESP, Role.EMPLOYE);
        var c = courrielEnvoye();
        assertThat(c.type()).isEqualTo("REPONSE_DEMANDE");
        assertThat(c.objet()).isEqualTo("Attestation");

        // Deja traitee : pas de second courriel.
        service.updateStatus(e.getId(), req, RESP, Role.EMPLOYE);
        org.mockito.Mockito.verify(courriels, org.mockito.Mockito.times(1)).demander(any());
    }
}
