package ma.jurika.ai.api;

import ma.jurika.ai.document.apercu.ApercuDocuments;
import ma.jurika.common.trial.ApercuPages;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Decision du 2026-10-14 (RG-ESS-05, RG-ESS-06) : route INTERNE (service a service, non routee par
 * la passerelle) qui rend un document en pages PNG d'apercu, la mention de l'essai incrustee
 * quand {@code filigrane=true}. Appelee par dataroom-service pour l'apercu des documents de la
 * Data Room (.docx compose sans LibreOffice, PDF, images) ; la decision de filigrane (etat de
 * l'essai du cabinet) appartient a l'appelant, qui a controle les droits avant de lire le
 * document. Format sans apercu ou document illisible : 422 nomme (jamais le fichier).
 */
@RestController
public class InternalApercuController {

    @PostMapping(value = "/internal/documents/apercu",
            consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ApercuPages apercu(@RequestBody byte[] octets,
                              @RequestParam(defaultValue = "") String filename,
                              @RequestParam(defaultValue = "") String type,
                              @RequestParam boolean filigrane) {
        return ApercuDocuments.fichier(octets, filename, type, filigrane);
    }
}
