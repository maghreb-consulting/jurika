package ma.jurika.dataroom.domain.port;

import java.util.UUID;

/**
 * Port d'archivage du document d'identité fusionné (recto + verso → 1 PDF) dans la Data Room.
 * <p>
 * L'implémentation infra construit le PDF (1 image par page) et persiste une entrée Data Room.
 */
public interface IdentityArchiver {

    /**
     * @param dossierId      dossier cible.
     * @param uploaderId     utilisateur qui déclenche l'archivage (audit).
     * @param documentType   document_type de la Data Room (ex : {@code CIN_NOUVELLE}, {@code CN}).
     * @param title          titre lisible (ex : "CIN nouvelle - Oussama BENATIK").
     * @param rectoBytes     octets de la face recto.
     * @param rectoFilename  nom d'origine recto.
     * @param rectoContentType MIME recto (peut être {@code null}).
     * @param versoBytes     octets de la face verso (peut être {@code null} → archive 1 page).
     * @param versoFilename  nom d'origine verso (peut être {@code null}).
     * @param versoContentType MIME verso (peut être {@code null}).
     * @return l'identifiant du document Data Room créé.
     */
    UUID archive(UUID dossierId,
                 UUID uploaderId,
                 String documentType,
                 String title,
                 byte[] rectoBytes, String rectoFilename, String rectoContentType,
                 byte[] versoBytes, String versoFilename, String versoContentType);
}
