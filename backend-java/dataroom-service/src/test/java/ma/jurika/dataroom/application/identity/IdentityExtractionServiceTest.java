package ma.jurika.dataroom.application.identity;

import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.api.dto.IdentityDtos.ExtractedIdentityDto;
import ma.jurika.dataroom.api.dto.IdentityDtos.IdentityType;
import ma.jurika.dataroom.domain.port.IdentityArchiver;
import ma.jurika.dataroom.domain.port.KieServiceClient;
import ma.jurika.dataroom.domain.port.KieServiceClient.KieExtractResponse;
import ma.jurika.dataroom.domain.port.KieServiceClient.KieServiceUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityExtractionServiceTest {

    @Mock KieServiceClient kie;
    @Mock IdentityArchiver archiver;

    @InjectMocks IdentityExtractionService service;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID dossierId = UUID.randomUUID();
    private final UUID uploaderId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.set(workspaceId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private MultipartFile png(String name) {
        return new MockMultipartFile(name, name + ".png", "image/png", new byte[]{1, 2, 3, 4});
    }

    // ---------------------------------------------------------------------
    // Mapping IdentityType -> doc_type
    // ---------------------------------------------------------------------

    @Test
    void docTypeForRecto_maps_each_identity_type() {
        assertThat(IdentityExtractionService.docTypeForRecto(IdentityType.NOUVELLE)).isEqualTo("cin_nouv_recto");
        assertThat(IdentityExtractionService.docTypeForRecto(IdentityType.ANCIENNE)).isEqualTo("cin_anc_recto");
        assertThat(IdentityExtractionService.docTypeForRecto(IdentityType.CN)).isEqualTo("cn");
    }

    @Test
    void docTypeForVerso_maps_cin_only_and_rejects_cn() {
        assertThat(IdentityExtractionService.docTypeForVerso(IdentityType.NOUVELLE)).isEqualTo("cin_nouv_verso");
        assertThat(IdentityExtractionService.docTypeForVerso(IdentityType.ANCIENNE)).isEqualTo("cin_anc_verso");
        assertThatThrownBy(() -> IdentityExtractionService.docTypeForVerso(IdentityType.CN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------------------------------------------------------------------
    // Fusion recto/verso : priorités de champs + warnings
    // ---------------------------------------------------------------------

    @Test
    void fuseFields_verso_wins_for_cin_sexe_adresse_dateValidite_nationalite() {
        KieExtractResponse recto = new KieExtractResponse("cin_nouv_recto",
                mapOf("nom", "BENATIK", "prenom", "OUSSAMA",
                      "cin", "AA111111", "sexe", "F",
                      "adresse", "ancienne adresse", "date_validite", "01.01.2020",
                      "nationalite", "FRA"),
                "kie", List.of("date_validite"));
        KieExtractResponse verso = new KieExtractResponse("cin_nouv_verso",
                mapOf("cin", "AB123456", "sexe", "M",
                      "adresse", "12 RUE X CASA", "date_validite", "01.04.2031",
                      "nationalite", "MAR"),
                "merged", List.of("date_validite"));

        Map<String, String> fused = IdentityExtractionService.fuseFields(recto, verso);

        assertThat(fused).containsEntry("cin", "AB123456");
        assertThat(fused).containsEntry("sexe", "M");
        assertThat(fused).containsEntry("adresse", "12 RUE X CASA");
        assertThat(fused).containsEntry("date_validite", "01.04.2031");
        assertThat(fused).containsEntry("nationalite", "MAR");
        // RECTO_WINS reste intact :
        assertThat(fused).containsEntry("nom", "BENATIK");
        assertThat(fused).containsEntry("prenom", "OUSSAMA");
    }

    @Test
    void fuseFields_recto_wins_for_nom_prenom_dateNaissance_lieuNaissance() {
        KieExtractResponse recto = new KieExtractResponse("cin_nouv_recto",
                mapOf("nom", "BENATIK", "prenom", "OUSSAMA",
                      "date_naissance", "12.05.1990", "lieu_naissance", "CASABLANCA"),
                "kie", List.of());
        // Verso retourne des valeurs concurrentes (improbable mais possible) :
        // elles ne doivent pas écraser le recto.
        KieExtractResponse verso = new KieExtractResponse("cin_nouv_verso",
                mapOf("nom", "AUTRE", "prenom", "AUTRE",
                      "date_naissance", "01.01.2000", "lieu_naissance", "AUTRE"),
                "merged", List.of());

        Map<String, String> fused = IdentityExtractionService.fuseFields(recto, verso);

        assertThat(fused).containsEntry("nom", "BENATIK");
        assertThat(fused).containsEntry("prenom", "OUSSAMA");
        assertThat(fused).containsEntry("date_naissance", "12.05.1990");
        assertThat(fused).containsEntry("lieu_naissance", "CASABLANCA");
    }

    @Test
    void fuseFields_with_only_recto_returns_recto_fields() {
        KieExtractResponse recto = new KieExtractResponse("cn",
                mapOf("numero_cn", "987654", "denomination", "ATLAS"),
                "kie", List.of());
        Map<String, String> fused = IdentityExtractionService.fuseFields(recto, null);
        assertThat(fused).containsEntry("numero_cn", "987654");
        assertThat(fused).containsEntry("denomination", "ATLAS");
    }

    @Test
    void fuseWarnings_dedupes_and_preserves_order() {
        KieExtractResponse recto = new KieExtractResponse("cin_anc_recto",
                Map.of(), "kie", List.of("cin_format", "date_date_naissance"));
        KieExtractResponse verso = new KieExtractResponse("cin_anc_verso",
                Map.of(), "kie", List.of("cin_format", "mrz_not_found"));
        List<String> merged = IdentityExtractionService.fuseWarnings(recto, verso);
        assertThat(merged).containsExactly("cin_format", "date_date_naissance", "mrz_not_found");
    }

    @Test
    void fuseFields_drops_blank_values() {
        KieExtractResponse recto = new KieExtractResponse("cin_nouv_recto",
                mapOf("nom", "", "prenom", "  ", "adresse", "X"),
                "kie", List.of());
        Map<String, String> fused = IdentityExtractionService.fuseFields(recto, null);
        assertThat(fused).doesNotContainKey("nom");
        assertThat(fused).doesNotContainKey("prenom");
        assertThat(fused).containsEntry("adresse", "X");
    }

    // ---------------------------------------------------------------------
    // Orchestration de bout en bout
    // ---------------------------------------------------------------------

    @Test
    void extract_cin_nouvelle_calls_kie_for_both_faces_and_archives() {
        when(kie.extract(eq("cin_nouv_recto"), anyString(), any(), any()))
                .thenReturn(new KieExtractResponse("cin_nouv_recto",
                        mapOf("nom", "BENATIK", "prenom", "OUSSAMA"),
                        "kie", List.of()));
        when(kie.extract(eq("cin_nouv_verso"), anyString(), any(), any()))
                .thenReturn(new KieExtractResponse("cin_nouv_verso",
                        mapOf("cin", "AB123456", "sexe", "M", "adresse", "12 RUE X"),
                        "merged", List.of()));
        UUID archivedId = UUID.randomUUID();
        when(archiver.archive(eq(dossierId), eq(uploaderId), eq("CIN_NOUVELLE"),
                              anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(archivedId);

        ExtractedIdentityDto out = service.extract(
                IdentityType.NOUVELLE, png("r"), png("v"),
                dossierId, true, uploaderId);

        assertThat(out.type()).isEqualTo(IdentityType.NOUVELLE);
        assertThat(out.source()).isEqualTo("merged");
        assertThat(out.fields()).containsEntry("nom", "BENATIK");
        assertThat(out.fields()).containsEntry("cin", "AB123456");
        assertThat(out.archivedDocumentId()).isEqualTo(archivedId);

        verify(kie, times(2)).extract(anyString(), anyString(), any(), any());
        verify(archiver, times(1)).archive(eq(dossierId), eq(uploaderId), eq("CIN_NOUVELLE"),
                                           anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void extract_cn_calls_kie_once_and_does_not_archive_when_archive_false() {
        when(kie.extract(eq("cn"), anyString(), any(), any()))
                .thenReturn(new KieExtractResponse("cn",
                        mapOf("numero_cn", "98765", "denomination", "ATLAS"),
                        "kie", List.of()));

        ExtractedIdentityDto out = service.extract(
                IdentityType.CN, png("r"), null, dossierId, false, uploaderId);

        assertThat(out.fields()).containsEntry("numero_cn", "98765");
        assertThat(out.archivedDocumentId()).isNull();
        verify(kie, times(1)).extract(anyString(), anyString(), any(), any());
        verifyNoInteractions(archiver);
    }

    @Test
    void extract_cn_ignores_verso_even_when_provided() {
        when(kie.extract(eq("cn"), anyString(), any(), any()))
                .thenReturn(new KieExtractResponse("cn", mapOf("numero_cn", "1"), "kie", List.of()));

        service.extract(IdentityType.CN, png("r"), png("v"), dossierId, false, uploaderId);

        // Aucun appel pour un verso.
        verify(kie, times(1)).extract(anyString(), anyString(), any(), any());
    }

    @Test
    void extract_does_not_archive_when_dossierId_null_even_if_archive_true() {
        when(kie.extract(eq("cin_anc_recto"), anyString(), any(), any()))
                .thenReturn(new KieExtractResponse("cin_anc_recto", mapOf("nom", "X"), "kie", List.of()));
        when(kie.extract(eq("cin_anc_verso"), anyString(), any(), any()))
                .thenReturn(new KieExtractResponse("cin_anc_verso", mapOf("cin", "Y"), "kie", List.of()));

        ExtractedIdentityDto out = service.extract(
                IdentityType.ANCIENNE, png("r"), png("v"), null, true, uploaderId);

        assertThat(out.archivedDocumentId()).isNull();
        verifyNoInteractions(archiver);
    }

    @Test
    void extract_propagates_kie_unavailability() {
        when(kie.extract(eq("cin_nouv_recto"), anyString(), any(), any()))
                .thenThrow(new KieServiceUnavailableException("boom"));

        assertThatThrownBy(() -> service.extract(
                IdentityType.NOUVELLE, png("r"), png("v"), dossierId, false, uploaderId))
                .isInstanceOf(KieServiceUnavailableException.class);

        verify(archiver, never()).archive(any(), any(), anyString(), anyString(),
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void extract_rejects_missing_recto() {
        MockMultipartFile empty = new MockMultipartFile("recto", "x.png", "image/png", new byte[0]);
        assertThatThrownBy(() -> service.extract(
                IdentityType.CN, empty, null, dossierId, false, uploaderId))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(kie);
    }

    private static Map<String, String> mapOf(String... kv) {
        if (kv.length % 2 != 0) throw new IllegalArgumentException("paires k/v attendues");
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }
}
