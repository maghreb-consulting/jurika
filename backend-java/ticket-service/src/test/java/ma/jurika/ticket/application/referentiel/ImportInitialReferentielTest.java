package ma.jurika.ticket.application.referentiel;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lot L4b (E2) : l'import initial ne touche pas a une version deja appliquee.
 * Lot L4d (E3) : une NOUVELLE version montee (dossier date du corpus) s'importe au demarrage ;
 * une version deja refusee n'est pas retentee (le refus reste au journal, l'ecran le montre).
 */
class ImportInitialReferentielTest {

    @Test
    void une_version_appliquee_arrete_l_import_initial() {
        ImportReferentielDemarches imports = mock(ImportReferentielDemarches.class);
        when(imports.versionActive()).thenReturn("CORPUS_2026-10-03");
        when(imports.versionMontee()).thenReturn("CORPUS_2026-10-03");
        new ImportInitialReferentiel(imports).tenter();
        verify(imports, never()).importer(null);
    }

    @Test
    void une_nouvelle_version_montee_est_importee() {
        ImportReferentielDemarches imports = mock(ImportReferentielDemarches.class);
        when(imports.versionActive()).thenReturn("CORPUS_2026-10-03");
        when(imports.versionMontee()).thenReturn("CORPUS_2026-10-11");
        when(imports.importer(null)).thenReturn(new ImportReferentielDemarches.Resultat(UUID.randomUUID(),
                "CORPUS_2026-10-11", "APPLIQUE", 7006, 301, 24, null));
        new ImportInitialReferentiel(imports).tenter();
        verify(imports).importer(null);
    }

    @Test
    void une_version_deja_refusee_n_est_pas_retentee() {
        ImportReferentielDemarches imports = mock(ImportReferentielDemarches.class);
        when(imports.versionActive()).thenReturn("CORPUS_2026-10-03");
        when(imports.versionMontee()).thenReturn("CORPUS_2026-10-11");
        when(imports.versionRefusee("CORPUS_2026-10-11")).thenReturn(true);
        new ImportInitialReferentiel(imports).tenter();
        verify(imports, never()).importer(null);
    }

    @Test
    void sans_version_le_corpus_est_importe_une_fois() {
        ImportReferentielDemarches imports = mock(ImportReferentielDemarches.class);
        when(imports.versionActive()).thenReturn(null);
        when(imports.versionMontee()).thenReturn("CORPUS_2026-10-03");
        when(imports.importer(null)).thenReturn(new ImportReferentielDemarches.Resultat(UUID.randomUUID(),
                "CORPUS_2026-10-03", "APPLIQUE", 7006, 301, 24, null));
        new ImportInitialReferentiel(imports).tenter();
        verify(imports).importer(null);
    }
}
