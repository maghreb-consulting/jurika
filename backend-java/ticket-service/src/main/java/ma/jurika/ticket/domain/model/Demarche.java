package ma.jurika.ticket.domain.model;

import java.util.List;
import java.util.UUID;

/**
 * Une demarche du referentiel (table {@code demarches_referentiel}).
 *
 * <p>Le referentiel vit en DONNEES, pas en code : il est charge par migration
 * Flyway a partir du guide du cabinet. Aucun {@code switch} ni aucune constante
 * Java ne decrit les 36 etapes -- les corriger doit couter une migration, pas un
 * redeploiement.
 *
 * <p>Les champs peuvent etre {@code null} : le guide comporte des cellules vides
 * (delai, cout, variables alimentees) qui n'ont volontairement pas ete
 * completees. Voir {@code output/lot1/referentiel-anomalies.md}.
 *
 * @param obligatoire {@code true} pour « O », {@code false} pour « C » (conditionnel)
 */
public record Demarche(
        UUID id,
        String workflowType,
        int ordre,
        String phaseCode,
        String phaseLibelle,
        String libelle,
        TicketStatut statutTicket,
        String acteur,
        String organisme,
        boolean obligatoire,
        String conditionApplication,
        String piecesEntrantes,
        String documentProduit,
        String justificatifsTexte,
        String modeleJurika,
        String delai,
        String coutIndicatif,
        String variablesAlimentees,
        /** Duree du delai legal, ou null si le guide ne permet pas de le calculer. */
        Integer delaiValeur,
        /** Unite du delai. Jamais convertie : « 3 mois » n'est pas « 90 jours ». */
        DelaiUnite delaiUnite,
        /** Ordre de l etape dont la date de cochage sert de point de depart au delai. */
        Integer delaiReferenceOrdre,
        List<JustificatifAttendu> justificatifsAttendus) {

    /**
     * Une echeance ne peut etre calculee que si le guide fournit a la fois une
     * duree, son unite et un point de depart mecanisable. Les etapes 16, 19 et
     * 26 en sont depourvues (« du debut d'activite », « de l'embauche du 1er
     * salarie ») : elles ne leveront jamais d'alerte, volontairement.
     */
    public boolean delaiCalculable() {
        return delaiValeur != null && delaiUnite != null && delaiReferenceOrdre != null;
    }

    /**
     * Une demarche dont le guide n'attend aucune piece televersable (le
     * « justificatif » y est un etat : « Dossier complet », « Ticket cloture »).
     * Le cochage n'exige alors rien de plus qu'un geste explicite de l'employe.
     */
    public boolean sansJustificatifDocumentaire() {
        return justificatifsAttendus == null || justificatifsAttendus.isEmpty();
    }

    /** Les groupes d'alternatives distincts : chaque groupe doit etre satisfait. */
    public List<Integer> groupesAttendus() {
        return justificatifsAttendus.stream()
                .map(JustificatifAttendu::alternativeGroupe)
                .distinct()
                .sorted()
                .toList();
    }
}
