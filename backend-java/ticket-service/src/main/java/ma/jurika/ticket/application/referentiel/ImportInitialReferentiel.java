package ma.jurika.ticket.application.referentiel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Lot L4b (E2, RG-DEM-01) : import INITIAL du referentiel des demarches.
 *
 * <p>Tant qu'aucune version n'est appliquee, aucun ticket hors creation ne peut passer de
 * statut (obstacle "referentiel non charge"). A la premiere mise en service, le service
 * importe donc lui-meme le corpus monte dans ai-service, par le meme chemin que l'ecran du
 * super-administrateur (journalise, refuse si l'analyse porte une erreur). ai-service peut
 * demarrer apres ticket-service : une tentative par minute, trente au plus. Une version deja
 * appliquee, ou un refus journalise, arrete les tentatives.
 *
 * <p>Lot L4d (E3) : la plateforme charge la version MONTEE. Quand le dossier date du corpus
 * change (ex. CORPUS_2026-10-03 vers CORPUS_2026-10-11), la nouvelle version s'importe au
 * demarrage par le meme chemin ; une version deja refusee n'est pas retentee (son refus est
 * au journal de l'ecran, d'ou l'on peut relancer l'import).
 */
@Component
@ConditionalOnProperty(name = "jurika.referentiel.import-initial", havingValue = "true", matchIfMissing = true)
public class ImportInitialReferentiel {

    private static final Logger log = LoggerFactory.getLogger(ImportInitialReferentiel.class);
    static final int TENTATIVES = 30;
    static final long PAUSE_MS = 60_000L;

    private final ImportReferentielDemarches imports;

    public ImportInitialReferentiel(ImportReferentielDemarches imports) {
        this.imports = imports;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void demarrer() {
        Thread t = new Thread(this::tenter, "import-initial-referentiel");
        t.setDaemon(true);
        t.start();
    }

    void tenter() {
        for (int i = 1; i <= TENTATIVES; i++) {
            try {
                String active = imports.versionActive();
                String montee = imports.versionMontee();
                if (montee == null) throw new IllegalStateException("version du corpus monte inconnue");
                if (montee.equals(active)) return;
                if (imports.versionRefusee(montee)) {
                    log.warn("Version {} du corpus deja refusee : import a relancer depuis l'ecran du super-administrateur.",
                            montee);
                    return;
                }
                ImportReferentielDemarches.Resultat r = imports.importer(null);
                log.info("Import initial du referentiel des demarches : version {} {} ({} lignes){}", r.version(),
                        r.statut(), r.lignes(), r.motif() == null ? "" : " : " + r.motif());
                return;
            } catch (RuntimeException ex) {
                log.warn("Import initial du referentiel des demarches, tentative {}/{} : {}", i, TENTATIVES,
                        ex.getMessage());
            }
            try {
                Thread.sleep(PAUSE_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.warn("Import initial du referentiel des demarches abandonne : importez-le depuis l'ecran du super-administrateur.");
    }
}
