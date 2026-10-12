package ma.jurika.ticket.application;

import ma.jurika.common.notification.NotificationPublisher;
import ma.jurika.common.security.TenantContext;
import ma.jurika.ticket.application.DemarcheUseCases.Avancement;
import ma.jurika.ticket.application.DemarcheUseCases.PointAttention;
import ma.jurika.ticket.application.DemarcheUseCases.Vue;
import ma.jurika.ticket.domain.model.EntrepriseDossier;
import ma.jurika.ticket.domain.model.FormeJuridique;
import ma.jurika.ticket.domain.model.Ticket;
import ma.jurika.ticket.domain.model.TicketPriorite;
import ma.jurika.ticket.domain.model.TicketStatut;
import ma.jurika.ticket.domain.model.TicketType;
import ma.jurika.ticket.domain.port.AlertesDelaisRegistre;
import ma.jurika.ticket.domain.port.AlertesDelaisRegistre.TicketASurveiller;
import ma.jurika.ticket.domain.port.DossierRepository;
import ma.jurika.ticket.domain.port.SuperviseursDuCabinet;
import ma.jurika.ticket.domain.port.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lot L5 (E9, RG-COM-08) : notification des delais de demarches a echeance.
 */
@ExtendWith(MockitoExtension.class)
class AlertesDelaisServiceTest {

    static final UUID WS = UUID.randomUUID();
    static final UUID TICKET = UUID.randomUUID();
    static final UUID DOSSIER = UUID.randomUUID();
    static final UUID RESPONSABLE = UUID.randomUUID();
    static final UUID SUPERVISEUR = UUID.randomUUID();
    static final LocalDate ECHEANCE = LocalDate.of(2026, 10, 20);

    @Mock AlertesDelaisRegistre registre;
    @Mock DemarcheUseCases demarches;
    @Mock TicketRepository tickets;
    @Mock DossierRepository dossiers;
    @Mock SuperviseursDuCabinet superviseurs;
    @Mock NotificationPublisher notifier;

    AlertesDelaisService service;

    @BeforeEach
    void setUp() {
        service = new AlertesDelaisService(registre, demarches, tickets, dossiers, superviseurs, notifier);
        lenient().when(registre.ticketsASurveiller()).thenReturn(List.of(new TicketASurveiller(WS, TICKET)));
        lenient().when(tickets.findById(WS, TICKET)).thenReturn(Optional.of(new Ticket(TICKET, WS, "TK-2026-001",
                "Creation", TicketType.CREATION, TicketStatut.DEROULEMENT_DEMARCHE, TicketPriorite.NORMALE,
                DOSSIER, RESPONSABLE, RESPONSABLE, null, null, null, null, null, Instant.now())));
        EntrepriseDossier dossier = EntrepriseDossier.creationStub(WS, "ATLAS SARL", FormeJuridique.SARL, RESPONSABLE);
        lenient().when(dossiers.findById(WS, DOSSIER)).thenReturn(Optional.of(new EntrepriseDossier(DOSSIER, WS,
                dossier.raisonSociale(), dossier.formeJuridique(), null, null, null, null, null, null, null, null,
                null, null, dossier.statut(), null, RESPONSABLE, dossier.createdAt(), dossier.updatedAt())));
        lenient().when(superviseurs.actifs(WS)).thenReturn(List.of(SUPERVISEUR));
        lenient().when(registre.retenirSiNouveau(any(), any(), anyInt(), any(), anyString())).thenReturn(true);
    }

    private void points(PointAttention... p) {
        Avancement av = new Avancement(TicketStatut.DEROULEMENT_DEMARCHE, 3, 4, 0, 0, null, null, List.of(p));
        when(demarches.vue(WS, TICKET)).thenReturn(new Vue("CREATION", TicketStatut.DEROULEMENT_DEMARCHE, List.of(), av));
    }

    @Test
    void echeance_proche_notifie_le_responsable_seul_dans_son_cabinet() {
        points(new PointAttention(23, "Inscription a la taxe professionnelle", "30 jours", ECHEANCE, 10, "APPROCHE"));

        AlertesDelaisService.Bilan bilan = service.detecter();

        assertThat(bilan.notifications()).isEqualTo(1);
        verify(notifier).notifyUser(eq(RESPONSABLE), eq(WS), eq("DEADLINE_DUE"), eq("Échéance proche"),
                contains("échéance le 20/10/2026, dans 10 jours."), eq("/tickets/" + TICKET), anyMap());
        verify(notifier, never()).notifyUser(eq(SUPERVISEUR), any(), any(), any(), any(), any(), any());
    }

    @Test
    void delai_depasse_notifie_aussi_le_superviseur() {
        points(new PointAttention(32, "Affiliation CNSS", "30 jours", ECHEANCE, -2, "DEPASSE"));

        service.detecter();

        verify(notifier).notifyUser(eq(RESPONSABLE), eq(WS), eq("DEADLINE_DUE"), eq("Délai dépassé"),
                contains("dépassée de 2 jours."), any(), anyMap());
        verify(notifier).notifyUser(eq(SUPERVISEUR), eq(WS), eq("DEADLINE_DUE"), eq("Délai dépassé"),
                any(), any(), anyMap());
    }

    @Test
    void un_palier_deja_notifie_ne_l_est_pas_une_seconde_fois() {
        points(new PointAttention(23, "Taxe professionnelle", "30 jours", ECHEANCE, 2, "CRITIQUE"));
        when(registre.retenirSiNouveau(WS, TICKET, 23, ECHEANCE, "CRITIQUE")).thenReturn(false);

        assertThat(service.detecter().notifications()).isZero();
        verify(notifier, never()).notifyUser(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void aucun_point_d_attention_aucune_notification() {
        points();
        service.detecter();
        verify(notifier, never()).notifyUser(any(), any(), any(), any(), any(), any(), any());
        verify(registre, never()).retenirSiNouveau(any(), any(), anyInt(), any(), any());
    }

    @Test
    void le_ticket_est_traite_dans_son_cabinet_puis_le_contexte_est_libere() {
        AtomicReference<UUID> vu = new AtomicReference<>();
        Avancement av = new Avancement(TicketStatut.DEROULEMENT_DEMARCHE, 3, 4, 0, 0, null, null, List.of());
        doAnswer(inv -> {
            vu.set(TenantContext.get());
            return new Vue("CREATION", TicketStatut.DEROULEMENT_DEMARCHE, List.of(), av);
        }).when(demarches).vue(WS, TICKET);

        service.detecter();

        assertThat(vu.get()).isEqualTo(WS);
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void un_ticket_en_erreur_est_compte_et_n_arrete_pas_les_autres() {
        UUID autre = UUID.randomUUID();
        when(registre.ticketsASurveiller()).thenReturn(List.of(
                new TicketASurveiller(WS, autre), new TicketASurveiller(WS, TICKET)));
        when(demarches.vue(WS, autre)).thenThrow(new IllegalStateException("panne"));
        points(new PointAttention(23, "Taxe professionnelle", "30 jours", ECHEANCE, 10, "APPROCHE"));

        AlertesDelaisService.Bilan bilan = service.detecter();

        assertThat(bilan.erreurs()).isEqualTo(1);
        assertThat(bilan.notifications()).isEqualTo(1);
        verify(notifier, times(1)).notifyUser(eq(RESPONSABLE), any(), any(), any(), any(), any(), any());
    }
}
