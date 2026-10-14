package ma.jurika.dataroom.application;

import ma.jurika.common.trial.ApercuPages;
import ma.jurika.common.trial.RenduApercu;
import ma.jurika.common.trial.TrialAccessChecker;
import ma.jurika.dataroom.domain.port.AiApercuClient;
import ma.jurika.dataroom.domain.port.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;

/**
 * Decision du 2026-10-14 (remplace la decision 23 du lot L7 ; RG-ESS-05, RG-ESS-06) : apercu d'un
 * document de la Data Room en AFFICHAGE seulement.
 *
 * <p>Le document est rendu par le serveur en pages PNG (JSON) : PDF et images ici (PDFBox), .docx
 * par ai-service (mise en page sans LibreOffice). Pendant l'essai (etat lu cote serveur ; etat
 * illisible = traite comme un essai), la mention "VERSION D'EVALUATION -- SANS VALEUR JURIDIQUE"
 * est incrustee dans chaque page. Aucun octet du fichier n'est transmis au navigateur ; un format
 * sans apercu (.doc, .xlsx, .odt...) est refuse et nomme, jamais servi tel quel. Hors essai : memes
 * pages, sans mention.
 */
@Component
public class ApercuPagesService {

    private static final Logger log = LoggerFactory.getLogger(ApercuPagesService.class);

    private final ObjectStorage storage;
    private final AiApercuClient ai;
    private final ObjectProvider<TrialAccessChecker> checker;

    public ApercuPagesService(ObjectStorage storage, AiApercuClient ai, ObjectProvider<TrialAccessChecker> checker) {
        this.storage = storage;
        this.ai = ai;
        this.checker = checker;
    }

    /** Vrai pendant l'essai (en cours ou en lecture seule) ; etat illisible : vrai (mention posee). */
    public boolean enEssai(UUID workspaceId) {
        TrialAccessChecker c = checker.getIfAvailable();
        if (c == null || workspaceId == null) return false;
        try {
            return c.getState(workspaceId).map(TrialAccessChecker.TrialState::enEssai).orElse(false);
        } catch (RuntimeException ex) {
            log.error("Etat d'essai illisible pour {} : apercu rendu avec la mention d'evaluation", workspaceId, ex);
            return true;
        }
    }

    public ApercuPages rendre(String objectKey, String filename, String contentType, UUID workspaceId) {
        boolean filigrane = enEssai(workspaceId);
        byte[] octets;
        try (InputStream in = storage.download(objectKey).stream()) {
            octets = in.readAllBytes();
        } catch (IOException e) {
            throw new RenduApercu.ApercuIndisponibleException(
                    "Le document n\u2019a pas pu \u00eatre lu pour l\u2019aper\u00e7u : r\u00e9essayez dans quelques instants.", e);
        }
        String n = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        String t = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (n.endsWith(".pdf") || t.startsWith("application/pdf")) {
            return RenduApercu.depuisPdf(octets, filigrane);
        }
        if (t.startsWith("image/") || n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                || n.endsWith(".gif") || n.endsWith(".bmp")) {
            return RenduApercu.depuisImage(octets, filigrane);
        }
        if (n.endsWith(".docx") || t.contains("wordprocessingml")) {
            return ai.apercu(octets, filename, contentType, filigrane);
        }
        throw new RenduApercu.ApercuImpossibleException("Aper\u00e7u indisponible pour ce format : seuls les "
                + "documents Word (.docx), les PDF et les images s\u2019affichent dans la plateforme.", null);
    }
}
