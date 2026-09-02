package ma.jurika.dataroom.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Modele de la « Fiche client » — carte d'identite juridique d'une societe,
 * assemblee cote serveur puis rendue en PDF (charte JURIKA). Quatre sections :
 *   1. Identifiants de la societe ({@link Identity})
 *   2. Operations juridiques subies ({@link Operation})
 *   3. Documents en vigueur (versions courantes)
 *   4. Historique documentaire ({@link DocumentHistoryEntry})
 *
 * <p>Reserve EMPLOYE (responsable) / SUPERVISEUR / SUPER_ADMIN — jamais le CLIENT.
 */
public final class FicheClientDtos {

    private FicheClientDtos() {}

    /** Section 1 — bloc d'identite brut (valeurs nullables, formatage cote PDF). */
    public record Identity(
            String raisonSociale,
            String formeJuridique,
            String ice,
            String rcNumero,
            String rcTribunal,
            String identifiantFiscal,
            String taxeProfessionnelle,
            String cnss,
            String adresseSiege,
            String ville,
            BigDecimal capitalSocialMad,
            LocalDate dateConstitution,
            String statut) {}

    /**
     * Section 2 — une operation juridique subie par la societe.
     *
     * @param type          type brut du ticket (CREATION, MODIFICATION, ...)
     * @param typeLabel     libelle metier (« Modification statutaire »)
     * @param sousTypeLabel precision (« changement de gerant ») ou null
     * @param dateDebut     date d'ouverture du ticket
     * @param dateFinalisation date de cloture du ticket, ou null si en cours
     * @param dateActe      date juridique de l'acte issue du workflow, ou null
     * @param finalisee     true si l'operation est cloturee
     */
    public record Operation(
            String type,
            String typeLabel,
            String sousTypeLabel,
            String reference,
            Instant dateDebut,
            Instant dateFinalisation,
            LocalDate dateActe,
            boolean finalisee) {}

    /** Section 3 — un document actuellement en vigueur (version courante). */
    public record DocumentEnVigueur(
            String documentType,
            String title,
            Instant dateEtablissement,
            short version) {}

    /**
     * Section 4 — une operation documentaire (ajout ou remplacement).
     *
     * @param action AJOUT | REMPLACEMENT | RESTAURATION
     */
    public record DocumentHistoryEntry(
            Instant date,
            String action,
            String documentType,
            String title,
            short version,
            String auteur,
            String motif) {}

    /**
     * Agregat complet passe au generateur PDF.
     *
     * @param cabinet     papier a en-tete du cabinet (logo + coordonnees + mentions)
     * @param generatedAt horodatage de generation (pied de page)
     */
    public record FicheClientView(
            UUID dossierId,
            ma.jurika.common.pdf.CabinetIdentity cabinet,
            Identity identity,
            List<Operation> operations,
            List<DocumentEnVigueur> documentsEnVigueur,
            List<DocumentHistoryEntry> historiqueDocuments,
            Instant generatedAt) {}
}
