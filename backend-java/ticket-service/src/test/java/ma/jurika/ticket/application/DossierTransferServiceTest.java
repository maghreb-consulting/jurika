package ma.jurika.ticket.application;

import ma.jurika.common.audit.AuditEventEmitter;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.notification.NotificationPublisher;
import ma.jurika.ticket.domain.model.DossierStatut;
import ma.jurika.ticket.domain.model.DossierTransfertRequest;
import ma.jurika.ticket.domain.model.EntrepriseDossier;
import ma.jurika.ticket.domain.model.FormeJuridique;
import ma.jurika.ticket.domain.model.TransfertStatut;
import ma.jurika.ticket.domain.port.DossierRepository;
import ma.jurika.ticket.domain.port.DossierTransfertRequestRepository;
import ma.jurika.ticket.domain.port.MemberDirectory;
import ma.jurika.ticket.domain.port.TicketEventPublisher;
import ma.jurika.ticket.domain.port.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifie le coeur du transfert — Lot Y (2026-07-04) : « seuls les datarooms se
 * transferent, pas les tickets ». Un transfert deplace l'owner durable du dossier
 * ({@code responsable_id}) et ecrit l'audit {@code DOSSIER_TRANSFERE} ; les tickets
 * ne sont plus reassignes (leur {@code assigne_id} reste inchange).
 */
@ExtendWith(MockitoExtension.class)
class DossierTransferServiceTest {

    private static final UUID WS = UUID.randomUUID();
    private static final UUID DOSSIER = UUID.randomUUID();
    private static final UUID ANCIEN = UUID.randomUUID();
    private static final UUID NOUVEAU = UUID.randomUUID();

    @Mock TicketRepository ticketRepository;
    @Mock DossierRepository dossierRepository;
    @Mock DossierTransfertRequestRepository requestRepository;
    @Mock TicketEventPublisher eventPublisher;
    @Mock AuditEventEmitter auditEmitter;
    @Mock NotificationPublisher notificationPublisher;
    @Mock MemberDirectory memberDirectory;

    private DossierTransferService service() {
        return new DossierTransferService(ticketRepository, dossierRepository, requestRepository,
                eventPublisher, auditEmitter, notificationPublisher, memberDirectory);
    }

    @Test
    void acceptTransfer_deplaceLeDataroom_sansToucherLesTickets_etEcritLAudit() {
        // Demande EN_ATTENTE adressee a NOUVEAU (qui accepte).
        DossierTransfertRequest pending = new DossierTransfertRequest(
                UUID.randomUUID(), WS, DOSSIER, ANCIEN, NOUVEAU,
                TransfertStatut.EN_ATTENTE, false, "charge", Instant.now(), null);
        when(requestRepository.findById(WS, pending.id())).thenReturn(Optional.of(pending));
        when(dossierRepository.findById(WS, DOSSIER)).thenReturn(Optional.of(dossier(ANCIEN)));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DossierTransfertRequest resolved = service().acceptTransfer(WS, NOUVEAU, pending.id());

        // 1. Owner durable (le dataroom) mis a jour vers le nouvel employe.
        verify(dossierRepository).updateResponsable(WS, DOSSIER, NOUVEAU);

        // 2. Les tickets ne bougent PLUS : aucune reassignation, aucun horodatage
        //    transferred_at, aucun changement d'assignation ni de statut. Le transfert
        //    ne va meme plus chercher les tickets ouverts du dossier.
        verify(ticketRepository, never()).search(any(), anyInt(), anyInt());
        verify(ticketRepository, never()).markTransferred(any(), any(), any());
        verify(ticketRepository, never()).updateAssignment(any(), any(), any(), any(), any(), any());
        verify(ticketRepository, never()).updateStatut(any(), any(), any(), any(), any());
        // Aucun event ticket.assigned n'est publie depuis le transfert.
        verify(eventPublisher, never()).publishTicketAssigned(any(), any());

        // 3. L'evenement d'audit DOSSIER_TRANSFERE est ecrit (ancien -> nouveau), sans
        //    compteur de tickets reassignes (les tickets ne bougent plus).
        ArgumentCaptor<Map<String, Object>> meta = ArgumentCaptor.forClass(Map.class);
        verify(auditEmitter).emit(eq(WS), eq(NOUVEAU), eq("DOSSIER_TRANSFERE"), eq("dossier"),
                eq(DOSSIER), meta.capture());
        assertThat(meta.getValue())
                .containsEntry("ancienResponsable", ANCIEN.toString())
                .containsEntry("nouveauResponsable", NOUVEAU.toString())
                .doesNotContainKey("ticketsReassignes");

        // 4. Notification persistante a l'INITIATEUR (ANCIEN) : transfert accepte.
        verify(notificationPublisher).notifyUser(eq(ANCIEN), eq(WS), eq("DOSSIER_TRANSFER"),
                any(), any(), eq("/dashboard"), anyMap());
        // 5. Le nouveau responsable (NOUVEAU) est l'acteur de l'acceptation : on ne le
        //    notifie PAS de sa propre action (anti-spam).
        verify(notificationPublisher, never()).notifyUser(eq(NOUVEAU), any(), any(),
                any(), any(), any(), anyMap());

        assertThat(resolved.statut()).isEqualTo(TransfertStatut.ACCEPTE);
        assertThat(resolved.decidedAt()).isNotNull();
    }

    @Test
    void requestTransfer_versUnEmploye_creeLaDemandeEtNotifieLaCible() {
        when(dossierRepository.findById(WS, DOSSIER)).thenReturn(Optional.of(dossier(ANCIEN)));
        when(memberDirectory.roleOf(WS, NOUVEAU)).thenReturn(Optional.of("EMPLOYE"));
        when(requestRepository.existsPendingForDossier(WS, DOSSIER)).thenReturn(false);
        when(requestRepository.save(any())).thenAnswer(inv -> {
            DossierTransfertRequest in = inv.getArgument(0);
            return new DossierTransfertRequest(UUID.randomUUID(), in.workspaceId(), in.dossierId(),
                    in.fromUserId(), in.toUserId(), in.statut(), in.direct(), in.motif(),
                    Instant.now(), null);
        });

        // ANCIEN (responsable courant) propose le dossier a NOUVEAU (EMPLOYE).
        service().requestTransfer(WS, ANCIEN, DOSSIER, NOUVEAU, "surcharge");

        verify(notificationPublisher).notifyUser(eq(NOUVEAU), eq(WS), eq("DOSSIER_TRANSFER"),
                any(), any(), eq("/dashboard"), anyMap());
    }

    @Test
    void requestTransfer_versUnSuperviseur_estRefuse_etNeCreeAucuneDemande() {
        when(dossierRepository.findById(WS, DOSSIER)).thenReturn(Optional.of(dossier(ANCIEN)));
        // La cible est un SUPERVISEUR (oversight only) : transfert interdit.
        when(memberDirectory.roleOf(WS, NOUVEAU)).thenReturn(Optional.of("SUPERVISEUR"));

        assertThatThrownBy(() -> service().requestTransfer(WS, ANCIEN, DOSSIER, NOUVEAU, "surcharge"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("employe");

        // Aucune demande creee, aucune notification, aucun changement.
        verify(requestRepository, never()).save(any());
        verify(notificationPublisher, never()).notifyUser(any(), any(), any(), any(), any(), any(), anyMap());
    }

    @Test
    void requestTransfer_versUneCibleInconnue_estRefuse() {
        when(dossierRepository.findById(WS, DOSSIER)).thenReturn(Optional.of(dossier(ANCIEN)));
        // roleOf vide = membre non interne / inconnu (CLIENT/SUPER_ADMIN/absent).
        when(memberDirectory.roleOf(WS, NOUVEAU)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().requestTransfer(WS, ANCIEN, DOSSIER, NOUVEAU, "surcharge"))
                .isInstanceOf(ValidationException.class);

        verify(requestRepository, never()).save(any());
    }

    @Test
    void rejectTransfer_neChangeNiLeResponsableNiLesTickets() {
        DossierTransfertRequest pending = new DossierTransfertRequest(
                UUID.randomUUID(), WS, DOSSIER, ANCIEN, NOUVEAU,
                TransfertStatut.EN_ATTENTE, false, null, Instant.now(), null);
        when(requestRepository.findById(WS, pending.id())).thenReturn(Optional.of(pending));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DossierTransfertRequest resolved = service().rejectTransfer(WS, NOUVEAU, pending.id());

        verify(dossierRepository, never()).updateResponsable(any(), any(), any());
        verify(ticketRepository, never()).updateAssignment(any(), any(), any(), any(), any(), any());
        verify(auditEmitter, never()).emit(any(), any(), eq("DOSSIER_TRANSFERE"), any(), any(), anyMap());
        assertThat(resolved.statut()).isEqualTo(TransfertStatut.REFUSE);
    }

    // --- helpers ---

    private EntrepriseDossier dossier(UUID responsable) {
        Instant now = Instant.now();
        return new EntrepriseDossier(DOSSIER, WS, "ACME SARL", FormeJuridique.SARL,
                null, null, null, null, null, null, null, null, null, null,
                DossierStatut.ACTIVE, null, responsable, now, now);
    }
}
