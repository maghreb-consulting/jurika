package ma.jurika.dataroom.application;

import ma.jurika.common.courriel.CourrielClientEvenement;
import ma.jurika.common.security.TenantContext;
import ma.jurika.dataroom.api.dto.DataroomDtos.DocumentSummary;
import ma.jurika.dataroom.domain.port.CourrielsClient;
import ma.jurika.dataroom.infrastructure.persistence.DocumentJpaRepository;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewEntity;
import ma.jurika.dataroom.infrastructure.persistence.DossierViewJpaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lot L5 (E9, RG-COM-09) : document mis a disposition -> courriel au client. */
class AnnonceDocumentsClientTest {

    static final UUID WS = UUID.randomUUID();
    static final UUID DOSSIER = UUID.randomUUID();
    static final UUID CLIENT = UUID.randomUUID();

    DossierViewJpaRepository dossiers = mock(DossierViewJpaRepository.class);
    DocumentJpaRepository documents = mock(DocumentJpaRepository.class);
    CourrielsClient courriels = mock(CourrielsClient.class);
    DossierViewEntity dossier = mock(DossierViewEntity.class);
    AnnonceDocumentsClient annonces = new AnnonceDocumentsClient(dossiers, documents, courriels);

    @BeforeEach
    void setUp() {
        TenantContext.set(WS);
        when(dossiers.findByWorkspaceIdAndId(WS, DOSSIER)).thenReturn(Optional.of(dossier));
        when(dossier.getClientId()).thenReturn(CLIENT);
        when(dossier.getRaisonSociale()).thenReturn("ATLAS SARL");
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    /** Construit un DocumentSummary par ses composants (robuste aux ajouts de champs). */
    static DocumentSummary doc(String titre, boolean visible) {
        RecordComponent[] comps = DocumentSummary.class.getRecordComponents();
        Object[] args = new Object[comps.length];
        Class<?>[] types = new Class<?>[comps.length];
        for (int i = 0; i < comps.length; i++) {
            types[i] = comps[i].getType();
            String n = comps[i].getName();
            Object v;
            switch (n) {
                case "id" -> v = UUID.randomUUID();
                case "dossierId" -> v = DOSSIER;
                case "title" -> v = titre;
                case "visibleClient" -> v = visible;
                case "createdAt" -> v = Instant.now();
                default -> v = null;
            }
            if (v == null && types[i].isPrimitive()) {
                if (types[i] == boolean.class) v = Boolean.FALSE;
                else if (types[i] == short.class) v = (short) 1;
                else if (types[i] == long.class) v = 0L;
                else if (types[i] == int.class) v = 0;
                else if (types[i] == double.class) v = 0d;
            }
            args[i] = v;
        }
        try {
            return DocumentSummary.class.getDeclaredConstructor(types).newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void un_seul_courriel_pour_les_documents_visibles() {
        boolean envoye = annonces.annoncer(List.of(doc("Statuts", true), doc("Note interne", false), doc("PV", true)));

        assertThat(envoye).isTrue();
        ArgumentCaptor<CourrielClientEvenement> c = ArgumentCaptor.forClass(CourrielClientEvenement.class);
        verify(courriels).demander(c.capture());
        assertThat(c.getValue().type()).isEqualTo(CourrielClientEvenement.TYPE_DOCUMENT_DISPONIBLE);
        assertThat(c.getValue().clientId()).isEqualTo(CLIENT);
        assertThat(c.getValue().objet()).isEqualTo("Statuts, PV");
    }

    @Test
    void un_document_masque_au_client_n_est_pas_annonce() {
        assertThat(annonces.annoncer(List.of(doc("Note interne", false)))).isFalse();
        verify(courriels, never()).demander(any());
    }

    @Test
    void un_dossier_sans_client_n_envoie_rien() {
        when(dossier.getClientId()).thenReturn(null);
        assertThat(annonces.annoncer(List.of(doc("Statuts", true)))).isFalse();
        verify(courriels, never()).demander(any());
    }
}
