package ma.jurika.ticket.domain.port;

import java.util.List;
import java.util.UUID;

/**
 * Lot L4c (E2, RG-CLO-04, RG-VAR-08) : a la cloture, la fiche societe recoit les
 * identifiants obtenus pendant le parcours (donnees du ticket). Une valeur de la fiche n'est
 * jamais ecrasee : un ecart est signale, la correction reste un geste de l'employe (RG-FIC-04).
 */
public interface FicheCloture {

    record Resultat(List<String> repris, List<String> ecarts) {}

    /**
     * @param auteurId employe qui cloture : auteur de la trace des identifiants reportes
     *                 (Lot L5, E7, RG-FIC-04), provenance FIN_PARCOURS ou EXTRAITE.
     */
    Resultat mettreAJour(UUID workspaceId, UUID ticketId, UUID auteurId);
}
