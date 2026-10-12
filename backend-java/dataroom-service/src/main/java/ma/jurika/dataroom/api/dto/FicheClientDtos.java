package ma.jurika.dataroom.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Lot L5 (E7) : fiche client et fiche dossier (cahier des charges 10.2 et 10.3).
 *
 * <ul>
 *   <li>Fiche client (RG-FIC-01 a 05) : UNIQUEMENT les identifiants de la societe, a leur
 *       valeur en vigueur, chacun avec sa provenance ; la taxe professionnelle avec toutes
 *       ses versions. Aucun historique d'operations.</li>
 *   <li>Fiche dossier (RG-FIC-06, 07) : les memes identifiants et toutes les operations
 *       juridiques du dossier (un ticket = une operation).</li>
 * </ul>
 * Reserve a l'employe responsable du dossier et au superviseur ; jamais au client.
 */
public final class FicheClientDtos {

    private FicheClientDtos() {}

    /**
     * Un identifiant de la societe.
     *
     * @param code           DENOMINATION, FORME, CAPITAL, SIEGE, RC, ICE, IF, TP, CNSS
     * @param libelle        intitule affiche
     * @param valeur         valeur en vigueur mise en forme, ou {@code null} si non renseignee
     * @param provenance     d'ou vient la valeur (RG-FIC-03), en clair
     * @param origine        code de provenance : SAISIE, FIN_PARCOURS, EXTRAITE, OUVERTURE,
     *                       PARCOURS, NON_TRACEE, ou {@code null} si la valeur manque
     */
    public record Identifiant(String code, String libelle, String valeur, String provenance, String origine) {}

    /** Une version de la taxe professionnelle (RG-FIC-02). {@code dateEffet} null : non renseignee. */
    public record VersionTp(String numero, LocalDate dateEffet, boolean enVigueur, Instant saisieLe,
                            String saisiePar) {}

    /** Une modification tracee d'un identifiant (RG-FIC-04). */
    public record Modification(String champ, String libelle, String ancienneValeur, String nouvelleValeur,
                               String provenance, String auteur, Instant date) {}

    /**
     * Une operation juridique du dossier (RG-FIC-06).
     *
     * @param dateFin    date de cloture, ou d'annulation, ou {@code null} si en cours
     * @param natureFin  CLOTURE, ANNULATION ou {@code null}
     */
    public record Operation(UUID ticketId, String type, String typeLabel, String sousTypeLabel, String reference,
                            Instant dateOuverture, Instant dateFin, String natureFin, String statut,
                            String statutLabel, String responsable) {}

    /** Fiche client : identifiants, versions de TP, et (a l'ecran seulement) la trace des modifications. */
    public record FicheClientView(UUID dossierId, String cabinet, String denomination,
                                  List<Identifiant> identifiants, List<VersionTp> versionsTp,
                                  List<Modification> modifications, boolean modifiable) {}

    /** Fiche dossier : la fiche client et les operations ; a jour de la derniere cloture (RG-CLO-04). */
    public record FicheDossierView(FicheClientView fiche, List<Operation> operations, Instant derniereCloture) {}
}
