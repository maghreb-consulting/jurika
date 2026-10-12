package ma.jurika.ticket.domain.model;

import ma.jurika.common.pdf.CabinetIdentity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Lot L5 (E6, RG-DEB-02) : donnees de l'etat des debours d'un ticket, toutes lues (rien n'est
 * suppose) : cabinet (donnees du workspace), societe (dossier), ticket, lignes et total.
 *
 * @param referenceDossier reference lisible du dossier (ReferencesLisibles), null si absente
 * @param dateEtat         date d'etablissement imprimee sur l'etat
 */
public record EtatDebours(
        CabinetIdentity cabinet,
        EntrepriseDossier dossier,
        String referenceDossier,
        Ticket ticket,
        LocalDate dateEtat,
        List<Debours> lignes,
        BigDecimal total
) {}
