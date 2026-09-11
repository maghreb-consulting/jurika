package ma.jurika.ticket.domain.model;

import java.util.List;
import java.util.UUID;

/**
 * Une demarche du referentiel (table {@code demarches_referentiel}).
 *
 * <p>Le referentiel vit en DONNEES, pas en code : il est charge par migration
 * Flyway a partir du guide du cabinet. Aucun {@code switch} ni aucune constante
 * Java ne decrit les 51 lignes -- les corriger doit couter une migration, pas un
 * redeploiement.
 *
 * <p>Les champs peuvent etre {@code null} : le guide comporte des cellules vides
 * (delai, cout, variables alimentees) qui n'ont volontairement pas ete
 * completees. Voir {@code output/lotB/rapport-cabinet.md}.
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
        /**
         * Lot B — nom de la DONNEE du dossier dont la valeur sert de point de
         * depart, quand le delai ne part d'aucun cochage.
         *
         * <p>Deux lignes sont dans ce cas, et ce sont deux obligations legales :
         * la taxe professionnelle et l'affiliation CNSS partent du DEBUT
         * D'ACTIVITE, qui est une date declaree, pas une etape qu'on coche.
         *
         * <p>Exclusive de {@link #delaiReferenceOrdre} : un delai a UN point de
         * depart, et la base le fait respecter.
         */
        String delaiReferenceDonnee,
        /**
         * Lot B — code de la formalite quand elle figure sur DEUX lignes, depot
         * puis retrait. {@code null} pour une ligne unique.
         */
        String formaliteCode,
        /** {@code DEPOT} ou {@code RETRAIT} ; {@code null} pour une ligne unique. */
        FormaliteVolet formaliteVolet,
        List<JustificatifAttendu> justificatifsAttendus) {

    /**
     * Une echeance ne peut etre calculee que si le guide fournit a la fois une
     * duree, son unite et un point de depart mecanisable — le cochage d'une
     * autre ligne, ou une donnee du dossier.
     *
     * <p>Une SEULE ligne du parcours du 9 septembre en est depourvue : la ligne
     * 15, « dans les 30 jours de la signature du contrat », aucune ligne
     * cochable ne portant cette signature. Elle ne levera jamais d'alerte,
     * volontairement, et l'interface le dit.
     *
     * <p>Les lignes 23 (taxe professionnelle) et 32 (CNSS) l'etaient aussi
     * jusqu'au lot B : leur depart, le debut d'activite, n'etait pas une donnee
     * du dossier. Il l'est desormais.
     *
     * <p>« Calculable » ne veut pas dire « calculee ». Une ligne dont le point
     * de depart est pose mais non encore renseigne — etape de reference non
     * cochee, ou date non saisie — ne leve aucune alerte.
     */
    public boolean delaiCalculable() {
        return delaiValeur != null && delaiUnite != null
                && (delaiReferenceOrdre != null || delaiReferenceDonnee != null);
    }

    /** Le delai part-il d'une donnee saisie plutot que du cochage d'une ligne ? */
    public boolean delaiPartDuneDonnee() {
        return delaiReferenceDonnee != null && !delaiReferenceDonnee.isBlank();
    }

    /**
     * Une demarche dont le guide n'attend aucune piece televersable (le
     * « justificatif » y est un etat : « Dossier complet », « Ticket cloture »).
     * Le cochage n'exige alors rien de plus qu'un geste explicite de l'employe.
     */
    public boolean sansJustificatifDocumentaire() {
        return justificatifsAttendus == null || justificatifsAttendus.isEmpty();
    }

    /**
     * Lot B — cette ligne est le RETRAIT d'une formalite dont le depot est une
     * autre ligne. Son attente ne court pas depuis l'ouverture du dossier mais
     * depuis la date du depot : sans ce lien, « depuis quand attendons-nous ? »
     * n'aurait pas de reponse.
     */
    public boolean estRetrait() {
        return formaliteVolet == FormaliteVolet.RETRAIT;
    }

    /** Cette ligne est le DEPOT d'une formalite qui comporte aussi un retrait. */
    public boolean estDepot() {
        return formaliteVolet == FormaliteVolet.DEPOT;
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
