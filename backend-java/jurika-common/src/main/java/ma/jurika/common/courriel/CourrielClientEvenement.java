package ma.jurika.common.courriel;

import java.util.UUID;

/**
 * Lot L5 (E9, RG-COM-09) : demande d'avertissement du CLIENT par courriel.
 *
 * <p>Le client est averti par courriel -- et non dans l'application -- d'une
 * requete du cabinet, d'un document mis a sa disposition et de la reponse a sa
 * demande. Le service qui constate le fait (dataroom) publie cet evenement sur
 * l'echange {@value #ECHANGE} ; auth-service, seul a connaitre l'adresse du
 * client et le nom du cabinet, le consomme et envoie le courriel par son port
 * {@code EmailSender}, depuis l'adresse unique de JURIKA, en nommant le cabinet.
 *
 * <p>Contrat partage par les deux services (meme classe, meme serialisation JSON).
 *
 * @param type          un des {@code TYPE_*}
 * @param workspaceId   cabinet a l'origine du message
 * @param dossierId     dossier concerne
 * @param clientId      compte client destinataire (verifie par auth : role CLIENT du cabinet)
 * @param raisonSociale societe du dossier, affichee dans le courriel
 * @param objet         sujet de la requete ou de la demande, titre du ou des documents
 * @param lien          chemin de l'application ou le client retrouve l'element (ex. /mes-requetes)
 */
public record CourrielClientEvenement(
        String type,
        UUID workspaceId,
        UUID dossierId,
        UUID clientId,
        String raisonSociale,
        String objet,
        String lien) {

    /** Echange topic partage des evenements de la plateforme. */
    public static final String ECHANGE = "jurika.events";
    /** Prefixe de cle de routage ; la cle complete est {@code courriel.client.<type>}. */
    public static final String ROUTAGE_PREFIXE = "courriel.client.";

    /** Le cabinet adresse une requete au client (ou lui demande un complement). */
    public static final String TYPE_REQUETE_CABINET = "REQUETE_CABINET";
    /** Le cabinet demande un complement sur une requete deja repondue. */
    public static final String TYPE_COMPLEMENT_REQUETE = "COMPLEMENT_REQUETE";
    /** Un ou plusieurs documents sont mis a la disposition du client. */
    public static final String TYPE_DOCUMENT_DISPONIBLE = "DOCUMENT_DISPONIBLE";
    /** La demande du client a ete traitee. */
    public static final String TYPE_REPONSE_DEMANDE = "REPONSE_DEMANDE";

    public String routage() {
        return ROUTAGE_PREFIXE + type;
    }
}
