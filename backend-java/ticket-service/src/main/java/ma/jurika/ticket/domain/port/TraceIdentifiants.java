package ma.jurika.ticket.domain.port;

import java.util.UUID;

/**
 * Lot L5 (E7, RG-FIC-03, RG-FIC-04) : declare, pour la transaction en cours, la provenance
 * des identifiants de la societe qui vont etre ecrits. La trace elle-meme (ancienne et
 * nouvelle valeur, auteur, date) est posee par le declencheur de la migration ticket V41,
 * quel que soit le service qui ecrit.
 */
public interface TraceIdentifiants {

    /** Une saisie ou une correction manuelle de l'employe, sur la fiche. */
    void declarerSaisie(UUID auteurId);
}
