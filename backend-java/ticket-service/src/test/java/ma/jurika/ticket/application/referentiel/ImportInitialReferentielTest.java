package ma.jurika.ticket.application.referentiel;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lot L4b (E2) : l'import initial ne touche pas a une version deja appliquee. */
class ImportInitialReferentielTest {

    @Test
    void une_version_appliquee_arrete_l_import_initial() {
        ImportReferentielDemarches imports = mock(ImportReferentielDemarches.class);
        when(imports.versionActive()).thenReturn("CORPUS_2026-10-03");
        new ImportInitialReferentiel(imports).tenter();
        verify(imports, never()).importer(null);
    }

    @Test
    void sans_version_le_corpus_est_importe_une_fois() {
        ImportReferentielDemarches imports = mock(ImportReferentielDemarches.class);
        when(imports.versionActive()).thenReturn(null);
        when(imports.importer(null)).thenReturn(new ImportReferentielDemarches.Resultat(UUID.randomUUID(),
                "CORPUS_2026-10-03", "APPLIQUE", 7006, 301, 24, null));
        new ImportInitialReferentiel(imports).tenter();
        verify(imports).importer(null);
    }
}
