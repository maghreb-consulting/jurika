package ma.jurika.ticket.application;

import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.security.Role;
import ma.jurika.common.security.TenantContext;
import ma.jurika.ticket.api.dto.DossierIdentifiantsDtos.DossierIdentifiantsView;
import ma.jurika.ticket.api.dto.DossierIdentifiantsDtos.UpdateIdentifiantsRequest;
import ma.jurika.ticket.domain.model.DeadlineRule;
import ma.jurika.ticket.domain.port.TaxeProfessionnelleVersionRepository;
import ma.jurika.ticket.infrastructure.persistence.DossierEntity;
import ma.jurika.ticket.infrastructure.persistence.DossierJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Édition des identifiants de la société : contrôle d'accès (responsable),
 * normalisation, validation, application des champs.
 */
class DossierIdentifiantsServiceTest {

    private final DossierJpaRepository dossiers = mock(DossierJpaRepository.class);
    private final DeadlineUseCase deadlineUseCase = mock(DeadlineUseCase.class);
    private final TaxeProfessionnelleVersionRepository versionsTp = mock(TaxeProfessionnelleVersionRepository.class);
    private DossierIdentifiantsService service;

    private final UUID ws = UUID.randomUUID();
    private final UUID dossierId = UUID.randomUUID();
    private final UUID responsable = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DossierIdentifiantsService(dossiers, deadlineUseCase, versionsTp);
        TenantContext.set(ws);
        when(dossiers.save(any(DossierEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private DossierEntity existing() {
        DossierEntity d = new DossierEntity();
        d.setId(dossierId);
        d.setWorkspaceId(ws);
        d.setRaisonSociale("ACME SARL");
        d.setFormeJuridique("SARL");
        d.setStatut("ACTIVE");
        d.setResponsableId(responsable);
        return d;
    }

    private UpdateIdentifiantsRequest req() {
        return new UpdateIdentifiantsRequest("002345", "RC-99", "Tribunal Casa",
                "IF-77", "TP-55", "CNSS-33", "12 rue X", "Casablanca",
                new BigDecimal("50000"), LocalDate.of(2024, 2, 1), null);
    }

    private UpdateIdentifiantsRequest reqTp(String tp, LocalDate dateEffet) {
        return new UpdateIdentifiantsRequest("002345", "RC-99", "Tribunal Casa",
                "IF-77", tp, "CNSS-33", "12 rue X", "Casablanca",
                new BigDecimal("50000"), LocalDate.of(2024, 2, 1), dateEffet);
    }

    // ---- Lot L1, etape E7 : versions datees de la taxe professionnelle (RG-VAR-08, RG-FIC-02) ----

    @Test
    void nouvelle_tp_cree_une_version_datee_et_conserve_les_precedentes() {
        DossierEntity d = existing();
        d.setTaxeProfessionnelle("TP-ANCIENNE");
        when(dossiers.findByWorkspaceIdAndId(ws, dossierId)).thenReturn(Optional.of(d));

        service.update(ws, Role.EMPLOYE, responsable, dossierId, reqTp("TP-NOUVELLE", LocalDate.of(2026, 1, 1)));

        verify(versionsTp).ajouter(ws, dossierId, "TP-NOUVELLE", LocalDate.of(2026, 1, 1), responsable);
        // Versions precedentes conservees : le port n'offre aucune suppression.
    }

    @Test
    void tp_inchangee_ne_cree_pas_de_version() {
        DossierEntity d = existing();
        d.setTaxeProfessionnelle("TP-55");
        when(dossiers.findByWorkspaceIdAndId(ws, dossierId)).thenReturn(Optional.of(d));

        service.update(ws, Role.EMPLOYE, responsable, dossierId, reqTp("TP-55", null));

        verify(versionsTp, never()).ajouter(any(), any(), any(), any(), any());
    }

    @Test
    void date_d_effet_absente_jamais_inventee() {
        when(dossiers.findByWorkspaceIdAndId(ws, dossierId)).thenReturn(Optional.of(existing()));

        service.update(ws, Role.EMPLOYE, responsable, dossierId, reqTp("TP-55", null));

        verify(versionsTp).ajouter(ws, dossierId, "TP-55", null, responsable);
    }

    @Test
    void date_d_effet_completee_plus_tard_sur_la_version_en_vigueur() {
        DossierEntity d = existing();
        d.setTaxeProfessionnelle("TP-55");
        when(dossiers.findByWorkspaceIdAndId(ws, dossierId)).thenReturn(Optional.of(d));

        service.update(ws, Role.EMPLOYE, responsable, dossierId, reqTp("TP-55", LocalDate.of(2026, 3, 1)));

        verify(versionsTp).completerDateEffet(ws, dossierId, "TP-55", LocalDate.of(2026, 3, 1));
        verify(versionsTp, never()).ajouter(any(), any(), any(), any(), any());
    }

    @Test
    void versions_lisibles_par_le_responsable_et_le_superviseur_seulement() {
        when(dossiers.findByWorkspaceIdAndId(ws, dossierId)).thenReturn(Optional.of(existing()));
        when(versionsTp.lister(ws, dossierId)).thenReturn(java.util.List.of());

        service.versionsTp(ws, responsable, false, dossierId);
        service.versionsTp(ws, UUID.randomUUID(), true, dossierId);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> service.versionsTp(ws, UUID.randomUUID(), false, dossierId))
                .isInstanceOf(ma.jurika.common.exception.NotFoundException.class);
    }

    @Test
    void employe_responsable_met_a_jour_les_identifiants() {
        when(dossiers.findByWorkspaceIdAndId(ws, dossierId)).thenReturn(Optional.of(existing()));
        DossierIdentifiantsView v = service.update(ws, Role.EMPLOYE, responsable, dossierId, req());

        assertThat(v.rcNumero()).isEqualTo("RC-99");
        assertThat(v.identifiantFiscal()).isEqualTo("IF-77");
        assertThat(v.taxeProfessionnelle()).isEqualTo("TP-55");
        assertThat(v.cnss()).isEqualTo("CNSS-33");
        assertThat(v.capitalSocialMad()).isEqualByComparingTo("50000");
        assertThat(v.dateConstitution()).isEqualTo(LocalDate.of(2024, 2, 1));
        verify(dossiers).save(any(DossierEntity.class));
    }

    @Test
    void employe_non_responsable_est_refuse() {
        when(dossiers.findByWorkspaceIdAndId(ws, dossierId)).thenReturn(Optional.of(existing()));
        assertThatThrownBy(() ->
                service.update(ws, Role.EMPLOYE, UUID.randomUUID(), dossierId, req()))
                .isInstanceOf(AccessDeniedException.class);
        verify(dossiers, never()).save(any());
    }

    @Test
    void superviseur_est_refuse_rg_u02_04() {
        when(dossiers.findByWorkspaceIdAndId(ws, dossierId)).thenReturn(Optional.of(existing()));
        assertThatThrownBy(() ->
                service.update(ws, Role.SUPERVISEUR, responsable, dossierId, req()))
                .isInstanceOf(AccessDeniedException.class);
        verify(dossiers, never()).save(any());
    }

    @Test
    void client_est_refuse() {
        when(dossiers.findByWorkspaceIdAndId(ws, dossierId)).thenReturn(Optional.of(existing()));
        assertThatThrownBy(() ->
                service.update(ws, Role.CLIENT, responsable, dossierId, req()))
                .isInstanceOf(AccessDeniedException.class);
        verify(dossiers, never()).save(any());
    }

    @Test
    void capital_negatif_est_rejete() {
        when(dossiers.findByWorkspaceIdAndId(ws, dossierId)).thenReturn(Optional.of(existing()));
        UpdateIdentifiantsRequest bad = new UpdateIdentifiantsRequest(null, null, null, null,
                null, null, null, null, new BigDecimal("-1"), null, null);
        assertThatThrownBy(() -> service.update(ws, Role.EMPLOYE, responsable, dossierId, bad))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void les_champs_blancs_sont_normalises_en_null() {
        when(dossiers.findByWorkspaceIdAndId(ws, dossierId)).thenReturn(Optional.of(existing()));
        UpdateIdentifiantsRequest blanks = new UpdateIdentifiantsRequest("   ", "", "  ", null,
                null, null, null, null, null, null, null);
        DossierIdentifiantsView v = service.update(ws, Role.EMPLOYE, responsable, dossierId, blanks);
        assertThat(v.ice()).isNull();
        assertThat(v.rcNumero()).isNull();
        assertThat(v.rcTribunal()).isNull();
    }

    @Test
    void obtention_rc_declenche_echeance_cnss_rattachee_au_ticket() {
        UUID origineTicket = UUID.randomUUID();
        DossierEntity d = existing();
        d.setCreatedByTicketId(origineTicket); // dossier issu d'une CREATION
        when(dossiers.findByWorkspaceIdAndId(ws, dossierId)).thenReturn(Optional.of(d));

        service.update(ws, Role.EMPLOYE, responsable, dossierId, req());

        ArgumentCaptor<DeadlineUseCase.AutoComputeCommand> cap =
                ArgumentCaptor.forClass(DeadlineUseCase.AutoComputeCommand.class);
        verify(deadlineUseCase).computeAuto(cap.capture());
        DeadlineUseCase.AutoComputeCommand cmd = cap.getValue();
        assertThat(cmd.rule()).isEqualTo(DeadlineRule.CNSS_DECL_30D);
        assertThat(cmd.ticketId()).isEqualTo(origineTicket);
        assertThat(cmd.dossierId()).isEqualTo(dossierId);
        // ancrage sur la date de constitution (= date RC) fournie dans req()
        assertThat(cmd.anchor())
                .isEqualTo(LocalDate.of(2024, 2, 1).atStartOfDay(ZoneId.of("Africa/Casablanca")).toInstant());
    }

    @Test
    void rc_deja_present_ne_recree_pas_l_echeance_cnss() {
        DossierEntity d = existing();
        d.setCreatedByTicketId(UUID.randomUUID());
        d.setRcNumero("RC-99"); // RC deja obtenu precedemment
        when(dossiers.findByWorkspaceIdAndId(ws, dossierId)).thenReturn(Optional.of(d));

        service.update(ws, Role.EMPLOYE, responsable, dossierId, req());

        verify(deadlineUseCase, never()).computeAuto(any());
    }

    @Test
    void obtention_rc_sans_ticket_origine_ne_declenche_pas_d_echeance() {
        DossierEntity d = existing(); // createdByTicketId == null
        when(dossiers.findByWorkspaceIdAndId(ws, dossierId)).thenReturn(Optional.of(d));

        service.update(ws, Role.EMPLOYE, responsable, dossierId, req());

        verify(deadlineUseCase, never()).computeAuto(any());
    }

    @Test
    void echec_calcul_echeance_ne_casse_pas_la_mise_a_jour() {
        DossierEntity d = existing();
        d.setCreatedByTicketId(UUID.randomUUID());
        when(dossiers.findByWorkspaceIdAndId(ws, dossierId)).thenReturn(Optional.of(d));
        when(deadlineUseCase.computeAuto(any())).thenThrow(new RuntimeException("boom"));

        DossierIdentifiantsView v = service.update(ws, Role.EMPLOYE, responsable, dossierId, req());

        assertThat(v.rcNumero()).isEqualTo("RC-99"); // best-effort : la mise a jour aboutit
    }
}
