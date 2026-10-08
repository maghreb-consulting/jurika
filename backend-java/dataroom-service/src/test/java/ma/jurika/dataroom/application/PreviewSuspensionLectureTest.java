package ma.jurika.dataroom.application;

import ma.jurika.common.exception.NotFoundException;
import ma.jurika.common.exception.ValidationException;
import ma.jurika.common.security.AuthenticatedUser;
import ma.jurika.common.security.Role;
import ma.jurika.dataroom.application.access.ClientAccessLogger;
import ma.jurika.dataroom.domain.port.PdfWatermarkService;
import ma.jurika.dataroom.infrastructure.persistence.DocumentEntity;
import ma.jurika.dataroom.infrastructure.persistence.DocumentJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewEntity;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.SettingsEntity;
import ma.jurika.dataroom.infrastructure.persistence.SettingsJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Lot L0, etape E16b (reponse E1 n° 5, inventaire W6) : le controle de
 * suspension de l'apercu (RG-DR03) ne laisse plus passer un CLIENT quand rien
 * n'est lisible. Reglages absents ET dossier illisible : refus. Reglages absents
 * mais dossier lisible : valeur par defaut ACTIVE (les reglages naissent a la
 * demande, cf. DataroomSettingsService#getOrCreate), l'apercu reste servi.
 */
class PreviewSuspensionLectureTest {

    private final UUID documentId = UUID.randomUUID();
    private final UUID dossierId = UUID.randomUUID();
    private final AuthenticatedUser client =
            new AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "c@test", Role.CLIENT);

    private DocumentJpaRepository documents;
    private DossierViewJpaRepository dossiers;
    private SettingsJpaRepository settings;
    private OfficePreviewSupport office;
    private PreviewDocumentUseCase useCase;

    @BeforeEach
    void setUp() {
        documents = mock(DocumentJpaRepository.class);
        dossiers = mock(DossierViewJpaRepository.class);
        settings = mock(SettingsJpaRepository.class);
        office = mock(OfficePreviewSupport.class);
        DocumentEntity doc = mock(DocumentEntity.class);
        when(doc.getDossierId()).thenReturn(dossierId);
        when(doc.getFilename()).thenReturn("acte.pdf");
        when(documents.findById(documentId)).thenReturn(Optional.of(doc));
        when(office.render(any(), any(), any())).thenReturn(new OfficePreviewSupport.Rendered(
                new ByteArrayInputStream(new byte[] {1}), 1, "application/pdf", "acte.pdf"));
        useCase = new PreviewDocumentUseCase(documents, dossiers, settings, office,
                mock(PdfWatermarkService.class), mock(ClientAccessLogger.class), false);
    }

    @Test
    void client_refuse_quand_ni_reglages_ni_dossier_ne_sont_lisibles() {
        when(settings.findById(dossierId)).thenReturn(Optional.empty());
        when(dossiers.findById(dossierId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(documentId, client)).isInstanceOf(NotFoundException.class);
        Mockito.verifyNoInteractions(office);
    }

    @Test
    void client_servi_quand_le_dossier_est_lisible_sans_reglages() {
        when(settings.findById(dossierId)).thenReturn(Optional.empty());
        when(dossiers.findById(dossierId)).thenReturn(Optional.of(mock(DossierViewEntity.class)));

        assertThat(useCase.execute(documentId, client).contentType()).isEqualTo("application/pdf");
    }

    @Test
    void client_refuse_sur_data_room_suspendue() {
        SettingsEntity s = mock(SettingsEntity.class);
        when(s.getAccessStatus()).thenReturn("SUSPENDED");
        when(settings.findById(dossierId)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> useCase.execute(documentId, client)).isInstanceOf(ValidationException.class);
    }
}
