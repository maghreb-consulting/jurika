package ma.jurika.dataroom.integration;

import ma.jurika.dataroom.domain.port.AccountantNotifier;
import ma.jurika.dataroom.infrastructure.notification.LoggerAccountantNotifier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sprint 14 ter C1 -- AccountantNotifierIT (3 cas).
 *
 * Tests deterministes du contrat {@link AccountantNotifier} via l'implementation
 * de developpement {@link LoggerAccountantNotifier}. Pas de Spring Boot context
 * necessaire : on instancie directement et on capture les logs SLF4J via
 * ListAppender pour verifier le contenu.
 *
 * Cas couverts :
 *   1. notifyFiscalUpload TVA -> log contient email/dossier/categorie/sous-classif/filename
 *   2. notifyUpload comptable -> log contient categorie standard (ACHATS/VENTES/...) sans prefixe FISCAL:
 *   3. notifyFiscalUpload avec email vide -> n'echoue pas (delegue + log neutre)
 *
 * Le default method notifyFiscalUpload delegue a notifyUpload en prefixant la
 * categorie par "FISCAL:" -- comportement specifie en commentaire JavaDoc du port.
 */
class AccountantNotifierIT {

    private final AccountantNotifier notifier = new LoggerAccountantNotifier();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private static Logger logger;

    @BeforeAll
    static void setupLogger() {
        logger = (Logger) LoggerFactory.getLogger(LoggerAccountantNotifier.class);
        logger.setLevel(Level.INFO);
    }

    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterAll
    static void cleanup() {
        // Niveau par defaut restaure -- pas critique pour les tests suivants
    }

    @Test
    @DisplayName("RG-DF21 cas 1 : notifyFiscalUpload TVA -> log contient email/dossier/categorie/filename")
    void notifyFiscalUploadEmitsExpectedLogPayload() {
        attachAppender();

        UUID dossierId = UUID.randomUUID();
        UUID uploaderId = UUID.randomUUID();
        notifier.notifyFiscalUpload("comptable@cabinet.ma", dossierId, "SARL ALPHA",
                (short) 2026, "TVA", "DECLARATION_MENSUELLE",
                "tva-janvier-2026.pdf", uploaderId);

        // L'implementation est @Async, mais ici on appelle directement (pas de proxy Spring)
        // donc la notification est synchrone. On verifie le contenu du dernier log INFO.
        assertThat(appender.list).isNotEmpty();
        String last = appender.list.get(appender.list.size() - 1).getFormattedMessage();
        assertThat(last).contains("comptable@cabinet.ma");
        assertThat(last).contains(dossierId.toString());
        assertThat(last).contains("SARL ALPHA");
        assertThat(last).contains("FISCAL:TVA/DECLARATION_MENSUELLE");
        assertThat(last).contains("tva-janvier-2026.pdf");
        assertThat(last).contains(uploaderId.toString());
    }

    @Test
    @DisplayName("RG-DC27 cas 2 : notifyUpload comptable -> categorie standard sans prefixe FISCAL:")
    void notifyUploadComptablePassesCategoryThrough() {
        attachAppender();
        appender.list.clear();

        UUID dossierId = UUID.randomUUID();
        UUID uploaderId = UUID.randomUUID();
        notifier.notifyUpload("comptable@cabinet.ma", dossierId, "SARL BETA",
                (short) 2026, "ACHATS", "facture-fournisseur.pdf", uploaderId);

        assertThat(appender.list).isNotEmpty();
        String last = appender.list.get(appender.list.size() - 1).getFormattedMessage();
        assertThat(last).contains("ACHATS");
        assertThat(last).doesNotContain("FISCAL:"); // le default delegate ne s'applique qu'a notifyFiscalUpload
    }

    @Test
    @DisplayName("RG-DF21 cas 3 : notifyFiscalUpload avec email vide n'echoue pas (best-effort cote port)")
    void notifyFiscalUploadHandlesEmptyEmailGracefully() {
        attachAppender();
        appender.list.clear();

        UUID dossierId = UUID.randomUUID();
        UUID uploaderId = UUID.randomUUID();
        // Le port lui-meme ne valide pas l'email -- c'est DataroomFiscalService.notifyFiscalUploadBestEffort
        // qui filtre les emails null/blank AVANT d'appeler le port. L'implementation Logger
        // accepte tout email sans exception.
        notifier.notifyFiscalUpload("", dossierId, "SARL GAMMA",
                (short) 2026, "IS", "DECLARATION_ANNUELLE",
                "is-declaration.pdf", uploaderId);

        // Pas d'exception levee, log produit (avec l'email vide affiche tel quel)
        assertThat(appender.list).isNotEmpty();
        assertThat(appender.list.get(appender.list.size() - 1).getFormattedMessage())
                .contains("IS")
                .contains("is-declaration.pdf");
    }
}
