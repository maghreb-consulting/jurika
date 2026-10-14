package ma.jurika.dataroom.application;

import ma.jurika.common.exception.PlanLimitException;
import ma.jurika.common.trial.EssaiRegles;
import ma.jurika.common.trial.FiligraneEssaiPdf;
import ma.jurika.common.trial.TrialAccessChecker;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.UUID;

/**
 * Lot L7 (E4) : apercu d'un document pendant l'essai gratuit.
 *
 * <ul>
 *   <li>RG-ESS-06 : un PDF affiche porte le filigrane "VERSION D'EVALUATION --
 *       SANS VALEUR JURIDIQUE" sur chaque page ;</li>
 *   <li>RG-ESS-05 : l'apercu ne doit pas devenir un telechargement. Seuls un PDF
 *       (filigrane) ou une image s'affichent ; un original Word ou Excel que la
 *       conversion n'a pas pu rendre en PDF est refuse (le servir reviendrait a
 *       le telecharger, sans filigrane).</li>
 * </ul>
 * Hors essai : apercu inchange.
 */
@Component
public class ApercuEssai {

    private final ObjectProvider<TrialAccessChecker> checker;

    public ApercuEssai(ObjectProvider<TrialAccessChecker> checker) {
        this.checker = checker;
    }

    public boolean enEssai(UUID workspaceId) {
        TrialAccessChecker c = checker.getIfAvailable();
        return c != null && workspaceId != null
                && c.getState(workspaceId).map(TrialAccessChecker.TrialState::enEssai).orElse(false);
    }

    public OfficePreviewSupport.Rendered adapter(OfficePreviewSupport.Rendered rd, UUID workspaceId) {
        if (!enEssai(workspaceId)) return rd;
        String type = rd.contentType() == null ? "" : rd.contentType().toLowerCase(Locale.ROOT);
        if (type.startsWith("image/")) return rd;
        if (!type.startsWith("application/pdf")) {
            fermer(rd.stream());
            throw new PlanLimitException("ESSAI_APERCU_INDISPONIBLE",
                    "Aper\u00e7u indisponible pour ce document pendant l\u2019essai gratuit : seuls les PDF et les "
                            + "images s\u2019affichent, et aucun t\u00e9l\u00e9chargement n\u2019est permis.",
                    "telechargement", 0, 0, EssaiRegles.FORFAIT_ESSAI,
                    "Souscrivez un forfait pour t\u00e9l\u00e9charger vos documents.");
        }
        byte[] marque;
        try (InputStream in = rd.stream()) {
            marque = FiligraneEssaiPdf.appliquer(in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new OfficePreviewSupport.Rendered(new ByteArrayInputStream(marque), marque.length,
                "application/pdf", rd.filename());
    }

    private static void fermer(InputStream in) {
        try {
            if (in != null) in.close();
        } catch (IOException ignoree) {
            // flux de lecture : rien a sauver
        }
    }
}
