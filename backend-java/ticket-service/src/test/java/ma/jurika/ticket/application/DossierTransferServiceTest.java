package ma.jurika.ticket.application;

import ma.jurika.common.audit.AuditEventEmitter;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.notification.NotificationPublisher;
import ma.jurika.ticket.domain.model.DossierReaffectation;
import ma.jurika.ticket.domain.model.DossierStatut;
import ma.jurika.ticket.domain.model.DossierTransfertRequest;
import ma.jurika.ticket.domain.model.EntrepriseDossier;
import ma.jurika.ticket.domain.model.FormeJuridique;
import ma.jurika.ticket.domain.model.NatureReaffectation;
import ma.jurika.ticket.domain.model.TransfertStatut;
import ma.jurika.ticket.domain.port.DossierReaffectationRepository;
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
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lot L1 (E4) : coeur du transfert et de la reaffectation d'office.
 *
 * <p>RG-DOS-02 : le transfert accepte porte sur le dossier ET tous ses tickets
 * (RG-TKT-08 : les tickets suivent leur dossier) ; il est trace (auteur, ancien et
 * nouveau responsable, nature ACCEPTEE). Le Lot Y (2026-07-04, "seuls les datarooms
 * se transferent") contredisait le cahier des charges, qui fait foi.
 * RG-DOS-03 : le superviseur reaffecte d'office (nature FORCEE), trace et notifie.
 */
@ExtendWith(MockitoExtension.class)
class DossierTransferServiceTest {

    private static final UUID WS = UUID.randomUUID();
    private static final UUID DOSSIER = UUID.randomUUID();
    private static final UUID ANCIEN = UUID.randomUUID();
    private static final UUID NOUVEAU = UUID.randomUUID();
    private static final UUID SUPERVISEUR = UUID.randomUUID();

    @Mock TicketRepository ticketRepository;
    @Mock DossierRepository dossierRepository;
    @Mock DossierTransfertRequestRepository requestRepository;
    @Mock TicketEventPublisher eventPublisher;
    @Mock AuditEventEmitter auditEmitter;
    @Mock NotificationPublisher notificationPublisher;
    @Mock MemberDirectory memberDirectory;
    @Mock DossierReaffectationRepository reaffectations;

    private DossierTransferService service() {
        return new DossierTransferService(ticketRepository, dossierRepository, requestRepository,
                eventPublisher, auditEmitter, notificationPublisher, memberDirectory, reaffectations);
    }

    @Test
    void acceptTransfer_deplaceLeDossierEtTousSesTickets_etTraceLaReaffectation() {
        DossierTransfertRequest pending = new DossierTransfertRequest(
                UUID.randomUUID(), WS, DOSSIER, ANCIEN, NOUVEAU,
                TransfertStatut.EN_ATTENTE, false, "charge", Instant.now(), null);
        when(requestRepository.findById(WS, pending.id())).thenReturn(Optional.of(pending));
        when(dossierRepository.findById(WS, DOSSIER)).thenReturn(Optional.of(dossier(ANCIEN)));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ticketRepository.realignerSurResponsable(WS, DOSSIER, NOUVEAU)).thenReturn(3);

        DossierTransfertRequest resolved = service().acceptTransfer(WS, NOUVEAU, pending.id());

        verify(dossierRepository).updateResponsable(WS, DOSSIER, NOUVEAU);
        // RG-DOS-02 / RG-TKT-08 : les tickets du dossier suivent le nouveau responsable.
        verify(ticketRepository).realignerSurResponsable(WS, DOSSIER, NOUVEAU);
        // Trace : auteur (la cible qui accepte), ancien, nouveau, nature, transfert.
        ArgumentCaptor<DossierReaffectation> trace = ArgumentCaptor.forClass(DossierReaffectation.class);
        verify(reaffectations).enregistrer(trace.capture());
        assertThat(trace.getValue().nature()).isEqualTo(NatureReaffectation.ACCEPTEE);
        assertThat(trace.getValue().ancienResponsableId()).isEqualTo(ANCIEN);
        assertThat(trace.getValue().nouveauResponsableId()).isEqualTo(NOUVEAU);
        assertThat(trace.getValue().auteurId()).isEqualTo(NOUVEAU);
        assertThat(trace.getValue().transfertId()).isEqualTo(pending.id());

        ArgumentCaptor<Map<String, Object>> meta = ArgumentCaptor.forClass(Map.class);
        verify(auditEmitter).emit(eq(WS), eq(NOUVEAU), eq("DOSSIER_TRANSFERE"), eq("dossier"),
                eq(DOSSIER), meta.capture());
        assertThat(meta.getValue())
                .containsEntry("ancienResponsable", ANCIEN.toString())
                .containsEntry("nouveauResponsable", NOUVEAU.toString())
                .containsEntry("nature", "ACCEPTEE")
                .containsEntry("ticketsReaffectes", 3);

        verify(notificationPublisher).notifyUser(eq(ANCIEN), eq(WS), eq("DOSSIER_TRANSFER"),
                any(), any(), eq("/dashboard"), anyMap());
        verify(notificationPublisher, never()).notifyUser(eq(NOUVEAU), any(), any(),
                any(), any(), any(), anyMap());
        assertThat(resolved.statut()).isEqualTo(TransfertStatut.ACCEPTE);
        assertThat(resolved.decidedAt()).isNotNull();
    }

    @Test
    void reaffectationDOffice_parLeSuperviseur_deplaceDossierEtTickets_traceeEtNotifiee() {
        DossierTransfertRequest enAttente = new DossierTransfertRequest(
                UUID.randomUUID(), WS, DOSSIER, ANCIEN, UUID.randomUUID(),
                TransfertStatut.EN_ATTENTE, false, null, Instant.now(), null);
        when(dossierRepository.findById(WS, DOSSIER)).thenReturn(Optional.of(dossier(ANCIEN)));
        when(memberDirectory.roleOf(WS, NOUVEAU)).thenReturn(Optional.of("EMPLOYE"));
        when(memberDirectory.estActif(WS, NOUVEAU)).thenReturn(true);
        when(requestRepository.findPendingForDossier(WS, DOSSIER)).thenReturn(Optional.of(enAttente));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ticketRepository.realignerSurResponsable(WS, DOSSIER, NOUVEAU)).thenReturn(2);

        service().reaffecterDOffice(WS, SUPERVISEUR, DOSSIER, NOUVEAU, "Depart de l'employe");

        verify(dossierRepository).updateResponsable(WS, DOSSIER, NOUVEAU);
        verify(ticketRepository).realignerSurResponsable(WS, DOSSIER, NOUVEAU);
        // La demande de transfert en attente devient sans objet : annulee.
        ArgumentCaptor<DossierTransfertRequest> annulee = ArgumentCaptor.forClass(DossierTransfertRequest.class);
        verify(requestRepository).save(annulee.capture());
        assertThat(annulee.getValue().statut()).isEqualTo(TransfertStatut.ANNULE);
        ArgumentCaptor<DossierReaffectation> trace = ArgumentCaptor.forClass(DossierReaffectation.class);
        verify(reaffectations).enregistrer(trace.capture());
        assertThat(trace.getValue().nature()).isEqualTo(NatureReaffectation.FORCEE);
        assertThat(trace.getValue().auteurId()).isEqualTo(SUPERVISEUR);
        assertThat(trace.getValue().motif()).isEqualTo("Depart de l'employe");
        // Notifiee (RG-DOS-03) : au nouveau ET a l'ancien responsable.
        verify(notificationPublisher).notifyUser(eq(NOUVEAU), eq(WS), eq("DOSSIER_TRANSFER"),
                any(), any(), any(), anyMap());
        verify(notificationPublisher).notifyUser(eq(ANCIEN), eq(WS), eq("DOSSIER_TRANSFER"),
                any(), any(), any(), anyMap());
        verify(auditEmitter).emit(eq(WS), eq(SUPERVISEUR), eq("DOSSIER_REAFFECTE"), eq("dossier"),
                eq(DOSSIER), anyMap());
    }

    @Test
    void reaffectationDOffice_sansMotif_refusee() {
        assertThatThrownBy(() -> service().reaffecterDOffice(WS, SUPERVISEUR, DOSSIER, NOUVEAU, "  "))
                .isInstanceOf(ValidationException.class).hasMessageContaining("motif");
        verify(dossierRepository, never()).updateResponsable(any(), any(), any());
    }

    @Test
    void reaffectationDOffice_versUnNonEmploye_refusee() {
        when(dossierRepository.findById(WS, DOSSIER)).thenReturn(Optional.of(dossier(ANCIEN)));
        when(memberDirectory.roleOf(WS, NOUVEAU)).thenReturn(Optional.of("SUPERVISEUR"));
        assertThatThrownBy(() -> service().reaffecterDOffice(WS, SUPERVISEUR, DOSSIER, NOUVEAU, "Absence"))
                .isInstanceOf(ValidationException.class).hasMessageContaining("employé");
        verify(dossierRepository, never()).updateResponsable(any(), any(), any());
        verify(reaffectations, never()).enregistrer(any());
    }

    @Test
    void reaffectationDOffice_versUnEmployeDesactive_refusee() {
        when(dossierRepository.findById(WS, DOSSIER)).thenReturn(Optional.of(dossier(ANCIEN)));
        when(memberDirectory.roleOf(WS, NOUVEAU)).thenReturn(Optional.of("EMPLOYE"));
        when(memberDirectory.estActif(WS, NOUVEAU)).thenReturn(false);
        assertThatThrownBy(() -> service().reaffecterDOffice(WS, SUPERVISEUR, DOSSIER, NOUVEAU, "Absence"))
                .isInstanceOf(ValidationException.class).hasMessageContaining("actif");
        verify(dossierRepository, never()).updateResponsable(any(), any(), any());
        verify(reaffectations, never()).enregistrer(any());
    }

    @Test
    void reaffectationDOffice_versLeResponsableActuel_refusee() {
        when(dossierRepository.findById(WS, DOSSIER)).thenReturn(Optional.of(dossier(ANCIEN)));
        assertThatThrownBy(() -> service().reaffecterDOffice(WS, SUPERVISEUR, DOSSIER, ANCIEN, "Absence"))
                .isInstanceOf(ValidationException.class).hasMessageContaining("déjà responsable");
        verify(dossierRepository, never()).updateResponsable(any(), any(), any());
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
        verify(ticketRepository, never()).realignerSurResponsable(any(), any(), any());
        verify(reaffectations, never()).enregistrer(any());
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
