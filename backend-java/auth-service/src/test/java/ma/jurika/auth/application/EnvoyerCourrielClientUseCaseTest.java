package ma.jurika.auth.application;

import ma.jurika.auth.domain.model.User;
import ma.jurika.auth.domain.model.UserStatus;
import ma.jurika.auth.domain.model.Workspace;
import ma.jurika.auth.domain.model.WorkspaceStatus;
import ma.jurika.auth.domain.port.EmailSender;
import ma.jurika.auth.domain.port.UserRepository;
import ma.jurika.auth.domain.port.WorkspaceRepository;
import ma.jurika.auth.infrastructure.messaging.CourrielsClientListener;
import ma.jurika.common.courriel.CourrielClientEvenement;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Lot L5 (E9, RG-COM-09) : le client est averti par courriel, depuis l'adresse
 * unique de JURIKA, en nommant le cabinet a l'origine du message.
 */
class EnvoyerCourrielClientUseCaseTest {

    static final UUID WS = UUID.randomUUID();
    static final UUID AUTRE_WS = UUID.randomUUID();
    static final UUID CLIENT = UUID.randomUUID();
    static final UUID EMPLOYE = UUID.randomUUID();
    static final UUID DOSSIER = UUID.randomUUID();

    /** Faux EmailSender : memorise les courriels envoyes, ou echoue sur demande. */
    static class FauxEmailSender implements EmailSender {
        record Courriel(String to, String subject, String template, Map<String, Object> vars) {}
        final List<Courriel> envoyes = new ArrayList<>();
        RuntimeException panne;

        @Override
        public void sendTemplated(String to, String subject, String templateName, Map<String, Object> variables) {
            if (panne != null) throw panne;
            envoyes.add(new Courriel(to, subject, templateName, variables));
        }
    }

    UserRepository users = mock(UserRepository.class);
    WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
    FauxEmailSender courriels = new FauxEmailSender();
    EnvoyerCourrielClientUseCase useCase;

    static User user(UUID id, UUID ws, Role role, UserStatus status) {
        return new User(id, ws, "karim@jurika.ma", "karim.c1@jurika.ma", "karim@example.ma", "hash",
                "Karim", "Benali", null, role, null, false, false, null, null, null, null,
                (short) 0, null, null, status, Instant.now());
    }

    @BeforeEach
    void setUp() {
        useCase = new EnvoyerCourrielClientUseCase(users, workspaces, courriels, "https://app.jurika.ma/login");
        when(workspaces.findById(WS)).thenReturn(Optional.of(new Workspace(WS, "JUR-ZC", "Cabinet Zerhouni Conseil",
                "contact@zc.ma", null, WorkspaceStatus.ACTIVE, Instant.now(), Instant.now())));
        when(users.findById(CLIENT)).thenReturn(Optional.of(user(CLIENT, WS, Role.CLIENT, UserStatus.ACTIVE)));
        when(users.findById(EMPLOYE)).thenReturn(Optional.of(user(EMPLOYE, WS, Role.EMPLOYE, UserStatus.ACTIVE)));
    }

    private static CourrielClientEvenement evt(String type, UUID ws, UUID client) {
        return new CourrielClientEvenement(type, ws, DOSSIER, client, "ATLAS SARL", "Copie de la CIN du gérant",
                "/mes-requetes");
    }

    @Test
    void requete_du_cabinet_envoyee_au_client_en_nommant_le_cabinet() {
        useCase.envoyer(evt(CourrielClientEvenement.TYPE_REQUETE_CABINET, WS, CLIENT));

        assertThat(courriels.envoyes).hasSize(1);
        FauxEmailSender.Courriel c = courriels.envoyes.get(0);
        assertThat(c.to()).isEqualTo("karim@example.ma");
        assertThat(c.template()).isEqualTo("client-notification");
        assertThat(c.subject()).startsWith("Cabinet Zerhouni Conseil").contains("ATLAS SARL");
        assertThat(c.vars()).containsEntry("cabinet", "Cabinet Zerhouni Conseil")
                .containsEntry("objet", "Copie de la CIN du gérant")
                .containsEntry("lienUrl", "https://app.jurika.ma/mes-requetes");
        assertThat((String) c.vars().get("annonce")).contains("Cabinet Zerhouni Conseil");
    }

    @Test
    void chaque_type_du_cahier_des_charges_a_son_courriel() {
        for (String type : List.of(CourrielClientEvenement.TYPE_REQUETE_CABINET,
                CourrielClientEvenement.TYPE_COMPLEMENT_REQUETE,
                CourrielClientEvenement.TYPE_DOCUMENT_DISPONIBLE,
                CourrielClientEvenement.TYPE_REPONSE_DEMANDE)) {
            useCase.envoyer(evt(type, WS, CLIENT));
        }
        assertThat(courriels.envoyes).hasSize(4)
                .allSatisfy(c -> assertThat(c.subject()).startsWith("Cabinet Zerhouni Conseil"));
    }

    @Test
    void un_type_inconnu_est_refuse() {
        assertThatThrownBy(() -> useCase.envoyer(evt("AUTRE", WS, CLIENT)))
                .isInstanceOf(EnvoyerCourrielClientUseCase.CourrielClientRefuse.class);
        assertThat(courriels.envoyes).isEmpty();
    }

    @Test
    void un_destinataire_qui_n_est_pas_client_est_refuse() {
        assertThatThrownBy(() -> useCase.envoyer(evt(CourrielClientEvenement.TYPE_REQUETE_CABINET, WS, EMPLOYE)))
                .isInstanceOf(EnvoyerCourrielClientUseCase.CourrielClientRefuse.class)
                .hasMessageContaining("n'est pas un client");
        assertThat(courriels.envoyes).isEmpty();
    }

    @Test
    void un_client_d_un_autre_cabinet_est_refuse() {
        assertThatThrownBy(() -> useCase.envoyer(evt(CourrielClientEvenement.TYPE_REQUETE_CABINET, AUTRE_WS, CLIENT)))
                .isInstanceOf(EnvoyerCourrielClientUseCase.CourrielClientRefuse.class);
        assertThat(courriels.envoyes).isEmpty();
    }

    @Test
    void un_client_desactive_n_est_plus_averti() {
        when(users.findById(CLIENT)).thenReturn(Optional.of(user(CLIENT, WS, Role.CLIENT, UserStatus.INACTIVE)));
        assertThatThrownBy(() -> useCase.envoyer(evt(CourrielClientEvenement.TYPE_DOCUMENT_DISPONIBLE, WS, CLIENT)))
                .isInstanceOf(EnvoyerCourrielClientUseCase.CourrielClientRefuse.class);
    }

    @Test
    void un_echec_d_envoi_n_est_pas_avale_le_message_part_en_file_d_echecs() {
        courriels.panne = new IllegalStateException("relais SMTP injoignable");
        CourrielsClientListener listener = new CourrielsClientListener(useCase);

        assertThatThrownBy(() -> listener.traiter(evt(CourrielClientEvenement.TYPE_REPONSE_DEMANDE, WS, CLIENT)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("relais SMTP injoignable");
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void le_listener_pose_le_cabinet_de_l_evenement() {
        UUID[] vu = new UUID[1];
        EmailSender espion = (to, subject, template, vars) -> vu[0] = TenantContext.get();
        new CourrielsClientListener(new EnvoyerCourrielClientUseCase(users, workspaces, espion, "https://x/login"))
                .traiter(evt(CourrielClientEvenement.TYPE_REQUETE_CABINET, WS, CLIENT));
        assertThat(vu[0]).isEqualTo(WS);
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void racine_de_l_application_deduite_de_l_adresse_de_connexion() {
        assertThat(EnvoyerCourrielClientUseCase.racine("https://app.jurika.ma/login")).isEqualTo("https://app.jurika.ma");
        assertThat(EnvoyerCourrielClientUseCase.racine("http://localhost:5173/login?x=1")).isEqualTo("http://localhost:5173");
    }
}
