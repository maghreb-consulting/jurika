package ma.jurika.dataroom.domain.port;

import java.util.UUID;

/**
 * Port d'envoi de notification au comptable du cabinet quand un client uploade
 * un document comptable (RG-DC27).
 * <p>
 * L'adapter par defaut logge la notification ; en prod, brancher un SMTP/SES/Resend.
 */
public interface AccountantNotifier {

    /**
     * @param toEmail       adresse du comptable (configure dans dataroom_settings.accountant_email)
     * @param dossierId     dossier concerne
     * @param raisonSociale nom de la societe (pour le sujet)
     * @param annee         annee comptable
     * @param categorie     ACHATS / VENTES / BANQUE / CAISSE / NDF / PAIE
     * @param documentName  nom du document uploade
     * @param uploaderId    qui a uploade (client OU employe)
     */
    void notifyUpload(String toEmail, UUID dossierId, String raisonSociale,
                      short annee, String categorie, String documentName, UUID uploaderId);

    /**
     * RG-DF21 : notification upload Fiscal (comptable + gerant).
     *
     * @param categorie     TVA / IS / IR / TP_TSC / RAS / ATTESTATIONS / CONTENTIEUX
     * @param sousClassification ex: DECLARATION_MENSUELLE, ACOMPTE_T1, etc.
     */
    default void notifyFiscalUpload(String toEmail, UUID dossierId, String raisonSociale,
                                     short annee, String categorie, String sousClassification,
                                     String documentName, UUID uploaderId) {
        // default = delegue a notifyUpload pour les implementations existantes
        notifyUpload(toEmail, dossierId, raisonSociale, annee,
                "FISCAL:" + categorie + "/" + sousClassification, documentName, uploaderId);
    }
}
